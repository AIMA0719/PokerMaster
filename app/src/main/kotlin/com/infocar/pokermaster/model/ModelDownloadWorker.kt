package com.infocar.pokermaster.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.infocar.pokermaster.BuildConfig
import com.infocar.pokermaster.core.model.ModelEntry
import kotlinx.serialization.json.Json

/**
 * 모델 다운로드 WorkManager Worker — v1.1 §5.4.
 *
 *  - [Constraints.setRequiredNetworkType] 은 enqueue 쪽에서 설정 (Wi-Fi only vs CONNECTED).
 *  - primaryUrl → fallbackUrls 순으로 시도하며 [RangeDownloader] 로 재진입 가능 다운로드.
 *  - 완료 파일의 SHA-256 이 매니페스트와 불일치하면 partFile 삭제 후 failure (재시도해도
 *    서버 파일이 같다면 동일한 불일치 → 매니페스트/서버 쪽 문제이므로 retry 의미 없음).
 *  - Network transient 실패는 [Result.retry] — WorkManager backoff 에 위임.
 *  - ENOSPC / 4xx HTTP / 모든 URL 4xx 는 [Result.failure] — 무한 retry 방지.
 *  - 백그라운드 시 OS 가 죽이지 않도록 [setForegroundAsync] 로 진행률 알림 표시.
 *
 * 진행률 보고는 [setProgress] 로 KEY_DOWNLOADED/KEY_TOTAL. 호출자 측 throttling 적용 —
 * 500ms 또는 1MB 단위 변화 시에만 publish 해 Room DB 부담 최소화.
 */
class ModelDownloadWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryJson = inputData.getString(KEY_ENTRY_JSON) ?: return Result.failure()
        val entry = runCatching { Json.Default.decodeFromString(ModelEntry.serializer(), entryJson) }
            .getOrElse { return Result.failure() }

        // 알림 권한 + Channel 준비 후 즉시 foreground 진입. 권한 없으면 일반 백그라운드로 동작.
        runCatching { setForeground(createForegroundInfo(entry, downloaded = 0L, total = entry.sizeBytes, indeterminate = true)) }

        val store = ModelStore(applicationContext)
        val partFile = store.partFileFor(entry)
        val finalFile = store.fileFor(entry)

        // 이미 설치됨 — 캐시된 SHA 만 비교 (재해싱 회피).
        if (finalFile.isFile && finalFile.length() == entry.sizeBytes) {
            return when (val v = store.verify(entry)) {
                is ModelStore.VerifyResult.Valid -> Result.success(
                    Data.Builder().putString(KEY_OUTPUT_SHA, entry.sha256).build()
                )
                is ModelStore.VerifyResult.HashMismatch -> {
                    finalFile.delete()
                    Result.retry()
                }
                else -> {
                    finalFile.delete()
                    Result.retry()
                }
            }
        }

        val urls = buildList {
            add(entry.primaryUrl)
            addAll(entry.fallbackUrls)
        }
        val ua = "PokerMaster/${BuildConfig.VERSION_NAME} (Android)"
        var lastOutcome: RangeDownloader.Outcome = RangeDownloader.Outcome.Http(code = -1)
        var lastReportMs = 0L
        var lastReportedBytes = 0L
        for (url in urls) {
            val outcome = RangeDownloader.download(
                url = url,
                partFile = partFile,
                expectedSize = entry.sizeBytes,
                userAgent = ua,
            ) { progress ->
                val now = System.currentTimeMillis()
                val bytesDelta = progress.downloaded - lastReportedBytes
                val finished = progress.downloaded >= progress.total
                if (finished || now - lastReportMs >= PROGRESS_THROTTLE_MS || bytesDelta >= PROGRESS_THROTTLE_BYTES) {
                    lastReportMs = now
                    lastReportedBytes = progress.downloaded
                    setProgress(
                        Data.Builder()
                            .putString(KEY_MODEL_ID, entry.id)
                            .putLong(KEY_DOWNLOADED, progress.downloaded)
                            .putLong(KEY_TOTAL, progress.total)
                            .build()
                    )
                    runCatching {
                        setForeground(
                            createForegroundInfo(
                                entry = entry,
                                downloaded = progress.downloaded,
                                total = progress.total,
                                indeterminate = false,
                            )
                        )
                    }
                }
            }
            lastOutcome = outcome
            if (outcome is RangeDownloader.Outcome.Completed) break
            // 영구 실패는 다음 URL 도 의미 없을 가능성이 높으나, 4xx 는 URL 별로 다를 수 있어 시도.
            if (outcome is RangeDownloader.Outcome.DiskFull) {
                // 디스크 풀은 다른 URL 시도 무의미.
                break
            }
        }

        when (val o = lastOutcome) {
            RangeDownloader.Outcome.Completed -> Unit  // 아래에서 SHA 검증
            is RangeDownloader.Outcome.DiskFull -> {
                partFile.delete()
                return Result.failure(
                    Data.Builder()
                        .putString(KEY_FAILURE_REASON, FAILURE_DISK_FULL)
                        .build()
                )
            }
            is RangeDownloader.Outcome.Http -> {
                val code = o.code
                // 416 Range Not Satisfiable 는 stale .part 가 원인일 가능성이 높음 (서버 파일이
                // 줄어들었거나 매니페스트 size 불일치). .part 를 폐기하고 retry — 다음 시도가
                // byte=0 부터 받음. (Codex P1 회귀: 옛 .part 가 있으면 재시도해도 같은 416 으로
                // 영구 stuck 되던 버그 수정.)
                if (code == 416) {
                    partFile.delete()
                    return Result.retry()
                }
                // 그 외 4xx (auth/not found 등) 는 즉시 failure — 재시도해도 변하지 않음.
                if (code in 400..499) {
                    return Result.failure(
                        Data.Builder()
                            .putString(KEY_FAILURE_REASON, FAILURE_HTTP_4XX)
                            .putInt(KEY_HTTP_CODE, code)
                            .build()
                    )
                }
                return Result.retry()
            }
            is RangeDownloader.Outcome.SizeMismatch,
            is RangeDownloader.Outcome.Network,
            -> return Result.retry()
        }

        // SHA-256 검증. 불일치면 .part 폐기 + failure.
        val sha = ModelStore.computeSha256Hex(partFile)
        if (!sha.equals(entry.sha256, ignoreCase = true)) {
            partFile.delete()
            return Result.failure(
                Data.Builder().putString(KEY_FAILURE_REASON, FAILURE_SHA_MISMATCH).build()
            )
        }

        // 원자적 commit: 기존 final 파일 제거 후 rename.
        if (finalFile.exists() && !finalFile.delete()) return Result.failure()
        if (!partFile.renameTo(finalFile)) return Result.failure()

        // UI 가 재해싱하지 않도록 검증 캐시 즉시 기록.
        store.markVerifiedFromWorker(entry, sha)

        return Result.success(
            Data.Builder().putString(KEY_OUTPUT_SHA, sha).build()
        )
    }

    private fun createForegroundInfo(
        entry: ModelEntry,
        downloaded: Long,
        total: Long,
        indeterminate: Boolean,
    ): ForegroundInfo {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI 모델 다운로드",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "포커 마스터 AI 모델 다운로드 진행률"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val cancelIntent = androidx.work.WorkManager.getInstance(appContext)
            .createCancelPendingIntent(id)

        // 알림 누르면 앱 열기.
        val openIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
        val openPi = openIntent?.let {
            PendingIntent.getActivity(
                appContext,
                0,
                it.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val percent = if (total > 0L) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("AI 모델 다운로드 중")
            .setContentText(
                if (indeterminate) "준비 중…"
                else "${entry.displayName} · $percent% (${formatMb(downloaded)} / ${formatMb(total)})"
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, percent, indeterminate)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "취소", cancelIntent)
        openPi?.let { builder.setContentIntent(it) }

        val notification = builder.build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun formatMb(bytes: Long): String = "%.1f MB".format(bytes.toDouble() / 1_048_576.0)

    companion object {
        const val KEY_ENTRY_JSON: String = "entryJson"
        const val KEY_MODEL_ID: String = "modelId"
        const val KEY_DOWNLOADED: String = "downloaded"
        const val KEY_TOTAL: String = "total"
        const val KEY_OUTPUT_SHA: String = "outputSha"
        const val KEY_FAILURE_REASON: String = "failureReason"
        const val KEY_HTTP_CODE: String = "httpCode"

        const val FAILURE_DISK_FULL: String = "disk_full"
        const val FAILURE_HTTP_4XX: String = "http_4xx"
        const val FAILURE_SHA_MISMATCH: String = "sha_mismatch"

        private const val CHANNEL_ID: String = "model_download"
        private const val NOTIFICATION_ID: Int = 0xC0DE

        // Throttle: setProgress + setForeground 호출 간격.
        private const val PROGRESS_THROTTLE_MS: Long = 500L
        private const val PROGRESS_THROTTLE_BYTES: Long = 1_048_576L  // 1 MB
    }
}
