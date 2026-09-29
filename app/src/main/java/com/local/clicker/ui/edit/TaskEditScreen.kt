package com.local.clicker.ui.edit

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.clicker.domain.DraftOpenApp
import com.local.clicker.domain.DraftStep
import com.local.clicker.domain.DraftTap
import com.local.clicker.domain.DraftWait
import com.local.clicker.domain.StepKind
import com.local.clicker.domain.TaskStatus
import com.local.clicker.domain.TapPoint
import com.local.clicker.domain.WAIT_MAX_MS
import com.local.clicker.domain.WAIT_MIN_MS
import com.local.clicker.domain.isTerminal
import com.local.clicker.exec.ClickerRuntimeService
import com.local.clicker.ui.AppTitleBar
import com.local.clicker.ui.TitleBarAction
import com.local.clicker.ui.list.statusLabel
import com.local.clicker.ui.theme.ClickerTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(taskId: Long, onBack: () -> Unit, onReplaced: (Long) -> Unit) {
    val context = LocalContext.current
    val vm: TaskEditViewModel = viewModel(
        key = "edit-$taskId",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TaskEditViewModel(context.applicationContext as android.app.Application, taskId) as T
            }
        },
    )
    val ui by vm.ui.collectAsState()
    val busy by ClickerRuntimeService.busy.collectAsState()
    TaskEditContent(
        ui = ui,
        busy = busy,
        actions = EditActions(
            save = { vm.save(onBack, onReplaced) },
            setName = vm::setName,
            setScheduledAt = vm::setScheduledAt,
            toggle = vm::toggle,
            move = vm::moveKey,
            remove = vm::remove,
            duplicate = vm::duplicate,
            trial = { vm.trial(it, onReplaced) },
            requestPick = vm::requestPick,
            updateStep = vm::updateStep,
            add = vm::add,
            insertBelow = vm::insertBelow,
            changeType = vm::changeType,
            runNow = { vm.runNow(onReplaced) },
            cancelSchedule = { vm.cancelSchedule(onBack) },
            copy = { vm.copy(onReplaced) },
        ),
    )
}

private data class EditActions(
    val save: () -> Unit,
    val setName: (String) -> Unit,
    val setScheduledAt: (Long?) -> Unit,
    val toggle: (Long) -> Unit,
    val move: (Long, Int) -> Unit,
    val remove: (Int) -> Unit,
    val duplicate: (Int) -> Unit,
    val trial: (Int) -> Unit,
    val requestPick: (Long) -> Unit,
    val updateStep: (DraftStep) -> Unit,
    val add: (StepKind) -> Unit,
    val insertBelow: (Int, StepKind) -> Unit,
    val changeType: (Int, StepKind) -> Unit,
    val runNow: () -> Unit,
    val cancelSchedule: () -> Unit,
    val copy: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditContent(ui: EditUi, busy: Boolean, actions: EditActions) {
    var kindTarget by remember { mutableStateOf<KindTarget?>(null) }
    var changeTarget by remember { mutableStateOf<Int?>(null) }
    var pickingApp by remember { mutableStateOf<Long?>(null) }
    var showDate by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        AppTitleBar(
            title = if (ui.taskId == 0L) "新任务" else ui.name.ifBlank { "任务" },
            action = if (ui.readOnly) null else TitleBarAction("保存", actions.save),
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(statusLabel(ui.status))
            ui.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            ui.limitHint?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            ui.trialBanner?.let { Text("试运行：$it") }
            OutlinedTextField(
                value = ui.name,
                onValueChange = actions.setName,
                label = { Text("名称") },
                enabled = !ui.readOnly,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(ui.scheduledAt?.let { "计划时间 ${formatWhen(it)}" } ?: "计划时间：仅手动")
            if (!ui.readOnly) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showDate = true }) { Text("选择时间") }
                    TextButton(onClick = { actions.setScheduledAt(null) }) { Text("清除时间") }
                }
            }
            Text("脚本按从上到下的顺序执行")
            ui.steps.forEachIndexed { index, step ->
                StepCard(
                    index = index,
                    step = step,
                    apps = ui.apps,
                    expanded = ui.expandedKey == step.key,
                    readOnly = ui.readOnly,
                    invalid = ui.showInvalid && step.invalid(ui.apps),
                    isFirst = index == 0,
                    isLast = index == ui.steps.lastIndex,
                    busy = busy,
                    saved = ui.taskId > 0L,
                    onToggle = { actions.toggle(step.key) },
                    onMove = { delta -> actions.move(step.key, delta) },
                    onRemove = { actions.remove(index) },
                    onDuplicate = { actions.duplicate(index) },
                    onInsert = { kindTarget = KindTarget.Insert(index) },
                    onChangeType = { changeTarget = index },
                    onTrial = { actions.trial(index) },
                    onPick = { actions.requestPick(step.key) },
                    onPickApp = { pickingApp = step.key },
                    onWait = { text ->
                        val parsed = text.toLongOrNull()
                        actions.updateStep(DraftWait(step.key, step.orderIndex, parsed))
                    },
                )
            }
            if (!ui.readOnly) {
                Button(
                    onClick = { kindTarget = KindTarget.Append },
                    enabled = ui.steps.size < 30,
                ) { Text("添加步骤") }
            }
            if (ui.taskId > 0L && !ui.readOnly && ScriptLooksExecutable(ui)) {
                Button(onClick = actions.runNow, enabled = !busy) { Text("立即执行") }
            }
            if (ui.status == TaskStatus.SCHEDULED) {
                TextButton(onClick = actions.cancelSchedule) { Text("取消计划") }
            }
            if (ui.status.isTerminal()) {
                TextButton(onClick = actions.copy) { Text("复制为新任务") }
            }
        }
    }
    kindTarget?.let { target ->
        KindDialog(
            onPick = { kind ->
                when (target) {
                    KindTarget.Append -> actions.add(kind)
                    is KindTarget.Insert -> actions.insertBelow(target.index, kind)
                }
                kindTarget = null
            },
            onDismiss = { kindTarget = null },
        )
    }
    changeTarget?.let { index ->
        AlertDialog(
            onDismissRequest = { changeTarget = null },
            title = { Text("更换类型") },
            text = { Text("更换后这一步已填写的内容会丢掉。") },
            confirmButton = {
                Column {
                    StepKind.entries.forEach { kind ->
                        TextButton(onClick = {
                            actions.changeType(index, kind)
                            changeTarget = null
                        }) { Text(kind.label()) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { changeTarget = null }) { Text("留下") } },
        )
    }
    pickingApp?.let { key ->
        ModalBottomSheet(onDismissRequest = { pickingApp = null }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.apps.forEach { app ->
                    TextButton(onClick = {
                        actions.updateStep(DraftOpenApp(key, 0, app.packageName))
                        pickingApp = null
                    }) { Text(app.label) }
                }
            }
        }
    }
    if (showDate) {
        ScheduleDialog(
            initial = ui.scheduledAt,
            onConfirm = {
                actions.setScheduledAt(it)
                showDate = false
            },
            onDismiss = { showDate = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskEditScreenPreview() {
    val sampleActions = EditActions(
        save = {},
        setName = {},
        setScheduledAt = {},
        toggle = {},
        move = { _, _ -> },
        remove = {},
        duplicate = {},
        trial = {},
        requestPick = {},
        updateStep = {},
        add = {},
        insertBelow = { _, _ -> },
        changeType = { _, _ -> },
        runNow = {},
        cancelSchedule = {},
        copy = {},
    )
    ClickerTheme {
        TaskEditContent(
            ui = EditUi(
                taskId = 1,
                name = "早晨签到",
                apps = listOf(AppOption("日历", "com.example.calendar")),
                steps = listOf(
                    DraftOpenApp(1, 0, "com.example.calendar"),
                    DraftWait(2, 1, 1_000),
                    DraftTap(3, 2, TapPoint(420, 860, 1080, 2400, 0)),
                ),
                expandedKey = 2,
            ),
            busy = false,
            actions = sampleActions,
        )
    }
}

private sealed interface KindTarget {
    data object Append : KindTarget
    data class Insert(val index: Int) : KindTarget
}

private fun ScriptLooksExecutable(ui: EditUi): Boolean =
    ui.steps.isNotEmpty() && ui.steps.none { it.invalid(ui.apps) }

@Composable
private fun StepCard(
    index: Int,
    step: DraftStep,
    apps: List<AppOption>,
    expanded: Boolean,
    readOnly: Boolean,
    invalid: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    busy: Boolean,
    saved: Boolean,
    onToggle: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onDuplicate: () -> Unit,
    onInsert: () -> Unit,
    onChangeType: () -> Unit,
    onTrial: () -> Unit,
    onPick: () -> Unit,
    onPickApp: () -> Unit,
    onWait: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var accum by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val border = if (invalid || dragging) MaterialTheme.colorScheme.error else Color.Transparent
    Card(Modifier.fillMaxWidth().border(1.dp, border)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text(
                    "${index + 1}",
                    modifier = Modifier.pointerInput(step.key, readOnly) {
                        if (readOnly) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragging = true },
                            onDragEnd = { dragging = false; accum = 0f },
                            onDragCancel = { dragging = false; accum = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                accum += amount.y
                                if (accum > threshold) {
                                    onMove(1)
                                    accum = 0f
                                } else if (accum < -threshold) {
                                    onMove(-1)
                                    accum = 0f
                                }
                            },
                        )
                    },
                )
                if (!readOnly) Icon(Icons.Default.DragHandle, contentDescription = "拖拽排序")
                Text(
                    step.summary(apps),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                TextButton(onClick = onToggle) { Text(if (expanded) "收起" else "展开") }
                if (!readOnly) {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "步骤菜单") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!isFirst) DropdownMenuItem(text = { Text("上移") }, onClick = { menu = false; onMove(-1) })
                        if (!isLast) DropdownMenuItem(text = { Text("下移") }, onClick = { menu = false; onMove(1) })
                        DropdownMenuItem(text = { Text("复制") }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onRemove() })
                        DropdownMenuItem(text = { Text("在下方插入") }, onClick = { menu = false; onInsert() })
                        DropdownMenuItem(text = { Text("更换类型") }, onClick = { menu = false; onChangeType() })
                    }
                }
            }
            if (expanded) {
                when (step) {
                    is DraftOpenApp -> TextButton(onClick = onPickApp, enabled = !readOnly) {
                        Text(step.summary(apps))
                    }
                    is DraftWait -> {
                        OutlinedTextField(
                            value = step.waitMs?.toString().orEmpty(),
                            onValueChange = onWait,
                            enabled = !readOnly,
                            label = { Text("等待毫秒") },
                            supportingText = { Text("允许 $WAIT_MIN_MS～$WAIT_MAX_MS") },
                        )
                    }
                    is DraftTap -> {
                        val point = step.tap
                        Text(
                            if (point == null) "点击（未取点）" else "点击 (${point.x}, ${point.y})  ${point.screenWidth}×${point.screenHeight} / ${point.rotation}",
                        )
                        if (!readOnly) TextButton(onClick = onPick) { Text("选取坐标") }
                    }
                }
                if (saved && step.isComplete() && !busy) {
                    TextButton(onClick = onTrial) { Text("试运行这一步") }
                }
            }
        }
    }
}

@Composable
private fun KindDialog(onPick: (StepKind) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择动作") },
        text = {
            Column {
                StepKind.entries.forEach { kind ->
                    TextButton(onClick = { onPick(kind) }) { Text(kind.label()) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun StepKind.label(): String = when (this) {
    StepKind.OPEN_APP -> "打开应用"
    StepKind.WAIT -> "等待"
    StepKind.TAP -> "点击"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDialog(initial: Long?, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val seed = initial?.let { Instant.ofEpochMilli(it).atZone(zone) }
    var date by remember {
        mutableStateOf(seed?.toLocalDate() ?: LocalDate.now().plusDays(1))
    }
    var time by remember { mutableStateOf(seed?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.of(8, 0)) }
    var page by remember { mutableStateOf(0) }
    if (page == 0) {
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    page = 1
                }) { Text("下一步") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        ) { androidx.compose.material3.DatePicker(state = state) }
    } else {
        val state = androidx.compose.material3.rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("选择时间") },
            text = { androidx.compose.material3.TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val local = date.atTime(state.hour, state.minute).atZone(zone)
                    onConfirm(local.toInstant().toEpochMilli())
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )
    }
}
