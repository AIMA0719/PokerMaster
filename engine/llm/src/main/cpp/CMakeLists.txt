cmake_minimum_required(VERSION 3.22.1)
project(pokermaster_llm LANGUAGES C CXX)

# M4-Phase2a: JNI stub. llama.cpp 실제 통합은 Phase2b.
add_library(pokermaster_llm SHARED
    pokermaster_llm.cpp
)

# ARMv8.2-A 최적화 — §5.5 "arm64-v8a 단일 + ARMv8.2-A 최적화".
#   -fp16 은 FP16 수학, -dotprod 는 INT8 DP (llama.cpp Q4/Q8 속도 향상).
target_compile_options(pokermaster_llm PRIVATE
    -O2
    -fno-exceptions
    -fno-rtti
    -march=armv8.2-a+fp16+dotprod
    -ffunction-sections
    -fdata-sections
)

# 16KB page size 대응 — §5.5. 2026-11 Play Store 요구사항.
target_link_options(pokermaster_llm PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,--gc-sections"
)

find_library(log-lib log)
target_link_libraries(pokermaster_llm ${log-lib})
