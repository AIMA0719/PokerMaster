# llama.cpp JNI bridge — Phase2c 감사 반영.
#
# R8 가 JNI 엔트리를 제거하면 System.loadLibrary 후 first-call 에서 UnsatisfiedLinkError.
# 너무 광범위하게 keep 하면 R8 이 무효화되므로 native method 만 좁게 보존한다.

# 1) 어떤 클래스든 native method 는 이름/시그니처 보존 (JNI 심볼 매칭 보장).
-keepclasseswithmembernames class * {
    native <methods>;
}

# 2) LlamaCppEngine 자체가 JNI RegisterNatives 타겟 (FindClass 경로 고정) — 클래스 이름/native 시그니처/
#    초기화 블록 유지. includedescriptorclasses: 파라미터/리턴 타입도 함께 보존해 JNI 시그니처 매칭 실패 방지.
-keep,includedescriptorclasses class com.infocar.pokermaster.engine.llm.LlamaCppEngine {
    native <methods>;
    static { *; }
}
