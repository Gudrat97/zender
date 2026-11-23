package zender.core

/** Сборка SRT из сегментов распознавания. Логика та же, что в tools/speech.py. */
fun buildSrt(segments: List<Segment>, maxChars: Int = 42): String {
    val cues = mutableListOf<Triple<Double, Double, String>>()
    for (seg in segments) {
        val chunks = chunkText(seg.text, maxChars)
        if (chunks.isEmpty()) continue
        val total = chunks.sumOf { it.length }.toDouble()
        var t = seg.start
        val dur = seg.end - seg.start
        for (c in chunks) {
            val d = dur * c.length / total
            cues += Triple(t, t + d, c)
            t += d
        }
    }
    return buildString {
        cues.forEachIndexed { i, (a, b, text) ->
            append(i + 1).append('\n')
            append("${timestamp(a)} --> ${timestamp(b)}\n")
            append(text).append("\n\n")
        }
    }
}

internal fun chunkText(text: String, maxChars: Int): List<String> {
    val lines = mutableListOf<String>()
    var cur = ""
    for (word in text.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
        if (cur.isNotEmpty() && cur.length + word.length + 1 > maxChars) {
            lines += cur
            cur = word
        } else {
            cur = if (cur.isEmpty()) word else "$cur $word"
        }
    }
    if (cur.isNotEmpty()) lines += cur
    return lines
}

internal fun timestamp(sec: Double): String {
    val ms = Math.round(sec * 1000)
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    val s = (ms % 60_000) / 1000
    val rest = ms % 1000
    return "%02d:%02d:%02d,%03d".format(h, m, s, rest)
}
