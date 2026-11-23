package zender.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

/** Фейковый Claude: отдаёт заранее заданные ответы по очереди и запоминает запросы. */
class ScriptedApi(private val replies: MutableList<JsonObject>) : ClaudeApi {
    val requests = mutableListOf<JsonObject>()
    override suspend fun createMessage(body: JsonObject): JsonObject {
        requests += body
        return replies.removeAt(0)
    }
}

fun toolUse(id: String, name: String, input: JsonObject) = buildJsonObject {
    put("stop_reason", "tool_use")
    put("content", buildJsonArray {
        add(buildJsonObject {
            put("type", "tool_use"); put("id", id); put("name", name); put("input", input)
        })
    })
}

fun endTurn(text: String) = buildJsonObject {
    put("stop_reason", "end_turn")
    put("content", buildJsonArray {
        add(buildJsonObject { put("type", "text"); put("text", text) })
    })
}

/** Фейковый Workspace: запоминает вызовы, работает без ffmpeg. */
class FakeWorkspace(
    private val info: VideoInfo = VideoInfo(30.0, 1080, 1920, 30.0, true),
    private val loudness: Double? = -14.0,
) : Workspace {
    val calls = mutableListOf<String>()
    var files = mutableMapOf<String, String>()

    override fun readText(path: String) = files[path] ?: error("нет файла $path")
    override suspend fun probe(path: String): VideoInfo { calls += "probe:$path"; return info }
    override suspend fun cut(path: String, start: Double, end: Double, outName: String): String {
        calls += "cut:$start-$end"; return "/out/$outName"
    }
    override suspend fun concat(paths: List<String>, outName: String) = "/out/$outName"
    override suspend fun toVertical(path: String, outName: String) = "/out/$outName"
    override suspend fun sceneDetect(path: String, threshold: Double) = listOf(2.0, 5.5)
    override suspend fun extractFrame(path: String, timeS: Double, outName: String) = "/out/$outName"
    override suspend fun measureLoudness(path: String): Double =
        loudness ?: throw UnsupportedFeature("громкость")
    override suspend fun normalizeAudio(path: String, outName: String) = "/out/$outName"
    override suspend fun transcribe(path: String, language: String) =
        listOf(Segment(0.0, 4.0, "Сегодня покажу рецепт латте"))
    override fun listMedia(folder: String) = listOf("a.mp4", "b.mp4")
    override fun fileHash(path: String) = if (path == "a.mp4" || path == "b.mp4") "same" else path
    override fun moveFile(src: String, dst: String) = "moved $src -> $dst"
    override fun saveText(name: String, content: String) = "/out/$name"
}

class AgentLoopTest {
    @Test
    fun `loop runs tool then returns final text`() = runBlocking {
        val ws = FakeWorkspace()
        val api = ScriptedApi(mutableListOf(
            toolUse("t1", "probe", buildJsonObject { put("path", "/in/v.mp4") }),
            endTurn("Готово: 30 с, вертикально."),
        ))
        val zender = Zender(api, ws)
        val result = zender.runAgent("editor", "проверь /in/v.mp4")

        assertEquals("Готово: 30 с, вертикально.", result)
        assertEquals(listOf("probe:/in/v.mp4"), ws.calls)
        // Второй запрос должен содержать tool_result для probe
        val secondMessages = api.requests[1]["messages"]!!.jsonArray
        val toolResult = secondMessages.last().jsonObject["content"]!!.jsonArray[0].jsonObject
        assertEquals("tool_result", toolResult["type"]!!.jsonPrimitive.content)
        assertEquals("t1", toolResult["tool_use_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `unknown tool returns error to model instead of crashing`() = runBlocking {
        val api = ScriptedApi(mutableListOf(
            toolUse("t1", "no_such_tool", buildJsonObject {}),
            endTurn("ok"),
        ))
        val result = Zender(api, FakeWorkspace()).runAgent("editor", "x")
        assertEquals("ok", result)
        val content = api.requests[1]["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray[0]
        assertTrue(content.jsonObject["content"]!!.jsonPrimitive.content.startsWith("ОШИБКА"))
    }

    @Test
    fun `step limit stops the loop`() = runBlocking {
        val replies = MutableList(3) { toolUse("t$it", "probe", buildJsonObject { put("path", "/x") }) }
        val api = ScriptedApi(replies)
        val result = AgentLoop(api).run(
            Zender(api, FakeWorkspace()).subagents.first { it.name == "editor" },
            "loop", maxSteps = 3,
        )
        assertTrue(result.contains("лимит шагов"))
    }
}

class RegistryTest {
    @Test
    fun `all ten subagents are present with tools`() {
        val agents = Zender(ScriptedApi(mutableListOf()), FakeWorkspace()).subagents
        assertEquals(
            setOf("trends", "scriptwriter", "editor", "subtitles", "audio",
                "cover", "media_manager", "publisher", "analytics", "qa"),
            agents.map { it.name }.toSet(),
        )
        agents.forEach { assertTrue(it.tools.isNotEmpty(), "нет инструментов у ${it.name}") }
    }

    @Test
    fun `orchestrator gets every subagent as a tool`() = runBlocking {
        val api = ScriptedApi(mutableListOf(endTurn("план готов")))
        Zender(api, FakeWorkspace()).produce("сделай видео")
        val toolNames = api.requests[0]["tools"]!!.jsonArray
            .map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertEquals(10, toolNames.size)
        assertTrue("qa" in toolNames && "editor" in toolNames)
    }

    @Test
    fun `move_file defaults to dry run`() = runBlocking {
        val tool = mediaManagerTools(FakeWorkspace()).first { it.name == "move_file" }
        val out = tool.handler(buildJsonObject { put("src", "a.mp4"); put("dst", "b/a.mp4") })
        assertTrue(out.startsWith("ПЛАН"))
    }

    @Test
    fun `find_duplicates groups equal hashes`() = runBlocking {
        val tool = mediaManagerTools(FakeWorkspace()).first { it.name == "find_duplicates" }
        val out = tool.handler(buildJsonObject {})
        assertEquals("a.mp4 = b.mp4", out)
    }
}

class SrtTest {
    @Test
    fun `cues are timed by weight and lines respect max length`() {
        val srt = buildSrt(listOf(
            Segment(0.0, 4.0, "Сегодня покажу рецепт латте с сиропом карамель"),
            Segment(4.0, 6.0, "Коротко"),
        ), maxChars = 20)
        val lines = srt.lines()
        assertEquals("1", lines[0])
        assertTrue(srt.contains("00:00:04,000 --> 00:00:06,000\nКоротко"))
        srt.lines().filter { it.isNotEmpty() && !it.contains("-->") && it.first().isLetter() }
            .forEach { assertTrue(it.length <= 20, "строка длиннее 20: $it") }
    }

    @Test
    fun `timestamp formats hours minutes milliseconds`() {
        assertEquals("01:02:03,450", timestamp(3723.45))
    }
}

class DataTest {
    private val trendsCsv = """
        name,type,growth_pct,views,niche
        #coffee,hashtag,35%,1000,кофейня
        sound_a,sound,120,500,кофейня
        #gym,hashtag,90,300,спорт
    """.trimIndent()

    @Test
    fun `trends sorted by growth and filtered by niche`() {
        assertEquals(listOf("sound_a", "#coffee"), loadTrends(trendsCsv, "кофе").map { it["name"] })
        assertEquals("sound_a", loadTrends(trendsCsv).first()["name"])
    }

    @Test
    fun `metrics compute engagement and completion`() {
        val csv = """
            video,views,likes,comments,shares,avg_watch_s,duration_s
            v1,1000,50,10,40,15,30
            v2,0,1,1,1,1,1
        """.trimIndent()
        val rows = loadMetrics(csv)
        assertEquals(1, rows.size, "строка с нулевыми просмотрами должна быть отброшена")
        assertEquals(10.0, rows[0].engagementPct)
        assertEquals(50.0, rows[0].completionPct)
    }

    @Test
    fun `csv tolerates BOM`() {
        val rows = parseCsv("﻿name,type\nx,y\n")
        assertEquals("x", rows[0]["name"])
    }
}

class QaTest {
    private val vertical = VideoInfo(30.0, 1080, 1920, 30.0, true)

    @Test
    fun `good video passes`() {
        assertEquals("PASS", qaCheck(vertical, lufs = -14.0).verdict)
    }

    @Test
    fun `reports every failed rule`() {
        val bad = VideoInfo(90.0, 1920, 1080, 30.0, false)
        val r = qaCheck(bad, lufs = null, requireSubtitles = true, hasSubtitles = false)
        assertEquals("FAIL", r.verdict)
        assertEquals(4, r.issues.size)
    }

    @Test
    fun `loud audio fails`() {
        val r = qaCheck(vertical, lufs = -8.0)
        assertEquals("FAIL", r.verdict)
    }

    @Test
    fun `qa tool reports unsupported loudness as note, not failure`() = runBlocking {
        val tool = qaTools(FakeWorkspace(loudness = null)).first()
        val out = tool.handler(buildJsonObject { put("path", "/v.mp4") })
        assertTrue(out.startsWith("verdict=PASS"))
        assertTrue(out.contains("не измерена"))
    }
}
