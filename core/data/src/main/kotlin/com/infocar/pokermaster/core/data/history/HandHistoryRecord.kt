package com.infocar.pokermaster.core.data.history

import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.GameState
import kotlinx.serialization.Serializable

/**
 * Repository 수준에서 주고받는 pure-Kotlin 핸드 히스토리 표현 — M5-A.
 *
 * Room [HandHistoryEntity] 와 달리 JSON 블롭 대신 파싱된 객체를 담아 UI 가 재생 시 즉시 사용
 * 가능하다. Repository 가 엔티티 ↔ record 변환을 담당.
 */
@Serializable
data class HandHistoryRecord(
    val id: Long,
    val mode: String,
    val handIndex: Long,
    val startedAt: Long,
    val endedAt: Long,
    val seedCommitHex: String,
    val serverSeedHex: String,
    val clientSeedHex: String,
    val nonce: Long,
    /** 핸드 시작 직후 상태 — 리플레이 entry point. */
    val initialState: GameState,
    /** 시간순 액션 로그. */
    val actions: List<ActionLogEntry>,
    /** 최종 결과 payload — ShowdownSummary 또는 간이 요약. UI 가 parsing 없이 표시. */
    val resultJson: String,
    val winnerSeat: Int?,
    val potSize: Long,
)

/** 한 개 액션 로그 엔트리 — 누가(seat) 무엇을(action) 했는지. */
@Serializable
data class ActionLogEntry(
    val seat: Int,
    val action: Action,
    /** 액션 적용 시점 기준 street index — 0:preflop / 1:flop / ... (홀덤 기준). */
    val streetIndex: Int = 0,
)
