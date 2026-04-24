package com.infocar.pokermaster.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infocar.pokermaster.core.data.history.HandHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

/**
 * 핸드 상세 화면 VM — M5-D. 네비 arg `id` 로 [HandHistoryRepository.byId] 1회 로드.
 * 현재는 정적 요약만 (step scrubber 는 후속).
 */
@HiltViewModel
class HandDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: HandHistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HandDetailUiState(loading = true))
    val state: StateFlow<HandDetailUiState> = _state.asStateFlow()

    init {
        val id = savedStateHandle.get<Long>(ARG_ID)
            ?: savedStateHandle.get<String>(ARG_ID)?.toLongOrNull()
            ?: -1L
        if (id <= 0L) {
            _state.value = HandDetailUiState(loading = false, notFound = true)
        } else {
            viewModelScope.launch {
                val record = repo.byId(id)
                _state.value = if (record == null) {
                    HandDetailUiState(loading = false, notFound = true)
                } else {
                    val verified = verifySeed(
                        serverSeedHex = record.serverSeedHex,
                        commitHex = record.seedCommitHex,
                    )
                    HandDetailUiState(
                        loading = false,
                        record = record,
                        seedVerified = verified,
                    )
                }
            }
        }
    }

    companion object {
        const val ARG_ID = "id"

        /** v1.1 §3.5 Provably Fair — commit == SHA-256(serverSeed). */
        internal fun verifySeed(serverSeedHex: String, commitHex: String): Boolean {
            val serverBytes = runCatching { serverSeedHex.hexToBytes() }.getOrNull() ?: return false
            val digest = MessageDigest.getInstance("SHA-256").digest(serverBytes)
            val actual = digest.toHexLower()
            return actual.equals(commitHex, ignoreCase = true)
        }
    }
}

data class HandDetailUiState(
    val loading: Boolean = false,
    val notFound: Boolean = false,
    val record: com.infocar.pokermaster.core.data.history.HandHistoryRecord? = null,
    /** seed commit 이 serverSeed reveal 로 검증되는지 (§3.5). */
    val seedVerified: Boolean = false,
)

// ---- hex helpers (파일 private — 다른 모듈 util 과 중복이지만 의존성 최소 유지) ----

private fun ByteArray.toHexLower(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex length must be even" }
    return ByteArray(length / 2) { i ->
        val hi = Character.digit(this[i * 2], 16)
        val lo = Character.digit(this[i * 2 + 1], 16)
        require(hi >= 0 && lo >= 0) { "invalid hex char" }
        ((hi shl 4) or lo).toByte()
    }
}
