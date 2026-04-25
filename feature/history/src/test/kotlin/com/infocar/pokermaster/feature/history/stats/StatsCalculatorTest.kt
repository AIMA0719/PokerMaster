package com.infocar.pokermaster.feature.history.stats

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.data.history.ActionLogEntry
import com.infocar.pokermaster.core.data.history.HandHistoryRecord
import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.Street
import com.infocar.pokermaster.core.model.TableConfig
import org.junit.Test

class StatsCalculatorTest {

    @Test
    fun `empty list returns EMPTY`() {
        val o = StatsCalculator.computeFromRecords(emptyList())
        assertThat(o).isEqualTo(StatsOverview.EMPTY)
    }

    @Test
    fun `winner equals human seat counts as won`() {
        val r = mkRecord(humanSeat = 0, winnerSeat = 0, pot = 1_000, mode = "HOLDEM_NL")
        val o = StatsCalculator.computeFromRecords(listOf(r))
        assertThat(o.totalHands).isEqualTo(1)
        assertThat(o.handsWon).isEqualTo(1)
        assertThat(o.winrate).isEqualTo(1.0)
        assertThat(o.totalPotChips).isEqualTo(1_000L)
        assertThat(o.biggestPot).isEqualTo(1_000L)
    }

    @Test
    fun `winner differs from human seat counts as loss`() {
        val r = mkRecord(humanSeat = 0, winnerSeat = 1, pot = 500)
        val o = StatsCalculator.computeFromRecords(listOf(r))
        assertThat(o.handsWon).isEqualTo(0)
        assertThat(o.winrate).isEqualTo(0.0)
    }

    @Test
    fun `null winnerSeat counts as loss (draw or side pot)`() {
        val r = mkRecord(humanSeat = 0, winnerSeat = null, pot = 300)
        val o = StatsCalculator.computeFromRecords(listOf(r))
        assertThat(o.handsWon).isEqualTo(0)
    }

    @Test
    fun `mode breakdown separates wins per mode`() {
        val records = listOf(
            mkRecord(0, 0, 100, "HOLDEM_NL"),
            mkRecord(0, 1, 200, "HOLDEM_NL"),
            mkRecord(0, 0, 300, "SEVEN_STUD"),
        )
        val o = StatsCalculator.computeFromRecords(records)
        assertThat(o.totalHands).isEqualTo(3)
        assertThat(o.handsWon).isEqualTo(2)
        assertThat(o.byMode).hasSize(2)

        val hn = o.byMode.getValue("HOLDEM_NL")
        assertThat(hn.hands).isEqualTo(2)
        assertThat(hn.wonHands).isEqualTo(1)
        assertThat(hn.winrate).isEqualTo(0.5)
        assertThat(hn.biggestPot).isEqualTo(200L)

        val st = o.byMode.getValue("SEVEN_STUD")
        assertThat(st.hands).isEqualTo(1)
        assertThat(st.wonHands).isEqualTo(1)
        assertThat(st.biggestPot).isEqualTo(300L)
    }

    @Test
    fun `biggestPot tracks max across records`() {
        val records = listOf(
            mkRecord(0, 0, 100),
            mkRecord(0, 0, 5_000),
            mkRecord(0, 0, 2_000),
        )
        val o = StatsCalculator.computeFromRecords(records)
        assertThat(o.biggestPot).isEqualTo(5_000L)
        assertThat(o.totalPotChips).isEqualTo(7_100L)
    }

    @Test
    fun `no human in initialState means all losses`() {
        val r = mkRecord(humanSeat = null, winnerSeat = 0, pot = 100)
        val o = StatsCalculator.computeFromRecords(listOf(r))
        assertThat(o.handsWon).isEqualTo(0)
    }

    // -------------------- VPIP / PFR (M7) --------------------

    @Test
    fun `vpip and pfr are zero when no preflop human action`() {
        val r = mkRecord(0, 0, 100, actions = emptyList())
        val o = StatsCalculator.computeFromRecords(listOf(r))
        assertThat(o.vpip).isEqualTo(0.0)
        assertThat(o.pfr).isEqualTo(0.0)
    }

    @Test
    fun `human preflop call counts vpip but not pfr`() {
        val r = mkRecord(
            humanSeat = 0, winnerSeat = 0, pot = 100,
            actions = listOf(
                ActionLogEntry(seat = 0, action = Action(ActionType.CALL), streetIndex = 0),
            ),
        )
        val o = StatsCalculator.computeFromRecords(listOf(r))
        assertThat(o.vpip).isEqualTo(1.0)
        assertThat(o.pfr).isEqualTo(0.0)
    }

    @Test
    fun `human preflop raise counts both vpip and pfr`() {
        val r = mkRecord(
            humanSeat = 0, winnerSeat = 0, pot = 200,
            actions = listOf(
                ActionLogEntry(seat = 0, action = Action(ActionType.RAISE, 100L), streetIndex = 0),
            ),
        )
        val o = StatsCalculator.computeFromRecords(listOf(r))
        assertThat(o.vpip).isEqualTo(1.0)
        assertThat(o.pfr).isEqualTo(1.0)
    }

    @Test
    fun `opponent action does not count toward human vpip pfr`() {
        val r = mkRecord(
            humanSeat = 0, winnerSeat = 1, pot = 50,
            actions = listOf(
                ActionLogEntry(seat = 1, action = Action(ActionType.RAISE, 100L), streetIndex = 0),
            ),
        )
        val o = StatsCalculator.computeFromRecords(listOf(r))
        assertThat(o.vpip).isEqualTo(0.0)
        assertThat(o.pfr).isEqualTo(0.0)
    }

    @Test
    fun `flop action does not count toward preflop vpip pfr`() {
        val r = mkRecord(
            humanSeat = 0, winnerSeat = 0, pot = 100,
            actions = listOf(
                ActionLogEntry(seat = 0, action = Action(ActionType.RAISE, 100L), streetIndex = 1),
            ),
        )
        val o = StatsCalculator.computeFromRecords(listOf(r))
        assertThat(o.vpip).isEqualTo(0.0)
        assertThat(o.pfr).isEqualTo(0.0)
    }

    private fun mkRecord(
        humanSeat: Int?,
        winnerSeat: Int?,
        pot: Long,
        mode: String = "HOLDEM_NL",
        actions: List<ActionLogEntry>,
    ): HandHistoryRecord =
        mkRecord(humanSeat, winnerSeat, pot, mode).copy(actions = actions)

    private fun mkRecord(
        humanSeat: Int?,
        winnerSeat: Int?,
        pot: Long,
        mode: String = "HOLDEM_NL",
    ): HandHistoryRecord {
        val p0 = PlayerState(
            seat = 0,
            nickname = "p0",
            isHuman = humanSeat == 0,
            chips = 10_000,
        )
        val p1 = PlayerState(
            seat = 1,
            nickname = "p1",
            isHuman = humanSeat == 1,
            chips = 10_000,
        )
        val state = GameState(
            mode = GameMode.valueOf(mode),
            config = TableConfig(mode = GameMode.valueOf(mode), seats = 2),
            stateVersion = 0,
            handIndex = 1,
            players = listOf(p0, p1),
            btnSeat = 0,
            toActSeat = 0,
            street = Street.PREFLOP,
        )
        return HandHistoryRecord(
            id = 0,
            mode = mode,
            handIndex = 1,
            startedAt = 0,
            endedAt = 0,
            seedCommitHex = "",
            serverSeedHex = "",
            clientSeedHex = "",
            nonce = 0,
            initialState = state,
            actions = emptyList<ActionLogEntry>(),
            resultJson = "{}",
            winnerSeat = winnerSeat,
            potSize = pot,
        )
    }
}
