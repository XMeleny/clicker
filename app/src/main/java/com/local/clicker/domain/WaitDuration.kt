package com.local.clicker.domain

import java.math.BigDecimal

fun formatWaitSeconds(waitMs: Long): String =
    BigDecimal.valueOf(waitMs, 3).stripTrailingZeros().toPlainString()

fun parseWaitSeconds(value: String): Long? = runCatching {
    value.trim().toBigDecimal().movePointRight(3).longValueExact()
}.getOrNull()
