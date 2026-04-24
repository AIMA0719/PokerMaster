package com.infocar.pokermaster.engine.llm

/**
 * llama.cpp JNI 브리지 — v1.1 §5.6.
 *
 *  - Phase2a (stub): `nativeVersion` / `nativePageSize` 만 제공. 빌드 파이프라인
 *    (NDK 28 + CMake 3.22 + 16KB page linker flag + arm64-v8a + ARMv8.2-A 최적화) 검증용.
 *  - Phase2b: llama.cpp (tag b8870) 를 실제 링크. `nativeBackendInit`/`Free` 추가.
 *  - Phase2c+: 모델 로드/샘플링/추론. Mutex + 전용 dispatcher 로 단일 호출 강제.
 *
 * 스레드 안전성: Phase3 에서 Mutex 기반 단일 호출 강제 예정. 현재는 backend 수명만 관리.
 */
class LlamaCppClient {

    /** 네이티브 라이브러리 버전 문자열 — 로드 성공 여부 확인용 스모크. llama.cpp system info 포함. */
    external fun nativeVersion(): String

    /** OS 페이지 크기 (sysconf _SC_PAGE_SIZE). Android 15+ 16KB 단말은 16384 반환. */
    external fun nativePageSize(): Long

    /** `llama_backend_init()` — 프로세스 수명당 1회. 모델 로드 전 필수. */
    external fun nativeBackendInit()

    /** `llama_backend_free()` — 앱 종료 혹은 AI 모듈 해제 시 호출. */
    external fun nativeBackendFree()

    companion object {
        init {
            System.loadLibrary("pokermaster_llm")
        }
    }
}
