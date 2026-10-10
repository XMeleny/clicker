package com.local.clicker.data

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID

internal class FailureLog(private val directory: File) {
    private val failureDirectory = File(directory, "failure-logs")
    private val active = mutableMapOf<Long, File>()

    @Synchronized
    fun begin(taskId: Long, source: String, trial: Boolean, scheduledAt: Long?) {
        runCatching {
            check(failureDirectory.isDirectory || failureDirectory.mkdirs())
            val file = File(failureDirectory, "${System.currentTimeMillis()}-$taskId-${UUID.randomUUID()}.log")
            active[taskId] = file
            append(file, "START task=$taskId source=$source trial=$trial scheduledAt=$scheduledAt")
        }.onFailure { Log.e("Clicker", "Unable to start failure log", it) }
    }

    @Synchronized
    fun event(taskId: Long, message: String) {
        active[taskId]?.let { file ->
            runCatching { append(file, message) }
                .onFailure { Log.e("Clicker", "Unable to append failure log", it) }
        }
    }

    @Synchronized
    fun finish(taskId: Long, status: String, message: String, trial: Boolean, time: Long) {
        runCatching {
            val file = active.remove(taskId) ?: newFile(taskId, time)
            append(file, "END task=$taskId status=$status trial=$trial reason=$message")
            if (status != "FAILED" && status != "MISSED") file.delete()
        }.onFailure { Log.e("Clicker", "Unable to finish failure log", it) }
    }

    @Synchronized
    fun interrupted(taskId: Long, time: Long) {
        runCatching {
            val file = active.remove(taskId) ?: failureDirectory.listFiles()
                ?.filter { it.isFile && it.name.contains("-$taskId-") && it.extension == "log" }
                ?.filter {
                    val content = it.readText()
                    content.contains("trial=false") && !content.contains(" END task=")
                }
                ?.maxByOrNull { it.lastModified() }
                ?: newFile(taskId, time)
            append(file, "END task=$taskId status=FAILED reason=execution interrupted")
        }.onFailure { Log.e("Clicker", "Unable to record interrupted task", it) }
    }

    private fun newFile(taskId: Long, time: Long): File {
        check(failureDirectory.isDirectory || failureDirectory.mkdirs())
        return File(failureDirectory, "$time-$taskId-${UUID.randomUUID()}.log")
    }

    private fun append(file: File, message: String) {
        val line = "${Instant.now()} ${message.replace('\n', ' ').replace('\r', ' ')}\n"
        FileOutputStream(file, true).use { stream ->
            stream.write(line.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
    }

    @Synchronized
    fun clear() {
        failureDirectory.listFiles()?.filter { it.isFile && it.extension == "log" }?.forEach { it.delete() }
        failureDirectory.delete()
        active.clear()
        File(directory, "failure-logs.txt").delete()
        File(directory, "failure-logs.previous.txt").delete()
    }
}
