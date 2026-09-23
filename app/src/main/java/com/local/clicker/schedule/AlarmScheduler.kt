package com.local.clicker.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.local.clicker.ClickerApp
import com.local.clicker.exec.ClickerRuntimeService
import com.local.clicker.ui.MainActivity
import kotlinx.coroutines.launch

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canSchedule(): Boolean = alarmManager.canScheduleExactAlarms()

    fun schedule(taskId: Long, at: Long) {
        if (!canSchedule()) return
        val show = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            MainActivity.intent(context, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(at, show),
            operation(taskId, at),
        )
    }

    fun cancel(taskId: Long) {
        val pending = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun operation(taskId: Long, at: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(ClickerRuntimeService.EXTRA_TASK_ID, taskId)
            .putExtra(ClickerRuntimeService.EXTRA_AT, at)
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ClickerRuntimeService.EXTRA_TASK_ID, -1L)
        if (taskId < 0L) return
        val at = intent.getLongExtra(ClickerRuntimeService.EXTRA_AT, -1L)
        val service = Intent(context, ClickerRuntimeService::class.java).apply {
            action = ClickerRuntimeService.ACTION_RUN
            putExtra(ClickerRuntimeService.EXTRA_TASK_ID, taskId)
            putExtra(ClickerRuntimeService.EXTRA_AT, at)
            putExtra(ClickerRuntimeService.EXTRA_SOURCE, ClickerRuntimeService.SOURCE_ALARM)
        }
        context.startForegroundService(service)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as ClickerApp
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                app.graph.reconciler.onBoot()
            } finally {
                pending.finish()
            }
        }
    }
}
