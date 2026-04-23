package com.infocar.pokermaster.feature.table

/**
 * 칩 표기 포맷터 — v1.1 §4.7 "원/만원" 금지, K/M/B 사용.
 *
 *  - < 1,000 → "500"
 *  - < 1,000,000 → "1.2K" (소수점 1자리)
 *  - < 1,000,000,000 → "2.5M"
 *  - 그 이상 → "1.2B"
 */
object ChipFormat {
    fun format(chips: Long): String {
        val abs = kotlin.math.abs(chips)
        val sign = if (chips < 0) "-" else ""
        return sign + when {
            abs < 1_000L -> abs.toString()
            abs < 1_000_000L -> formatUnit(abs, 1_000L, "K")
            abs < 1_000_000_000L -> formatUnit(abs, 1_000_000L, "M")
            else -> formatUnit(abs, 1_000_000_000L, "B")
        }
    }

    private fun formatUnit(value: Long, divisor: Long, suffix: String): String {
        val whole = value / divisor
        val remainder = value % divisor
        val tenth = (remainder * 10 / divisor).coerceIn(0, 9)
        return if (tenth == 0L) "$whole$suffix" else "$whole.$tenth$suffix"
    }
}
