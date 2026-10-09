package com.local.clicker.ui.permission

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.local.clicker.exec.ClickerAccessibilityService

data class PermissionGate(
    val accessibility: Boolean,
    val alarms: Boolean,
    val sms: Boolean,
) {
    val ready: Boolean = accessibility && alarms && sms
}

fun permissionGate(context: Context): PermissionGate {
    val service = ComponentName(context, ClickerAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    )?.split(':')?.any { ComponentName.unflattenFromString(it) == service } == true
    val alarms = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    val sms = context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    return PermissionGate(enabled, alarms, sms)
}
