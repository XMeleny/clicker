package com.local.clicker.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapStepTest {
    @Test
    fun rangeCoordinatesStayInsideRectangleWhenDraggedBackwards() {
        val step = TapStep(1, 0, 80, 90, 100, 100, 0, 20, 30)
        val random = Random(42)
        val samples = List(1_000) { step.pickCoordinate(random) }

        assertTrue(samples.all { (x, y) -> x in 20..80 && y in 30..90 })
        assertTrue(samples.map { it.first }.distinct().size > 1)
        assertTrue(samples.map { it.second }.distinct().size > 1)
    }

    @Test
    fun legacyPointRemainsFixedAndPartialRangeIsInvalid() {
        val step = TapStep(1, 0, 25, 35, 100, 100, 0)
        assertEquals(25 to 35, step.pickCoordinate())
        assertTrue(TapPoint(25, 35, 100, 100, 0).isInside())
        assertTrue(!TapPoint(25, 35, 100, 100, 0, 50, null).isInside())
    }
}
