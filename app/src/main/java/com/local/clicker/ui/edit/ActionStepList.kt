package com.local.clicker.ui.edit

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.clicker.domain.DraftStep

internal data class ActionStepListActions(
    val add: () -> Unit,
    val move: (Long, Int) -> Unit,
    val remove: (Int) -> Unit,
    val edit: (DraftStep) -> Unit,
    val duplicate: ((Int) -> Unit)? = null,
    val insert: ((Int) -> Unit)? = null,
    val changeType: ((Int) -> Unit)? = null,
    val showMoveActions: Boolean = false,
)

internal fun LazyListScope.actionStepList(
    steps: List<DraftStep>,
    apps: List<AppOption>,
    readOnly: Boolean,
    invalid: (DraftStep) -> Boolean,
    actions: ActionStepListActions,
) {
    item(key = "step-header") {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("脚本按从上到下的顺序执行", modifier = Modifier.weight(1f))
            if (!readOnly) IconButton(
                onClick = actions.add,
                enabled = steps.size < 30,
            ) { Icon(Icons.Default.AddCircleOutline, contentDescription = "添加步骤") }
        }
    }
    itemsIndexed(steps, key = { _, step -> step.key }) { index, step ->
        Box(Modifier.fillMaxWidth().animateItem(placementSpec = tween(durationMillis = 220))) {
            StepRow(
                step = step,
                apps = apps,
                readOnly = readOnly,
                invalid = invalid(step),
                onMove = { delta -> actions.move(step.key, delta) },
                onRemove = { actions.remove(index) },
                onEdit = { actions.edit(step) },
                onDuplicate = actions.duplicate?.let { action -> { action(index) } },
                onInsert = actions.insert?.let { action -> { action(index) } },
                onChangeType = actions.changeType?.let { action -> { action(index) } },
                onMoveUp = if (actions.showMoveActions && index > 0) ({ actions.move(step.key, -1) }) else null,
                onMoveDown = if (actions.showMoveActions && index < steps.lastIndex) ({ actions.move(step.key, 1) }) else null,
            )
        }
    }
}
