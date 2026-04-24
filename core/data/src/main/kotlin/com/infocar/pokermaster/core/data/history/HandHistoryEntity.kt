package com.infocar.pokermaster.core.data.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 핸드 히스토리 Room 엔티티 — M5-A (§1.2.G/§1.2.H 기획설계서).
 *
 * 저장 전략:
 *  - 초기 상태 [initialStateJson] + RNG seed 3종 + [actionsJson] (Action 시퀀스) → 리플레이는
 *    `HoldemReducer.startHand` 재현 후 actions 를 순차 replay 하면 deterministic 복원.
 *  - [resultJson] 은 최종 ShowdownSummary + payouts (UI 즉시 표시용, 리플레이 확인 용).
 *  - 카드/이벤트 하나하나를 쪼개 저장하지 않는다 (초기 시드로 재생 가능하므로).
 *
 * 스키마 버전:
 *  - v1: 이 엔티티. 이후 migration 필요 시 `schemaVersion` 컬럼 추가 고려.
 */
@Entity(
    tableName = "hand_history",
    indices = [
        Index(value = ["started_at"], orders = [Index.Order.DESC]),
        Index(value = ["mode", "started_at"]),
    ],
)
data class HandHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** GameMode.name (enum 안정성 위해 문자열). */
    @ColumnInfo(name = "mode")
    val mode: String,

    /** 해당 세션에서 몇 번째 핸드였는지. 1-based. */
    @ColumnInfo(name = "hand_index")
    val handIndex: Long,

    /** 핸드 시작 epoch ms. 리스트 정렬 키. */
    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    /** 핸드 종료 epoch ms (showdown 후). */
    @ColumnInfo(name = "ended_at")
    val endedAt: Long,

    /** RNG serverSeed SHA-256 commit hex — v1.1 §3.5 Provably Fair 검증용. */
    @ColumnInfo(name = "seed_commit_hex")
    val seedCommitHex: String,

    /** 핸드 종료 후 reveal 된 serverSeed hex. */
    @ColumnInfo(name = "server_seed_hex")
    val serverSeedHex: String,

    /** clientSeed hex. */
    @ColumnInfo(name = "client_seed_hex")
    val clientSeedHex: String,

    /** RNG nonce (= handIndex 일반적). */
    @ColumnInfo(name = "nonce")
    val nonce: Long,

    /** 핸드 시작 직후 GameState 스냅샷 (core:model 의 @Serializable GameState). */
    @ColumnInfo(name = "initial_state_json")
    val initialStateJson: String,

    /** 시간순 Action 시퀀스 JSON — List&lt;ActionLogEntry(seat, action)&gt;. */
    @ColumnInfo(name = "actions_json")
    val actionsJson: String,

    /** 최종 결과 JSON — ShowdownSummary / 승자 seat / payouts. UI list 에 즉시 보여줄 요약. */
    @ColumnInfo(name = "result_json")
    val resultJson: String,

    /** 승자 seat (무승부나 사이드팟은 null — 상세는 resultJson). 리스트 정렬/필터 용 캐시 컬럼. */
    @ColumnInfo(name = "winner_seat")
    val winnerSeat: Int?,

    /** 최종 pot 크기 (합계). 리스트 정렬/필터 용 캐시 컬럼. */
    @ColumnInfo(name = "pot_size")
    val potSize: Long,
)
