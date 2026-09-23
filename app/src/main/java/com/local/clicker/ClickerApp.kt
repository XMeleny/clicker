package com.local.clicker

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.local.clicker.data.ClickerDatabase
import com.local.clicker.data.RoomTaskRepository
import com.local.clicker.exec.Notifier
import com.local.clicker.schedule.AlarmScheduler
import com.local.clicker.schedule.ScheduleReconciler
import com.local.clicker.schedule.TaskCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClickerApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        Notifier.ensureChannels(this)
        graph.start()
    }
}

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(
        appContext,
        ClickerDatabase::class.java,
        "clicker.db",
    ).build()
    val repository = RoomTaskRepository(database.dao(), appContext.packageManager)
    val alarms = AlarmScheduler(appContext)
    val coordinator = TaskCoordinator(repository, alarms)
    val reconciler = ScheduleReconciler(appContext, repository, alarms)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch { reconciler.onColdStart() }
    }
}
