package com.infocar.pokermaster.feature.lobby

/**
 * 사용자 티어. totalEarnedLifetime 기반 임계치.
 *
 * 진급 모달 (Phase C) 은 enum.ordinal 비교 — DIAMOND 가 최고.
 * 임계치는 게임 밸런스 결정 — 향후 사용자 결정으로 조정 가능.
 */
enum class TierLevel(
    val emoji: String,
    val label: String,
    val threshold: Long,
    /** Phase C2: 진급 시 1회성 보상 chip. BRONZE 는 시작 티어라 0. */
    val rewardChips: Long,
) {
    BRONZE("🥉", "BRONZE", 0L, rewardChips = 0L),
    SILVER("🥈", "SILVER", 50_000L, rewardChips = 5_000L),
    GOLD("🥇", "GOLD", 200_000L, rewardChips = 15_000L),
    PLATINUM("💎", "PLATINUM", 1_000_000L, rewardChips = 50_000L),
    DIAMOND("👑", "DIAMOND", 5_000_000L, rewardChips = 200_000L);

    /** 다음 티어 — DIAMOND 면 null. */
    fun next(): TierLevel? {
        val ordinals = entries
        return ordinals.getOrNull(ordinal + 1)
    }

    companion object {
        fun forLifetime(lifetime: Long): TierLevel = when {
            lifetime >= DIAMOND.threshold -> DIAMOND
            lifetime >= PLATINUM.threshold -> PLATINUM
            lifetime >= GOLD.threshold -> GOLD
            lifetime >= SILVER.threshold -> SILVER
            else -> BRONZE
        }
    }
}
