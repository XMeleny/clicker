package com.local.clicker.ui.permission

import android.app.AlarmManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import com.local.clicker.exec.ClickerAccessibilityService
import com.local.clicker.ui.MainActivity
import com.local.clicker.ui.theme.ClickerTheme

private const val MOCK_ACCESSIBILITY_FOR_TASK_SETUP = true

class PermissionActivity : ComponentActivity() {
    private var gate = mutableStateOf(PermissionGate(false, false))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        gate.value = permissionGate(this)
        setContent {
            ClickerTheme {
                PermissionScreen(
                    gate = gate.value,
                    onAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onAlarms = {
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        gate.value = permissionGate(this)
        if (gate.value.ready && !isFinishing) {
            startActivity(MainActivity.intent(this, intent.getLongExtra(MainActivity.EXTRA_TASK_ID, 0L)))
            finish()
        }
    }
}

data class PermissionGate(
    val accessibility: Boolean,
    val alarms: Boolean,
    val mockAccessibility: Boolean = false,
) {
    val ready: Boolean = accessibility || mockAccessibility
}

fun permissionGate(context: Context): PermissionGate {
    val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
    val enabled = accessibilityManager
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { service ->
            service.resolveInfo.serviceInfo.let { info ->
                info.packageName == context.packageName && info.name == ClickerAccessibilityService::class.java.name
            }
        }
    val alarms = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    // Temporary debug-only gate bypass for testing task setup without reauthorizing accessibility.
    val mockAccessibility = MOCK_ACCESSIBILITY_FOR_TASK_SETUP &&
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    return PermissionGate(enabled, alarms, mockAccessibility)
}

@Preview(showBackground = true)
@Composable
private fun PermissionActivityPreview() {
    ClickerTheme {
        PermissionScreen(PermissionGate(false, false), {}, {})
    }
}
