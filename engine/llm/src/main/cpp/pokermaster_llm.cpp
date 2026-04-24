// M4-Phase2b: llama.cpp (tag b8870) 연결.
// Phase2a 의 빌드 파이프라인 검증 스텁에 backend init/free + 버전 조회를 추가한다.
// 모델 로드/추론은 Phase2c~ 에서 LlamaCppClient 본체와 함께 확장한다.

#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <string>

#include "llama.h"

#define LOG_TAG "pokermaster_llm"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_infocar_pokermaster_engine_llm_LlamaCppClient_nativeVersion(
    JNIEnv *env, jobject /* thiz */) {
    // llama.cpp 런타임에서 직접 가져오는 빌드 문자열 — 태그 검증 겸 스모크.
    std::string v = std::string("pokermaster-llm/") + llama_print_system_info();
    ALOGI("nativeVersion() -> %s", v.c_str());
    return env->NewStringUTF(v.c_str());
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

extern "C" JNIEXPORT void JNICALL
Java_com_infocar_pokermaster_engine_llm_LlamaCppClient_nativeBackendInit(
    JNIEnv * /* env */, jobject /* thiz */) {
    llama_backend_init();
    ALOGI("llama_backend_init()");
}

extern "C" JNIEXPORT void JNICALL
Java_com_infocar_pokermaster_engine_llm_LlamaCppClient_nativeBackendFree(
    JNIEnv * /* env */, jobject /* thiz */) {
    llama_backend_free();
    ALOGI("llama_backend_free()");
}
