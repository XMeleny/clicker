package com.local.clicker.ui.edit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.local.clicker.ui.theme.ClickerTheme

class TaskEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var taskId by rememberSaveable { mutableLongStateOf(intent.getLongExtra(EXTRA_TASK_ID, 0L)) }
            ClickerTheme {
                TaskEditScreen(
                    taskId = taskId,
                    onBack = ::finish,
                    onReplaced = { taskId = it },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TASK_ID = "taskId"

        fun intent(context: Context, taskId: Long): Intent =
            Intent(context, TaskEditActivity::class.java).putExtra(EXTRA_TASK_ID, taskId)
    }
}
