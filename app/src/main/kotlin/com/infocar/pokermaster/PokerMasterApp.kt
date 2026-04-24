package com.infocar.pokermaster

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import com.infocar.pokermaster.di.AppScope
import com.infocar.pokermaster.engine.llm.LlmEngineHandle
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase3b-II: `ComponentCallbacks2` 를 구현해 kernel 메모리 압력 시 엔진 backend 를 정리한다.
 *
 * 설계서 §5.7 의 무조건 ON_STOP+30s 언로드는 **채택하지 않는다** (감사 Mem#4: mmap+16KB 환경에서
 * kernel 이 file-backed 페이지를 자유롭게 evict 하므로 명시적 unload 가 오히려 cold-start 지연만
 * 추가). 대신 `TRIM_MEMORY_RUNNING_MODERATE/CRITICAL` 등 실제 압박 신호에만 반응한다.
 *
 * 엔진 로드 실패 단말 ([LlmEngineHandle.Unavailable]) 에서는 콜백이 no-op.
 */
@HiltAndroidApp
class PokerMasterApp : Application(), ComponentCallbacks2 {

    @Inject lateinit var llmEngineHandle: LlmEngineHandle

    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // RUNNING_MODERATE (10) 이상만 반응. UI_HIDDEN(20), BACKGROUND(40), COMPLETE(80) 은 이미 포함.
        // MODERATE 미만은 단순 hint — 살아있는 모델까지 해제하면 재로드 비용이 더 크다.
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) return
        val engine = llmEngineHandle.engineOrNull() ?: return
        Log.i(TAG, "onTrimMemory(level=$level) -> backendFree()")
        appScope.launch { engine.backendFree() }
    }

    companion object {
        private const val TAG = "PokerMasterApp"
    }
}
