package com.infocar.pokermaster

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import androidx.work.WorkManager
import com.infocar.pokermaster.di.AppScope
import com.infocar.pokermaster.engine.llm.LlmSession
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase3b-II + Phase3c-III.
 *
 * 부트스트랩:
 *  - `onCreate` 에서 [LlmSession.initBackend] 를 [appScope] 에 launch — 프로세스 기동 직후
 *    비동기로 네이티브 backend 를 올려 첫 generate 호출 지연 제거.
 *
 * 메모리 정책:
 *  - `ComponentCallbacks2.onTrimMemory` 에서 kernel 압박 시그널 (RUNNING_MODERATE 이상) 받으면
 *    [LlmSession.release] — backend + 모델 동시에 해제. 이후 플레이 진입하면 VM/화면이 다시
 *    `initBackend` + `loadModel` 하는 패턴.
 *  - 설계서 §5.7 의 무조건 ON_STOP+30s 언로드는 채택하지 않음 (감사 Mem#4: mmap+16KB 환경에서
 *    kernel 이 file-backed 페이지를 이미 evict 하므로 명시적 unload 가 오히려 cold-start 지연만
 *    추가). 실제 압박 신호에만 반응.
 */
@HiltAndroidApp
class PokerMasterApp : Application(), ComponentCallbacks2 {

    @Inject lateinit var llmSession: LlmSession

    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // 옛 finished 모델 다운로드 WorkInfo 정리 — 어제 세션의 SUCCEEDED/FAILED 가 오늘 첫
        // observe() 에 emit 되어 ModelGateScreen 의 race 에 끼어드는 것을 사전에 차단.
        runCatching { WorkManager.getInstance(this).pruneWork() }
        // backend 초기화는 suspend — Application.onCreate 를 블로킹할 수 없으므로 AppScope 에 launch.
        // 실패해도 LlmSession 이 LoadFailed 로 전이하고 UI 는 StateFlow 를 읽어 분기한다.
        appScope.launch {
            Log.i(TAG, "bootstrap: LlmSession.initBackend()")
            llmSession.initBackend()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) return
        Log.i(TAG, "onTrimMemory(level=$level) -> LlmSession.release()")
        appScope.launch { llmSession.release() }
    }

    companion object {
        private const val TAG = "PokerMasterApp"
    }
}
