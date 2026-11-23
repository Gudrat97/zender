package zender.core

import kotlinx.serialization.json.*

const val MODEL = "claude-sonnet-5-5"
const val ORCHESTRATOR_MODEL = "claude-opus-5-5"
const val MAX_TOKENS = 4096
const val MAX_STEPS = 10
const val ORCHESTRATOR_STEPS = 25

/** Транспорт к Claude Messages API. Реализация для Android — в модуле app. */
interface ClaudeApi {
    suspend fun createMessage(body: JsonObject): JsonObject
}

/** Инструмент: схема для модели и обработчик, который выполняет работу на устройстве. */
class Tool(
    val name: String,
    val description: String,
    val schema: JsonObject,
    val handler: suspend (JsonObject) -> String,
) {
    fun apiSchema(): JsonObject = buildJsonObject {
        put("name", name)
        put("description", description)
        put("input_schema", schema)
    }
}

/** Описание агента: роль, системный промпт, модель и инструменты. */
class AgentSpec(
    val name: String,
    val role: String,
    val system: String,
    val tools: List<Tool>,
    val model: String = MODEL,
) {
    /** Оборачивает агента как инструмент, чтобы оркестратор мог его вызвать. */
    fun asTool(loop: AgentLoop): Tool = Tool(
        name = name,
        description = "Субагент «$name»: $role. Передай полную задачу и все нужные данные.",
        schema = obj("task" to str("Полная задача с данными"), required = listOf("task")),
        handler = { input -> loop.run(this, input.str("task")) },
    )
}

/** Цикл «модель → инструмент → результат → модель», как в Python-версии. */
class AgentLoop(
    private val api: ClaudeApi,
    private val onLog: (String) -> Unit = {},
) {
    suspend fun run(agent: AgentSpec, task: String, maxSteps: Int = MAX_STEPS): String {
        val handlers = agent.tools.associateBy { it.name }
        val messages = mutableListOf<JsonElement>(
            buildJsonObject {
                put("role", "user")
                put("content", task)
            }
        )

        repeat(maxSteps) {
            val body = buildJsonObject {
                put("model", agent.model)
                put("max_tokens", MAX_TOKENS)
                put("system", agent.system)
                put("messages", JsonArray(messages.toList()))  // снимок: тело запроса не должно меняться дальше
                if (agent.tools.isNotEmpty()) {
                    put("tools", JsonArray(agent.tools.map { it.apiSchema() }))
                }
            }
            val resp = api.createMessage(body)
            val content = resp["content"]?.jsonArray ?: JsonArray(emptyList())
            messages += buildJsonObject {
                put("role", "assistant")
                put("content", content)
            }

            val stop = resp["stop_reason"]?.jsonPrimitive?.contentOrNull
            if (stop != "tool_use") {
                return content
                    .filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "text" }
                    .joinToString("\n") { it.jsonObject["text"]!!.jsonPrimitive.content }
                    .trim()
            }

            val results = content
                .map { it.jsonObject }
                .filter { it["type"]?.jsonPrimitive?.contentOrNull == "tool_use" }
                .map { block ->
                    val name = block["name"]!!.jsonPrimitive.content
                    val id = block["id"]!!.jsonPrimitive.content
                    val input = block["input"]?.jsonObject ?: JsonObject(emptyMap())
                    onLog("[${agent.name}] → $name")
                    val output = try {
                        val handler = handlers[name] ?: error("нет инструмента $name")
                        handler.handler(input)
                    } catch (e: Exception) {
                        "ОШИБКА: ${e.message ?: e::class.simpleName}"  // модель увидит и исправится
                    }
                    buildJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", id)
                        put("content", output)
                    }
                }
            messages += buildJsonObject {
                put("role", "user")
                put("content", JsonArray(results))
            }
        }
        return "[${agent.name}] остановлен: достигнут лимит шагов ($maxSteps)"
    }
}

// ---- Хелперы для схем и аргументов ----

fun str(description: String? = null) = buildJsonObject {
    put("type", "string")
    description?.let { put("description", it) }
}

fun num(description: String? = null) = buildJsonObject {
    put("type", "number")
    description?.let { put("description", it) }
}

fun bool(description: String? = null) = buildJsonObject {
    put("type", "boolean")
    description?.let { put("description", it) }
}

fun strArray(description: String? = null) = buildJsonObject {
    put("type", "array")
    put("items", buildJsonObject { put("type", "string") })
    description?.let { put("description", it) }
}

fun obj(vararg props: Pair<String, JsonObject>, required: List<String> = emptyList()): JsonObject =
    buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(mapOf(*props)))
        put("required", JsonArray(required.map { JsonPrimitive(it) }))
    }

fun JsonObject.str(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull ?: error("нет параметра «$key»")

fun JsonObject.strOr(key: String, default: String): String =
    this[key]?.jsonPrimitive?.contentOrNull ?: default

fun JsonObject.num(key: String): Double =
    this[key]?.jsonPrimitive?.doubleOrNull ?: error("нет числового параметра «$key»")

fun JsonObject.numOr(key: String, default: Double): Double =
    this[key]?.jsonPrimitive?.doubleOrNull ?: default

fun JsonObject.intOr(key: String, default: Int): Int =
    this[key]?.jsonPrimitive?.intOrNull ?: default

fun JsonObject.boolOr(key: String, default: Boolean): Boolean =
    this[key]?.jsonPrimitive?.booleanOrNull ?: default

fun JsonObject.strList(key: String): List<String> =
    this[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
