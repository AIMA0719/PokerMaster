package com.infocar.pokermaster.engine.llm

/**
 * llama.cpp JNI 브리지 — v1.1 §5.6.
 *
 *  - Phase2a (stub): `nativeVersion` / `nativePageSize` 만 제공. 빌드 파이프라인
 *    (NDK 28 + CMake 3.22 + 16KB page linker flag + arm64-v8a + ARMv8.2-A 최적화) 검증용.
 *  - Phase2b 에서 llama.cpp 심볼 추가, Phase3 에서 mutex/dispatcher 를 가진 실사용 클라이언트 완성.
 *
 * 스레드 안전성: Phase3 에서 Mutex 기반 단일 호출 강제 예정. 현재 stub 은 reentrant 로 안전.
 */
class LlamaCppClient {

    /** 네이티브 라이브러리 버전 문자열 — 로드 성공 여부 확인용 스모크. */
    external fun nativeVersion(): String

    /** OS 페이지 크기 (sysconf _SC_PAGE_SIZE). Android 15+ 16KB 단말은 16384 반환. */
    external fun nativePageSize(): Long

    companion object {
        init {
            System.loadLibrary("pokermaster_llm")
        }
    }
}
