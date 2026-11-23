package zender.core

/** Разбор CSV без кавычек внутри полей (для выгрузок трендов и метрик достаточно). */
fun parseCsv(text: String): List<Map<String, String>> {
    val lines = text.removePrefix("﻿").lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return emptyList()
    val header = lines.first().split(",").map { it.trim() }
    return lines.drop(1).map { line ->
        val cells = line.split(",").map { it.trim() }
        header.mapIndexed { i, key -> key to cells.getOrElse(i) { "" } }.toMap()
    }
}

private fun parseNum(v: String?): Double =
    v?.replace("%", "")?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0

/** Топ трендов по росту с фильтром по нише. */
fun loadTrends(csvText: String, niche: String = "", topN: Int = 10): List<Map<String, Any>> {
    val rows = parseCsv(csvText)
        .filter { niche.isEmpty() || (it["niche"] ?: "").lowercase().contains(niche.lowercase()) }
        .map { r ->
            mapOf<String, Any>(
                "name" to (r["name"] ?: ""),
                "type" to (r["type"] ?: ""),
                "growth_pct" to parseNum(r["growth_pct"]),
                "views" to parseNum(r["views"]),
                "niche" to (r["niche"] ?: ""),
            )
        }
        .sortedByDescending { it["growth_pct"] as Double }
    return rows.take(topN.coerceIn(1, 50))
}

data class VideoMetric(
    val video: String,
    val views: Long,
    val engagementPct: Double,
    val completionPct: Double,
)

/** Метрики: вовлечённость = (лайки+комменты+шеры)/просмотры, досмотр = средний просмотр/длительность. */
fun loadMetrics(csvText: String): List<VideoMetric> =
    parseCsv(csvText).mapNotNull { r ->
        val views = parseNum(r["views"])
        if (views <= 0) return@mapNotNull null
        val interactions = parseNum(r["likes"]) + parseNum(r["comments"]) + parseNum(r["shares"])
        val duration = parseNum(r["duration_s"])
        val completion = if (duration > 0) minOf(parseNum(r["avg_watch_s"]) / duration, 1.0) else 0.0
        VideoMetric(
            video = r["video"] ?: "",
            views = views.toLong(),
            engagementPct = Math.round(interactions / views * 10000) / 100.0,
            completionPct = Math.round(completion * 1000) / 10.0,
        )
    }.sortedByDescending { it.views }

data class QaResult(val verdict: String, val issues: List<String>)

/** Проверка готового видео: те же правила, что в tools/qa.py. */
fun qaCheck(
    info: VideoInfo,
    lufs: Double?,
    minSeconds: Double = 15.0,
    maxSeconds: Double = 60.0,
    requireSubtitles: Boolean = false,
    hasSubtitles: Boolean = false,
): QaResult {
    val issues = mutableListOf<String>()
    if (info.durationS !in minSeconds..maxSeconds) {
        issues += "длительность ${info.durationS} c вне диапазона $minSeconds–$maxSeconds c"
    }
    if (info.width > 0 && info.height > 0 && info.height <= info.width) {
        issues += "кадр не вертикальный (нужен 9:16)"
    }
    if (!info.hasAudio) {
        issues += "нет аудиодорожки"
    } else if (lufs != null && lufs !in -16.0..-12.0) {
        issues += "громкость $lufs LUFS, целевой диапазон -16…-12"
    }
    if (requireSubtitles && !hasSubtitles) {
        issues += "требуются субтитры, но файл SRT не найден"
    }
    return QaResult(if (issues.isEmpty()) "PASS" else "FAIL", issues)
}
