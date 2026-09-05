package dev.aether.core.net

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest

/** Одна единица загрузки: откуда, куда и с какой контрольной суммой. */
data class DownloadTask(
    val url: String,
    val target: File,
    val sha1: String? = null,
    val size: Long? = null,
    val executable: Boolean = false,
)

/** Прогресс, который UI показывает в диалоге запуска. */
data class Progress(
    val stage: String,
    val done: Long,
    val total: Long,
    val currentFile: String = "",
) {
    val fraction: Float get() = if (total <= 0) 0f else (done.toDouble() / total).toFloat().coerceIn(0f, 1f)
}

object Downloader {

    private const val PARALLELISM = 8

    fun sha1(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Файл считается валидным, только если совпал SHA-1 из манифеста Mojang. */
    fun isValid(task: DownloadTask): Boolean {
        val f = task.target
        if (!f.isFile) return false
        if (task.size != null && f.length() != task.size) return false
        if (task.sha1 != null) return sha1(f).equals(task.sha1, ignoreCase = true)
        return true
    }

    /**
     * Скачивает список файлов параллельно, пропуская уже валидные.
     * Загрузка идёт во временный файл и переименовывается атомарно —
     * прерванный запуск не оставляет «полуфайлов» в кэше.
     */
    suspend fun downloadAll(
        tasks: List<DownloadTask>,
        stage: String,
        onProgress: (Progress) -> Unit = {},
    ) = coroutineScope {
        val pending = tasks.filterNot { isValid(it) }
        if (pending.isEmpty()) {
            onProgress(Progress(stage, 1, 1))
            return@coroutineScope
        }
        val total = pending.size.toLong()
        var done = 0L
        val gate = Semaphore(PARALLELISM)
        val lock = Any()

        pending.map { task ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    download(task)
                    synchronized(lock) {
                        done++
                        onProgress(Progress(stage, done, total, task.target.name))
                    }
                }
            }
        }.awaitAll()
    }

    suspend fun download(task: DownloadTask) {
        Http.assertAllowed(task.url)
        task.target.parentFile?.mkdirs()
        val tmp = File(task.target.parentFile, task.target.name + ".part")

        val response = Http.client.get(task.url)
        check(response.status.value in 200..299) {
            "Не удалось скачать ${task.url}: HTTP ${response.status.value}"
        }
        tmp.writeBytes(response.readRawBytes())

        if (task.sha1 != null) {
            val actual = sha1(tmp)
            if (!actual.equals(task.sha1, ignoreCase = true)) {
                tmp.delete()
                error("Контрольная сумма не совпала для ${task.url}: ожидалось ${task.sha1}, получено $actual")
            }
        }
        task.target.delete()
        check(tmp.renameTo(task.target)) { "Не удалось переместить ${tmp.name}" }
        if (task.executable) task.target.setExecutable(true, false)
    }

}


