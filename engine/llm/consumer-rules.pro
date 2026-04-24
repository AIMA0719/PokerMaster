# llama.cpp JNI bridge — 네이티브 심볼은 System.loadLibrary 후 reflection 으로 호출되므로
# minify 대상 앱이 LlamaCppClient 의 external 메서드 시그니처를 보존해야 한다.
# (실제 keep 규칙은 M4-Phase3 LLM runtime 에서 확장.)
-keep class com.infocar.pokermaster.engine.llm.** { *; }
