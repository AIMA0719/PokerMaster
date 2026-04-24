package com.infocar.pokermaster.di

import javax.inject.Qualifier

/**
 * Application 수명 범위의 `CoroutineScope` 를 식별하는 qualifier.
 *
 * Phase3b-II: `PokerMasterApp.onTrimMemory` 같은 lifecycle 콜백은 컴포넌트 수명이 없으므로
 * `SupervisorJob` + `Dispatchers.Default` 기반의 프로세스 전역 스코프가 필요하다.
 * ViewModel 의 `viewModelScope` 나 Activity 의 lifecycleScope 와 혼동하지 않도록 qualifier 로 분리.
 */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class AppScope
