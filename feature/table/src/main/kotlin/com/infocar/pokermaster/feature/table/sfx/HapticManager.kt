package com.infocar.pokermaster.feature.table.sfx

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 액션별 햅틱 피드백 래퍼.
 *
 * - API 31+: [VibratorManager]에서 default [Vibrator]를 획득.
 * - API 26~30: 레거시 [Context.VIBRATOR_SERVICE] 사용.
 * - API 25 이하: 호출은 no-op (현재 프로젝트 minSdk와 무관하게 방어적 처리).
 *
 * [SfxPolicy.hapticEnabled]가 false면 호출부에서 스킵해야 함 — 이 클래스는 단일 책임.
 */
class HapticManager(context: Context) {

    private val vibrator: Vibrator? = resolveVibrator(context.applicationContext)

    /** 액션 바 버튼 탭 (Check/Call/Raise 등 일반 터치). */
    fun onAction() {
        vibrateOneShot(durationMs = 12L, amplitude = AMPLITUDE_LIGHT)
    }

    /** 칩 확정 (베팅 commit). 약간 더 단단한 느낌. */
    fun onChipCommit() {
        vibrateOneShot(durationMs = 22L, amplitude = AMPLITUDE_MEDIUM)
    }

    /** 카드 플립 (펼치기/쇼다운). 짧고 가벼운 틱. */
    fun onCardFlip() {
        vibrateOneShot(durationMs = 8L, amplitude = AMPLITUDE_LIGHT)
    }

    /** 핸드 승리. 짧은 펄스 두 번. */
    fun onWin() {
        vibrateWaveform(
            timings = longArrayOf(0L, 35L, 50L, 55L),
            amplitudes = intArrayOf(0, AMPLITUDE_STRONG, 0, AMPLITUDE_STRONG),
        )
    }

    /** Bust (탈락/올인 후 패배). 길고 둔탁한 단일 진동. */
    fun onBust() {
        vibrateOneShot(durationMs = 140L, amplitude = AMPLITUDE_STRONG)
    }

    private fun vibrateOneShot(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }

    private fun vibrateWaveform(timings: LongArray, amplitudes: IntArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, /* repeat = */ -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(timings, -1)
        }
    }

    @SuppressLint("NewApi")
    private fun resolveVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    companion object {
        private const val AMPLITUDE_LIGHT: Int = 60
        private const val AMPLITUDE_MEDIUM: Int = 140
        private const val AMPLITUDE_STRONG: Int = 220
    }
}
