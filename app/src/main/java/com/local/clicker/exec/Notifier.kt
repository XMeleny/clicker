package com.local.clicker.exec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.local.clicker.R

object Notifier {
    const val CHANNEL_RUN = "clicker_run"
    const val ID_RUN = 10

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUN, "执行中", NotificationManager.IMPORTANCE_LOW),
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
}
