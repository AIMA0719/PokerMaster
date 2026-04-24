package com.infocar.pokermaster.engine.llm.di

import com.infocar.pokermaster.engine.llm.LlamaCppEngine
import com.infocar.pokermaster.engine.llm.LlmEngineHandle
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * LlmEngine Hilt 배선 — v1.1 §5.6.
 *
 * `llama_backend_init/free` 가 프로세스 전역 상태를 건드리므로 반드시 프로세스 수명당 1개
 * 만 존재해야 한다. `@Singleton` 바인딩은 `SingletonComponent` 에 설치되어 Application scope
 * 에서만 재사용된다.
 *
 * 네이티브 라이브러리 로드는 실패 가능 (미지원 ABI/단말, 손상 .so). 실패를 예외로 전파하면
 * Hilt 가 전체 앱 초기화를 폭발시키므로 [LlmEngineHandle] sealed 로 래핑해 주입한다
 * (Kotlin `Result<T>` 는 inline value class 라 Dagger/javapoet 이 이름 mangling 을 처리 못 함).
 */
@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    @Provides
    @Singleton
    fun provideLlmEngineHandle(): LlmEngineHandle =
        LlamaCppEngine.tryCreate().fold(
            onSuccess = { LlmEngineHandle.Available(it) },
            onFailure = { LlmEngineHandle.Unavailable(it) },
        )
}
