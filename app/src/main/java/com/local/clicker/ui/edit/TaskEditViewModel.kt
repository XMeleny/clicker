package com.local.clicker.ui.edit

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.local.clicker.ClickerApp
import com.local.clicker.domain.DraftOpenApp
import com.local.clicker.domain.DraftStep
import com.local.clicker.domain.DraftTap
import com.local.clicker.domain.DraftWait
import com.local.clicker.domain.EditResult
import com.local.clicker.domain.SaveResult
import com.local.clicker.domain.ScriptEditor
import com.local.clicker.domain.StepKind
import com.local.clicker.domain.TaskStatus
import com.local.clicker.domain.isTerminal
import com.local.clicker.exec.ClickerAccessibilityService
import com.local.clicker.exec.ClickerRuntimeService
import com.local.clicker.exec.PickerBus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppOption(val label: String, val packageName: String)

data class EditUi(
    val taskId: Long = 0L,
    val name: String = "",
    val scheduledAt: Long? = null,
    val steps: List<DraftStep> = emptyList(),
    val expandedKey: Long? = null,
    val status: TaskStatus = TaskStatus.DRAFT,
    val readOnly: Boolean = false,
    val notice: String? = null,
    val limitHint: String? = null,
    val trialBanner: String? = null,
    val apps: List<AppOption> = emptyList(),
    val showInvalid: Boolean = false,
    val loaded: Boolean = false,
)

class TaskEditViewModel(app: Application, private val initialId: Long, private val isNew: Boolean) : AndroidViewModel(app) {
    private val graph = (app as ClickerApp).graph
    private val _ui = MutableStateFlow(EditUi(taskId = initialId))
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = loadApps()
            _ui.update { it.copy(apps = apps) }
            if (isNew) {
                val id = initialId.takeIf { it > 0L } ?: graph.repository.reserveTaskId()
                val zone = ZoneId.systemDefault()
                val todayAtNineOhFive = LocalDate.now(zone)
                    .atTime(LocalTime.of(21, 5))
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
                _ui.update {
                    it.copy(taskId = id, name = "任务$id", scheduledAt = todayAtNineOhFive, loaded = true)
                }
            } else if (initialId > 0L) {
                graph.repository.loadDraft(initialId)?.let { draft ->
                    _ui.update {
                        it.copy(
                            name = draft.task.name,
                            scheduledAt = draft.task.scheduledAt,
                            steps = draft.steps,
                            status = draft.task.status,
                            readOnly = draft.task.status == TaskStatus.RUNNING || draft.task.status.isTerminal(),
                            loaded = true,
                        )
                    }
                } ?: _ui.update { it.copy(loaded = true, notice = "任务不存在") }
            } else {
                _ui.update { it.copy(loaded = true) }
            }
        }
        viewModelScope.launch {
            if (isNew || initialId <= 0L) return@launch
            graph.repository.observeTask(initialId).collect { task ->
                if (task == null) return@collect
                _ui.update {
                    it.copy(
                        status = task.status,
                        readOnly = task.status == TaskStatus.RUNNING || task.status.isTerminal(),
                    )
                }
            }
        }
        viewModelScope.launch {
            PickerBus.events.collect { event ->
                when (event) {
                    is PickerBus.Event.Point -> _ui.update { state ->
                        state.copy(
                            steps = ScriptEditor.update(
                                state.steps,
                                event.key,
                                DraftTap(event.key, 0, event.point),
                            ),
                            expandedKey = event.key,
                        )
                    }
                    is PickerBus.Event.Failed -> _ui.update { it.copy(notice = event.message) }
                }
            }
        }
        viewModelScope.launch {
            ClickerRuntimeService.trialResults.collect { result ->
                if (result.taskId == _ui.value.taskId) {
                    _ui.update { it.copy(trialBanner = result.message) }
                }
            }
        }
    }

    fun setName(value: String) {
        if (_ui.value.readOnly) return
        _ui.update { it.copy(name = value.take(40)) }
    }

    fun setScheduledAt(value: Long?) {
        if (_ui.value.readOnly) return
        _ui.update { it.copy(scheduledAt = value?.let(::floorToMinute)) }
    }

    fun toggle(key: Long) {
        _ui.update { it.copy(expandedKey = if (it.expandedKey == key) null else key) }
    }

    fun add(kind: StepKind) = applyEdit(ScriptEditor.append(_ui.value.steps, kind))

    fun insertBelow(index: Int, kind: StepKind) = applyEdit(ScriptEditor.insert(_ui.value.steps, index + 1, kind))

    fun remove(index: Int) {
        if (_ui.value.readOnly) return
        _ui.update { it.copy(steps = ScriptEditor.remove(it.steps, index), showInvalid = false) }
    }

    fun moveKey(key: Long, delta: Int) {
        if (_ui.value.readOnly) return
        val index = _ui.value.steps.indexOfFirst { it.key == key }
        if (index < 0) return
        _ui.update { it.copy(steps = ScriptEditor.moveBy(it.steps, index, delta)) }
    }

    fun moveTo(from: Int, to: Int) {
        if (_ui.value.readOnly) return
        _ui.update { it.copy(steps = ScriptEditor.move(it.steps, from, to)) }
    }

    fun duplicate(index: Int) = applyEdit(ScriptEditor.duplicate(_ui.value.steps, index))

    fun changeType(index: Int, kind: StepKind) {
        if (_ui.value.readOnly) return
        val steps = ScriptEditor.changeType(_ui.value.steps, index, kind)
        _ui.update { it.copy(steps = steps, expandedKey = steps.getOrNull(index)?.key, showInvalid = false) }
    }

    fun updateStep(step: DraftStep) {
        if (_ui.value.readOnly) return
        _ui.update { it.copy(steps = ScriptEditor.update(it.steps, step.key, step), showInvalid = false) }
    }

    fun requestPick(key: Long) {
        val service = ClickerAccessibilityService.instance
        if (service == null) {
            _ui.update { it.copy(notice = "无障碍服务未开启") }
            return
        }
        service.beginPick(key)
    }

    fun save(onLeave: () -> Unit, onReplaced: (Long) -> Unit) {
        viewModelScope.launch {
            val state = _ui.value
            if (state.readOnly) return@launch
            val result = graph.coordinator.save(
                id = state.taskId.takeIf { it > 0L },
                name = state.name,
                scheduledAt = state.scheduledAt,
                steps = state.steps,
                now = System.currentTimeMillis(),
            )
            when (result) {
                is SaveResult.Rejected -> _ui.update {
                    it.copy(
                        notice = result.message,
                        scheduledAt = if (result.message.contains("计划时间")) result.revertScheduledAt else it.scheduledAt,
                    )
                }
                is SaveResult.Saved -> {
                    _ui.update {
                        it.copy(
                            taskId = result.taskId,
                            status = result.status,
                            notice = result.notice,
                            showInvalid = result.notice != null,
                        )
                    }
                    when {
                        result.leaveEditor -> onLeave()
                        isNew -> onReplaced(result.taskId)
                    }
                }
            }
        }
    }

    fun trial(index: Int, onReplaced: (Long) -> Unit) {
        viewModelScope.launch {
            val state = _ui.value
            if (state.readOnly || ClickerRuntimeService.busy.value) return@launch
            val saved = persist(state) ?: return@launch
            val draft = graph.repository.loadDraft(saved.taskId) ?: return@launch
            _ui.update {
                it.copy(taskId = saved.taskId, steps = draft.steps, status = saved.status, showInvalid = false)
            }
            val step = draft.steps.getOrNull(index)
            if (step != null && step.isComplete()) {
                val context = getApplication<Application>()
                context.startForegroundService(
                    ClickerRuntimeService.runIntent(
                        context,
                        saved.taskId,
                        ClickerRuntimeService.SOURCE_TRIAL,
                        step.key,
                    ),
                )
            } else {
                _ui.update { it.copy(notice = "这一步还没完成", showInvalid = true) }
            }
            if (isNew) onReplaced(saved.taskId)
        }
    }

    fun runNow(onReplaced: (Long) -> Unit) {
        viewModelScope.launch {
            val state = _ui.value
            if (state.readOnly || ClickerRuntimeService.busy.value) return@launch
            val saved = persist(state) ?: return@launch
            val draft = graph.repository.loadDraft(saved.taskId)
            val ready = draft != null && draft.steps.isNotEmpty() && draft.steps.none { it.invalid(state.apps) }
            _ui.update {
                it.copy(
                    taskId = saved.taskId,
                    steps = draft?.steps ?: it.steps,
                    status = saved.status,
                    notice = if (ready) saved.notice else "还有未完成的步骤，已保存为草稿",
                    showInvalid = !ready,
                )
            }
            if (!ready) {
                if (isNew) onReplaced(saved.taskId)
                return@launch
            }
            if (saved.status != TaskStatus.DRAFT && saved.status != TaskStatus.SCHEDULED) return@launch
            val context = getApplication<Application>()
            context.startForegroundService(
                ClickerRuntimeService.runIntent(context, saved.taskId, ClickerRuntimeService.SOURCE_MANUAL),
            )
            if (isNew) onReplaced(saved.taskId)
        }
    }

    private suspend fun persist(state: EditUi): SaveResult.Saved? {
        val result = graph.coordinator.save(
            id = state.taskId.takeIf { it > 0L },
            name = state.name,
            scheduledAt = state.scheduledAt,
            steps = state.steps,
            now = System.currentTimeMillis(),
        )
        return when (result) {
            is SaveResult.Rejected -> {
                _ui.update {
                    it.copy(
                        notice = result.message,
                        scheduledAt = if (result.message.contains("计划时间")) result.revertScheduledAt else it.scheduledAt,
                    )
                }
                null
            }
            is SaveResult.Saved -> {
                _ui.update { it.copy(taskId = result.taskId, status = result.status, notice = result.notice) }
                result
            }
        }
    }

    fun cancelSchedule(onLeave: () -> Unit) {
        viewModelScope.launch {
            val id = _ui.value.taskId
            if (id > 0L) graph.coordinator.cancelSchedule(id)
            onLeave()
        }
    }

    fun copy(onReplaced: (Long) -> Unit) {
        viewModelScope.launch {
            val id = _ui.value.taskId
            val newId = graph.repository.copyAsNew(id, System.currentTimeMillis())
            if (newId != null) onReplaced(newId)
        }
    }

    private fun applyEdit(result: EditResult) {
        if (_ui.value.readOnly) return
        when (result) {
            is EditResult.Updated -> _ui.update {
                it.copy(steps = result.steps, expandedKey = result.focusKey ?: it.expandedKey, limitHint = null, showInvalid = false)
            }
            is EditResult.AtLimit -> _ui.update { it.copy(limitHint = "已达 30 步上限") }
        }
    }

    private fun loadApps(): List<AppOption> {
        val pm = getApplication<Application>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { info ->
                AppOption(info.loadLabel(pm).toString(), info.activityInfo.packageName)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

fun DraftStep.summary(apps: List<AppOption>): String = when (this) {
    is DraftOpenApp -> packageName?.let { pkg ->
        "打开 " + (apps.find { it.packageName == pkg }?.label ?: pkg)
    } ?: "打开应用（未选择）"
    is DraftWait -> waitMs?.let { "等待 ${it}ms" } ?: "等待（未填写）"
    is DraftTap -> tap?.let { "点击 (${it.x}, ${it.y})" } ?: "点击（未取点）"
}

fun DraftStep.invalid(apps: List<AppOption>): Boolean = when (this) {
    is DraftOpenApp -> !isComplete() || apps.none { it.packageName == packageName }
    else -> !isComplete()
}
