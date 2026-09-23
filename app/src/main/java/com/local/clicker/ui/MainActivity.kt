package com.local.clicker.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.local.clicker.exec.ClickerAccessibilityService
import com.local.clicker.ui.edit.TaskEditScreen
import com.local.clicker.ui.list.TaskListScreen
import com.local.clicker.ui.permission.PermissionScreen
import com.local.clicker.ui.theme.ClickerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClickerTheme {
                var gate by remember { mutableStateOf(permissionGate(this)) }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) gate = permissionGate(this@MainActivity)
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                if (!gate.ready) {
                    PermissionScreen(gate)
                } else {
                    val nav = rememberNavController()
                    val startId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
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
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "taskId"

        fun intent(context: Context, taskId: Long): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_TASK_ID, taskId)
    }
}

data class PermissionGate(val notifications: Boolean, val accessibility: Boolean) {
    val ready: Boolean = notifications && accessibility
}

fun permissionGate(context: Context): PermissionGate {
    val notifications = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    val component = ComponentName(context, ClickerAccessibilityService::class.java).flattenToString()
    return PermissionGate(notifications, enabled.contains(component))
}
