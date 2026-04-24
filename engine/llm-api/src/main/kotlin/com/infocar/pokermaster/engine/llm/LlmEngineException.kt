package com.infocar.pokermaster.engine.llm

/**
 * LlmEngine 계약상 발생 가능한 예외들. 네이티브 런타임 예외는 JNI 층에서
 * java.lang.RuntimeException / OutOfMemoryError 로 변환된다 — 이 타입들은 Kotlin 경계에서
 * 의미 있는 분기가 필요한 실패만 정의한다.
 */
sealed class LlmEngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    /** libpokermaster_llm.so 로드 실패 — UnsatisfiedLinkError/LinkageError 의 래퍼. */
    class UnsupportedDevice(cause: Throwable) :
        LlmEngineException("libpokermaster_llm.so 로드 실패 (미지원 단말/ABI)", cause)
}
