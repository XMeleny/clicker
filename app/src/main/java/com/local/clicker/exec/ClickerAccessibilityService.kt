package com.local.clicker.exec

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.local.clicker.domain.OPEN_APP_TIMEOUT_MS
import com.local.clicker.domain.OpenAppStep
import com.local.clicker.domain.RUN_WALL_MS
import com.local.clicker.domain.Step
import com.local.clicker.domain.TAP_HOLD_MS
import com.local.clicker.domain.TapPoint
import com.local.clicker.domain.TapStep
import com.local.clicker.domain.WAKE_START_TIMEOUT_MS
import com.local.clicker.domain.WAKE_TIMEOUT_MS
import com.local.clicker.domain.WaitStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ClickerAccessibilityService : AccessibilityService() {
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private var keepAwake: View? = null
    private var pickView: View? = null
    private var pickKey: Long? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        clearOverlays()
        if (instance == this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clearOverlays()
        if (pickKey != null) {
            PickerBus.tryFail("无障碍服务已断开")
        }
        if (instance == this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (pickView != null && event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            cancelPick()
            return true
        }
        return false
    }

    fun showKeepAwake() {
        if (keepAwake != null) return
        val view = View(this)
        val params = overlayParams(1, 1).apply {
            flags = flags or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            alpha = 0f
            gravity = Gravity.START or Gravity.TOP
        }
        windowManager.addView(view, params)
        keepAwake = view
    }

    fun hideKeepAwake() {
        keepAwake?.let { runCatching { windowManager.removeView(it) } }
        keepAwake = null
    }

    suspend fun wakeAndUnlock(onDiagnostic: (String) -> Unit): String? {
        val requestId = WakeActivity.beginRequest(onDiagnostic)
        onDiagnostic("wake requested ${screenState()}")
        try {
            val intent = Intent(this, WakeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(WakeActivity.EXTRA_REQUEST_ID, requestId)
            try {
                startActivity(intent)
                onDiagnostic("wake activity launch requested")
            } catch (error: RuntimeException) {
                onDiagnostic("wake activity launch failed ${error.javaClass.simpleName} ${screenState()}")
                Log.w("Clicker", "wake activity launch failed", error)
                return "唤醒界面启动失败：${error.javaClass.simpleName}"
            }

            val startDeadline = SystemClock.elapsedRealtime() + WAKE_START_TIMEOUT_MS
            while (WakeActivity.startedAt(requestId) == null && SystemClock.elapsedRealtime() < startDeadline) delay(40)
            val startedAt = WakeActivity.startedAt(requestId)
            if (startedAt == null || startedAt > startDeadline) {
                onDiagnostic("wake activity start timeout ${screenState()}")
                Log.w("Clicker", "wake activity start timeout")
                return "唤醒界面启动超时（${screenState()}）"
            }
            onDiagnostic("wake activity started ${screenState()}")

            val readyDeadline = startedAt + WAKE_TIMEOUT_MS
            while (SystemClock.elapsedRealtime() < readyDeadline) {
                if (!screenReady()) {
                    delay(40)
                    continue
                }
                WakeActivity.finishRequest(requestId)
                val gone = SystemClock.elapsedRealtime() + 800
                while (WakeActivity.isAlive(requestId) && SystemClock.elapsedRealtime() < gone) delay(20)
                if (WakeActivity.isAlive(requestId)) {
                    onDiagnostic("wake activity finish timeout ${screenState()}")
                    Log.w("Clicker", "wake activity finish timeout")
                    return "唤醒界面退出超时（${screenState()}）"
                }
                if (!screenReady()) {
                    onDiagnostic("screen not ready after wake ${screenState()}")
                    Log.w("Clicker", "screen not ready after wake activity finished")
                    return "唤醒界面退出后屏幕未就绪（${screenState()}）"
                }
                onDiagnostic("wake complete ${screenState()}")
                return null
            }
            val state = screenState()
            onDiagnostic("wake ready timeout $state")
            Log.w("Clicker", "wake ready timeout $state")
            return "屏幕或解锁等待超时（$state）"
        } finally {
            WakeActivity.finishRequest(requestId)
        }
    }

    fun screenReady(): Boolean {
        val power = getSystemService(android.os.PowerManager::class.java)
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        return power.isInteractive && !keyguard.isKeyguardLocked
    }

    fun screenState(): String {
        val power = getSystemService(android.os.PowerManager::class.java)
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        return "interactive=${power.isInteractive}, keyguardLocked=${keyguard.isKeyguardLocked}"
    }

    suspend fun runStep(step: Step, cancelled: () -> Boolean, startedAt: Long): String? {
        if (instance == null) return "无障碍服务已断开"
        return when (step) {
            is OpenAppStep -> openApp(step.packageName, cancelled)
            is WaitStep -> waitFor(step.waitMs, cancelled, startedAt)
            is TapStep -> tap(step.x, step.y)
        }
    }

    fun beginPick(stepKey: Long) {
        pickKey = stepKey
        showBubble()
    }

    private fun openApp(packageName: String, cancelled: () -> Boolean): String? {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: return "打开应用失败：$packageName"
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(launch)
        } catch (_: Exception) {
            return "打开应用失败：$packageName"
        }
        return null
    }

    suspend fun waitUntilForeground(packageName: String, cancelled: () -> Boolean): String? {
        val deadline = SystemClock.elapsedRealtime() + OPEN_APP_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (cancelled()) return CANCELLED
            if (rootInActiveWindow?.packageName?.toString() == packageName) return null
            delay(100)
        }
        return "打开应用失败：$packageName"
    }

    private suspend fun waitFor(waitMs: Long, cancelled: () -> Boolean, startedAt: Long): String? {
        val end = SystemClock.elapsedRealtime() + waitMs
        while (SystemClock.elapsedRealtime() < end) {
            if (cancelled()) return CANCELLED
            if (System.currentTimeMillis() - startedAt > RUN_WALL_MS) return "执行超过 10 分钟"
            if (!screenReady()) return "屏幕已熄灭或已锁定"
            delay(50)
        }
        return null
    }

    private suspend fun tap(x: Int, y: Int): String? {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_HOLD_MS))
            .build()
        val ok = suspendCancellableCoroutine { cont ->
            val sent = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null,
            )
            if (!sent && cont.isActive) cont.resume(false)
        }
        return if (ok) null else "点击失败：($x,$y)"
    }

    private fun showBubble() {
        removePickView()
        val density = resources.displayMetrics.density
        val buttonSize = (48 * density).toInt()
        val pick = TextView(this).apply {
            text = "取点"
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1F6FEB.toInt())
            setOnClickListener { showCatcher() }
        }
        val cancel = TextView(this).apply {
            text = "取消"
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC333333.toInt())
            setOnClickListener { cancelPick() }
        }
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(pick, LinearLayout.LayoutParams(buttonSize, buttonSize))
            addView(cancel, LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
                marginStart = (4 * density).toInt()
            })
        }
        val params = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        windowManager.addView(bubble, params)
        pickView = bubble
    }

    private fun showCatcher() {
        removePickView()
        val root = FrameLayout(this).apply {
            setBackgroundColor(0x66000000)
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    deliverPick(event.rawX.toInt(), event.rawY.toInt())
                }
                true
            }
        }
        val hint = TextView(this).apply {
            text = "点击要记录的位置"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
        }
        root.addView(
            hint,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = (48 * resources.displayMetrics.density).toInt() },
        )
        val cancel = TextView(this).apply {
            text = "取消"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC333333.toInt())
            setPadding(48, 24, 48, 24)
            setOnClickListener { cancelPick() }
        }
        root.addView(
            cancel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply { bottomMargin = (32 * resources.displayMetrics.density).toInt() },
        )
        val params = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
        windowManager.addView(root, params)
        pickView = root
    }

    private fun deliverPick(x: Int, y: Int) {
        val key = pickKey
        val screen = liveScreen()
        removePickView()
        pickKey = null
        if (key != null) {
            PickerBus.tryPoint(key, TapPoint(x, y, screen.width, screen.height, screen.rotation))
        }
        bringSelfToFront()
    }

    private fun cancelPick() {
        pickKey = null
        removePickView()
        bringSelfToFront()
    }

    private fun bringSelfToFront() {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(launch)
    }

    private fun removePickView() {
        pickView?.let { runCatching { windowManager.removeView(it) } }
        pickView = null
    }

    private fun clearOverlays() {
        removePickView()
        hideKeepAwake()
    }

    private fun overlayParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    )

    companion object {
        const val CANCELLED = "__cancelled__"
        @Volatile var instance: ClickerAccessibilityService? = null
    }
}

object PickerBus {
    sealed interface Event {
        data class Point(val key: Long, val point: TapPoint) : Event
        data class Failed(val message: String) : Event
    }

    val events = kotlinx.coroutines.flow.MutableSharedFlow<Event>(extraBufferCapacity = 4)

    fun tryPoint(key: Long, point: TapPoint) {
        events.tryEmit(Event.Point(key, point))
    }

    fun tryFail(message: String) {
        events.tryEmit(Event.Failed(message))
    }
}
