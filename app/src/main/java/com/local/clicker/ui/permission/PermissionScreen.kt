package com.local.clicker.ui.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.local.clicker.ui.PermissionGate

@Composable
fun PermissionScreen(gate: PermissionGate) {
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("使用前需要打开这些权限", style = MaterialTheme.typography.headlineSmall)
        Text("通知用来显示正在执行的步骤，以及成功、失败或错过的结果。")
        Button(
            onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
            enabled = !gate.notifications,
        ) { Text(if (gate.notifications) "通知已允许" else "允许通知") }
        Text(
            "无障碍服务用来在你设定的时刻打开应用，并点击你事先选好的坐标。它不会读取通知，也不会上传屏幕内容。" +
                "这个安装包需要先在系统设置里允许受限制的设置：打开本应用的应用信息，在右上角菜单里允许受限制的设置，然后再到无障碍设置里打开「点击器」。",
        )
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            })
        }) { Text("打开应用信息") }
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            enabled = !gate.accessibility,
        ) { Text(if (gate.accessibility) "无障碍已开启" else "打开无障碍设置") }
        Text("闹钟和提醒未授予时，仍可以编辑任务并立即执行，但不能保存为到点执行。开机自启动需要在系统设置里允许，否则重启后计划可能不会自动恢复。若到点不准，可在系统设置里对本应用关闭电池限制。")
        Button(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                },
            )
        }) { Text("打开闹钟和提醒") }
    }
}
