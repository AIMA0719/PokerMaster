package com.infocar.pokermaster.model

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.infocar.pokermaster.core.model.ModelEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * 모델 다운로드 orchestrator — WorkManager 를 감싼 thin layer.
 *
 *  - [enqueue]: `wifiOnly=true` 면 Constraints.NetworkType.UNMETERED (Wi-Fi/Ethernet 만),
 *    `false` 면 CONNECTED (셀룰러 허용) — 셀룰러 동의 토글은 호출자 책임.
 *  - Unique work name = `model-download:<entry.id>`. 정책 REPLACE — 옛 finished WorkInfo 의
 *    잔존이 새 요청과 race 하는 것을 차단 (예: 옛 ENQUEUED 가 firstOrNull 로 픽되어
 *    "대기중..." 으로 stuck 되는 버그).
 *  - [observe]: WorkInfo 스트림을 [DownloadState] 로 치환해 UI 구독. final 파일이 디스크에
 *    존재하면 WorkInfo 상태와 무관하게 Succeeded 로 단락 — race 차단.
 *  - Hilt 주입 없이 단일 인스턴스(Application 범위) 로 충분.
 */
class ModelDownloadRepository(context: Context) {

    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val store = ModelStore(appContext)

    fun enqueue(entry: ModelEntry, wifiOnly: Boolean = true): String {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val entryJson = Json.Default.encodeToString(ModelEntry.serializer(), entry)
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(ModelDownloadWorker.KEY_ENTRY_JSON, entryJson)
                    .build()
            )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag(TAG_ALL)
            .addTag(tagForEntry(entry))
            .build()
        val uniqueName = uniqueNameForEntry(entry)
        // REPLACE: 옛 finished/active WorkInfo 를 명시적으로 정리해 firstOrNull race 차단.
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
        return uniqueName
    }

    fun cancel(entry: ModelEntry) {
        workManager.cancelUniqueWork(uniqueNameForEntry(entry))
    }

    fun observe(entry: ModelEntry): Flow<DownloadState> =
        workManager.getWorkInfosForUniqueWorkFlow(uniqueNameForEntry(entry))
            .map { infos ->
                // 단락 1) 디스크에 이미 final 파일이 정상 크기로 존재하면 무조건 Succeeded —
                //   옛 ENQUEUED/BLOCKED WorkInfo 가 잔존해 firstOrNull 로 픽 되어도 UI 가
                //   "대기중..." 에 갇히지 않도록 한다 ("다 받았는데 대기중" 버그의 root cause).
                val finalFile = store.fileFor(entry)
                if (finalFile.isFile && finalFile.length() == entry.sizeBytes) {
                    return@map DownloadState.Succeeded
                }
                // 단락 2) 활성(미완료) WorkInfo 가 있으면 그것을 우선. 옛 finished 잔존 무시.
                val active = infos.firstOrNull { !it.state.isFinished }
                val pick = active ?: infos.maxByOrNull { it.state.ordinal }
                toDownloadState(pick, entry.sizeBytes)
            }

    companion object {
        const val TAG_ALL: String = "model-download"
        private const val UNIQUE_PREFIX: String = "model-download:"

        fun uniqueNameForEntry(entry: ModelEntry): String = "$UNIQUE_PREFIX${entry.id}"
        fun tagForEntry(entry: ModelEntry): String = "model:${entry.id}"

        internal fun toDownloadState(info: WorkInfo?, expectedTotal: Long): DownloadState {
            if (info == null) return DownloadState.Idle
            return when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadState.Queued
                WorkInfo.State.RUNNING -> {
                    val d = info.progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED, 0L)
                    val t = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, expectedTotal)
                    DownloadState.Running(downloaded = d, total = t)
                }
                WorkInfo.State.SUCCEEDED -> DownloadState.Succeeded
                WorkInfo.State.FAILED -> {
                    val reason = info.outputData.getString(ModelDownloadWorker.KEY_FAILURE_REASON)
                    val code = info.outputData.getInt(ModelDownloadWorker.KEY_HTTP_CODE, -1)
                    DownloadState.Failed(reason, httpCode = if (code > 0) code else null)
                }
                WorkInfo.State.CANCELLED -> DownloadState.Cancelled
            }
        }
    }
}

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Queued : DownloadState
    data class Running(val downloaded: Long, val total: Long) : DownloadState {
        val fraction: Float get() = if (total > 0L) (downloaded.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
    }
    data object Succeeded : DownloadState
    data class Failed(val reason: String?, val httpCode: Int? = null) : DownloadState
    data object Cancelled : DownloadState
}
