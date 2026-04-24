package com.infocar.pokermaster.engine.llm

/**
 * LLM 런타임 수명 상태 — UI/ViewModel 분기용.
 *
 * 전이:
 *   Uninitialized → Ready (backendInit 성공)
 *   Uninitialized → LoadFailed (네이티브 라이브러리 로드 실패)
 *   Ready → Released (backendFree 성공)
 *   Released → Ready (backendInit 재호출)
 */
sealed class LlmState {
    /** 아직 backendInit() 전. 또는 release 되지 않은 중립 상태. */
    data object Uninitialized : LlmState()

    /** 네이티브 로드·초기화 성공. 모델 로드 가능. */
    data object Ready : LlmState()

    /** backendFree 후 idle. 재초기화 가능. */
    data object Released : LlmState()

    /** 네이티브 로드 실패 — 미지원 단말/ABI/ 손상 .so. UI 는 안내 분기. */
    data class LoadFailed(val cause: Throwable) : LlmState()
}
