package com.local.clicker.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import com.local.clicker.ClickerApp
import com.local.clicker.domain.SaveResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

class SmsTaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val sender = messages.first().originatingAddress
        if (!SmsTaskCommands.allowedSender(sender) || messages.any { it.originatingAddress != sender }) return
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        val command = SmsTaskCommands.parse(body) ?: return
        val receivedAt = messages.first().timestampMillis
        val now = System.currentTimeMillis()
        if (receivedAt > now + 2 * 60_000L || now - receivedAt > 30 * 60_000L) return
        if (command.scheduledAt <= now) return

        val pending = goAsync()
        val app = context.applicationContext as ClickerApp
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                createTask(app, sender.orEmpty(), body, receivedAt, command)
            } catch (error: Exception) {
                Log.e(TAG, "SMS task creation failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun createTask(
        app: ClickerApp,
        sender: String,
        body: String,
        receivedAt: Long,
        command: SmsTaskCommand,
    ) = gate.withLock {
        val context = app.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$sender\n$receivedAt\n$body".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val recent = prefs.getString(KEY_RECENT, "").orEmpty().lines().filter { it.isNotEmpty() }
        if (digest in recent) return@withLock

        val template = app.graph.templates.loadSelectable(command.templateId) ?: run {
            Log.w(TAG, "SMS template unavailable: ${command.templateId}")
            return@withLock
        }
        val now = System.currentTimeMillis()
        if (command.scheduledAt <= now) return@withLock
        val steps = template.steps.mapIndexed { index, step ->
            step.copyWithKey(-index.toLong() - 1L).withIndex(index)
        }
        val result = app.graph.coordinator.save(
            id = null,
            name = template.name,
            scheduledAt = command.scheduledAt,
            steps = steps,
            now = now,
        )
        if (result is SaveResult.Saved) {
            prefs.edit().putString(KEY_RECENT, (listOf(digest) + recent).take(50).joinToString("\n")).commit()
            Log.i(TAG, "SMS task scheduled: ${result.taskId}")
        } else {
            Log.w(TAG, "SMS task rejected: $result")
        }
    }

    private companion object {
        const val TAG = "ClickerSms"
        const val PREFS = "clicker_sms"
        const val KEY_RECENT = "recent_messages"
        val gate = Mutex()
    }
}
