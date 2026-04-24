// M4-Phase2a: JNI stub.
// llama.cpp 심볼은 Phase2b 에서 추가. 현재는 버전 문자열만 반환해 native 빌드 파이프라인
// (NDK + CMake + 16KB page linker 플래그 + 로드) 이 올바로 동작하는지 확인한다.

#include <jni.h>
#include <android/log.h>
#include <unistd.h>

#define POKERMASTER_LLM_VERSION "pokermaster-llm-stub-0.1"
#define LOG_TAG "pokermaster_llm"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_infocar_pokermaster_engine_llm_LlamaCppClient_nativeVersion(
    JNIEnv *env, jobject /* thiz */) {
    ALOGI("nativeVersion() -> %s", POKERMASTER_LLM_VERSION);
    return env->NewStringUTF(POKERMASTER_LLM_VERSION);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_infocar_pokermaster_engine_llm_LlamaCppClient_nativePageSize(
    JNIEnv * /* env */, jobject /* thiz */) {
    // 16KB page size 대응 검증용 — sysconf(_SC_PAGE_SIZE) 는 OS 페이지 크기.
    // Android 15+ / 적응된 단말은 16384 를 반환해야 함.
    long ps = sysconf(_SC_PAGE_SIZE);
    ALOGI("nativePageSize() -> %ld", ps);
    return static_cast<jlong>(ps);
}
