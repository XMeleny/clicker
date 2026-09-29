package com.local.clicker.schedule

import android.content.Context
import android.os.SystemClock
import com.local.clicker.domain.GRACE_MS
import com.local.clicker.domain.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ScheduleReconciler(
    context: Context,
    private val repository: TaskRepository,
    private val alarms: AlarmScheduler,
) {
    val showBootBanner = MutableStateFlow(false)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gate = Mutex()

    suspend fun onColdStart() = gate.withLock {
        repository.markInterruptedRunning()
        val now = System.currentTimeMillis()
        val scheduled = repository.listScheduled()
        var future = false
        for (task in scheduled) {
            val at = task.scheduledAt
            when {
                at == null || now - at > GRACE_MS -> repository.markMissed(task.id)
                at > now -> {
                    alarms.schedule(task.id, at)
                    future = true
                }
            }
        }
        val bootTime = now - SystemClock.elapsedRealtime()
        showBootBanner.value = future && prefs.getLong(KEY_BOOT, 0L) < bootTime
    }

    suspend fun onBoot() = gate.withLock {
        prefs.edit().putLong(KEY_BOOT, System.currentTimeMillis()).apply()
        showBootBanner.value = false
        repository.markInterruptedRunning()
        val now = System.currentTimeMillis()
        for (task in repository.listScheduled()) {
            val at = task.scheduledAt
            if (at == null || at <= now) repository.markMissed(task.id)
            else alarms.schedule(task.id, at)
        }
    }

    private companion object {
        const val PREFS = "clicker_schedule"
        const val KEY_BOOT = "boot_handled"
    }
}

class TaskCoordinator(
    private val repository: TaskRepository,
    private val alarms: AlarmScheduler,
) {
    suspend fun save(
        id: Long?,
        name: String,
        scheduledAt: Long?,
        steps: List<com.local.clicker.domain.DraftStep>,
        now: Long,
    ): com.local.clicker.domain.SaveResult {
        val result = repository.save(
            id = id,
            name = name,
            scheduledAt = scheduledAt,
            steps = steps,
            now = now,
            allowSchedule = alarms.canSchedule(),
        )
        if (result is com.local.clicker.domain.SaveResult.Saved) {
            if (id != null) alarms.cancel(id)
            if (result.status == com.local.clicker.domain.TaskStatus.SCHEDULED && result.scheduledAt != null) {
                alarms.schedule(result.taskId, result.scheduledAt)
            }
        }
        return result
    }

    suspend fun delete(id: Long) {
        alarms.cancel(id)
        repository.delete(id)
    }

    suspend fun cancelSchedule(id: Long) {
        alarms.cancel(id)
        repository.cancelSchedule(id)
    }
}
