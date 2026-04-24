package com.infocar.pokermaster.model

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.infocar.pokermaster.core.model.ModelEntry
import kotlinx.serialization.json.Json

/**
 * 모델 다운로드 WorkManager Worker — v1.1 §5.4.
 *
 *  - [Constraints.setRequiredNetworkType] 은 enqueue 쪽에서 설정 (Wi-Fi only vs CONNECTED).
 *  - primaryUrl → fallbackUrls 순으로 시도하며 [RangeDownloader] 로 재진입 가능 다운로드.
 *  - 완료 파일의 SHA-256 이 매니페스트와 불일치하면 partFile 삭제 후 failure (재시도해도
 *    서버 파일이 같다면 동일한 불일치 → 매니페스트/서버 쪽 문제이므로 retry 의미 없음).
 *  - 네트워크 실패 전반(모든 URL 실패)은 [Result.retry] — WorkManager backoff 에 위임.
 *
 * 진행률 보고는 [setProgress] 로 KEY_DOWNLOADED/KEY_TOTAL. 호출자는 WorkInfo.progress 구독.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entryJson = inputData.getString(KEY_ENTRY_JSON) ?: return Result.failure()
        val entry = runCatching { Json.Default.decodeFromString(ModelEntry.serializer(), entryJson) }
            .getOrElse { return Result.failure() }

        val store = ModelStore(applicationContext)
        val partFile = store.partFileFor(entry)
        val finalFile = store.fileFor(entry)

        // 이미 설치됨 — 크기 일치하면 SHA 확인 후 종료.
        if (finalFile.isFile && finalFile.length() == entry.sizeBytes) {
            return if (store.verify(entry) is ModelStore.VerifyResult.Valid) Result.success()
            else {
                finalFile.delete()
                Result.retry()
            }
        }

        val urls = buildList {
            add(entry.primaryUrl)
            addAll(entry.fallbackUrls)
        }
        var lastOutcome: RangeDownloader.Outcome = RangeDownloader.Outcome.Http(code = -1)
        for (url in urls) {
            val outcome = RangeDownloader.download(
                url = url,
                partFile = partFile,
                expectedSize = entry.sizeBytes,
            ) { progress ->
                setProgress(
                    Data.Builder()
                        .putString(KEY_MODEL_ID, entry.id)
                        .putLong(KEY_DOWNLOADED, progress.downloaded)
                        .putLong(KEY_TOTAL, progress.total)
                        .build()
                )
            }
            lastOutcome = outcome
            if (outcome is RangeDownloader.Outcome.Completed) break
        }

        if (lastOutcome !is RangeDownloader.Outcome.Completed) {
            // 전 URL 실패 — backoff 에 맡겨 재시도.
            return Result.retry()
        }

        // SHA-256 검증. 불일치면 .part 폐기 + failure.
        val sha = ModelStore.computeSha256Hex(partFile)
        if (!sha.equals(entry.sha256, ignoreCase = true)) {
            partFile.delete()
            return Result.failure()
        }

        // 원자적 commit: 기존 final 파일 제거 후 rename.
        if (finalFile.exists() && !finalFile.delete()) return Result.failure()
        if (!partFile.renameTo(finalFile)) return Result.failure()
        return Result.success()
    }

    companion object {
        const val KEY_ENTRY_JSON: String = "entryJson"
        const val KEY_MODEL_ID: String = "modelId"
        const val KEY_DOWNLOADED: String = "downloaded"
        const val KEY_TOTAL: String = "total"
    }
}
