package com.infocar.pokermaster.feature.table

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.TableConfig
import com.infocar.pokermaster.engine.controller.GameController
import com.infocar.pokermaster.engine.controller.ResumeSeed
import com.infocar.pokermaster.engine.controller.llm.LlmAdvisor
import com.infocar.pokermaster.engine.rules.Rng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 테이블 화면 ViewModel. [GameController] 를 감싸 [StateFlow] 로 UI 에 노출.
 *
 * 핵심 책임:
 *  - NPC 턴 자동 처리 (백그라운드 tick).
 *  - Resume 스냅샷 로드/저장 (v1.1 §1.2.D).
 *  - 사람 액션 디스패치 + 다음 핸드 / 항복 처리.
 *
 * M3 MVP: 2인 헤즈업 홀덤. 로비에서 모드 전달 → NPC 기본 persona = PRO.
 */
class TableViewModel private constructor(
    private val config: TableConfig,
    private val initialPlayers: List<PlayerState>,
    private val resumeRepo: ResumeRepository?,
    /** NPC 턴 간 UI 연출용 최소 대기(ms). 너무 빠르면 사용자가 NPC 액션을 놓침. */
    private val npcDelayMs: Long = 700L,
    /**
     * LLM advisor. null 이면 기존 DecisionCore/persona 결정만 사용 (모델 미로드/미지원 단말).
     * Hilt 주입은 [createDefault] 팩토리에서 옵션으로 받아 내려준다.
     */
    private val llmAdvisor: LlmAdvisor? = null,
) : ViewModel() {

    private var controller: GameController = GameController(config, initialPlayers)

    private val _state = MutableStateFlow(controller.state)
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _resumePrompt = MutableStateFlow<ResumePrompt?>(null)
    val resumePrompt: StateFlow<ResumePrompt?> = _resumePrompt.asStateFlow()

    private var tickJob: Job? = null

    init {
        val snap = resumeRepo?.load()
        if (snap != null && snap.state.mode == config.mode) {
            // 복원 제안만 노출 — 사용자 결정 전까지 NPC tick 시작하지 않음.
            _resumePrompt.value = snap.toPrompt()
        } else {
            // 새 핸드부터 바로 진행.
            startTicking()
        }
    }

    // ---------------------------------------------------------------- Actions

    fun onHumanAction(action: Action) {
        val s = _state.value
        val seat = s.toActSeat ?: return
        val p = s.players.firstOrNull { it.seat == seat } ?: return
        if (!p.isHuman) return
        val next = controller.humanAct(action)
        _state.value = next
        persistSnapshot()
        startTicking()
    }

    fun onNextHand() {
        val s = _state.value
        if (s.pendingShowdown != null) {
            controller.ackShowdown()
        }
        val active = controller.state.players.count { it.chips > 0 }
        if (active >= 2) {
            _state.value = controller.nextHand()
            persistSnapshot()
            startTicking()
        } else {
            _state.value = controller.state
            // 파산 리셋은 v1.1 §1.2.L — M3 범위 외.
        }
    }

    fun onSurrender() {
        val s = _state.value
        val seat = s.toActSeat ?: return
        val p = s.players.firstOrNull { it.seat == seat } ?: return
        if (!p.isHuman) return
        onHumanAction(Action(ActionType.FOLD))
    }

    // ---------------------------------------------------------------- Resume

    /** 사용자가 "이어하기" 선택. */
    fun onResumeAccept() {
        val snap = resumeRepo?.load() ?: run {
            _resumePrompt.value = null
            startTicking()
            return
        }
        val rng = Rng.ofSeeds(
            serverSeed = snap.rngServerSeedHex.hexToBytes(),
            clientSeed = snap.rngClientSeedHex.hexToBytes(),
            nonce = snap.rngNonce,
        )
        controller = GameController(
            config = config,
            initialPlayers = initialPlayers,
            resumeFrom = ResumeSeed(state = snap.state, rng = rng),
        )
        _state.value = controller.state
        _resumePrompt.value = null
        startTicking()
    }

    /** "포기하고 새 핸드" (discard=true) 또는 "취소=snapshot 유지 + 새 핸드" (discard=false). */
    fun onResumeDismiss(discard: Boolean) {
        if (discard) resumeRepo?.clear()
        _resumePrompt.value = null
        startTicking()
    }

    // ---------------------------------------------------------------- Internal

    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = viewModelScope.launch {
            while (true) {
                val s = _state.value
                val seat = s.toActSeat ?: return@launch
                if (s.pendingShowdown != null) return@launch
                val p = s.players.firstOrNull { it.seat == seat } ?: return@launch
                if (p.isHuman) return@launch
                delay(npcDelayMs)
                // Phase5-II-B: advisor 가 있으면 LLM → DecisionCore 폴백 경로를 타고, 없으면
                // 기존 경로 (pure DecisionCore). npcActWithLlm 은 suspend 라 Dispatchers.Default
                // 에 명시적으로 올려 Monte Carlo blocking CPU 작업을 UI 스레드 밖으로 보낸다.
                val next = withContext(Dispatchers.Default) {
                    if (llmAdvisor != null) controller.npcActWithLlm(llmAdvisor)
                    else controller.npcAct()
                }
                _state.value = next
                persistSnapshot()
            }
        }
    }

    private fun persistSnapshot() {
        val repo = resumeRepo ?: return
        val s = _state.value
        // 쇼다운 상태는 보존 가치 낮음 — 다음 핸드 시작 후 저장.
        if (s.pendingShowdown != null) {
            repo.clear()
            return
        }
        val r = controller.rng
        repo.save(
            ResumeSnapshot(
                state = s,
                rngServerSeedHex = r.serverSeed.toHexLower(),
                rngClientSeedHex = r.clientSeed.toHexLower(),
                rngNonce = r.nonce,
            )
        )
    }

    companion object {
        /**
         * M3 MVP 기본 세팅: 2인 헤즈업 홀덤 (SB 25 / BB 50, 10k chips).
         * Context 를 받으면 ResumeRepository 가 활성화됨. null 이면 비활성.
         */
        fun createDefault(
            context: Context?,
            mode: GameMode = GameMode.HOLDEM_NL,
            humanNickname: String = "나",
            npcPersonaId: String = "PRO",
            startingChips: Long = 10_000L,
            /** Phase5-II-B: LLM advisor (Hilt EntryPoint 로 가져온 것을 전달). null 이면 기존 경로. */
            llmAdvisor: LlmAdvisor? = null,
        ): TableViewModel {
            require(mode == GameMode.HOLDEM_NL) {
                "M3 MVP supports HOLDEM_NL only (mode=$mode)"
            }
            val config = TableConfig(mode = mode, seats = 2)
            val players = listOf(
                PlayerState(
                    seat = 0, nickname = humanNickname, isHuman = true,
                    personaId = null, chips = startingChips,
                ),
                PlayerState(
                    seat = 1, nickname = "프로", isHuman = false,
                    personaId = npcPersonaId, chips = startingChips,
                ),
            )
            val repo = context?.let { ResumeRepository(it) }
            return TableViewModel(
                config = config,
                initialPlayers = players,
                resumeRepo = repo,
                llmAdvisor = llmAdvisor,
            )
        }
    }
}

// ---------------------------------------------------------------------------- Hex helpers

private fun ByteArray.toHexLower(): String =
    joinToString("") { "%02x".format(it) }

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex string must have even length" }
    return ByteArray(length / 2) { i ->
        val hi = Character.digit(this[i * 2], 16)
        val lo = Character.digit(this[i * 2 + 1], 16)
        require(hi >= 0 && lo >= 0) { "invalid hex char at index ${i * 2}" }
        ((hi shl 4) or lo).toByte()
    }
}
