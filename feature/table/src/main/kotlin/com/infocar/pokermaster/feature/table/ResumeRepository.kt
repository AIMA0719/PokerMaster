package com.infocar.pokermaster.feature.table

import android.content.Context
import com.infocar.pokermaster.core.model.GameState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 진행 중 핸드 스냅샷 (v1.1 §1.2.D CRITICAL).
 *
 *  - [state]: 현재 게임 상태 (immutable copy).
 *  - [rngServerSeedHex]/[rngClientSeedHex]/[rngNonce]: Rng 재생성용 시드.
 *    Rng 는 deterministic (Knuth Fisher-Yates + SHA-256 DRBG) 이므로 seed 3개면 같은 덱 복원 가능.
 *  - [schemaVersion]: 호환성 키. 1 = 최초.
 */
@Serializable
data class ResumeSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val state: GameState,
    val rngServerSeedHex: String,
    val rngClientSeedHex: String,
    val rngNonce: Long,
    /** 모드 변경 감지용 (v1.1 §1.2.D: 모드 변경 시 무효화). */
    val modeName: String = state.mode.name,
) {
    companion object {
        const val CURRENT_SCHEMA: Int = 1
    }
}

/**
 * SharedPreferences 기반 Resume 저장소. 단일 진행 중 핸드만 보관.
 *
 *  - [save]: 액션 직후 트랜잭션 저장.
 *  - [load]: 앱 재진입 시 유효성(TTL 24h, schema 버전) 검사 후 반환.
 *  - [clear]: '포기'/'지우기' 또는 핸드 완료(showdown ack) 시 제거.
 */
class ResumeRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(snapshot: ResumeSnapshot) {
        val encoded = json.encodeToString(snapshot)
        prefs.edit()
            .putString(KEY_SNAPSHOT, encoded)
            .putLong(KEY_TS, System.currentTimeMillis())
            .apply()
    }

    fun load(): ResumeSnapshot? {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        val snap = runCatching {
            json.decodeFromString(ResumeSnapshot.serializer(), raw)
        }.getOrNull() ?: run {
            clear()
            return null
        }
        if (snap.schemaVersion != ResumeSnapshot.CURRENT_SCHEMA) {
            clear()
            return null
        }
        if (isStale()) {
            clear()
            return null
        }
        return snap
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun isStale(): Boolean {
        val ts = prefs.getLong(KEY_TS, 0L)
        if (ts == 0L) return true
        return System.currentTimeMillis() - ts > TTL_MS
    }

    companion object {
        private const val PREFS_NAME = "pokermaster_resume"
        private const val KEY_SNAPSHOT = "snapshot_json"
        private const val KEY_TS = "saved_ms"
        /** v1.1 §1.2.D: 24시간 경과 시 자동 폐기. */
        const val TTL_MS: Long = 24L * 60 * 60 * 1000
    }
}

/** Snapshot 의 UI 프리뷰 카드용 간이 요약. */
data class ResumePrompt(
    val potSize: Long,
    val myChips: Long,
    val modeName: String,
    val handIndex: Long,
)

fun ResumeSnapshot.toPrompt(): ResumePrompt {
    val pot = state.players.sumOf { it.committedThisHand }
    val me = state.players.firstOrNull { it.isHuman }
    return ResumePrompt(
        potSize = pot,
        myChips = me?.chips ?: 0L,
        modeName = modeName,
        handIndex = state.handIndex,
    )
}

/** Snapshot ↔ GameState 변환 유틸 (state 필드만 바꾼 버전). */
fun ResumeSnapshot.withState(state: GameState): ResumeSnapshot = copy(state = state)
