package com.infocar.pokermaster

import android.app.ActivityManager
import android.content.Context

/**
 * 단말 성능 티어 — v1.1 §1.2.O + §5.3.
 *
 * M4 LLM 추론 시 티어별로 자동 분기:
 *  - Flagship/UpperMid: 풀 LLM
 *  - Mid: 폴백 권유 토스트
 *  - Low: 결정형 강제 모드 (LLM 로드 거부)
 *
 * M3 범위에서는 배지/안내 수준. 실제 LLM 분기는 M4 에서 `LlamaCppClient` 가 이 값을 참조.
 */
enum class DeviceTier {
    FLAGSHIP,
    UPPER_MID,
    MID,
    LOW,
}

object DeviceFingerprint {

    /**
     * 런타임 측정 후 티어 산출. Context 가 없으면 [Runtime] 기반 축소 평가.
     *
     * 기준(단순 heuristic):
     *  - totalMem ≥ 6 GB + cores ≥ 8 → Flagship
     *  - totalMem ≥ 4 GB + cores ≥ 6 → UpperMid
     *  - totalMem ≥ 2 GB + cores ≥ 4 → Mid
     *  - 나머지 → Low
     */
    fun classify(context: Context?): DeviceTier {
        val cores = Runtime.getRuntime().availableProcessors()
        val totalMemMb = context?.totalMemMb() ?: (Runtime.getRuntime().maxMemory() / 1024 / 1024)
        return when {
            totalMemMb >= 6_144 && cores >= 8 -> DeviceTier.FLAGSHIP
            totalMemMb >= 4_096 && cores >= 6 -> DeviceTier.UPPER_MID
            totalMemMb >= 2_048 && cores >= 4 -> DeviceTier.MID
            else -> DeviceTier.LOW
        }
    }

    /** 사용자 문구 (짧은 요약, Toast/배지 용). */
    fun label(tier: DeviceTier): String = when (tier) {
        DeviceTier.FLAGSHIP -> "최상급 단말 — 풀 기능 권장"
        DeviceTier.UPPER_MID -> "준최상급 단말 — 풀 기능 가능"
        DeviceTier.MID -> "중급 단말 — LLM 탑재 시 폴백 권장"
        DeviceTier.LOW -> "저사양 단말 — 기본 AI 모드 강제"
    }

    /** M4 에서 이 값이 true 이면 LLM 로드 차단 + 결정형 모드 강제. */
    fun shouldForceFallback(tier: DeviceTier): Boolean = tier == DeviceTier.LOW

    /**
     * Android `ActivityManager.isLowRamDevice()` — 플랫폼이 스스로 저RAM 으로 분류한 단말.
     * 이 플래그가 켜지면 LLM 로드 시 확정적으로 OOM/thrash 하므로 (750MB 모델 + 34MB KV) 차단.
     * Context 가 null 이면 false (보수적 — 차단 전 실물 확인 필요).
     */
    fun isLowRamDevice(context: Context?): Boolean {
        val am = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.isLowRamDevice
    }

    /**
     * LLM 로드 **하드 게이트** — Phase3b-II. 아래 중 하나라도 true 이면 모델 다운로드/로드 차단.
     *  - [shouldForceFallback]: 티어 LOW (totalMem < 2GB 또는 cores < 4)
     *  - [isLowRamDevice]: 플랫폼 저RAM 플래그
     *
     * 반환 문자열은 Unsupported 메시지로 UI 에 그대로 노출.
     */
    fun llmBlockReason(tier: DeviceTier, context: Context?): String? = when {
        shouldForceFallback(tier) -> "저사양 단말 — LLM 로드 불가 (기본 AI 모드로 계속)"
        isLowRamDevice(context) -> "플랫폼 저RAM 단말로 분류 — LLM 로드 차단"
        else -> null
    }

    private fun Context.totalMemMb(): Long {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.totalMem / 1024 / 1024
    }
}
