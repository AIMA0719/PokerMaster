package com.infocar.pokermaster.feature.table.sfx

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlin.random.Random

/**
 * 효과음 종류. 실제 raw 리소스는 통합 패스에서 매핑.
 */
enum class SfxKind {
    CardDeal,
    ChipCommit,
    PotSweep,
    Check,
    Fold,
    AllIn,
    HandWin,
}

/**
 * SoundPool 기반 짧은 효과음 재생기.
 *
 * 라이프사이클:
 *  1. [load] — [SfxKind]별 raw 리소스 ID 맵을 전달. 빈 맵도 허용 (무음 동작).
 *  2. [play] — 각 액션 호출부에서 트리거.
 *  3. [release] — ViewModel.onCleared 등에서 해제.
 *
 * 주의: 실제 raw 파일은 이 패스에서 생성하지 않음. 호출부가 나중에 ID 맵을 채워 넘김.
 */
class SoundManager(private val context: Context) {

    private var soundPool: SoundPool? = null

    /** [SfxKind] → SoundPool 내부 sound id. */
    private val loadedIds: MutableMap<SfxKind, Int> = mutableMapOf()

    /** 로드 완료 콜백 — SoundPool.setOnLoadCompleteListener 가 채움. */
    private val readyIds: MutableSet<Int> = mutableSetOf()

    /**
     * raw 리소스 ID 맵을 받아 SoundPool에 비동기 로드.
     * 빈 맵을 주면 SoundPool만 생성되고 실제 재생은 no-op.
     */
    /**
     * 각 [SfxKind]의 Int 값은 `@RawRes` 리소스 ID여야 함.
     * (Kotlin 타입 인자 위치에서는 애너테이션을 강제할 수 없어 주석으로 명시.)
     */
    fun load(resources: Map<SfxKind, Int>) {
        if (soundPool == null) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            soundPool = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(attrs)
                .build()
                .also { pool ->
                    pool.setOnLoadCompleteListener { _, sampleId, status ->
                        if (status == 0) readyIds.add(sampleId)
                    }
                }
        }
        val pool = soundPool ?: return
        for ((kind, resId) in resources) {
            if (loadedIds.containsKey(kind)) continue
            val sid = pool.load(context, resId, /* priority = */ 1)
            loadedIds[kind] = sid
        }
    }

    /**
     * 효과음 재생. 로드되지 않았거나 아직 준비 안 된 경우 조용히 무시.
     *
     * Phase3: pitch jitter — 같은 SFX 가 연속 재생될 때 단조로움 제거. effect 별 jitter 폭은
     * [jitterFor] 가 분류 (HandWin / PotSweep 같은 강한 시그널은 0, CardDeal 은 ±0.08).
     */
    fun play(effect: SfxKind, volume: Float = DEFAULT_VOLUME) {
        val pool = soundPool ?: return
        val sid = loadedIds[effect] ?: return
        if (sid !in readyIds) return
        val clamped = volume.coerceIn(0f, 1f)
        val jitter = jitterFor(effect)
        val rate = if (jitter > 0f) {
            (1f + (Random.nextFloat() - 0.5f) * 2f * jitter).coerceIn(0.5f, 2f)
        } else 1f
        pool.play(sid, clamped, clamped, /* priority = */ 1, /* loop = */ 0, rate)
    }

    /** SFX 종류별 pitch jitter 폭 (±). 0 이면 jitter 없음 (안정 톤). */
    private fun jitterFor(effect: SfxKind): Float = when (effect) {
        SfxKind.HandWin, SfxKind.PotSweep -> 0f
        SfxKind.CardDeal -> 0.08f
        SfxKind.ChipCommit, SfxKind.Check, SfxKind.Fold -> 0.06f
        SfxKind.AllIn -> 0.04f
    }

    /** ViewModel.onCleared / 앱 종료 시 호출. */
    fun release() {
        soundPool?.release()
        soundPool = null
        loadedIds.clear()
        readyIds.clear()
    }

    companion object {
        private const val MAX_STREAMS: Int = 6
        private const val DEFAULT_VOLUME: Float = 0.85f
    }
}
