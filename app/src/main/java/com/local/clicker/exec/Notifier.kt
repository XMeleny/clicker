package com.local.clicker.exec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.local.clicker.R
import com.local.clicker.ui.MainActivity

object Notifier {
    const val CHANNEL_RUN = "clicker_run"
    const val CHANNEL_RESULT = "clicker_result"
    const val ID_RUN = 10
    const val ID_RESULT = 11

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUN, "执行中", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "执行结果", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun running(context: Context, title: String, text: String): Notification {
        val stop = PendingIntent.getService(
            context,
            1,
            Intent(context, ClickerRuntimeService::class.java).setAction(ClickerRuntimeService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_RUN)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .addAction(0, "停止", stop)
            .build()
    }

    fun result(context: Context, taskId: Long, title: String, text: String) {
        val open = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            MainActivity.intent(context, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_RESULT, notification)
    }
}
