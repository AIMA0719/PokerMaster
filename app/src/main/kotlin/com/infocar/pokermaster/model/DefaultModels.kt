package com.infocar.pokermaster.model

import com.infocar.pokermaster.core.model.ModelEntry
import com.infocar.pokermaster.core.model.ModelFamily
import com.infocar.pokermaster.core.model.ModelLicense

/**
 * M4-Phase1 MVP 하드코딩 엔트리.
 *
 *  - 실제 배포에선 [ManifestFetcher] 로 서명된 manifest.json 을 받아 동적 결정.
 *  - [sizeBytes] / [sha256] 은 실 파일과 맞지 않으면 다운로드 직후 SHA 검증에서 fail —
 *    현재 값은 placeholder. 배포 전 정확한 값으로 갱신 필수.
 */
object DefaultModels {

    val llama3_2_1b_q4km: ModelEntry = ModelEntry(
        id = "llama-3.2-1b-q4km",
        displayName = "Llama 3.2 1B (Q4_K_M)",
        family = ModelFamily.LLAMA,
        fileName = "llama-3.2-1b-q4_k_m.gguf",
        // 실측값 (2026-04-24, unsloth/Llama-3.2-1B-Instruct-GGUF Q4_K_M). 서명된 manifest fetch 붙으면 교체.
        sizeBytes = 807_694_464L,
        sha256 = "6f85a640a97cf2bf5b8e764087b1e83da0fdb51d7c9fab7d0fece9385611df83",
        primaryUrl = "https://huggingface.co/unsloth/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
        fallbackUrls = emptyList(),
        license = ModelLicense.LLAMA_COMMUNITY,
        attributionNotice = "Built with Llama",
    )

    val default: ModelEntry = llama3_2_1b_q4km
}
