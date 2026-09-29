package com.local.clicker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.local.clicker.domain.TaskStatus
import com.local.clicker.domain.TaskSummary
import com.local.clicker.ui.edit.TaskEditScreen
import com.local.clicker.ui.list.ListUi
import com.local.clicker.ui.list.TaskListContent
import com.local.clicker.ui.list.TaskListScreen
import com.local.clicker.ui.permission.PermissionActivity
import com.local.clicker.ui.permission.permissionGate
import com.local.clicker.ui.theme.ClickerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!permissionGate(this).ready) {
            startActivity(Intent(this, PermissionActivity::class.java).apply {
                putExtra(EXTRA_TASK_ID, intent.getLongExtra(EXTRA_TASK_ID, 0L))
            })
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            ClickerTheme {
                MainNavigation(intent.getLongExtra(EXTRA_TASK_ID, 0L))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!permissionGate(this).ready && !isFinishing) {
            startActivity(Intent(this, PermissionActivity::class.java).apply {
                putExtra(EXTRA_TASK_ID, intent.getLongExtra(EXTRA_TASK_ID, 0L))
            })
            finish()
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "taskId"

        fun intent(context: Context, taskId: Long): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_TASK_ID, taskId)
    }
}

@Composable
private fun MainNavigation(startId: Long) {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = if (startId > 0L) "edit/$startId" else "list",
    ) {
        composable("list") {
            TaskListScreen(
                onOpen = { id -> nav.navigate("edit/$id") },
                onCreate = { nav.navigate("edit/0") },
            )
        }
        composable(
            route = "edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            TaskEditScreen(
                taskId = id,
                onBack = { nav.popBackStack() },
                onReplaced = { newId ->
                    nav.navigate("edit/$newId") {
                        popUpTo("edit/$id") { inclusive = true }
                    }
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainActivityPreview() {
    ClickerTheme {
        TaskListContent(
            ui = ListUi(
                listOf(TaskSummary(1, "早晨签到", null, TaskStatus.DRAFT, 0, null, true)),
                false,
            ),
            busy = false,
            onOpen = {},
            onCreate = {},
            onRun = {},
            onCancel = {},
            onDelete = {},
            onCopy = {},
        )
    }
}
