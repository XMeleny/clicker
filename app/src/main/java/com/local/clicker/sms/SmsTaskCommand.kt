package com.local.clicker.sms

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

internal data class SmsTaskCommand(val templateId: Long, val scheduledAt: Long)

internal object SmsTaskCommands {
    private val pattern = Regex("^CLICKER\\s+([1-9][0-9]*)\\s+([0-9]{4}-[0-9]{2}-[0-9]{2})\\s+([0-9]{2}:[0-9]{2})$")
    private val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
        .withResolverStyle(ResolverStyle.STRICT)

    fun allowedSender(address: String?): Boolean {
        val normalized = address?.trim() ?: return false
        if (!normalized.matches(Regex("^\\+?[0-9 ()-]+$"))) return false
        val digits = normalized.filter(Char::isDigit)
        return digits.length >= 7 && digits.endsWith("4211")
    }

    fun parse(body: String, zone: ZoneId = ZoneId.systemDefault()): SmsTaskCommand? {
        val match = pattern.matchEntire(body.trim()) ?: return null
        val templateId = match.groupValues[1].toLongOrNull() ?: return null
        val localTime = runCatching {
            LocalDateTime.parse("${match.groupValues[2]} ${match.groupValues[3]}", formatter)
        }.getOrNull() ?: return null
        val offsets = zone.rules.getValidOffsets(localTime)
        if (offsets.size != 1) return null
        return SmsTaskCommand(templateId, localTime.toInstant(offsets.single()).toEpochMilli())
    }
}
