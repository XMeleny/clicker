package com.local.clicker.ui.edit

import android.widget.NumberPicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
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
import com.local.clicker.domain.formatWaitSeconds
import com.local.clicker.domain.parseWaitSeconds
import com.local.clicker.exec.ClickerRuntimeService
import com.local.clicker.ui.theme.ClickerTheme
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    taskId: Long,
    onBack: () -> Unit,
    onReplaced: (Long) -> Unit,
    onAlarmPermissionRequired: () -> Unit,
) {
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
    if (!ui.loaded) return
    val busy by ClickerRuntimeService.busy.collectAsState()
    TaskEditContent(
        ui = ui,
        busy = busy,
        actions = EditActions(
            save = { vm.save(onBack, onReplaced, onAlarmPermissionRequired) },
            setName = vm::setName,
            setScheduledAt = vm::setScheduledAt,
            move = vm::moveKey,
            remove = vm::remove,
            requestPick = vm::requestPick,
            updateStep = vm::updateStep,
            add = vm::add,
            selectTemplate = vm::selectTemplate,
            runNow = { vm.runNow(onReplaced) },
            cancelSchedule = { vm.cancelSchedule(onBack) },
        ),
    )
}

private data class EditActions(
    val save: () -> Unit,
    val setName: (String) -> Unit,
    val setScheduledAt: (Long?) -> Unit,
    val move: (Long, Int) -> Unit,
    val remove: (Int) -> Unit,
    val requestPick: (Long) -> Unit,
    val updateStep: (DraftStep) -> Unit,
    val add: (StepKind) -> Unit,
    val selectTemplate: (Long) -> Unit,
    val runNow: () -> Unit,
    val cancelSchedule: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditContent(ui: EditUi, busy: Boolean, actions: EditActions) {
    var showAdd by remember { mutableStateOf(false) }
    var pickingApp by remember { mutableStateOf<Long?>(null) }
    var editingWait by remember { mutableStateOf<DraftWait?>(null) }
    var showScheduleOptions by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = ui.name,
                onValueChange = actions.setName,
                placeholder = { Text("任务名") },
                enabled = !ui.readOnly,
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.titleLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                ),
            )
            if (!ui.readOnly) TextButton(onClick = actions.save) { Text("保存") }
        }
        HorizontalDivider()
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ui.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            ui.limitHint?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            ui.trialBanner?.let { Text("试运行：$it") }
            Text(
                ui.scheduledAt?.let { "计划时间 ${formatWhen(it)}" } ?: "计划时间：仅手动",
                modifier = Modifier.fillMaxWidth().clickable(enabled = !ui.readOnly) { showScheduleOptions = true }
                    .padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("脚本按从上到下的顺序执行", modifier = Modifier.weight(1f))
                if (!ui.readOnly) IconButton(
                    onClick = { showAdd = true },
                    enabled = ui.steps.size < 30,
                ) { Icon(Icons.Default.AddCircleOutline, contentDescription = "添加步骤") }
            }
            ui.steps.forEachIndexed { index, step ->
                StepRow(
                    step = step,
                    apps = ui.apps,
                    readOnly = ui.readOnly,
                    invalid = ui.showInvalid && step.invalid(ui.apps),
                    onMove = { delta -> actions.move(step.key, delta) },
                    onRemove = { actions.remove(index) },
                    onEdit = {
                        when (step) {
                            is DraftOpenApp -> pickingApp = step.key
                            is DraftWait -> editingWait = step
                            is DraftTap -> actions.requestPick(step.key)
                        }
                    },
                )
            }
            if (ui.taskId > 0L && !ui.readOnly && ScriptLooksExecutable(ui)) {
                Button(onClick = actions.runNow, enabled = !busy) { Text("立即执行") }
            }
            if (ui.status == TaskStatus.SCHEDULED) {
                TextButton(onClick = actions.cancelSchedule) { Text("取消计划") }
            }
        }
    }
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加步骤") },
            text = {
                Column {
                    TextButton(onClick = { showAdd = false; showTemplates = true }) { Text("选择动作模板") }
                    StepKind.entries.forEach { kind ->
                        TextButton(onClick = { actions.add(kind); showAdd = false }) { Text(kind.label()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } },
        )
    }
    if (showScheduleOptions) {
        AlertDialog(
            onDismissRequest = { showScheduleOptions = false },
            title = { Text("计划时间") },
            text = {
                Column {
                    TextButton(onClick = { showScheduleOptions = false; showDate = true }) { Text("选择日期") }
                    TextButton(onClick = { showScheduleOptions = false; showTime = true }) { Text("选择时间") }
                    TextButton(onClick = { actions.setScheduledAt(null); showScheduleOptions = false }) { Text("清除时间") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showScheduleOptions = false }) { Text("取消") } },
        )
    }
    if (showTemplates) {
        ModalBottomSheet(onDismissRequest = { showTemplates = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("选择动作模板", style = MaterialTheme.typography.titleMedium)
                if (ui.templates.isEmpty()) Text("暂无动作模板")
                ui.templates.forEach { template ->
                    TextButton(
                        onClick = {
                            actions.selectTemplate(template.id)
                            showTemplates = false
                        },
                        enabled = template.selectable,
                    ) {
                        Text("${template.name} · ${template.steps.size} 步" + if (template.selectable) "" else "（未完成）")
                    }
                }
            }
        }
    }
    editingWait?.let { step ->
        WaitDialog(
            step = step,
            onConfirm = { actions.updateStep(DraftWait(step.key, step.orderIndex, it)); editingWait = null },
            onDismiss = { editingWait = null },
        )
    }
    pickingApp?.let { key ->
        AlertDialog(
            onDismissRequest = { pickingApp = null },
            title = { Text("选择应用") },
            text = {
                Column(Modifier.height(320.dp).verticalScroll(rememberScrollState())) {
                    ui.apps.forEach { app ->
                        TextButton(onClick = {
                            actions.updateStep(DraftOpenApp(key, 0, app.packageName))
                            pickingApp = null
                        }) { Text(app.label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickingApp = null }) { Text("取消") } },
        )
    }
    if (showDate) {
        ScheduleDateDialog(
            initial = ui.scheduledAt,
            onConfirm = {
                actions.setScheduledAt(it)
                showDate = false
            },
            onDismiss = { showDate = false },
        )
    }
    if (showTime) {
        ScheduleTimeDialog(
            initial = ui.scheduledAt,
            onConfirm = {
                actions.setScheduledAt(it)
                showTime = false
            },
            onDismiss = { showTime = false },
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
        move = { _, _ -> },
        remove = {},
        requestPick = {},
        updateStep = {},
        add = {},
        selectTemplate = {},
        runNow = {},
        cancelSchedule = {},
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

private fun ScriptLooksExecutable(ui: EditUi): Boolean =
    ui.steps.isNotEmpty() && ui.steps.none { it.invalid(ui.apps) }

@Composable
private fun StepRow(
    step: DraftStep,
    apps: List<AppOption>,
    readOnly: Boolean,
    invalid: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var accum by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).pointerInput(step.key, readOnly) {
            if (readOnly) return@pointerInput
            detectDragGesturesAfterLongPress(
                onDragStart = { dragging = true },
                onDragEnd = { dragging = false; accum = 0f },
                onDragCancel = { dragging = false; accum = 0f },
                onDrag = { change, amount ->
                    change.consume()
                    accum += amount.y
                    if (accum > threshold) { onMove(1); accum = 0f }
                    if (accum < -threshold) { onMove(-1); accum = 0f }
                },
            )
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            step.summary(apps),
            modifier = Modifier.weight(1f).clickable(enabled = !readOnly, onClick = onEdit),
            color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!readOnly) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "长按拖拽排序",
                modifier = Modifier.size(40.dp),
                tint = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, contentDescription = "步骤选项") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
private fun WaitDialog(step: DraftWait, onConfirm: (Long?) -> Unit, onDismiss: () -> Unit) {
    var seconds by remember(step.key) { mutableStateOf(step.waitMs?.let(::formatWaitSeconds).orEmpty()) }
    val waitMs = parseWaitSeconds(seconds)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("等待时间") },
        text = {
            OutlinedTextField(
                value = seconds,
                onValueChange = { seconds = it },
                label = { Text("秒") },
                supportingText = { Text("允许 ${formatWaitSeconds(WAIT_MIN_MS)}～${formatWaitSeconds(WAIT_MAX_MS)} 秒") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(waitMs) },
                enabled = waitMs != null && waitMs in WAIT_MIN_MS..WAIT_MAX_MS,
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

internal fun StepKind.label(): String = when (this) {
    StepKind.OPEN_APP -> "打开应用"
    StepKind.WAIT -> "等待"
    StepKind.TAP -> "点击"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDateDialog(initial: Long?, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val seed = Instant.ofEpochMilli(initial ?: defaultScheduledAt()).atZone(zone)
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = seed.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onConfirm(date.atTime(seed.toLocalTime()).atZone(zone).toInstant().toEpochMilli())
                }
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) { androidx.compose.material3.DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTimeDialog(initial: Long?, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val seed = Instant.ofEpochMilli(initial ?: defaultScheduledAt()).atZone(zone)
    var hour by remember(initial) { mutableIntStateOf(seed.hour) }
    var minute by remember(initial) { mutableIntStateOf(seed.minute) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("时")
                    TimeWheel(hour, 23, "小时", onValueChange = { hour = it })
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("分")
                    TimeWheel(minute, 59, "分钟", onValueChange = { minute = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(seed.toLocalDate().atTime(hour, minute).atZone(zone).toInstant().toEpochMilli())
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TimeWheel(value: Int, max: Int, label: String, onValueChange: (Int) -> Unit) {
    AndroidView(
        modifier = Modifier.width(96.dp).height(176.dp),
        factory = { context ->
            NumberPicker(context).apply {
                minValue = 0
                maxValue = max
                this.value = value
                wrapSelectorWheel = true
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                setFormatter { number -> String.format(Locale.getDefault(), "%02d", number) }
                contentDescription = label
                setOnValueChangedListener { _, _, selected -> onValueChange(selected) }
            }
        },
        update = { picker -> if (picker.value != value) picker.value = value },
    )
}
