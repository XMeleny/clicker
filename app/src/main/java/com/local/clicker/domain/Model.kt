package com.local.clicker.domain

enum class TaskStatus {
    DRAFT,
    SCHEDULED,
    RUNNING,
    SUCCESS,
    FAILED,
    MISSED,
    CANCELLED,
}

fun TaskStatus.isTerminal(): Boolean = when (this) {
    TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.MISSED, TaskStatus.CANCELLED -> true
    else -> false
}

enum class StepKind {
    OPEN_APP,
    WAIT,
    TAP,
}

data class TapPoint(
    val x: Int,
    val y: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val rotation: Int,
) {
    fun isInside(): Boolean = x in 0 until screenWidth && y in 0 until screenHeight
}

sealed interface Step {
    val id: Long
    val orderIndex: Int
}

data class OpenAppStep(
    override val id: Long,
    override val orderIndex: Int,
    val packageName: String,
) : Step

data class WaitStep(
    override val id: Long,
    override val orderIndex: Int,
    val waitMs: Long,
) : Step

data class TapStep(
    override val id: Long,
    override val orderIndex: Int,
    val x: Int,
    val y: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val rotation: Int,
) : Step

sealed interface DraftStep {
    val key: Long
    val orderIndex: Int
    val kind: StepKind

    fun isComplete(): Boolean

    fun withIndex(index: Int): DraftStep

    fun copyWithKey(key: Long): DraftStep
}

data class DraftOpenApp(
    override val key: Long,
    override val orderIndex: Int,
    val packageName: String?,
) : DraftStep {
    override val kind: StepKind = StepKind.OPEN_APP
    override fun isComplete(): Boolean = !packageName.isNullOrBlank()
    override fun withIndex(index: Int): DraftStep = copy(orderIndex = index)
    override fun copyWithKey(key: Long): DraftStep = copy(key = key)
}

data class DraftWait(
    override val key: Long,
    override val orderIndex: Int,
    val waitMs: Long?,
) : DraftStep {
    override val kind: StepKind = StepKind.WAIT
    override fun isComplete(): Boolean = waitMs != null && waitMs in WAIT_MIN_MS..WAIT_MAX_MS
    override fun withIndex(index: Int): DraftStep = copy(orderIndex = index)
    override fun copyWithKey(key: Long): DraftStep = copy(key = key)
}

data class DraftTap(
    override val key: Long,
    override val orderIndex: Int,
    val tap: TapPoint?,
) : DraftStep {
    override val kind: StepKind = StepKind.TAP
    override fun isComplete(): Boolean = tap != null && tap.isInside()
    override fun withIndex(index: Int): DraftStep = copy(orderIndex = index)
    override fun copyWithKey(key: Long): DraftStep = copy(key = key)
}

data class TaskRecord(
    val id: Long,
    val name: String,
    val scheduledAt: Long?,
    val status: TaskStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val lastRunAt: Long?,
    val lastMessage: String?,
)

data class TaskSummary(
    val id: Long,
    val name: String,
    val scheduledAt: Long?,
    val status: TaskStatus,
    val updatedAt: Long,
    val lastMessage: String?,
    val executable: Boolean,
    val steps: List<String> = emptyList(),
)

data class ExecutionLog(
    val id: Long,
    val taskId: Long,
    val taskName: String,
    val status: TaskStatus,
    val message: String,
    val createdAt: Long,
    val trial: Boolean,
)

data class TaskDraft(
    val task: TaskRecord,
    val steps: List<DraftStep>,
)

data class ExecutionSnapshot(
    val taskId: Long,
    val name: String,
    val status: TaskStatus,
    val scheduledAt: Long?,
    val steps: List<Step>,
)

sealed interface SaveResult {
    data class Saved(
        val taskId: Long,
        val status: TaskStatus,
        val scheduledAt: Long?,
        val leaveEditor: Boolean,
        val notice: String?,
    ) : SaveResult

    data class Rejected(val message: String, val revertScheduledAt: Long?) : SaveResult
}

sealed interface EditResult {
    data class Updated(val steps: List<DraftStep>, val focusKey: Long?) : EditResult
    data class AtLimit(val steps: List<DraftStep>) : EditResult
}

const val WAIT_MIN_MS = 100L
const val WAIT_MAX_MS = 120_000L
const val MAX_STEPS = 30
const val GRACE_MS = 30_000L
const val NAME_MAX = 40
const val RUN_WALL_MS = 10 * 60 * 1000L
const val WAKE_TIMEOUT_MS = 3_000L
const val OPEN_APP_TIMEOUT_MS = 3_000L
const val TAP_HOLD_MS = 50L
