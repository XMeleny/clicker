package com.local.clicker.ui.list

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.clicker.ClickerApp
import com.local.clicker.ui.AppTitleBar
import com.local.clicker.ui.TitleBarAction
import com.local.clicker.domain.TaskStatus
import com.local.clicker.domain.TaskSummary
import com.local.clicker.exec.ClickerRuntimeService
import com.local.clicker.ui.edit.formatWhen
import com.local.clicker.ui.theme.ClickerTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class TaskListViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as ClickerApp).graph
    val tasks = combine(graph.repository.observeTasks(), graph.reconciler.showBootBanner) { list, banner ->
        ListUi(list, banner)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUi(emptyList(), false))

    private val busy = ClickerRuntimeService.busy

    fun isBusy(): Boolean = busy.value

    fun runNow(id: Long) {
        val context = getApplication<Application>()
        context.startForegroundService(
            ClickerRuntimeService.runIntent(context, id, ClickerRuntimeService.SOURCE_MANUAL),
        )
    }

    fun cancel(id: Long) {
        viewModelScope.launch { graph.coordinator.cancelSchedule(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { graph.coordinator.delete(id) }
    }

}

data class ListUi(val tasks: List<TaskSummary>, val bootBanner: Boolean)

@Composable
fun TaskListScreen(onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    val vm: TaskListViewModel = viewModel()
    val ui by vm.tasks.collectAsState()
    val busy by ClickerRuntimeService.busy.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.tasks) {
        while (true) {
            val next = ui.tasks.mapNotNull { it.scheduledAt }.filter { it > now }.minOrNull()
            delay(((next ?: now + 60_000L) - now + 1L).coerceAtLeast(1L))
            now = System.currentTimeMillis()
        }
    }
    TaskListContent(
        ui = ui,
        busy = busy,
        now = now,
        onOpen = onOpen,
        onCreate = onCreate,
        onRun = vm::runNow,
        onCancel = vm::cancel,
        onDelete = vm::delete,
    )
}

@Composable
internal fun TaskListContent(
    ui: ListUi,
    busy: Boolean,
    now: Long = System.currentTimeMillis(),
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onRun: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    Scaffold(
        topBar = { AppTitleBar(title = "点击器", action = TitleBarAction("新建", onCreate)) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.bootBanner) {
                item {
                    Text("计划可能在重启后丢失，请检查自启动。打开本应用后，仍在未来的计划会重新登记。")
                }
            }
            items(ui.tasks, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    busy = busy,
                    now = now,
                    onOpen = { onOpen(task.id) },
                    onRun = { onRun(task.id) },
                    onCancel = { onCancel(task.id) },
                    onDelete = { pendingDelete = task.id },
                )
            }
        }
    }
    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除任务") },
            text = { Text("删除后闹钟也会取消。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(id)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("留下") } },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskListScreenPreview() {
    ClickerTheme {
        TaskListContent(
            ui = ListUi(
                tasks = listOf(
                    TaskSummary(1, "早晨签到", null, TaskStatus.DRAFT, 0, null, true, listOf("打开 日历", "等待 1000ms")),
                    TaskSummary(2, "午间提醒", 1_800_000_000_000L, TaskStatus.SCHEDULED, 0, null, true, listOf("点击 (420, 860)")),
                    TaskSummary(3, "上次执行", 1_700_000_000_000L, TaskStatus.SUCCESS, 0, "已完成", true, listOf("等待 500ms")),
                ),
                bootBanner = false,
            ),
            busy = false,
            now = 1_750_000_000_000L,
            onOpen = {},
            onCreate = {},
            onRun = {},
            onCancel = {},
            onDelete = {},
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskSummary,
    busy: Boolean,
    now: Long,
    onOpen: () -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val color = if (task.scheduledAt != null && task.scheduledAt > now) Color(0xFFE5F3E9) else Color(0xFFE9EAEC)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(task.name, style = MaterialTheme.typography.titleMedium)
            Text(task.scheduledAt?.let(::formatWhen) ?: "未设置时间", style = MaterialTheme.typography.bodyMedium)
            if (task.steps.isEmpty()) {
                Text("暂无步骤", style = MaterialTheme.typography.bodySmall)
            } else {
                task.steps.forEachIndexed { index, step ->
                    Text("${index + 1}. $step", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val canRun = task.executable &&
                    (task.status == TaskStatus.DRAFT || task.status == TaskStatus.SCHEDULED) &&
                    !busy
                if (canRun) Button(onClick = onRun) { Text("立即执行") }
                if (task.status == TaskStatus.SCHEDULED) TextButton(onClick = onCancel) { Text("取消计划") }
                if (task.status != TaskStatus.RUNNING) TextButton(onClick = onOpen) { Text("编辑") }
                if (task.status != TaskStatus.RUNNING) TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

fun statusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.DRAFT -> "草稿"
    TaskStatus.SCHEDULED -> "已计划"
    TaskStatus.RUNNING -> "执行中"
    TaskStatus.SUCCESS -> "成功"
    TaskStatus.FAILED -> "失败"
    TaskStatus.MISSED -> "错过"
    TaskStatus.CANCELLED -> "已取消"
}
