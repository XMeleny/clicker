package com.local.clicker.data

import android.content.pm.PackageManager
import com.local.clicker.domain.DraftOpenApp
import com.local.clicker.domain.DraftStep
import com.local.clicker.domain.DraftTap
import com.local.clicker.domain.DraftWait
import com.local.clicker.domain.ExecutionSnapshot
import com.local.clicker.domain.ExecutionLog
import com.local.clicker.domain.NAME_MAX
import com.local.clicker.domain.OpenAppStep
import com.local.clicker.domain.SaveResult
import com.local.clicker.domain.Step
import com.local.clicker.domain.StepKind
import com.local.clicker.domain.TapPoint
import com.local.clicker.domain.TapStep
import com.local.clicker.domain.TaskDraft
import com.local.clicker.domain.TaskRecord
import com.local.clicker.domain.TaskRepository
import com.local.clicker.domain.TaskStatus
import com.local.clicker.domain.TaskSummary
import com.local.clicker.domain.WAIT_MAX_MS
import com.local.clicker.domain.WAIT_MIN_MS
import com.local.clicker.domain.WaitStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomTaskRepository(
    private val dao: ClickerDao,
    private val packageManager: PackageManager,
) : TaskRepository {

    override fun observeLogs(): Flow<List<ExecutionLog>> =
        dao.observeLogs().map { rows -> rows.map { it.toLog() } }

    override fun observeTasks(): Flow<List<TaskSummary>> =
        combine(dao.observeTasks(), dao.observeSteps()) { tasks, steps ->
            val byTask = steps.groupBy { it.taskId }
            tasks.map { task ->
                val rows = byTask[task.id].orEmpty()
                TaskSummary(
                    id = task.id,
                    name = task.name,
                    scheduledAt = task.scheduledAt,
                    status = task.status.toStatus(),
                    updatedAt = task.updatedAt,
                    lastMessage = task.lastMessage,
                    executable = rows.arePersistedStepsExecutable(packageManager),
                    steps = rows.map { it.summary(packageManager) },
                )
            }
        }

    override fun observeTask(id: Long): Flow<TaskRecord?> =
        dao.observeTask(id).map { it?.toRecord() }

    override suspend fun loadDraft(id: Long): TaskDraft? {
        val task = dao.getTask(id) ?: return null
        return TaskDraft(task.toRecord(), dao.stepsOf(id).map { it.toDraft() })
    }

    override suspend fun save(
        id: Long?,
        name: String,
        scheduledAt: Long?,
        steps: List<DraftStep>,
        now: Long,
        allowSchedule: Boolean,
    ): SaveResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > NAME_MAX) {
            return SaveResult.Rejected("请填写 1～40 个字符的名称", null)
        }
        val existing = id?.let { dao.getTask(it) }
        if (existing?.status == TaskStatus.RUNNING.name) {
            return SaveResult.Rejected("当前状态不能修改步骤", existing.scheduledAt)
        }
        if (scheduledAt != null && scheduledAt <= now) {
            return SaveResult.Rejected("计划时间必须是将来的时间", existing?.scheduledAt)
        }

        val executable = steps.areDraftStepsExecutable(packageManager)
        val status = when {
            executable && scheduledAt != null && allowSchedule -> TaskStatus.SCHEDULED
            else -> TaskStatus.DRAFT
        }
        val notice = when {
            executable && scheduledAt != null && !allowSchedule -> "未授予闹钟权限，已保存为草稿"
            !executable -> "还有未完成的步骤，已保存为草稿"
            else -> null
        }
        val taskId = if (existing == null) {
            dao.insertTask(
                TaskEntity(
                    name = trimmed,
                    scheduledAt = scheduledAt,
                    status = status.name,
                    createdAt = now,
                    updatedAt = now,
                    lastRunAt = null,
                    lastMessage = notice,
                ),
            )
        } else {
            dao.upsertTask(
                existing.copy(
                    name = trimmed,
                    scheduledAt = scheduledAt,
                    status = status.name,
                    updatedAt = now,
                    lastMessage = notice,
                ),
            )
            existing.id
        }
        dao.replaceSteps(taskId, steps.map { it.toEntity(taskId) })
        return SaveResult.Saved(
            taskId = taskId,
            status = status,
            scheduledAt = scheduledAt,
            leaveEditor = status == TaskStatus.SCHEDULED || (executable && scheduledAt == null),
            notice = notice,
        )
    }

    override suspend fun delete(id: Long) {
        dao.deleteTask(id)
    }

    override suspend fun cancelSchedule(id: Long) {
        val task = dao.getTask(id) ?: return
        if (task.status.toStatus() != TaskStatus.SCHEDULED) return
        dao.upsertTask(
            task.copy(
                status = TaskStatus.CANCELLED.name,
                updatedAt = System.currentTimeMillis(),
                lastMessage = "已取消计划",
            ),
        )
    }

    override suspend fun get(id: Long): TaskRecord? = dao.getTask(id)?.toRecord()

    override suspend fun executionSnapshot(id: Long): ExecutionSnapshot? {
        val task = dao.getTask(id) ?: return null
        val steps = dao.stepsOf(id).mapNotNull { it.toStep() }
        val rows = dao.stepsOf(id)
        if (rows.isEmpty() || steps.size != rows.size) return null
        return ExecutionSnapshot(task.id, task.name, task.status.toStatus(), task.scheduledAt, steps)
    }

    override suspend fun stepSnapshot(id: Long, stepKey: Long): ExecutionSnapshot? {
        val task = dao.getTask(id) ?: return null
        val step = dao.stepsOf(id).firstOrNull { it.id == stepKey }?.toStep() ?: return null
        return ExecutionSnapshot(
            taskId = task.id,
            name = task.name,
            status = task.status.toStatus(),
            scheduledAt = task.scheduledAt,
            steps = listOf(step),
        )
    }

    override suspend fun markRunning(id: Long, now: Long) {
        val task = dao.getTask(id) ?: return
        dao.upsertTask(task.copy(status = TaskStatus.RUNNING.name, lastRunAt = now, updatedAt = now))
    }

    override suspend fun markTerminal(id: Long, status: TaskStatus, message: String, runAt: Long?) {
        val task = dao.getTask(id) ?: return
        val now = System.currentTimeMillis()
        dao.upsertTaskAndLog(
            task.copy(
                status = status.name,
                lastMessage = message.take(200),
                lastRunAt = runAt ?: task.lastRunAt,
                updatedAt = now,
            ),
            ExecutionLogEntity(
                taskId = id,
                taskName = task.name,
                status = status.name,
                message = message,
                createdAt = now,
                trial = false,
            ),
        )
    }

    override suspend fun recordTrial(id: Long, status: TaskStatus, message: String) {
        val task = dao.getTask(id) ?: return
        dao.insertLog(
            ExecutionLogEntity(
                taskId = id,
                taskName = task.name,
                status = status.name,
                message = message,
                createdAt = System.currentTimeMillis(),
                trial = true,
            ),
        )
    }

    override suspend fun clearLogs() = dao.clearLogs()

    override suspend fun markMissed(id: Long) {
        markTerminal(id, TaskStatus.MISSED, "已超过计划时间 30 秒，未执行", null)
    }

    override suspend fun markInterruptedRunning() {
        dao.tasksByStatus(TaskStatus.RUNNING.name).forEach { task ->
            val now = System.currentTimeMillis()
            dao.upsertTaskAndLog(
                task.copy(
                    status = TaskStatus.FAILED.name,
                    lastMessage = "执行被中断",
                    updatedAt = now,
                ),
                ExecutionLogEntity(
                    taskId = task.id,
                    taskName = task.name,
                    status = TaskStatus.FAILED.name,
                    message = "执行被中断",
                    createdAt = now,
                    trial = false,
                ),
            )
        }
    }

    override suspend fun listScheduled(): List<TaskRecord> =
        dao.tasksByStatus(TaskStatus.SCHEDULED.name).map { it.toRecord() }
}

private fun String.toStatus(): TaskStatus = TaskStatus.valueOf(this)

private fun TaskEntity.toRecord() = TaskRecord(
    id = id,
    name = name,
    scheduledAt = scheduledAt,
    status = status.toStatus(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastRunAt = lastRunAt,
    lastMessage = lastMessage,
)

private fun StepEntity.summary(packageManager: PackageManager): String = when (StepKind.valueOf(type)) {
    StepKind.OPEN_APP -> packageName?.let { pkg ->
        val label = runCatching {
            val app = packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            packageManager.getApplicationLabel(app).toString()
        }.getOrDefault(pkg)
        "打开 $label"
    } ?: "打开应用（未选择）"
    StepKind.WAIT -> waitMs?.let { "等待 ${it}ms" } ?: "等待（未填写）"
    StepKind.TAP -> if (x != null && y != null) "点击 ($x, $y)" else "点击（未取点）"
}

private fun ExecutionLogEntity.toLog() = ExecutionLog(
    id = id,
    taskId = taskId,
    taskName = taskName,
    status = status.toStatus(),
    message = message,
    createdAt = createdAt,
    trial = trial,
)

private fun StepEntity.toDraft(): DraftStep = when (StepKind.valueOf(type)) {
    StepKind.OPEN_APP -> DraftOpenApp(id, orderIndex, packageName)
    StepKind.WAIT -> DraftWait(id, orderIndex, waitMs)
    StepKind.TAP -> DraftTap(
        id,
        orderIndex,
        if (x != null && y != null && screenWidth != null && screenHeight != null && rotation != null) {
            TapPoint(x, y, screenWidth, screenHeight, rotation)
        } else {
            null
        },
    )
}

private fun StepEntity.toStep(): Step? {
    if (!complete) return null
    return when (StepKind.valueOf(type)) {
        StepKind.OPEN_APP -> packageName?.takeIf { it.isNotBlank() }?.let {
            OpenAppStep(id, orderIndex, it)
        }

        StepKind.WAIT -> waitMs?.takeIf { it in WAIT_MIN_MS..WAIT_MAX_MS }?.let {
            WaitStep(id, orderIndex, it)
        }

        StepKind.TAP -> {
            if (x == null || y == null || screenWidth == null || screenHeight == null || rotation == null) {
                null
            } else {
                val point = TapPoint(x, y, screenWidth, screenHeight, rotation)
                if (!point.isInside()) null else TapStep(id, orderIndex, x, y, screenWidth, screenHeight, rotation)
            }
        }
    }
}

private fun DraftStep.toEntity(taskId: Long): StepEntity {
    val persistedId = if (key > 0) key else 0
    return when (this) {
        is DraftOpenApp -> StepEntity(
            id = persistedId,
            taskId = taskId,
            orderIndex = orderIndex,
            type = kind.name,
            complete = isComplete(),
            packageName = packageName,
            waitMs = null,
            x = null,
            y = null,
            screenWidth = null,
            screenHeight = null,
            rotation = null,
        )

        is DraftWait -> StepEntity(
            id = persistedId,
            taskId = taskId,
            orderIndex = orderIndex,
            type = kind.name,
            complete = isComplete(),
            packageName = null,
            waitMs = waitMs,
            x = null,
            y = null,
            screenWidth = null,
            screenHeight = null,
            rotation = null,
        )

        is DraftTap -> StepEntity(
            id = persistedId,
            taskId = taskId,
            orderIndex = orderIndex,
            type = kind.name,
            complete = isComplete(),
            packageName = null,
            waitMs = null,
            x = tap?.x,
            y = tap?.y,
            screenWidth = tap?.screenWidth,
            screenHeight = tap?.screenHeight,
            rotation = tap?.rotation,
        )
    }
}

private fun List<StepEntity>.arePersistedStepsExecutable(packageManager: PackageManager): Boolean {
    if (isEmpty() || size > com.local.clicker.domain.MAX_STEPS) return false
    return all { row ->
        when (val step = row.toStep()) {
            null -> false
            is OpenAppStep -> packageManager.getLaunchIntentForPackage(step.packageName) != null
            else -> true
        }
    }
}

private fun List<DraftStep>.areDraftStepsExecutable(packageManager: PackageManager): Boolean {
    if (isEmpty() || size > com.local.clicker.domain.MAX_STEPS) return false
    return all { step ->
        when (step) {
            is DraftOpenApp -> {
                val pkg = step.packageName
                step.isComplete() && pkg != null && packageManager.getLaunchIntentForPackage(pkg) != null
            }

            is DraftWait -> step.isComplete()
            is DraftTap -> step.isComplete()
        }
    }
}
