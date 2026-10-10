package com.local.clicker.exec

import android.app.KeyguardManager
import android.content.Context
import android.util.Log
import com.local.clicker.domain.ExecutionSnapshot
import com.local.clicker.domain.MAX_STEPS
import com.local.clicker.domain.OpenAppStep
import com.local.clicker.domain.RUN_WALL_MS
import com.local.clicker.domain.TapStep

sealed interface Outcome {
    data class Success(val message: String) : Outcome
    data class Failed(val message: String) : Outcome
    data object Cancelled : Outcome
}

class ExecutionEngine(private val context: Context) {
    suspend fun prepare(snapshot: ExecutionSnapshot, onDiagnostic: (String) -> Unit): Outcome? {
        val service = ClickerAccessibilityService.instance
            ?: return Outcome.Failed("无障碍服务未开启")
        onDiagnostic("accessibility connected")
        if (snapshot.steps.isEmpty() || snapshot.steps.size > MAX_STEPS) {
            return Outcome.Failed("任务步骤不合法")
        }
        for (step in snapshot.steps) {
            if (step is OpenAppStep && context.packageManager.getLaunchIntentForPackage(step.packageName) == null) {
                return Outcome.Failed("应用不存在：${step.packageName}")
            }
        }
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        onDiagnostic("keyguardSecure=${keyguard.isKeyguardSecure}")
        if (keyguard.isKeyguardSecure) return Outcome.Failed("设备设有锁屏密码")
        service.showKeepAwake()
        val wakeFailure = service.wakeAndUnlock(onDiagnostic)
        if (wakeFailure != null) {
            service.hideKeepAwake()
            return Outcome.Failed("无法点亮或解除锁屏：$wakeFailure")
        }
        val live = context.liveScreen()
        val mismatch = snapshot.steps.filterIsInstance<TapStep>().any {
            it.screenWidth != live.width || it.screenHeight != live.height || it.rotation != live.rotation
        }
        if (mismatch) {
            service.hideKeepAwake()
            return Outcome.Failed("屏幕分辨率或方向已变化")
        }
        onDiagnostic("screen ready resolution=${live.width}x${live.height} rotation=${live.rotation}")
        return null
    }

    suspend fun runSteps(
        snapshot: ExecutionSnapshot,
        cancelled: () -> Boolean,
        onDiagnostic: (String) -> Unit,
        onProgress: (index: Int, total: Int, label: String) -> Unit,
    ): Outcome {
        val started = System.currentTimeMillis()
        Log.i(TAG, "start task=${snapshot.taskId} steps=${snapshot.steps.size}")
        try {
            snapshot.steps.forEachIndexed { index, step ->
                if (cancelled()) return Outcome.Cancelled
                if (System.currentTimeMillis() - started > RUN_WALL_MS) {
                    return Outcome.Failed("执行超过 10 分钟")
                }
                val service = ClickerAccessibilityService.instance
                    ?: return Outcome.Failed("无障碍服务已断开")
                if (!service.screenReady()) {
                    onDiagnostic("screen not ready before step ${index + 1} ${service.screenState()}")
                    return Outcome.Failed(stepMessage(index, "屏幕已熄灭或已锁定"))
                }
                val label = step.shortLabel(context)
                onProgress(index, snapshot.steps.size, label)
                onDiagnostic("step ${index + 1}/${snapshot.steps.size} start $label")
                Log.i(TAG, "step ${index + 1} $label")
                val error = service.runStep(step, cancelled, started)
                if (error != null) onDiagnostic("step ${index + 1} error=$error")
                if (error == ClickerAccessibilityService.CANCELLED) return Outcome.Cancelled
                if (error != null) return Outcome.Failed(stepMessage(index, error))
                if (step is OpenAppStep) {
                    val foreground = service.waitUntilForeground(step.packageName, cancelled)
                    if (foreground != null) onDiagnostic("step ${index + 1} foreground=$foreground")
                    if (foreground == ClickerAccessibilityService.CANCELLED) return Outcome.Cancelled
                    if (foreground != null) return Outcome.Failed(stepMessage(index, foreground))
                }
                onDiagnostic("step ${index + 1} completed")
            }
            return Outcome.Success("已完成 ${snapshot.steps.size} 步")
        } finally {
            ClickerAccessibilityService.instance?.hideKeepAwake()
            WakeActivity.finishIfAlive()
        }
    }

    private fun stepMessage(index: Int, reason: String) = "第${index + 1}步：$reason"

    companion object {
        const val TAG = "Clicker"
    }
}
