package com.infocar.pokermaster.feature.table.sfx

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.RawRes

/**
 * 배경음악 (BGM) 재생기. SoundPool 은 짧은 효과음 전용이라 BGM 은 [MediaPlayer] 로 분리.
 *
 * - 자산은 raw resource 로 검색 (`bgm_lobby` / `bgm_table` 등). 자산이 없으면 0 반환 →
 *   no-op (사용자가 OGG 트랙을 직접 추가하기 전까지 silent).
 * - 동일 트랙 재호출 시 무시 (이미 재생 중).
 * - 다른 트랙 호출 시 이전 트랙 release 후 새 인스턴스.
 * - SfxPolicy.bgmEnabled=false 또는 자산 미설치 시 호출자가 [stop]/[release].
 */
class BgmManager(private val context: Context) {

    private var current: MediaPlayer? = null
    private var currentTrackId: Int? = null

    /**
     * raw resource ID 로 BGM 재생. 동일 트랙이면 no-op. 다른 트랙이면 이전 정지 후 신규.
     * MediaPlayer.create 실패 (자산 손상/포맷 미지원) 시 silent fail + 로그.
     */
    fun play(@RawRes trackId: Int, volume: Float = DEFAULT_VOLUME) {
        if (currentTrackId == trackId && current?.isPlaying == true) return
        stop()
        val mp = runCatching { MediaPlayer.create(context, trackId) }
            .onFailure { android.util.Log.w("BgmManager", "create failed for $trackId", it) }
            .getOrNull() ?: return
        mp.isLooping = true
        mp.setVolume(volume, volume)
        runCatching { mp.start() }
            .onFailure {
                android.util.Log.w("BgmManager", "start failed", it)
                runCatching { mp.release() }
                return
            }
        current = mp
        currentTrackId = trackId
    }

    fun stop() {
        runCatching { current?.takeIf { it.isPlaying }?.stop() }
        runCatching { current?.release() }
        current = null
        currentTrackId = null
    }

    fun release() = stop()

    companion object {
        /** BGM 기본 볼륨 — 효과음 (0.85) 보다 낮게. 분위기 유지 + 액션 우선. */
        const val DEFAULT_VOLUME: Float = 0.3f

        /**
         * raw resource 이름으로 트랙 ID 조회. 자산이 없으면 0 → 호출자가 null 처리.
         * 자산 추가 가이드: `feature/table/src/main/res/raw/bgm_lobby.ogg` 등에 OGG/MP3 드롭.
         */
        fun resolveTrack(context: Context, name: String): Int {
            return context.resources.getIdentifier(name, "raw", context.packageName)
        }
    }
}
