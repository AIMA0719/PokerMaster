cmake_minimum_required(VERSION 3.22.1)
project(pokermaster_llm LANGUAGES C CXX)

# M4-Phase2b: llama.cpp 통합 (tag b8870, 2026-04-21).
#
# 프로젝트 루트 기준 third_party/llama.cpp — engine/llm/src/main/cpp 로부터 ../../../../..
# 로 올라가 절대경로로 고정한다 (Gradle 외부 주입 없이 동작).
get_filename_component(POKERMASTER_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.." ABSOLUTE)
set(LLAMA_CPP_DIR "${POKERMASTER_ROOT}/third_party/llama.cpp")

# llama.cpp 빌드 옵션 — add_subdirectory 전에 CACHE FORCE 로 고정해야 내부에서 무시되지 않음.
#  - GGML_NATIVE OFF: 우리 target_compile_options 의 -march=armv8.2-a+fp16+dotprod 유지용.
#    (native 켜지면 host arch 플래그로 덮어쓰여 cross-compile 깨진다.)
#  - GGML_OPENMP OFF: Android NDK 에 OpenMP 부재.
#  - LLAMA_BUILD_* OFF: 테스트/예제/서버/CLI 툴 제외 (APK 비대화 방지).
#  - BUILD_SHARED_LIBS OFF: ggml/llama 를 static 으로 빌드 → 우리 SO 하나로 통합.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_CPP_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp EXCLUDE_FROM_ALL)

add_library(pokermaster_llm SHARED
    pokermaster_llm.cpp
)

# 컴파일 옵션 — Phase2c 감사에서 수정:
#  -fno-exceptions 제거: llama.cpp 가 bad GGUF/OOM 에서 std::runtime_error 를 throw.
#    우리 frame 이 nounwind 면 unwind 가 UB → std::terminate → SIGABRT. JNI 에서 catch 가능해야 함.
#  -fno-rtti 유지: catch(std::exception&) 는 ABI 레벨이라 RTTI 불필요. 코드/SO 크기 절약.
#  -fvisibility=hidden + -fvisibility-inlines-hidden: JNIEXPORT 심볼만 export, SO 수백 KB 축소.
#  -march=armv8.2-a+fp16+dotprod: v1.1 §5.5 ARMv8.2-A 최적화 (FP16 수학 + INT8 DP → Q4/Q8 가속).
target_compile_options(pokermaster_llm PRIVATE
    -O2
    -fno-rtti
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -march=armv8.2-a+fp16+dotprod
    -ffunction-sections
    -fdata-sections
)

# 16KB page size 대응 — §5.5. 2026-11 Play Store 요구사항.
# 참고: NDK 28+ 는 기본 16KB 정렬이지만 명시해 둔다 (다운그레이드 방지).
target_link_options(pokermaster_llm PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,--gc-sections"
)

find_library(log-lib log)

# llama 만 링크한다. `common` 은 CLI 헬퍼 번들 (libllama-common.so ~82MB) 로,
# 온디바이스에 불필요. 샘플링/프롬프트 포매팅은 Phase2c~ 에서 자체 구현.
target_link_libraries(pokermaster_llm PRIVATE llama ${log-lib})
