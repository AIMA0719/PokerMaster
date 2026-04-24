package com.infocar.pokermaster.engine.llm

/**
 * KV cache 양자화 — v1.1 §5.6 (감사 Mem#1 권고).
 *
 * Llama 3.2 1B (16 layers, GQA 8 KV heads, head_dim=64) 기준 ctx=2048 에서:
 *  - [F16]: 2 × 16 × 2048 × 8 × 64 × 2 B ≈ 67 MB
 *  - [Q8_0]: 약 34 MB, 품질 손실 거의 없음 (권장 기본값)
 *
 * Ordinal 은 JNI 경계에서 int 로 전달되므로 순서를 바꾸면 안 된다 — 새 값은 항상 끝에 추가.
 */
enum class KvQuant {
    /** `GGML_TYPE_F16` — 최대 정밀도, KV 메모리 2배. */
    F16,

    /** `GGML_TYPE_Q8_0` — 메모리 절반, 정밀도 거의 동일. 기본 권장. */
    Q8_0,
}
