package com.infocar.pokermaster.engine.controller

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.Street
import com.infocar.pokermaster.core.model.TableConfig
import com.infocar.pokermaster.engine.rules.Rng
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * 한국식 7스터드 Hi-Lo Declare E2E 통합 시나리오 테스트.
 *
 * 본 테스트는 Team Rules + Team Process 가 각각 ShowdownResolverHiLo + StudReducer.act(DECLARE_*)
 * 를 머지하기 전까지 비활성(@Disabled) 상태로 둔다. 머지 후 다음 항목을 확인:
 *
 *  - 2인 헤즈업: 7th 베팅 종료 → Street.DECLARE 진입
 *  - DECLARE_HI/LO/BOTH 액션 직렬 적용 → Street.SHOWDOWN 자동 전이
 *  - pendingShowdown.payouts 가 선언 룰에 따라 계산
 *  - PotSummary.scoopWinnerSeats 가 단독 양방향 우승 좌석에 채워짐
 *  - 칩 보존 (∑ chips_after == ∑ chips_before)
 *
 * 머지 후 Team Tests 가 @Disabled 제거 + 시나리오 본문 채움. (Team Rules + Process 통합 후 활성화)
 */
class HiLoDeclareIntegrationTest {

    private val hiLoConfig = TableConfig(
        mode = GameMode.SEVEN_STUD_HI_LO,
        seats = 2,
        smallBlind = 0L,
        bigBlind = 0L,
        ante = 10L,
        bringIn = 25L,
    )

    private fun players(vararg chips: Long): List<PlayerState> =
        chips.mapIndexed { i, c ->
            PlayerState(
                seat = i,
                nickname = "P$i",
                isHuman = i == 0,
                personaId = if (i == 0) null else "PRO",
                chips = c,
            )
        }

    private fun rngOf(nonce: Long = 1L): Rng {
        val seed = ByteArray(Rng.SEED_BYTES) { (it + nonce.toInt()).toByte() }
        return Rng.ofSeeds(seed, seed, nonce)
    }

    /**
     * 시나리오 A: 2인 Hi-Lo, 둘 다 HI 선언 → 베스트 hi 가 팟 전체 수령.
     * 머지 후 활성화: payouts.size==1, 승자 chips 증가, 칩 보존.
     */
    @Disabled("Team Rules + Process 통합 후 활성화 — Street.DECLARE 와 ShowdownResolverHiLo 머지 대기")
    @Test fun e2e_two_player_both_hi_payout() {
        var s = StudReducer.startHand(
            config = hiLoConfig,
            players = players(10_000L, 10_000L),
            prevBtnSeat = null,
            rng = rngOf(nonce = 11L),
            handIndex = 1L,
            startingVersion = 0L,
        )
        val totalBefore = s.players.sumOf { it.chips + it.committedThisHand }

        // 7th 까지 콜다운 (체크 가능하면 체크).
        var guard = 0
        while (s.street != Street.DECLARE && s.pendingShowdown == null && guard++ < 80) {
            val seat = s.toActSeat ?: break
            val me = s.players.first { it.seat == seat }
            val act = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
            s = StudReducer.act(s, seat, Action(act), rngOf())
        }
        assertThat(s.street).isEqualTo(Street.DECLARE)

        // 두 좌석 다 HI 선언 (toAct 순서대로).
        guard = 0
        while (s.street == Street.DECLARE && guard++ < 4) {
            val seat = s.toActSeat ?: break
            s = StudReducer.act(s, seat, Action(ActionType.DECLARE_HI), rngOf())
        }
        assertThat(s.pendingShowdown).isNotNull()

        // 칩 보존.
        val totalAfter = s.players.sumOf { it.chips }
        assertThat(totalAfter).isEqualTo(totalBefore)
        // 승자 1명만 payout 보유.
        val winners = s.pendingShowdown!!.payouts.filterValues { it > 0L }.keys
        assertThat(winners).hasSize(1)
    }

    /**
     * 시나리오 B: 2인 Hi-Lo, 한쪽 HI / 한쪽 LO → 양쪽 분배 (홀수칩 hi 측 우선).
     */
    @Disabled("Team Rules + Process 통합 후 활성화 — DECLARE_LO 처리 + half-split 검증")
    @Test fun e2e_two_player_split_hi_lo() {
        // TODO: HI 선언 1좌석 + LO 선언 1좌석 → 두 좌석 모두 양수 payout
        //  + LO 자격 미달이어도 LO 선언자는 payout 0 (Team Rules 룰: forfeit, qualifier 무관)
    }

    /**
     * 시나리오 C: 2인 Hi-Lo, 양쪽 BOTH → 한쪽이 sole-1st-both 면 scoop, 동률 면 forfeit 양쪽.
     */
    @Disabled("Team Rules + Process 통합 후 활성화 — STRICT BOTH forfeit 룰 + recompute")
    @Test fun e2e_two_player_both_both_scoop_or_forfeit() {
        // TODO: 두 좌석 BOTH 선언, scoopWinnerSeats 가 단독 좌석이면 1명, 동률이면 emptySet
        //  + forfeit 시 PotSummary.winnerSeats 도 emptySet (팟이 dead 처리 또는 다음 핸드 이월?)
    }

    /**
     * 시나리오 D: 3인 Hi-Lo — A=HI, B=LO, C=BOTH → C scoop 또는 forfeit 후 A/B 분배.
     */
    @Disabled("Team Rules + Process 통합 후 활성화")
    @Test fun e2e_three_player_mixed_directions() {
        // TODO: 3인 시드 검색 + 결정적 선언 시퀀스
    }
}
