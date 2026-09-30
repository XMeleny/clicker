package com.local.clicker.ui.template

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.clicker.ClickerApp
import com.local.clicker.data.TemplateSummary
import com.local.clicker.ui.AppTitleBar
import com.local.clicker.ui.TitleBarAction
import com.local.clicker.ui.theme.ClickerTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplateListViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as ClickerApp).graph.templates
    val templates = repository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}

@Composable
fun TemplateListScreen(onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    val vm: TemplateListViewModel = viewModel()
    val templates by vm.templates.collectAsState()
    val context = LocalContext.current
    var smsEnabled by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    val requestSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        smsEnabled = it
    }
    TemplateListContent(templates, onOpen, onCreate, vm::delete, smsEnabled) {
        requestSms.launch(Manifest.permission.RECEIVE_SMS)
    }
}

@Composable
internal fun TemplateListContent(
    templates: List<TemplateSummary>,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onDelete: (Long) -> Unit,
    smsEnabled: Boolean,
    onRequestSms: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    var showSmsFormat by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        AppTitleBar("动作模板", TitleBarAction("新建", onCreate))
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("短信建任务", modifier = Modifier.weight(1f))
                    if (smsEnabled) Text("已开启")
                    else OutlinedButton(onClick = onRequestSms) { Text("开启") }
                    IconButton(onClick = { showSmsFormat = true }) {
                        Icon(Icons.Default.Info, contentDescription = "查看短信格式")
                    }
                }
            }
            if (templates.isEmpty()) item { Text("暂无动作模板") }
            items(templates, key = { it.id }) { template ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(template.id) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(template.name)
                        Text("短信编号 ${template.id}", style = MaterialTheme.typography.bodySmall)
                        template.steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                        if (!template.selectable) Text("未完成")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onOpen(template.id) }) { Text("编辑") }
                            TextButton(onClick = { pendingDelete = template.id }) { Text("删除") }
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除动作模板") },
            text = { Text("已加入任务的步骤不会受影响。") },
            confirmButton = {
                TextButton(onClick = { onDelete(id); pendingDelete = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
    if (showSmsFormat) {
        AlertDialog(
            onDismissRequest = { showSmsFormat = false },
            title = { Text("短信格式") },
            text = { Text("从尾号 4211 的号码发送：\nCLICKER 模板编号 YYYY-MM-DD HH:mm\n例如：CLICKER 12 2026-10-01 08:00") },
            confirmButton = { TextButton(onClick = { showSmsFormat = false }) { Text("知道了") } },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TemplateListPreview() {
    ClickerTheme {
        TemplateListContent(
            listOf(TemplateSummary(1, "签到", listOf("打开 日历", "等待 1 秒", "点击 (420, 860)"), true)),
            {}, {}, {}, false, {},
        )
    }
}
