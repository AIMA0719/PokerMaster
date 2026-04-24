package com.infocar.pokermaster.core.model

import kotlinx.serialization.Serializable

/**
 * 모델 매니페스트 — v1.1 §5.4.
 *
 *  - 앱 시작 시 HuggingFace/R2/GitHub 에서 `manifest.json` 을 fetch.
 *  - `manifest.json.sig` (별도 파일) = manifest.json 바이트 전체에 대한 Ed25519 서명.
 *    BuildConfig 에 임베드된 개발자 공개키로 검증. (서명 검증 로직은 M4-Phase1b 에서.)
 *  - [models] 는 앱이 다운로드 가능한 온디바이스 GGUF 목록. Gemini Nano 는
 *    AICore OS 제공이라 매니페스트에 포함되지 않는다.
 */
@Serializable
data class ModelManifest(
    /** 매니페스트 스키마 버전. 호환 불가 변경 시 증가. 현재 1. */
    val version: Int,
    /** 매니페스트 생성 시각 (epoch seconds). 클라이언트가 로컬 시각과 비교해 신선도 판단. */
    val generatedAtEpochSec: Long,
    val models: List<ModelEntry>,
)

/**
 * 온디바이스 다운로드 가능한 모델 하나의 메타데이터.
 *
 *  - [primaryUrl] HuggingFace resolve URL 권장 (`.../resolve/main/{file}.gguf`).
 *  - [fallbackUrls] Cloudflare R2 미러, GitHub Release attachment 순.
 *  - [sha256] 완료된 파일 전체의 SHA-256 (lowercase hex 64자).
 *  - [sizeBytes] 서버 파일 크기 (Content-Length 비교 + Range resume 산출 기준).
 */
@Serializable
data class ModelEntry(
    /** 안정 식별자 — 예: "llama-3.2-1b-q4km". 경로·DataStore 키로 재사용 가능하게 slug 포맷. */
    val id: String,
    val displayName: String,
    val family: ModelFamily,
    /** 디스크 저장 시 사용할 파일명. 예: "llama-3.2-1b-q4_k_m.gguf". */
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val primaryUrl: String,
    val fallbackUrls: List<String> = emptyList(),
    /** llama_context ctx_size 목표 (§5.3: 2048 고정). 모델별 hard-limit 이 이보다 작으면 안전장치. */
    val requiredContextTokens: Int = 2048,
    val license: ModelLicense,
    /** "Built with Llama" 같이 앱 내 표기 필요한 문구. null 이면 의무 없음. */
    val attributionNotice: String? = null,
)

/** 지원 모델 패밀리. Gemini Nano 는 AICore 제공이라 매니페스트 다운로드 대상이 아님. */
@Serializable
enum class ModelFamily {
    LLAMA,
    QWEN,
}

/**
 * 라이선스 분류 — 앱 내 고지 요구사항을 결정한다.
 *  - LLAMA_COMMUNITY: "Built with Llama" 표기 + MAU 7억 미만 + AUP 준수.
 *  - APACHE_2_0: NOTICE 파일에 출처만.
 *  - MIT: 저작권 표시 + 라이선스 사본.
 */
@Serializable
enum class ModelLicense {
    LLAMA_COMMUNITY,
    APACHE_2_0,
    MIT,
}
