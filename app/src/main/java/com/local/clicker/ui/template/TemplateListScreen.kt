package com.local.clicker.ui.template

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    TemplateListContent(templates, onOpen, onCreate, vm::delete)
}

@Composable
internal fun TemplateListContent(
    templates: List<TemplateSummary>,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onDelete: (Long) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize()) {
        AppTitleBar("动作模板", TitleBarAction("新建", onCreate))
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (templates.isEmpty()) item { Text("暂无动作模板") }
            items(templates, key = { it.id }) { template ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(template.id) },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE9EAEC)),
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(template.name, style = MaterialTheme.typography.titleMedium)
                            Text("短信编号 ${template.id}", style = MaterialTheme.typography.bodyMedium)
                            if (template.steps.isEmpty()) {
                                Text("暂无步骤", style = MaterialTheme.typography.bodySmall)
                            } else {
                                template.steps.forEachIndexed { index, step ->
                                    Text("${index + 1}. $step", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (!template.selectable) Text("未完成", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = { pendingDelete = template.id },
                            modifier = Modifier.width(104.dp).height(32.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        ) { Text("删除", style = MaterialTheme.typography.bodySmall) }
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
}

@Preview(showBackground = true)
@Composable
private fun TemplateListPreview() {
    ClickerTheme {
        TemplateListContent(
            listOf(TemplateSummary(1, "签到", listOf("打开 日历", "等待 1 秒", "点击 (420, 860)"), true)),
            {}, {}, {},
        )
    }
}
