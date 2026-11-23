package zender.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import zender.core.ClaudeApi
import java.net.HttpURLConnection
import java.net.URL

/** HTTP-клиент Claude Messages API на стандартном HttpURLConnection, без лишних зависимостей. */
class ClaudeHttpApi(private val apiKey: String) : ClaudeApi {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createMessage(body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                // Ключ в сообщение об ошибке не попадает: только код и начало ответа API
                error("API ответил $code: ${text.take(300)}")
            }
            json.parseToJsonElement(text).jsonObject
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    }
}
