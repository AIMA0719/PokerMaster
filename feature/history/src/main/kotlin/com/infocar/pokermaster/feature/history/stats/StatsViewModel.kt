package com.infocar.pokermaster.feature.history.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infocar.pokermaster.core.data.history.HandHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 통계 대시보드 VM — M6-B.
 *
 * [HandHistoryRepository.observeRecent] 의 상위 [MAX_HISTORY_SCAN] 건을 모두 메모리에 올려
 * [StatsCalculator] 로 집계. Paging 도입 전까지는 1000건이면 로컬 환경에서 부담 없음.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    repo: HandHistoryRepository,
) : ViewModel() {

    val state: StateFlow<StatsUiState> = repo.observeRecent(MAX_HISTORY_SCAN)
        .map { records ->
            StatsUiState(
                loaded = true,
                overview = StatsCalculator.computeFromRecords(records),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(loaded = false, overview = StatsOverview.EMPTY),
        )

    companion object {
        const val MAX_HISTORY_SCAN: Int = 1000
    }
}

data class StatsUiState(
    val loaded: Boolean,
    val overview: StatsOverview,
)
