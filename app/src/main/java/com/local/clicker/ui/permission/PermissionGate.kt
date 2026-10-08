package com.local.clicker.ui.permission

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.local.clicker.exec.ClickerAccessibilityService

data class PermissionGate(
    val accessibility: Boolean,
    val alarms: Boolean,
) {
    val ready: Boolean = accessibility && alarms
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
    return PermissionGate(enabled, alarms)
}
