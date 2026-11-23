package zender.core

import kotlinx.serialization.json.*

private fun tool(name: String, description: String, schema: JsonObject, handler: suspend (JsonObject) -> String) =
    Tool(name, description, schema, handler)

/** Инструменты монтажа. */
fun editorTools(ws: Workspace): List<Tool> = listOf(
    tool("probe", "Метаданные видео: длительность, размер кадра, fps, есть ли звук",
        obj("path" to str(), required = listOf("path"))) { a ->
        ws.probe(a.str("path")).toString()
    },
    tool("scene_detect", "Моменты смены сцены в секундах (threshold 0.05–0.95, выше — меньше сцен)",
        obj("path" to str(), "threshold" to num(), required = listOf("path"))) { a ->
        ws.sceneDetect(a.str("path"), a.numOr("threshold", 0.4)).joinToString(", ")
    },
    tool("cut", "Вырезать фрагмент по времени (секунды) и сохранить в outputs",
        obj("path" to str(), "start" to num(), "end" to num(), "out_name" to str(),
            required = listOf("path", "start", "end", "out_name"))) { a ->
        ws.cut(a.str("path"), a.num("start"), a.num("end"), a.str("out_name"))
    },
    tool("concat", "Склеить несколько видео в одно",
        obj("paths" to strArray(), "out_name" to str(), required = listOf("paths", "out_name"))) { a ->
        ws.concat(a.strList("paths"), a.str("out_name"))
    },
    tool("to_vertical", "Привести видео к 9:16 (1080x1920) с обрезкой по центру",
        obj("path" to str(), "out_name" to str(), required = listOf("path", "out_name"))) { a ->
        ws.toVertical(a.str("path"), a.str("out_name"))
    },
)

fun subtitleTools(ws: Workspace): List<Tool> = listOf(
    tool("transcribe_to_srt", "Распознать речь в видео и сохранить субтитры SRT в outputs",
        obj("path" to str(), "out_name" to str(), "language" to str(),
            required = listOf("path", "out_name"))) { a ->
        val segments = ws.transcribe(a.str("path"), a.strOr("language", "ru"))
        ws.saveText(a.str("out_name"), buildSrt(segments))
    },
)

fun audioTools(ws: Workspace): List<Tool> = listOf(
    tool("measure_loudness", "Интегральная громкость дорожки в LUFS",
        obj("path" to str(), required = listOf("path"))) { a ->
        "${ws.measureLoudness(a.str("path"))} LUFS"
    },
    tool("normalize_audio", "Выровнять громкость под TikTok (около -14 LUFS)",
        obj("path" to str(), "out_name" to str(), required = listOf("path", "out_name"))) { a ->
        ws.normalizeAudio(a.str("path"), a.str("out_name"))
    },
)

fun coverTools(ws: Workspace): List<Tool> = listOf(
    tool("extract_frame", "Сохранить кадр из видео в указанной секунде (.jpg или .png)",
        obj("path" to str(), "time_s" to num(), "out_name" to str(),
            required = listOf("path", "time_s", "out_name"))) { a ->
        ws.extractFrame(a.str("path"), a.num("time_s"), a.str("out_name"))
    },
)

fun mediaManagerTools(ws: Workspace): List<Tool> = listOf(
    tool("scan_media", "Список медиафайлов в папке медиатеки",
        obj("folder" to str())) { a ->
        ws.listMedia(a.strOr("folder", "")).joinToString("\n")
    },
    tool("find_duplicates", "Найти одинаковые файлы в медиатеке по SHA-256",
        obj("folder" to str())) { a ->
        val files = ws.listMedia(a.strOr("folder", ""))
        val groups = files.groupBy { ws.fileHash(it) }.values.filter { it.size > 1 }
        if (groups.isEmpty()) "дубликатов нет" else groups.joinToString("\n") { it.joinToString(" = ") }
    },
    tool("move_file", "Переместить или переименовать файл в медиатеке. По умолчанию только план (dry_run)",
        obj("src" to str(), "dst" to str(), "dry_run" to bool(),
            required = listOf("src", "dst"))) { a ->
        if (a.boolOr("dry_run", true)) {
            "ПЛАН (dry_run, файл не тронут): ${a.str("src")} -> ${a.str("dst")}"
        } else {
            ws.moveFile(a.str("src"), a.str("dst"))
        }
    },
)

fun analyticsTools(ws: Workspace): List<Tool> = listOf(
    tool("load_metrics", "Прочитать CSV метрик и посчитать итоги (путь к файлу внутри приложения)",
        obj("path" to str(), required = listOf("path"))) { a ->
        val rows = loadMetrics(ws.readText(a.str("path")))
        if (rows.isEmpty()) "нет валидных строк" else {
            val avgE = rows.map { it.engagementPct }.average()
            val avgC = rows.map { it.completionPct }.average()
            "count=${rows.size}, avg_engagement=${"%.2f".format(avgE)}%, avg_completion=${"%.1f".format(avgC)}%\n" +
                "top: ${rows.take(3)}\nbottom: ${rows.takeLast(3)}"
        }
    },
)

fun trendTools(ws: Workspace): List<Tool> = listOf(
    tool("load_trends", "Прочитать CSV трендов (name,type,growth_pct,views,niche) и вернуть топ по росту",
        obj("path" to str(), "niche" to str(), "top_n" to num(), required = listOf("path"))) { a ->
        loadTrends(ws.readText(a.str("path")), a.strOr("niche", ""), a.intOr("top_n", 10)).joinToString("\n")
    },
)

fun qaTools(ws: Workspace): List<Tool> = listOf(
    tool("qa_check", "Проверить готовое видео: длительность, вертикальность, звук, громкость, субтитры",
        obj("path" to str(), "min_seconds" to num(), "max_seconds" to num(),
            "require_subtitles" to bool(), "subtitles_path" to str(),
            required = listOf("path"))) { a ->
        val info = ws.probe(a.str("path"))
        val lufs = try {
            ws.measureLoudness(a.str("path"))
        } catch (e: UnsupportedFeature) {
            null
        }
        val subs = a.strOr("subtitles_path", "")
        val result = qaCheck(
            info, lufs,
            minSeconds = a.numOr("min_seconds", 15.0),
            maxSeconds = a.numOr("max_seconds", 60.0),
            requireSubtitles = a.boolOr("require_subtitles", false),
            hasSubtitles = subs.isNotEmpty(),
        )
        buildString {
            append("verdict=${result.verdict}")
            result.issues.forEach { append("\n- $it") }
            if (lufs == null) append("\n(громкость не измерена на этом устройстве)")
            append("\ninfo=$info")
        }
    },
)

fun publisherTools(ws: Workspace): List<Tool> = listOf(
    tool("save_text", "Сохранить текстовый результат в outputs",
        obj("name" to str(), "content" to str(), required = listOf("name", "content"))) { a ->
        ws.saveText(a.str("name"), a.str("content"))
    },
)
