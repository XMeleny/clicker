package com.local.clicker.ui.edit

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val zone: ZoneId = ZoneId.systemDefault()
private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

fun formatWhen(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(formatter)

fun defaultScheduledAt(now: Long = System.currentTimeMillis()): Long {
    val fiveMinutesLater = now + 5 * 60_000L
    return ((fiveMinutesLater + 59_999L) / 60_000L) * 60_000L
}

fun floorToMinute(millis: Long): Long {
    val local = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().withSecond(0).withNano(0)
    return local.atZone(zone).toInstant().toEpochMilli()
}
