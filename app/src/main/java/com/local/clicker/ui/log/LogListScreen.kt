package com.local.clicker.ui.log

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.clicker.ClickerApp
import com.local.clicker.domain.ExecutionLog
import com.local.clicker.domain.TaskStatus
import com.local.clicker.ui.AppTitleBar
import com.local.clicker.ui.TitleBarAction
import com.local.clicker.ui.list.statusLabel
import com.local.clicker.ui.theme.ClickerTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogListViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as ClickerApp).graph.repository
    val logs = repository.observeLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clear() {
        viewModelScope.launch { repository.clearLogs() }
    }
}

@Composable
fun LogListScreen() {
    val vm: LogListViewModel = viewModel()
    val logs by vm.logs.collectAsState()
    LogListContent(logs, vm::clear)
}

@Composable
private fun LogListContent(logs: List<ExecutionLog>, onClear: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            AppTitleBar(
                title = "日志",
                action = if (logs.isEmpty()) null else TitleBarAction("清空", { confirmClear = true }),
            )
        },
    ) { padding ->
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(logs, key = { it.id }) { log ->
                    LogRow(log)
                    HorizontalDivider()
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空日志") },
            text = { Text("清空后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    confirmClear = false
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun LogRow(log: ExecutionLog) {
    val statusColor: Color = when (log.status) {
        TaskStatus.FAILED, TaskStatus.MISSED -> MaterialTheme.colorScheme.error
        TaskStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(log.taskName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(statusLabel(log.status), color = statusColor, style = MaterialTheme.typography.labelLarge)
        }
        Text(
            formatLogTime(log.createdAt) + if (log.trial) " · 试运行" else "",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(log.message, style = MaterialTheme.typography.bodyMedium)
    }
}

private val logTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun formatLogTime(time: Long): String =
    Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(logTimeFormat)

@Preview(showBackground = true)
@Composable
private fun LogListScreenPreview() {
    ClickerTheme {
        LogListContent(
            listOf(
                ExecutionLog(1, 1, "早晨签到", TaskStatus.SUCCESS, "已完成", 1_800_000_000_000L, false),
                ExecutionLog(2, 2, "午间任务", TaskStatus.FAILED, "未找到目标应用", 1_799_999_000_000L, true),
            ),
            {},
        )
    }
}
