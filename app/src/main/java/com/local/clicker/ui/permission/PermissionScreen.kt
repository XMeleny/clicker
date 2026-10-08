package com.local.clicker.ui.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.local.clicker.ui.AppTitleBar

@Composable
fun PermissionScreen(
    gate: PermissionGate,
    onAccessibility: () -> Unit,
    onAlarms: () -> Unit,
    onSms: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AppTitleBar(title = "权限")
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PermissionRow("无障碍", "用于执行任务中的点击、滑动等屏幕操作。", gate.accessibility, onAccessibility)
            HorizontalDivider()
            PermissionRow("闹钟和提醒", "用于在设定时间准时启动任务。", gate.alarms, onAlarms)
            HorizontalDivider()
            PermissionRow(
                title = "短信建任务",
                description = "仅接收尾号 4211 的号码发送的指令。格式：CLICKER 模板编号 YYYY-MM-DD HH:mm\n例如：CLICKER 12 2026-10-01 08:00",
                checked = gate.sms,
                onClick = onSms,
            )
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = { onClick() },
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
