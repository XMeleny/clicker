package com.local.clicker.ui.edit

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.local.clicker.domain.DraftOpenApp
import com.local.clicker.domain.DraftStep
import com.local.clicker.domain.DraftTap
import com.local.clicker.domain.DraftWait
import com.local.clicker.domain.StepKind
import com.local.clicker.domain.WAIT_MAX_MS
import com.local.clicker.domain.WAIT_MIN_MS
import com.local.clicker.domain.formatWaitSeconds

@Composable
internal fun StepCard(
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
                                if (accum > threshold) { onMove(1); accum = 0f }
                                else if (accum < -threshold) { onMove(-1); accum = 0f }
                            },
                        )
                    },
                )
                if (!readOnly) Icon(Icons.Default.DragHandle, contentDescription = "拖拽排序")
                Text(step.summary(apps), modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
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
                    is DraftOpenApp -> TextButton(onClick = onPickApp, enabled = !readOnly) { Text(step.summary(apps)) }
                    is DraftWait -> {
                        var waitSeconds by remember(step.key) {
                            mutableStateOf(step.waitMs?.let(::formatWaitSeconds).orEmpty())
                        }
                        OutlinedTextField(
                            value = waitSeconds,
                            onValueChange = { waitSeconds = it; onWait(it) },
                            enabled = !readOnly,
                            label = { Text("等待时间（秒）") },
                            supportingText = { Text("允许 ${formatWaitSeconds(WAIT_MIN_MS)}～${formatWaitSeconds(WAIT_MAX_MS)} 秒") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    is DraftTap -> {
                        val point = step.tap
                        Text(if (point == null) "点击?" else "点击 (${point.x}, ${point.y})  ${point.screenWidth}×${point.screenHeight} / ${point.rotation}")
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
internal fun KindDialog(onPick: (StepKind) -> Unit, onDismiss: () -> Unit) {
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
