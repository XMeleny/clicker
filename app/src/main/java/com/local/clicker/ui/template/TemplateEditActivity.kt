package com.local.clicker.ui.template

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.clicker.ClickerApp
import com.local.clicker.domain.DraftOpenApp
import com.local.clicker.domain.DraftStep
import com.local.clicker.domain.DraftTap
import com.local.clicker.domain.DraftWait
import com.local.clicker.domain.EditResult
import com.local.clicker.domain.ScriptEditor
import com.local.clicker.domain.StepKind
import com.local.clicker.exec.ClickerAccessibilityService
import com.local.clicker.exec.PickerBus
import com.local.clicker.ui.AppTitleBar
import com.local.clicker.ui.TitleBarAction
import com.local.clicker.ui.edit.AppOption
import com.local.clicker.ui.edit.KindDialog
import com.local.clicker.ui.edit.StepCard
import com.local.clicker.ui.edit.invalid
import com.local.clicker.ui.edit.label
import com.local.clicker.ui.theme.ClickerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TemplateEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClickerTheme {
                TemplateEditScreen(intent.getLongExtra(EXTRA_ID, 0L), ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "templateId"
        fun intent(context: Context, id: Long): Intent =
            Intent(context, TemplateEditActivity::class.java).putExtra(EXTRA_ID, id)
    }
}

data class TemplateEditUi(
    val name: String = "未命名模板",
    val steps: List<DraftStep> = emptyList(),
    val expandedKey: Long? = null,
    val apps: List<AppOption> = emptyList(),
    val limitHint: String? = null,
    val loaded: Boolean = false,
)

class TemplateEditViewModel(app: Application, private val id: Long) : AndroidViewModel(app) {
    private val repository = (app as ClickerApp).graph.templates
    private val _ui = MutableStateFlow(TemplateEditUi())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val pm = app.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .map { AppOption(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
            val draft = if (id > 0) repository.load(id) else null
            _ui.update {
                it.copy(name = draft?.name ?: it.name, steps = draft?.steps.orEmpty(), apps = apps, loaded = true)
            }
        }
        viewModelScope.launch {
            PickerBus.events.collect { event ->
                when (event) {
                    is PickerBus.Event.Point -> _ui.update {
                        it.copy(
                            steps = ScriptEditor.update(it.steps, event.key, DraftTap(event.key, 0, event.point)),
                            expandedKey = event.key,
                        )
                    }
                    is PickerBus.Event.Failed -> toast(event.message)
                }
            }
        }
    }

    fun setName(value: String) = _ui.update { it.copy(name = value.take(40)) }
    fun toggle(key: Long) = _ui.update { it.copy(expandedKey = if (it.expandedKey == key) null else key) }
    fun add(kind: StepKind) = applyEdit(ScriptEditor.append(_ui.value.steps, kind))
    fun insert(index: Int, kind: StepKind) = applyEdit(ScriptEditor.insert(_ui.value.steps, index + 1, kind))
    fun duplicate(index: Int) = applyEdit(ScriptEditor.duplicate(_ui.value.steps, index))
    fun remove(index: Int) = _ui.update { it.copy(steps = ScriptEditor.remove(it.steps, index)) }
    fun move(key: Long, delta: Int) {
        _ui.update { state ->
            val index = state.steps.indexOfFirst { it.key == key }
            state.copy(steps = ScriptEditor.moveBy(state.steps, index, delta))
        }
    }
    fun changeType(index: Int, kind: StepKind) = _ui.update {
        it.copy(steps = ScriptEditor.changeType(it.steps, index, kind))
    }
    fun updateStep(step: DraftStep) = _ui.update {
        it.copy(steps = ScriptEditor.update(it.steps, step.key, step))
    }
    fun requestPick(key: Long) {
        val service = ClickerAccessibilityService.instance
        if (service == null) toast("无障碍服务未开启") else service.beginPick(key)
    }
    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val state = _ui.value
            repository.save(id, state.name, state.steps)
                .onSuccess { onDone() }
                .onFailure { toast(it.message ?: "保存失败") }
        }
    }
    private fun applyEdit(result: EditResult) {
        when (result) {
            is EditResult.Updated -> _ui.update {
                it.copy(steps = result.steps, expandedKey = result.focusKey ?: it.expandedKey, limitHint = null)
            }
            is EditResult.AtLimit -> _ui.update { it.copy(limitHint = "已达 30 步上限") }
        }
    }
    private fun toast(message: String) = Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
}

@Composable
private fun TemplateEditScreen(id: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val vm: TemplateEditViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TemplateEditViewModel(context.applicationContext as Application, id) as T
            }
        },
    )
    val ui by vm.ui.collectAsState()
    if (!ui.loaded) return
    TemplateEditContent(ui, onSave = { vm.save(onDone) }, vm = vm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateEditContent(ui: TemplateEditUi, onSave: () -> Unit, vm: TemplateEditViewModel?) {
    var kindTarget by remember { mutableStateOf<Int?>(null) }
    var changeTarget by remember { mutableStateOf<Int?>(null) }
    var pickingApp by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize()) {
        AppTitleBar(if (ui.name.isBlank()) "动作模板" else ui.name, TitleBarAction("保存", onSave))
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = ui.name,
                onValueChange = { vm?.setName(it) },
                label = { Text("模板名称") },
                modifier = Modifier.fillMaxWidth(),
            )
            ui.limitHint?.let { Text(it) }
            ui.steps.forEachIndexed { index, step ->
                StepCard(
                    index = index,
                    step = step,
                    apps = ui.apps,
                    expanded = ui.expandedKey == step.key,
                    readOnly = vm == null,
                    invalid = step.invalid(ui.apps),
                    isFirst = index == 0,
                    isLast = index == ui.steps.lastIndex,
                    busy = false,
                    saved = false,
                    onToggle = { vm?.toggle(step.key) },
                    onMove = { vm?.move(step.key, it) },
                    onRemove = { vm?.remove(index) },
                    onDuplicate = { vm?.duplicate(index) },
                    onInsert = { kindTarget = index },
                    onChangeType = { changeTarget = index },
                    onTrial = {},
                    onPick = { vm?.requestPick(step.key) },
                    onPickApp = { pickingApp = step.key },
                    onWait = { vm?.updateStep(DraftWait(step.key, 0, it.toLongOrNull())) },
                )
            }
            Button(onClick = { kindTarget = ui.steps.size }, enabled = ui.steps.size < 30) {
                Text("添加步骤")
            }
        }
    }
    kindTarget?.let { index ->
        KindDialog(
            onPick = { kind ->
                if (index == ui.steps.size) vm?.add(kind) else vm?.insert(index, kind)
                kindTarget = null
            },
            onDismiss = { kindTarget = null },
        )
    }
    changeTarget?.let { index ->
        AlertDialog(
            onDismissRequest = { changeTarget = null },
            title = { Text("更换类型") },
            text = { Text("更换后这一步已填写的内容会丢掉。") },
            confirmButton = {
                Column {
                    StepKind.entries.forEach { kind ->
                        TextButton(onClick = { vm?.changeType(index, kind); changeTarget = null }) {
                            Text(kind.label())
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { changeTarget = null }) { Text("留下") } },
        )
    }
    pickingApp?.let { key ->
        ModalBottomSheet(onDismissRequest = { pickingApp = null }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.apps.forEach { app ->
                    TextButton(onClick = {
                        vm?.updateStep(DraftOpenApp(key, 0, app.packageName))
                        pickingApp = null
                    }) { Text(app.label) }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TemplateEditPreview() {
    ClickerTheme { TemplateEditContent(TemplateEditUi(steps = listOf(DraftWait(1, 0, 1000)), loaded = true), {}, null) }
}
