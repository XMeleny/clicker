package com.local.clicker.domain

import java.util.concurrent.atomic.AtomicLong

object ScriptEditor {
    private val nextKey = AtomicLong(-1L)

    fun newKey(): Long = nextKey.getAndDecrement()

    fun append(steps: List<DraftStep>, kind: StepKind): EditResult =
        insert(steps, steps.size, kind)

    fun insert(steps: List<DraftStep>, index: Int, kind: StepKind): EditResult {
        if (steps.size >= MAX_STEPS) return EditResult.AtLimit(steps)
        val card = blank(kind)
        val at = index.coerceIn(0, steps.size)
        val next = steps.toMutableList()
        next.add(at, card)
        return EditResult.Updated(reindex(next), card.key)
    }

    fun remove(steps: List<DraftStep>, index: Int): List<DraftStep> {
        if (index !in steps.indices) return steps
        return reindex(steps.filterIndexed { i, _ -> i != index })
    }

    fun move(steps: List<DraftStep>, from: Int, to: Int): List<DraftStep> {
        if (from !in steps.indices || to !in steps.indices || from == to) return steps
        val next = steps.toMutableList()
        val item = next.removeAt(from)
        next.add(to, item)
        return reindex(next)
    }

    fun moveBy(steps: List<DraftStep>, index: Int, delta: Int): List<DraftStep> {
        val to = index + delta
        if (index !in steps.indices || to !in steps.indices) return steps
        return move(steps, index, to)
    }

    fun duplicate(steps: List<DraftStep>, index: Int): EditResult {
        if (steps.size >= MAX_STEPS) return EditResult.AtLimit(steps)
        if (index !in steps.indices) return EditResult.Updated(steps, null)
        val copy = steps[index].copyWithKey(newKey())
        val next = steps.toMutableList()
        next.add(index + 1, copy)
        return EditResult.Updated(reindex(next), copy.key)
    }

    fun changeType(steps: List<DraftStep>, index: Int, kind: StepKind): List<DraftStep> {
        if (index !in steps.indices) return steps
        val old = steps[index]
        if (old.kind == kind) return steps
        val replaced = blank(kind, old.key).withIndex(old.orderIndex)
        return steps.mapIndexed { i, step -> if (i == index) replaced else step }
    }

    fun update(steps: List<DraftStep>, key: Long, step: DraftStep): List<DraftStep> =
        steps.map { if (it.key == key) step.withIndex(it.orderIndex) else it }

    fun fieldsReady(steps: List<DraftStep>): Boolean =
        steps.isNotEmpty() && steps.size <= MAX_STEPS && steps.all { it.isComplete() }

    private fun blank(kind: StepKind, key: Long = newKey()): DraftStep = when (kind) {
        StepKind.OPEN_APP -> DraftOpenApp(key, 0, null)
        StepKind.WAIT -> DraftWait(key, 0, null)
        StepKind.TAP -> DraftTap(key, 0, null)
    }

    private fun reindex(steps: List<DraftStep>): List<DraftStep> =
        steps.mapIndexed { index, step -> step.withIndex(index) }
}
