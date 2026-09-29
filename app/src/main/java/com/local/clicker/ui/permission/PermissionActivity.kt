package com.local.clicker.ui.permission

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.local.clicker.exec.ClickerAccessibilityService
import com.local.clicker.ui.MainActivity
import com.local.clicker.ui.theme.ClickerTheme

class PermissionActivity : ComponentActivity() {
    private var gate = mutableStateOf(PermissionGate(false, false, false))
    private val notificationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refreshPermissions()
        if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            openNotificationSettings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        gate.value = permissionGate(this)
        setContent {
            ClickerTheme {
                PermissionScreen(
                    gate = gate.value,
                    onNotifications = {
                        if (gate.value.notifications) {
                            openNotificationSettings()
                        } else {
                            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
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

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        })
    }
}

data class PermissionGate(
    val notifications: Boolean,
    val accessibility: Boolean,
    val alarms: Boolean,
) {
    val ready: Boolean = notifications && accessibility
}

fun permissionGate(context: Context): PermissionGate {
    val notifications = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    val component = ComponentName(context, ClickerAccessibilityService::class.java).flattenToString()
    val alarms = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    return PermissionGate(notifications, enabled.split(':').contains(component), alarms)
}

@Preview(showBackground = true)
@Composable
private fun PermissionActivityPreview() {
    ClickerTheme {
        PermissionScreen(PermissionGate(false, false, false), {}, {}, {})
    }
}
