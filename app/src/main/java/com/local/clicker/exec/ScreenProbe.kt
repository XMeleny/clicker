package com.local.clicker.exec

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import com.local.clicker.domain.OpenAppStep
import com.local.clicker.domain.Step
import com.local.clicker.domain.TapStep
import com.local.clicker.domain.WaitStep

data class LiveScreen(val width: Int, val height: Int, val rotation: Int)

fun Context.liveScreen(): LiveScreen {
    val window = getSystemService(WindowManager::class.java)
    val bounds = window.currentWindowMetrics.bounds
    val rotation = getSystemService(DisplayManager::class.java)
        .getDisplay(Display.DEFAULT_DISPLAY)
        .rotation
    return LiveScreen(bounds.width(), bounds.height(), rotation)
}

fun Step.shortLabel(context: Context): String = when (this) {
    is OpenAppStep -> {
        val label = runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
        "打开 $label"
    }
    is WaitStep -> "等待 ${waitMs}ms"
    is TapStep -> "点击 ($x,$y)"
}
