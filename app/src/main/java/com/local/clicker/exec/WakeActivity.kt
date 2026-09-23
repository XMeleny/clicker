package com.local.clicker.exec

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import android.view.WindowManager

class WakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        setTurnScreenOn(true)
        setShowWhenLocked(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked && !keyguard.isKeyguardSecure) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = Unit
                    override fun onDismissError() = Unit
                    override fun onDismissCancelled() = Unit
                },
            )
        }
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var instance: WakeActivity? = null

        fun isAlive(): Boolean = instance != null

        fun finishIfAlive() {
            instance?.finish()
        }
    }
}
