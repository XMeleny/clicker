package com.local.clicker.exec

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicLong

class WakeActivity : Activity() {
    private var requestId = -1L
    private var createdAtElapsed = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = intent.getLongExtra(EXTRA_REQUEST_ID, -1L)
        if (requestId != activeRequestId) {
            finish()
            return
        }
        createdAtElapsed = SystemClock.elapsedRealtime()
        instance = this
        diagnostic(requestId, "wake activity created")
        setTurnScreenOn(true)
        setShowWhenLocked(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val keyguard = getSystemService(KeyguardManager::class.java)
        diagnostic(requestId, "keyguard locked=${keyguard.isKeyguardLocked} secure=${keyguard.isKeyguardSecure}")
        if (keyguard.isKeyguardLocked && !keyguard.isKeyguardSecure) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        diagnostic(requestId, "keyguard dismiss succeeded")
                        Log.i("Clicker", "keyguard dismiss succeeded")
                    }

                    override fun onDismissError() {
                        diagnostic(requestId, "keyguard dismiss error")
                        Log.w("Clicker", "keyguard dismiss error")
                    }

                    override fun onDismissCancelled() {
                        diagnostic(requestId, "keyguard dismiss cancelled")
                        Log.w("Clicker", "keyguard dismiss cancelled")
                    }
                },
            )
        }
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_REQUEST_ID = "com.local.clicker.WAKE_REQUEST_ID"

        private val nextRequestId = AtomicLong()
        @Volatile private var activeRequestId = -1L
        @Volatile private var instance: WakeActivity? = null
        @Volatile private var onDiagnostic: ((String) -> Unit)? = null

        fun beginRequest(callback: (String) -> Unit): Long = nextRequestId.incrementAndGet().also {
            onDiagnostic = callback
            activeRequestId = it
        }

        private fun diagnostic(requestId: Long, message: String) {
            if (activeRequestId == requestId) onDiagnostic?.invoke(message)
        }

        fun startedAt(requestId: Long): Long? = instance?.takeIf { it.requestId == requestId }?.createdAtElapsed

        fun isAlive(requestId: Long): Boolean = startedAt(requestId) != null

        fun finishRequest(requestId: Long) {
            if (activeRequestId == requestId) {
                activeRequestId = -1L
                onDiagnostic = null
            }
            instance?.takeIf { it.requestId == requestId }?.finish()
        }

        fun finishIfAlive() {
            activeRequestId = -1L
            onDiagnostic = null
            instance?.finish()
        }
    }
}
