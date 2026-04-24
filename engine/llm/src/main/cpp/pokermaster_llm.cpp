// M4-Phase2b: llama.cpp (tag b8870) 연결.
// Phase2a 의 빌드 파이프라인 검증 스텁에 backend init/free + 버전 조회를 추가한다.
// 모델 로드/추론은 Phase2c~ 에서 LlamaCppClient 본체와 함께 확장한다.

#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <atomic>
#include <cstdio>
#include <cstdint>
#include <string>
#include <vector>
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
    const llama_vocab *vocab = nullptr;  // Phase3c-I: cached for tokenize/detokenize/generate
    std::atomic<bool> cancelFlag{false};  // Phase3c-II: coroutine cancel → abort decode
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

        const llama_vocab *vocab = llama_model_get_vocab(model);
        if (vocab == nullptr) {
            llama_free(ctx);
            llama_model_free(model);
            jclass rex = env->FindClass("java/lang/RuntimeException");
            if (rex != nullptr) env->ThrowNew(rex, "llama_model_get_vocab returned null");
            return 0;
        }

        auto *session = new LlamaSession { model, ctx, vocab };

        // Phase3c-II: abort callback — llama.cpp 가 decode 루프 중 주기적으로 콜백을 돌려 true 면 중단.
        // Kotlin 측 코루틴 cancel 이 nativeCancel 을 호출하면 cancelFlag 가 true 로 flip 된다.
        llama_set_abort_callback(
            session->ctx,
            [](void *ud) -> bool {
                return reinterpret_cast<LlamaSession *>(ud)->cancelFlag.load(std::memory_order_relaxed);
            },
            session
        );

        // Phase3c-II: warmup — dcache + kernel JIT 경로를 1 token decode 로 데워서 첫 generate() 의
        // first-token 지연을 줄인다. 실패해도 세션 수명에는 영향 없음 (로그만 남기고 진행).
        {
            // BOS 또는 첫 vocab token 을 시드로. eos/eot 는 피한다.
            llama_token seed_tok = 0;  // 대부분 BOS = 0 (Llama/GPT-family 관습)
            llama_batch warm = llama_batch_get_one(&seed_tok, 1);
            int dec = llama_decode(session->ctx, warm);
            if (dec == 0) {
                ALOGI("nativeModelLoad: warmup OK (1 token decode)");
            } else {
                ALOGI("nativeModelLoad: warmup decode rc=%d (continuing)", dec);
            }
            // warmup 토큰이 KV 에 남지 않도록 clear. b8870 은 memory API 를 제공한다:
            // llama_memory_clear(llama_get_memory(ctx), true) — data=true 로 전체 리셋.
            llama_memory_clear(llama_get_memory(session->ctx), true);
        }

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

// Phase3c-II: Kotlin 코루틴 cancel → cancelFlag=true. 현재 decode 루프는 abort_callback 을 통해
// 중단되며, 이어지는 nativeGenerate 루프는 rc != 0 이 되어 자연스럽게 빠져나온다.
static void nativeCancel(JNIEnv *env, jobject /*thiz*/, jlong handle) {
    try {
        if (handle == 0) return;
        auto *s = reinterpret_cast<LlamaSession *>(handle);
        s->cancelFlag.store(true, std::memory_order_relaxed);
        ALOGI("nativeCancel: session=%p cancel=true", s);
    } catch (...) { ThrowJavaForCurrent(env, "nativeCancel"); }
}

// Phase3c-II: 이전 cancel 상태가 새 generate 호출로 새지 않도록 명시 리셋.
static void nativeResetCancel(JNIEnv *env, jobject /*thiz*/, jlong handle) {
    try {
        if (handle == 0) return;
        auto *s = reinterpret_cast<LlamaSession *>(handle);
        s->cancelFlag.store(false, std::memory_order_relaxed);
    } catch (...) { ThrowJavaForCurrent(env, "nativeResetCancel"); }
}

// Phase3c-I: text -> token ids. 2-pass size probe (llama_tokenize returns negative
// count when buffer too small — that negative is the required size).
static jintArray nativeTokenize(JNIEnv *env, jobject /*thiz*/, jlong handle, jstring text) {
    try {
        if (handle == 0) {
            jclass c = env->FindClass("java/lang/IllegalStateException");
            if (c != nullptr) env->ThrowNew(c, "nativeTokenize: handle == 0");
            return nullptr;
        }
        auto *s = reinterpret_cast<LlamaSession *>(handle);
        const char *utf = env->GetStringUTFChars(text, nullptr);
        if (utf == nullptr) {
            ThrowJavaForCurrent(env, "nativeTokenize GetStringUTFChars");
            return nullptr;
        }
        std::string t(utf);
        env->ReleaseStringUTFChars(text, utf);

        // size probe: with n_tokens_max=0, llama_tokenize returns negative of the required size.
        int32_t probe = llama_tokenize(s->vocab, t.c_str(), static_cast<int32_t>(t.size()),
                                       nullptr, 0, /*add_special*/ true, /*parse_special*/ false);
        int need = -probe;
        if (need <= 0) {
            // 0 tokens (empty input) — return empty jintArray.
            return env->NewIntArray(0);
        }
        std::vector<llama_token> toks(static_cast<size_t>(need));
        int32_t got = llama_tokenize(s->vocab, t.c_str(), static_cast<int32_t>(t.size()),
                                     toks.data(), static_cast<int32_t>(toks.size()),
                                     /*add_special*/ true, /*parse_special*/ false);
        if (got < 0) {
            got = -got;
            if (got > static_cast<int32_t>(toks.size())) got = static_cast<int32_t>(toks.size());
        }

        jintArray out = env->NewIntArray(got);
        if (out == nullptr) {
            ThrowJavaForCurrent(env, "nativeTokenize NewIntArray");
            return nullptr;
        }
        // llama_token == int32_t; safe to alias to jint.
        env->SetIntArrayRegion(out, 0, got, reinterpret_cast<const jint *>(toks.data()));
        return out;
    } catch (...) {
        ThrowJavaForCurrent(env, "nativeTokenize");
        return nullptr;
    }
}

// Phase3c-I: token ids -> text. Per-token llama_token_to_piece with 2-pass grow on
// negative return (requested buffer length).
static jstring nativeDetokenize(JNIEnv *env, jobject /*thiz*/, jlong handle, jintArray tokens) {
    try {
        if (handle == 0) {
            jclass c = env->FindClass("java/lang/IllegalStateException");
            if (c != nullptr) env->ThrowNew(c, "nativeDetokenize: handle == 0");
            return nullptr;
        }
        auto *s = reinterpret_cast<LlamaSession *>(handle);

        const jsize n = env->GetArrayLength(tokens);
        std::vector<llama_token> ids(static_cast<size_t>(n));
        if (n > 0) {
            env->GetIntArrayRegion(tokens, 0, n, reinterpret_cast<jint *>(ids.data()));
            if (env->ExceptionCheck()) return nullptr;
        }

        std::string out;
        out.reserve(static_cast<size_t>(n) * 4);
        std::vector<char> tmp(128);
        for (jsize i = 0; i < n; ++i) {
            int32_t w = llama_token_to_piece(s->vocab, ids[i], tmp.data(),
                                             static_cast<int32_t>(tmp.size()),
                                             /*lstrip*/ 0, /*special*/ false);
            if (w < 0) {
                tmp.resize(static_cast<size_t>(-w));
                w = llama_token_to_piece(s->vocab, ids[i], tmp.data(),
                                         static_cast<int32_t>(tmp.size()),
                                         /*lstrip*/ 0, /*special*/ false);
                if (w < 0) w = 0;  // give up on this token, skip
            }
            if (w > 0) out.append(tmp.data(), static_cast<size_t>(w));
        }

        jstring js = env->NewStringUTF(out.c_str());
        if (env->ExceptionCheck()) return nullptr;
        return js;
    } catch (...) {
        ThrowJavaForCurrent(env, "nativeDetokenize");
        return nullptr;
    }
}

// Phase3c-I: prompt decode -> sampler loop -> return generated token ids.
// Sampler chain = top_k -> top_p -> temp -> dist. Sampler is freed on every exit path.
static jintArray nativeGenerate(JNIEnv *env, jobject /*thiz*/, jlong handle,
                                jintArray promptTokens, jint maxNewTokens,
                                jfloat temperature, jint topK, jfloat topP,
                                jlong seed) {
    llama_sampler *smpl = nullptr;
    try {
        if (handle == 0) {
            jclass c = env->FindClass("java/lang/IllegalStateException");
            if (c != nullptr) env->ThrowNew(c, "nativeGenerate: handle == 0");
            return nullptr;
        }
        auto *s = reinterpret_cast<LlamaSession *>(handle);
        // Phase3c-II: defensive — Kotlin 측도 리셋하지만, JNI 진입 시에도 한 번 더.
        s->cancelFlag.store(false, std::memory_order_relaxed);

        // copy prompt tokens into a mutable vector (llama_batch_get_one takes non-const ptr).
        const jsize nPrompt = env->GetArrayLength(promptTokens);
        if (nPrompt <= 0) {
            jclass c = env->FindClass("java/lang/IllegalArgumentException");
            if (c != nullptr) env->ThrowNew(c, "nativeGenerate: empty prompt");
            return nullptr;
        }
        std::vector<llama_token> prompt(static_cast<size_t>(nPrompt));
        env->GetIntArrayRegion(promptTokens, 0, nPrompt, reinterpret_cast<jint *>(prompt.data()));
        if (env->ExceptionCheck()) return nullptr;

        // Build sampler chain.
        auto sparams = llama_sampler_chain_default_params();
        smpl = llama_sampler_chain_init(sparams);
        if (smpl == nullptr) {
            jclass rex = env->FindClass("java/lang/RuntimeException");
            if (rex != nullptr) env->ThrowNew(rex, "llama_sampler_chain_init returned null");
            return nullptr;
        }
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(static_cast<int32_t>(topK)));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, /*min_keep*/ 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        uint32_t sd = (seed >= 0) ? static_cast<uint32_t>(seed) : LLAMA_DEFAULT_SEED;
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(sd));

        // Decode prompt in one shot.
        {
            llama_batch b = llama_batch_get_one(prompt.data(),
                                                static_cast<int32_t>(prompt.size()));
            int32_t rc = llama_decode(s->ctx, b);
            if (rc != 0) {
                llama_sampler_free(smpl);
                smpl = nullptr;
                jclass rex = env->FindClass("java/lang/RuntimeException");
                if (rex != nullptr) {
                    char msg[64];
                    snprintf(msg, sizeof(msg), "llama_decode(prompt) failed rc=%d", rc);
                    env->ThrowNew(rex, msg);
                }
                return nullptr;
            }
        }

        // Generation loop.
        std::vector<llama_token> out;
        out.reserve(static_cast<size_t>(maxNewTokens > 0 ? maxNewTokens : 0));
        for (int i = 0; i < maxNewTokens; ++i) {
            llama_token id = llama_sampler_sample(smpl, s->ctx, -1);
            llama_sampler_accept(smpl, id);
            if (llama_vocab_is_eog(s->vocab, id)) break;
            out.push_back(id);
            llama_batch b = llama_batch_get_one(&out.back(), 1);
            int32_t rc = llama_decode(s->ctx, b);
            if (rc != 0) break;
        }

        llama_sampler_free(smpl);
        smpl = nullptr;

        jintArray arr = env->NewIntArray(static_cast<jsize>(out.size()));
        if (arr == nullptr) {
            ThrowJavaForCurrent(env, "nativeGenerate NewIntArray");
            return nullptr;
        }
        if (!out.empty()) {
            env->SetIntArrayRegion(arr, 0, static_cast<jsize>(out.size()),
                                   reinterpret_cast<const jint *>(out.data()));
        }
        return arr;
    } catch (...) {
        if (smpl != nullptr) llama_sampler_free(smpl);
        ThrowJavaForCurrent(env, "nativeGenerate");
        return nullptr;
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
    {"nativeCancel",      "(J)V",                       reinterpret_cast<void *>(nativeCancel)},
    {"nativeResetCancel", "(J)V",                       reinterpret_cast<void *>(nativeResetCancel)},
    {"nativeTokenize",    "(JLjava/lang/String;)[I",    reinterpret_cast<void *>(nativeTokenize)},
    {"nativeDetokenize",  "(J[I)Ljava/lang/String;",    reinterpret_cast<void *>(nativeDetokenize)},
    {"nativeGenerate",    "(J[IIFIFJ)[I",               reinterpret_cast<void *>(nativeGenerate)},
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
