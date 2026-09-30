package com.local.clicker.data

import android.content.pm.PackageManager
import com.local.clicker.domain.DraftOpenApp
import com.local.clicker.domain.DraftStep
import com.local.clicker.domain.MAX_STEPS
import com.local.clicker.domain.NAME_MAX
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class TemplateSummary(
    val id: Long,
    val name: String,
    val steps: List<String>,
    val selectable: Boolean,
)

data class TemplateDraft(val id: Long, val name: String, val steps: List<DraftStep>)

class RoomTemplateRepository(
    private val dao: ClickerDao,
    private val packageManager: PackageManager,
) {
    fun observe(): Flow<List<TemplateSummary>> =
        combine(dao.observeTemplates(), dao.observeTemplateSteps()) { templates, steps ->
            val byTemplate = steps.groupBy { it.templateId }
            templates.map { template ->
                val rows = byTemplate[template.id].orEmpty()
                val drafts = rows.map { it.toDraft() }
                TemplateSummary(
                    id = template.id,
                    name = template.name,
                    steps = rows.map { it.toTaskStep().summary(packageManager) },
                    selectable = drafts.isNotEmpty() && drafts.size <= MAX_STEPS && drafts.all { it.selectable() },
                )
            }
        }

    suspend fun load(id: Long): TemplateDraft? {
        val template = dao.getTemplate(id) ?: return null
        return TemplateDraft(id, template.name, dao.templateStepsOf(id).map { it.toDraft() })
    }

    suspend fun loadSelectable(id: Long): TemplateDraft? = load(id)?.takeIf { draft ->
        draft.steps.isNotEmpty() && draft.steps.size <= MAX_STEPS && draft.steps.all { it.selectable() }
    }

    suspend fun save(id: Long, name: String, steps: List<DraftStep>): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > NAME_MAX) {
            return Result.failure(IllegalArgumentException("请填写 1～40 个字符的模板名称"))
        }
        if (steps.size > MAX_STEPS) {
            return Result.failure(IllegalArgumentException("最多只能有 30 个步骤"))
        }
        val now = System.currentTimeMillis()
        val rows = steps.mapIndexed { index, step -> step.withIndex(index).toTemplateStep(id) }
        if (id <= 0L) {
            return Result.success(dao.insertTemplateWithSteps(ActionTemplateEntity(name = trimmed, updatedAt = now), rows))
        }
        val existing = dao.getTemplate(id)
            ?: return Result.failure(IllegalArgumentException("模板不存在"))
        dao.saveTemplate(existing.copy(name = trimmed, updatedAt = now), rows)
        return Result.success(id)
    }

    suspend fun delete(id: Long) = dao.deleteTemplate(id)

    private fun DraftStep.selectable(): Boolean = when (this) {
        is DraftOpenApp -> isComplete() && packageName != null &&
            packageManager.getLaunchIntentForPackage(packageName) != null
        else -> isComplete()
    }
}

private fun TemplateStepEntity.toTaskStep(): StepEntity = StepEntity(
    id = id,
    taskId = 0,
    orderIndex = orderIndex,
    type = type,
    complete = complete,
    packageName = packageName,
    waitMs = waitMs,
    x = x,
    y = y,
    screenWidth = screenWidth,
    screenHeight = screenHeight,
    rotation = rotation,
)

private fun TemplateStepEntity.toDraft(): DraftStep = toTaskStep().toDraft()

private fun DraftStep.toTemplateStep(templateId: Long): TemplateStepEntity {
    val taskStep = toEntity(0)
    return TemplateStepEntity(
        id = 0,
        templateId = templateId,
        orderIndex = taskStep.orderIndex,
        type = taskStep.type,
        complete = taskStep.complete,
        packageName = taskStep.packageName,
        waitMs = taskStep.waitMs,
        x = taskStep.x,
        y = taskStep.y,
        screenWidth = taskStep.screenWidth,
        screenHeight = taskStep.screenHeight,
        rotation = taskStep.rotation,
    )
}
