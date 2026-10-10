package com.local.clicker.data

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal class FailureLog(private val directory: File) {
    private val file = File(directory, "failure-logs.txt")
    private val previous = File(directory, "failure-logs.previous.txt")

    @Synchronized
    fun record(taskId: Long, status: String, message: String, trial: Boolean, time: Long) {
        runCatching {
            val timestamp = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val line = "$timestamp task=$taskId status=$status trial=$trial " +
                message.replace('\n', ' ').replace('\r', ' ') + "\n"
            if (file.length() + line.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
                previous.delete()
                if (!file.renameTo(previous)) file.delete()
            }
            FileOutputStream(file, true).use { stream ->
                stream.write(line.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
        }.onFailure { Log.e("Clicker", "Unable to persist failure log", it) }
    }

    @Synchronized
    fun clear() {
        file.delete()
        previous.delete()
    }

    companion object {
        private const val MAX_BYTES = 256 * 1024
    }
}
