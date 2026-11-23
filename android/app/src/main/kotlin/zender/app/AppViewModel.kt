package zender.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zender.core.Zender
import java.io.File

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val store = ApiKeyStore(application)
    private val workspace = AndroidWorkspace(application)

    var goal by mutableStateOf("")
    var agentName by mutableStateOf("")          // пусто = полный цикл через оркестратора
    var running by mutableStateOf(false)
    var hasKey by mutableStateOf(store.get() != null)
        private set
    val inputs = mutableStateListOf<String>()     // пути к файлам внутри приложения
    val log = mutableStateListOf<String>()

    /** Копирует видео из системного выбора во внутреннюю папку: агенты работают с обычными путями. */
    fun addVideo(uri: Uri) {
        viewModelScope.launch {
            try {
                val target = withContext(Dispatchers.IO) {
                    val dir = File(getApplication<Application>().cacheDir, "inputs").apply { mkdirs() }
                    val name = (uri.lastPathSegment ?: "video").substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val file = File(dir, "${System.currentTimeMillis()}_$name")
                    getApplication<Application>().contentResolver.openInputStream(uri)!!.use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                    file
                }
                inputs += target.absolutePath
                append("Добавлено: ${target.name}")
            } catch (e: Exception) {
                append("Не удалось добавить файл: ${e.message}")
            }
        }
    }

    fun saveKey(key: String) {
        if (key.isBlank()) return
        store.set(key)
        hasKey = true
        append("Ключ сохранён на устройстве")
    }

    fun run() {
        if (running) return
        val key = store.get()
        if (key == null) {
            append("Сначала введите ключ API в настройках ниже")
            return
        }
        if (goal.isBlank()) {
            append("Опишите задачу")
            return
        }
        running = true
        viewModelScope.launch {
            try {
                val zender = Zender(ClaudeHttpApi(key), workspace, onLog = { append(it) })
                val fullGoal = buildString {
                    append(goal.trim())
                    if (inputs.isNotEmpty()) {
                        append("\n\nВходные файлы:\n")
                        inputs.forEach { append("- $it\n") }
                    }
                }
                val result = if (agentName.isBlank()) {
                    zender.produce(fullGoal)
                } else {
                    zender.runAgent(agentName.trim(), fullGoal)
                }
                append("Итог:\n$result")
            } catch (e: Exception) {
                append("Ошибка: ${e.message ?: e::class.simpleName}")
            } finally {
                running = false
            }
        }
    }

    private fun append(line: String) {
        log += line
        if (log.size > 300) log.removeRange(0, log.size - 300)
    }
}
