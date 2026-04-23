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

    private fun Context.totalMemMb(): Long {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.totalMem / 1024 / 1024
    }
}
