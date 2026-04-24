// M4-Phase2b: llama.cpp (tag b8870) 연결.
// Phase2a 의 빌드 파이프라인 검증 스텁에 backend init/free + 버전 조회를 추가한다.
// 모델 로드/추론은 Phase2c~ 에서 LlamaCppClient 본체와 함께 확장한다.

#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <string>
#include <exception>
#include <new>

#include "llama.h"
#include "ggml.h"

#define LOG_TAG "pokermaster_llm"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// JNI 엔트리 공통 예외 래핑.
//  - std::bad_alloc 은 java.lang.OutOfMemoryError.
//  - std::exception 은 java.lang.RuntimeException (message 전달).
//  - 그 외는 RuntimeException ("unknown native exception").
// 호출자는 ThrowNew 후 기본값 반환해야 하며, 이후 Java 로 제어 돌아가면서 pending throw 가 실행된다.
static void ThrowJavaForCurrent(JNIEnv *env, const char *where) {
    try { throw; }
    catch (const std::bad_alloc &) {
        jclass c = env->FindClass("java/lang/OutOfMemoryError");
        if (c != nullptr) env->ThrowNew(c, where);
    } catch (const std::exception &e) {
        jclass c = env->FindClass("java/lang/RuntimeException");
        if (c != nullptr) env->ThrowNew(c, e.what());
    } catch (...) {
        jclass c = env->FindClass("java/lang/RuntimeException");
        if (c != nullptr) env->ThrowNew(c, "unknown native exception");
    }
}

// 모델 로드 후 model+context 를 한 묶음으로 살려두기 위한 opaque 핸들.
// Kotlin 쪽은 jlong 포인터로만 보관하며 내부 구조는 모른다.
struct LlamaSession {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
};

static jstring nativeVersion(JNIEnv *env, jobject /* thiz */) {
    try {
        // llama.cpp 런타임에서 직접 가져오는 빌드 문자열 — 태그 검증 겸 스모크.
        std::string v = std::string("pokermaster-llm/") + llama_print_system_info();
        ALOGI("nativeVersion() -> %s", v.c_str());
        jstring out = env->NewStringUTF(v.c_str());
        if (env->ExceptionCheck()) return nullptr;  // NewStringUTF OOM
        return out;
    } catch (...) {
        ThrowJavaForCurrent(env, "nativeVersion");
        return nullptr;
    }
}

static jlong nativePageSize(JNIEnv *env, jobject /* thiz */) {
    try {
        // 16KB page size 대응 검증용 — sysconf(_SC_PAGE_SIZE) 는 OS 페이지 크기.
        // Android 15+ / 적응된 단말은 16384 를 반환해야 함.
        long ps = sysconf(_SC_PAGE_SIZE);
        ALOGI("nativePageSize() -> %ld", ps);
        return static_cast<jlong>(ps);
    } catch (...) {
        ThrowJavaForCurrent(env, "nativePageSize");
        return 0;
    }
}

static void nativeBackendInit(JNIEnv *env, jobject /* thiz */) {
    try {
        llama_backend_init();
        ALOGI("llama_backend_init()");
    } catch (...) {
        ThrowJavaForCurrent(env, "nativeBackendInit");
        return;
    }
}

static void nativeBackendFree(JNIEnv *env, jobject /* thiz */) {
    try {
        llama_backend_free();
        ALOGI("llama_backend_free()");
    } catch (...) {
        ThrowJavaForCurrent(env, "nativeBackendFree");
        return;
    }
}

// kvQuantOrdinal: 0 = F16, 1 = Q8_0. Kotlin enum ordinal 과 일치해야 한다.
static jlong nativeModelLoad(JNIEnv *env, jobject /*thiz*/,
                             jstring path, jint nCtx, jint nThreads,
                             jboolean useMmap, jboolean useMlock,
                             jint kvQuantOrdinal) {
    std::string pathStr;
    try {
        const char *pathCStr = env->GetStringUTFChars(path, nullptr);
        if (pathCStr == nullptr) {
            ThrowJavaForCurrent(env, "nativeModelLoad: GetStringUTFChars");
            return 0;
        }
        pathStr.assign(pathCStr);
        env->ReleaseStringUTFChars(path, pathCStr);

        llama_model_params mp = llama_model_default_params();
        mp.use_mmap = (useMmap == JNI_TRUE);
        mp.use_mlock = (useMlock == JNI_TRUE);

        llama_model *model = llama_model_load_from_file(pathStr.c_str(), mp);
        if (model == nullptr) {
            jclass rex = env->FindClass("java/lang/RuntimeException");
            if (rex != nullptr) env->ThrowNew(rex, "llama_model_load_from_file returned null");
            return 0;
        }

        llama_context_params cp = llama_context_default_params();
        cp.n_ctx = static_cast<uint32_t>(nCtx);
        cp.n_threads = nThreads;
        cp.n_threads_batch = nThreads;
        cp.type_k = (kvQuantOrdinal == 1) ? GGML_TYPE_Q8_0 : GGML_TYPE_F16;
        cp.type_v = cp.type_k;

        llama_context *ctx = llama_init_from_model(model, cp);
        if (ctx == nullptr) {
            llama_model_free(model);
            jclass rex = env->FindClass("java/lang/RuntimeException");
            if (rex != nullptr) env->ThrowNew(rex, "llama_init_from_model returned null");
            return 0;
        }

        auto *session = new LlamaSession { model, ctx };
        ALOGI("nativeModelLoad: session=%p n_ctx=%d n_threads=%d kvQ=%d mmap=%d mlock=%d",
              session, nCtx, nThreads, kvQuantOrdinal,
              static_cast<int>(useMmap), static_cast<int>(useMlock));
        return reinterpret_cast<jlong>(session);
    } catch (...) {
        ThrowJavaForCurrent(env, "nativeModelLoad");
        return 0;
    }
}

static void nativeModelUnload(JNIEnv *env, jobject /*thiz*/, jlong handle) {
    try {
        if (handle == 0) return;
        auto *session = reinterpret_cast<LlamaSession *>(handle);
        if (session->ctx != nullptr) llama_free(session->ctx);
        if (session->model != nullptr) llama_model_free(session->model);
        ALOGI("nativeModelUnload: session=%p", session);
        delete session;
    } catch (...) {
        ThrowJavaForCurrent(env, "nativeModelUnload");
    }
}

// JNI_OnLoad: Phase3a — auto-symbol 대신 RegisterNatives 로 명시 매핑.
// 네이티브 함수 이름/심볼 가시성과 Kotlin 쪽 class/package rename 을 분리한다.
// FindClass 경로만 여기서 유지하면 된다.
static const JNINativeMethod kLlamaMethods[] = {
    {"nativeVersion",     "()Ljava/lang/String;", reinterpret_cast<void *>(nativeVersion)},
    {"nativePageSize",    "()J",                  reinterpret_cast<void *>(nativePageSize)},
    {"nativeBackendInit", "()V",                  reinterpret_cast<void *>(nativeBackendInit)},
    {"nativeBackendFree", "()V",                  reinterpret_cast<void *>(nativeBackendFree)},
    {"nativeModelLoad",   "(Ljava/lang/String;IIZZI)J", reinterpret_cast<void *>(nativeModelLoad)},
    {"nativeModelUnload", "(J)V",                       reinterpret_cast<void *>(nativeModelUnload)},
};

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void * /*reserved*/) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        ALOGI("JNI_OnLoad: GetEnv(JNI_VERSION_1_6) failed");
        return JNI_ERR;
    }
    jclass cls = env->FindClass("com/infocar/pokermaster/engine/llm/LlamaCppEngine");
    if (cls == nullptr) {
        ALOGI("JNI_OnLoad: FindClass LlamaCppEngine failed");
        return JNI_ERR;
    }
    const int n = static_cast<int>(sizeof(kLlamaMethods) / sizeof(kLlamaMethods[0]));
    if (env->RegisterNatives(cls, kLlamaMethods, n) < 0) {
        ALOGI("JNI_OnLoad: RegisterNatives failed");
        env->DeleteLocalRef(cls);
        return JNI_ERR;
    }
    env->DeleteLocalRef(cls);
    ALOGI("JNI_OnLoad: registered %d native methods", n);
    return JNI_VERSION_1_6;
}
