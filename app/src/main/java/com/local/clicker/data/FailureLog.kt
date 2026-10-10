package com.local.clicker.data

import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class FailureLog(private val directory: File) {
    private val failureDirectory = File(directory, "failure-logs")

    @Synchronized
    fun record(taskId: Long, status: String, message: String, trial: Boolean, time: Long) {
        runCatching {
            check(failureDirectory.isDirectory || failureDirectory.mkdirs())
            val file = File(failureDirectory, "$time-$taskId-${UUID.randomUUID()}.log")
            val timestamp = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val start = time - LOG_WINDOW_MS
            val header = "$timestamp task=$taskId status=$status trial=$trial\n" +
                "reason=${message.replace('\n', ' ').replace('\r', ' ')}\n" +
                "app UID logcat since ${Instant.ofEpochMilli(start)}:\n"
            FileOutputStream(file).use { stream ->
                stream.write(header.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            val result = captureLogcat(file, start)
            FileOutputStream(file, true).use { stream ->
                stream.write("\n$result\n".toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
        }.onFailure { Log.e("Clicker", "Unable to persist failure log", it) }
    }

    private fun captureLogcat(file: File, start: Long): String {
        val since = "${start / 1000}.${(start % 1000).toString().padStart(3, '0')}"
        return runCatching {
            val process = ProcessBuilder(
                "logcat", "-d", "-b", "main", "-v", "epoch", "-t", since,
                "--uid=${Process.myUid()}",
            ).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(file))
                .start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.SECONDS)
                "logcat capture timed out"
            } else {
                "logcat exit=${process.exitValue()}"
            }
        }.getOrElse { "logcat capture failed: ${it.javaClass.simpleName}" }
    }

    @Synchronized
    fun clear() {
        failureDirectory.listFiles()?.filter { it.isFile && it.extension == "log" }?.forEach { it.delete() }
        failureDirectory.delete()
        File(directory, "failure-logs.txt").delete()
        File(directory, "failure-logs.previous.txt").delete()
    }

    companion object {
        private const val LOG_WINDOW_MS = 60_000L
    }
}
