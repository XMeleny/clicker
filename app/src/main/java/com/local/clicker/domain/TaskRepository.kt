package com.local.clicker.domain

import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeTasks(): Flow<List<TaskSummary>>

    fun observeLogs(): Flow<List<ExecutionLog>>

    fun observeTask(id: Long): Flow<TaskRecord?>

    suspend fun loadDraft(id: Long): TaskDraft?

    suspend fun reserveTaskId(): Long

    suspend fun save(
        id: Long?,
        name: String,
        scheduledAt: Long?,
        steps: List<DraftStep>,
        now: Long,
        allowSchedule: Boolean,
    ): SaveResult

    suspend fun delete(id: Long)

    suspend fun copyAsNew(id: Long, now: Long): Long?

    suspend fun cancelSchedule(id: Long)

    suspend fun get(id: Long): TaskRecord?

    suspend fun executionSnapshot(id: Long): ExecutionSnapshot?

    suspend fun stepSnapshot(id: Long, stepKey: Long): ExecutionSnapshot?

    suspend fun markRunning(id: Long, now: Long)

    suspend fun markTerminal(id: Long, status: TaskStatus, message: String, runAt: Long?)

    suspend fun recordTrial(id: Long, status: TaskStatus, message: String)

    suspend fun clearLogs()

    suspend fun markMissed(id: Long)

    suspend fun markInterruptedRunning()

    suspend fun listScheduled(): List<TaskRecord>
}
