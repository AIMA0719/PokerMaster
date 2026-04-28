package com.infocar.pokermaster.feature.history.coaching

import com.infocar.pokermaster.core.data.history.HandHistoryRecord
import com.infocar.pokermaster.core.model.ActionType

/**
 * 핸드 기록 → LLM 코칭 prompt. 본인 좌석 액션 + 결과 (net = payout - committed) +
 * 모드/팟 만 포함 — 80 토큰 cap 안에 맞추려 컴팩트.
 *
 * record → prompt 변환은 :core:data (HandHistoryRecord) 의존 — engine 의존 회피 위해
 * feature/history 에 위치.
 *
 * 본인 좌석 없으면 null (코칭 대상 부재).
 */
internal object CoachPromptFormatter {

    fun format(record: HandHistoryRecord): String? {
        val human = record.initialState.players.firstOrNull { it.isHuman } ?: return null
        val seat = human.seat

        val payout = if (record.winnerSeat == seat) record.potSize else 0L
        val committed = human.committedThisHand
        val net = payout - committed
        val outcomeKor = when {
            net > 0L -> "승리(+$net)"
            net < 0L -> "패배($net)"
            else -> "무손실"
        }

        val streetKor = mapOf(0 to "프리", 1 to "플롭", 2 to "턴", 3 to "리버")
        val myActions = record.actions
            .filter { it.seat == seat }
            .joinToString(" ") { entry ->
                val s = streetKor[entry.streetIndex] ?: "S${entry.streetIndex}"
                val amt = if (entry.action.amount > 0L) ":${entry.action.amount}" else ""
                "$s.${entry.action.type}$amt"
            }
            .ifBlank { "(액션 없음)" }

        val foldedPre = record.actions
            .filter { it.seat == seat && it.streetIndex == 0 }
            .let { acts ->
                acts.any { it.action.type == ActionType.FOLD } &&
                    acts.none { it.action.type in VPIP_TYPES }
            }

        return buildString {
            append("[task] 한국어 50자 이내 한 줄 포커 코칭 평. 결과 평가 + 핵심 조언 1가지.\n")
            append("[모드] ${record.mode}\n")
            append("[팟] ${record.potSize}칩\n")
            append("[본인 결과] $outcomeKor")
            if (foldedPre) append(" / 프리폴드")
            append("\n[본인 액션] $myActions\n")
            append("[답변] ")
        }
    }

    private val VPIP_TYPES = setOf(
        ActionType.CALL,
        ActionType.BET,
        ActionType.RAISE,
        ActionType.ALL_IN,
        ActionType.COMPLETE,
    )
}
