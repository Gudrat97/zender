package zender.core

/**
 * Всё, что агенты делают с файлами и медиа. Реализация для Android — AndroidWorkspace в модуле app.
 * Методы, которые на устройстве пока не поддержаны, бросают UnsupportedOperationException
 * с понятным сообщением, и агент сообщает об этом пользователю.
 */
interface Workspace {
    fun readText(path: String): String
    suspend fun probe(path: String): VideoInfo
    suspend fun cut(path: String, start: Double, end: Double, outName: String): String
    suspend fun concat(paths: List<String>, outName: String): String
    suspend fun toVertical(path: String, outName: String): String
    suspend fun sceneDetect(path: String, threshold: Double): List<Double>
    suspend fun extractFrame(path: String, timeS: Double, outName: String): String
    suspend fun measureLoudness(path: String): Double
    suspend fun normalizeAudio(path: String, outName: String): String
    suspend fun transcribe(path: String, language: String): List<Segment>
    fun listMedia(folder: String): List<String>
    fun fileHash(path: String): String
    fun moveFile(src: String, dst: String): String
    fun saveText(name: String, content: String): String
}

data class VideoInfo(
    val durationS: Double,
    val width: Int,
    val height: Int,
    val fps: Double,
    val hasAudio: Boolean,
)

data class Segment(val start: Double, val end: Double, val text: String)

class UnsupportedFeature(feature: String) :
    UnsupportedOperationException("$feature пока не поддерживается на Android-версии")
