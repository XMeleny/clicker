package com.local.clicker.exec

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import com.local.clicker.ClickerApp
import com.local.clicker.domain.GRACE_MS
import com.local.clicker.domain.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

class ClickerRuntimeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private val cancel = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            Notifier.ID_RUN,
            Notifier.running(this, "准备执行", ""),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        when (intent?.action) {
            ACTION_STOP -> cancel.set(true)
            ACTION_RUN -> {
                requests.trySend(
                    RunRequest(
                        taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L),
                        scheduledAt = intent.getLongExtra(EXTRA_AT, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE },
                        source = intent.getIntExtra(EXTRA_SOURCE, SOURCE_MANUAL),
                        trialStepKey = intent.getLongExtra(EXTRA_STEP_KEY, Long.MIN_VALUE)
                            .takeIf { it != Long.MIN_VALUE },
                    ),
                )
            }
        }
        ensureLoop()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        busy.value = false
        super.onDestroy()
    }

    private fun ensureLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (isActive) {
                val request = withTimeoutOrNull(300) { requests.receive() }
                if (request == null) {
                    if (requests.isEmpty) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        break
                    }
                    continue
                }
                if (request.taskId < 0L) continue
                busy.value = true
                cancel.set(false)
                try {
                    handle(request)
                } finally {
                    busy.value = false
                }
            }
        }
    }

    private suspend fun handle(request: RunRequest) {
        val app = application as ClickerApp
        val repo = app.graph.repository
        val now = System.currentTimeMillis()
        val task = repo.get(request.taskId) ?: return
        val trial = request.trialStepKey != null
        if (request.source == SOURCE_ALARM) {
            if (task.status != TaskStatus.SCHEDULED || task.scheduledAt != request.scheduledAt) {
                Log.i(ExecutionEngine.TAG, "drop alarm task=${request.taskId}")
                return
            }
            val at = task.scheduledAt
            if (at != null && now - at > GRACE_MS) {
                repo.markMissed(task.id)
                Notifier.result(this, task.id, "错过", "已超过计划时间 30 秒，未执行")
                Log.i(ExecutionEngine.TAG, "end task=${task.id} status=MISSED reason=grace")
                return
            }
        }
        val snapshot = if (trial) {
            repo.stepSnapshot(request.taskId, request.trialStepKey!!)
        } else {
            if (task.status != TaskStatus.DRAFT && task.status != TaskStatus.SCHEDULED) return
            repo.executionSnapshot(request.taskId)
        }
        if (snapshot == null) {
            if (!trial) {
                repo.markTerminal(request.taskId, TaskStatus.FAILED, "任务步骤不合法", null)
                Notifier.result(this, request.taskId, "失败", "任务步骤不合法")
            }
            return
        }
        if (!trial && task.status == TaskStatus.SCHEDULED) {
            app.graph.alarms.cancel(task.id)
        }
        val engine = ExecutionEngine(this)
        val blocked = engine.prepare(snapshot)
        if (blocked != null) {
            publish(request, snapshot.name, blocked, trial, now)
            return
        }
        if (!trial) repo.markRunning(request.taskId, now)
        val outcome = engine.runSteps(snapshot, cancelled = { cancel.get() }) { index, total, label ->
            val notification = Notifier.running(
                this,
                if (trial) "正在试运行「${snapshot.name}」" else "正在执行「${snapshot.name}」",
                "步骤 ${index + 1}/$total $label",
            )
            startForeground(Notifier.ID_RUN, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
        publish(request, snapshot.name, outcome, trial, now)
    }

    private suspend fun publish(
        request: RunRequest,
        name: String,
        outcome: Outcome,
        trial: Boolean,
        runAt: Long,
    ) {
        val message = when (outcome) {
            is Outcome.Success -> outcome.message
            is Outcome.Failed -> outcome.message
            Outcome.Cancelled -> "已取消"
        }
        Log.i(ExecutionEngine.TAG, "end task=${request.taskId} trial=$trial result=$message")
        if (trial) {
            trialResults.emit(TrialResult(request.taskId, message))
            return
        }
        val app = application as ClickerApp
        val status = when (outcome) {
            is Outcome.Success -> TaskStatus.SUCCESS
            is Outcome.Failed -> TaskStatus.FAILED
            Outcome.Cancelled -> TaskStatus.CANCELLED
        }
        val title = when (status) {
            TaskStatus.SUCCESS -> "成功"
            TaskStatus.FAILED -> "失败"
            else -> "已取消"
        }
        app.graph.repository.markTerminal(request.taskId, status, message, runAt)
        Notifier.result(this, request.taskId, title, message)
    }

    data class TrialResult(val taskId: Long, val message: String)

    private data class RunRequest(
        val taskId: Long,
        val scheduledAt: Long?,
        val source: Int,
        val trialStepKey: Long?,
    )

    companion object {
        const val ACTION_RUN = "com.local.clicker.RUN"
        const val ACTION_STOP = "com.local.clicker.STOP"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_AT = "scheduledAt"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_STEP_KEY = "stepKey"
        const val SOURCE_ALARM = 1
        const val SOURCE_MANUAL = 2
        const val SOURCE_TRIAL = 3

        val busy = MutableStateFlow(false)
        val trialResults = MutableSharedFlow<TrialResult>(extraBufferCapacity = 4)
        private val requests = Channel<RunRequest>(Channel.UNLIMITED)

        fun runIntent(
            context: android.content.Context,
            taskId: Long,
            source: Int,
            stepKey: Long? = null,
        ): Intent = Intent(context, ClickerRuntimeService::class.java).apply {
            action = ACTION_RUN
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_SOURCE, source)
            if (stepKey != null) putExtra(EXTRA_STEP_KEY, stepKey)
        }
    }
}
