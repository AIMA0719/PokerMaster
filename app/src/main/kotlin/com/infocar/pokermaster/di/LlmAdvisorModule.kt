package com.infocar.pokermaster.di

import com.infocar.pokermaster.engine.controller.llm.LlmAdvisor
import com.infocar.pokermaster.engine.controller.llm.LlmAdvisorImpl
import com.infocar.pokermaster.engine.llm.LlmSession
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * LLM Advisor Hilt 배선 — Phase5-II-B.
 *
 * [LlmSession] 을 의존성으로 받아 [LlmAdvisorImpl] 을 래핑. SingletonComponent 에 설치되어
 * `:app` / feature 어디서든 동일 인스턴스 공유. 엔진 로드 실패 단말이어도 advisor 자체는
 * 여전히 제공되며 내부 `suggest()` 가 null 반환 (폴백 유도).
 */
@Module
@InstallIn(SingletonComponent::class)
object LlmAdvisorModule {

    @Provides
    @Singleton
    fun provideLlmAdvisor(session: LlmSession): LlmAdvisor = LlmAdvisorImpl(session)
}

/**
 * Composable 에서 Hilt 로 [LlmAdvisor] / [com.infocar.pokermaster.core.data.history.HandHistoryRepository]
 * / [@AppScope CoroutineScope] 를 꺼내기 위한 entry point.
 *
 * ViewModel 이 [androidx.hilt.navigation.compose.hiltViewModel] 로 주입받는 표준 경로 외에,
 * 이미 팩토리 기반 (`TableViewModel.createDefault`) 으로 만들어진 VM 에 나중 주입이 필요할 때
 * 사용한다.
 *
 * ```
 * val ep = EntryPointAccessors.fromApplication(ctx.applicationContext, TableDepsEntryPoint::class.java)
 * TableViewModel.createDefault(ctx, mode, llmAdvisor = ep.llmAdvisor(), historyRepo = ep.historyRepo(), historyScope = ep.appScope())
 * ```
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LlmAdvisorEntryPoint {
    fun llmAdvisor(): LlmAdvisor

    fun historyRepo(): com.infocar.pokermaster.core.data.history.HandHistoryRepository

    /** M6-C: 테이블 buy-in / settle 을 위한 wallet. */
    fun walletRepo(): com.infocar.pokermaster.core.data.wallet.WalletRepository

    /** 사용자 닉네임 (기본 "나") — table 진입 시 humanNickname 인자로 전달. */
    fun nicknameRepo(): com.infocar.pokermaster.core.data.profile.NicknameRepository

    @AppScope
    fun appScope(): kotlinx.coroutines.CoroutineScope
}
