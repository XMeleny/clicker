package com.local.clicker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.local.clicker.R
import com.local.clicker.domain.TaskStatus
import com.local.clicker.domain.TaskSummary
import com.local.clicker.ui.edit.TaskEditActivity
import com.local.clicker.ui.list.ListUi
import com.local.clicker.ui.list.TaskListContent
import com.local.clicker.ui.list.TaskListFragment
import com.local.clicker.ui.log.LogListFragment
import com.local.clicker.ui.permission.PermissionFragment
import com.local.clicker.ui.permission.PermissionGate
import com.local.clicker.ui.permission.permissionGate
import com.local.clicker.ui.template.TemplateListFragment
import com.local.clicker.ui.theme.ClickerTheme

class MainActivity : FragmentActivity() {
    private var selectedTab by mutableIntStateOf(TAB_TASKS)
    private var gate by mutableStateOf(PermissionGate(false, false))
    private val backToTasks = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = selectTab(TAB_TASKS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        gate = permissionGate(this)
        selectedTab = savedInstanceState?.getInt(KEY_TAB) ?: if (gate.accessibility) TAB_TASKS else TAB_PERMISSIONS
        onBackPressedDispatcher.addCallback(this, backToTasks)
        backToTasks.isEnabled = selectedTab != TAB_TASKS
        setContentView(R.layout.activity_main)
        findViewById<ComposeView>(R.id.main_tabs).setContent {
            ClickerTheme { MainTabs(selectedTab, gate, ::selectTab) }
        }
        if (savedInstanceState == null) {
            selectTab(selectedTab)
            val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
            if (taskId > 0L) startActivity(TaskEditActivity.intent(this, taskId))
        }
    }

    override fun onResume() {
        super.onResume()
        gate = permissionGate(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_TAB, selectedTab)
        super.onSaveInstanceState(outState)
    }

    private fun selectTab(tab: Int) {
        val tag = tabTag(tab)
        val target = supportFragmentManager.findFragmentByTag(tag)
        if (target == null || tab != selectedTab) {
            val currentTag = tabTag(selectedTab)
            val current = supportFragmentManager.findFragmentByTag(currentTag)
            supportFragmentManager.beginTransaction().apply {
                setReorderingAllowed(true)
                if (current != null && current != target) hide(current)
                if (target == null) {
                    val fragment: Fragment = when (tab) {
                        TAB_PERMISSIONS -> PermissionFragment()
                        TAB_TASKS -> TaskListFragment()
                        TAB_TEMPLATES -> TemplateListFragment()
                        else -> LogListFragment()
                    }
                    add(R.id.main_content, fragment, tag)
                } else {
                    show(target)
                }
            }.commitNow()
        }
        selectedTab = tab
        backToTasks.isEnabled = tab != TAB_TASKS
    }

    private fun tabTag(tab: Int): String = when (tab) {
        TAB_PERMISSIONS -> PERMISSIONS_TAG
        TAB_TASKS -> TASKS_TAG
        TAB_TEMPLATES -> TEMPLATES_TAG
        else -> LOGS_TAG
    }

    companion object {
        const val EXTRA_TASK_ID = "taskId"
        private const val KEY_TAB = "selectedTab"
        private const val TAB_PERMISSIONS = 0
        private const val TAB_TASKS = 1
        private const val TAB_TEMPLATES = 2
        private const val TAB_LOGS = 3
        private const val PERMISSIONS_TAG = "permissions"
        private const val TASKS_TAG = "tasks"
        private const val TEMPLATES_TAG = "templates"
        private const val LOGS_TAG = "logs"

        fun intent(context: Context, taskId: Long): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_TASK_ID, taskId)
    }
}

@Composable
private fun MainTabs(selected: Int, gate: PermissionGate, onSelect: (Int) -> Unit) {
    val permissionColor = when {
        !gate.accessibility -> Color(0xFFC62828)
        gate.ready -> Color(0xFF2E7D32)
        else -> Color(0xFFB26A00)
    }
    NavigationBar {
        NavigationBarItem(
            selected = selected == 0,
            onClick = { onSelect(0) },
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = permissionColor) },
            label = { Text("权限") },
        )
        NavigationBarItem(
            selected = selected == 1,
            onClick = { onSelect(1) },
            icon = { Icon(Icons.Default.Checklist, contentDescription = null) },
            label = { Text("任务") },
        )
        NavigationBarItem(
            selected = selected == 2,
            onClick = { onSelect(2) },
            icon = { Icon(Icons.Default.Build, contentDescription = null) },
            label = { Text("动作模板") },
        )
        NavigationBarItem(
            selected = selected == 3,
            onClick = { onSelect(3) },
            icon = { Icon(Icons.Default.History, contentDescription = null) },
            label = { Text("日志") },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainActivityPreview() {
    ClickerTheme {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                TaskListContent(
                    ui = ListUi(listOf(TaskSummary(1, "早晨签到", null, TaskStatus.DRAFT, 0, null, true)), false),
                    busy = false,
                    onOpen = {},
                    onCreate = {},
                    onRun = {},
                    onCancel = {},
                    onDelete = {},
                )
            }
            MainTabs(1, PermissionGate(true, true), {})
        }
    }
}
