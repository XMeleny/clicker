package com.local.clicker.ui.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
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
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AppTitleBar(title = "权限")
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PermissionSwitch("无障碍", gate.accessibility, onAccessibility)
            HorizontalDivider()
            PermissionSwitch("闹钟和提醒", gate.alarms, onAlarms)
        }
    }
}

@Composable
private fun PermissionSwitch(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = { onClick() },
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}
