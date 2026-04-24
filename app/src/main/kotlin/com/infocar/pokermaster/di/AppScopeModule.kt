package com.infocar.pokermaster.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Application 수명 `CoroutineScope` Hilt 배선 — Phase3b-II.
 *
 * `SupervisorJob` 으로 child 실패가 scope 전체를 취소하지 않도록 분리. `Dispatchers.Default` 는
 * 콜백 (예: [android.content.ComponentCallbacks2.onTrimMemory]) 에서 가볍게 시작하는 정리 작업에
 * 적합 — LLM 네이티브 호출은 엔진 내부의 단일 스레드 executor 가 다시 직렬화하므로 여기서는
 * dispatcher 선택이 크리티컬하지 않다.
 *
 * 앱 프로세스가 죽을 때 함께 사라지므로 cancel 불필요.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {

    @Provides
    @Singleton
    @AppScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
