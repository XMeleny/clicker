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
        setTurnScreenOn(true)
        setShowWhenLocked(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked && !keyguard.isKeyguardSecure) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        Log.i("Clicker", "keyguard dismiss succeeded")
                    }

                    override fun onDismissError() {
                        Log.w("Clicker", "keyguard dismiss error")
                    }

                    override fun onDismissCancelled() {
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

        fun beginRequest(): Long = nextRequestId.incrementAndGet().also { activeRequestId = it }

        fun startedAt(requestId: Long): Long? = instance?.takeIf { it.requestId == requestId }?.createdAtElapsed

        fun isAlive(requestId: Long): Boolean = startedAt(requestId) != null

        fun finishRequest(requestId: Long) {
            if (activeRequestId == requestId) activeRequestId = -1L
            instance?.takeIf { it.requestId == requestId }?.finish()
        }

        fun finishIfAlive() {
            activeRequestId = -1L
            instance?.finish()
        }
    }
}
