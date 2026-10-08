package com.local.clicker.ui.permission

import android.accessibilityservice.AccessibilityServiceInfo
import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import com.local.clicker.exec.ClickerAccessibilityService

data class PermissionGate(
    val accessibility: Boolean,
    val alarms: Boolean,
    val sms: Boolean,
) {
    val ready: Boolean = accessibility && alarms && sms
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
    val sms = context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    return PermissionGate(enabled, alarms, sms)
}
