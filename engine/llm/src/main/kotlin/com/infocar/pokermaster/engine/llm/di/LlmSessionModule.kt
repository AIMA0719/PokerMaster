package com.infocar.pokermaster.engine.llm.di

import com.infocar.pokermaster.engine.llm.LlmEngineHandle
import com.infocar.pokermaster.engine.llm.LlmSession
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * [LlmSession] Hilt 배선 — Phase3c-III.
 *
 * [LlmModule] 이 제공하는 [LlmEngineHandle] 을 그대로 넘겨받아 session wrapper 로 감싼다.
 * `@Singleton` 이라 Application 수명 동안 동일 인스턴스가 공유 — VM 은 StateFlow 를
 * collectAsState 로 구독한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object LlmSessionModule {

    @Provides
    @Singleton
    fun provideLlmSession(handle: LlmEngineHandle): LlmSession = LlmSession(handle)
}
