package com.infocar.pokermaster.feature.table

/**
 * 칩 표기 포맷터 — 한국식. 가장 높은 단위 + 바로 아래 단위 두 개만 표기.
 *
 *  단위 stack: 경 > 조 > 억 > 만 > 원 (가장 작은 단위)
 *
 *  -          ..  9,999          → "8,500원"          (원만 표기)
 *  -    1만   .. 9,999만 9,999    → "1만 5,000원"      (만 + 원)
 *  -    1억   .. 9,999억 9,999만   → "1억 5,000만"     (억 + 만)
 *  -    1조   .. 9,999조 9,999억   → "1조 5,000억"     (조 + 억)
 *  -    1경   ..                  → "1경 5,000조"     (경 + 조)
 *
 *  사용자 예: 1조 + 1억 + 1만 → "1조 1억" (두 단위만, 만 무시).
 */
object ChipFormat {
    private const val MAN: Long = 10_000L
    private const val EOK: Long = 100_000_000L
    private const val JO: Long = 1_000_000_000_000L
    private const val GYEONG: Long = 10_000_000_000_000_000L  // 1경 = 10^16

    fun format(chips: Long): String {
        val abs = kotlin.math.abs(chips)
        val sign = if (chips < 0) "-" else ""
        return sign + when {
            abs < MAN -> "%,d원".format(abs)
            abs < EOK -> {
                val man = abs / MAN
                val rem = abs % MAN
                if (rem == 0L) "%,d만".format(man) else "%,d만 %,d원".format(man, rem)
            }
            abs < JO -> {
                val eok = abs / EOK
                val man = (abs % EOK) / MAN
                if (man == 0L) "%,d억".format(eok) else "%,d억 %,d만".format(eok, man)
            }
            abs < GYEONG -> {
                val jo = abs / JO
                val eok = (abs % JO) / EOK
                if (eok == 0L) "%,d조".format(jo) else "%,d조 %,d억".format(jo, eok)
            }
            else -> {
                val gyeong = abs / GYEONG
                val jo = (abs % GYEONG) / JO
                if (jo == 0L) "%,d경".format(gyeong) else "%,d경 %,d조".format(gyeong, jo)
            }
        }
    }
}
