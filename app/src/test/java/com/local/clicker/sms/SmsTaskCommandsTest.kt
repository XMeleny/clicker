package com.local.clicker.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SmsTaskCommandsTest {
    @Test fun acceptsOnlyPhoneNumbersEndingIn4211() {
        assertTrue(SmsTaskCommands.allowedSender("+86 138-0000-4211"))
        assertFalse(SmsTaskCommands.allowedSender("13800004212"))
        assertFalse(SmsTaskCommands.allowedSender("ABC4211"))
        assertFalse(SmsTaskCommands.allowedSender("4211"))
    }

    @Test fun parsesStrictLocalTime() {
        val zone = ZoneId.of("Asia/Shanghai")
        val expected = LocalDateTime.of(2026, 10, 1, 8, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(SmsTaskCommand(12, expected), SmsTaskCommands.parse("CLICKER 12 2026-10-01 08:00", zone))
        assertNull(SmsTaskCommands.parse("CLICKER 12 2026-02-30 08:00", zone))
        assertNull(SmsTaskCommands.parse("CLICKER 0 2026-10-01 08:00", zone))
        assertNull(SmsTaskCommands.parse("CLICKER 12 2026-10-01 8:00", zone))
    }

    @Test fun rejectsNonexistentAndAmbiguousLocalTimes() {
        val zone = ZoneId.of("America/New_York")
        assertNull(SmsTaskCommands.parse("CLICKER 1 2026-03-08 02:30", zone))
        assertNull(SmsTaskCommands.parse("CLICKER 1 2026-11-01 01:30", zone))
    }
}
