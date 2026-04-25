package com.infocar.pokermaster.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP Range 기반 재진입 가능 다운로더 — v1.1 §5.4.
 *
 *  - partFile 에 기존 바이트가 있으면 `Range: bytes=<len>-` 헤더로 이어받기 시도.
 *  - 서버가 Range 무시(200 OK 응답)하면 partFile 을 삭제하고 처음부터.
 *  - onProgress 는 매 read 마다 호출 — 호출자가 throttling 책임 (Worker 에서 500ms 단위).
 *  - 코루틴 취소 시 즉시 connection.disconnect() 로 stalled read 를 깨워 응답 지연 제거.
 */
object RangeDownloader {

    data class Progress(val downloaded: Long, val total: Long)

    sealed interface Outcome {
        data object Completed : Outcome
        /** transient — Worker 가 retry 해야 함. */
        data class Network(val cause: Throwable) : Outcome
        /** HTTP 코드. 4xx/410 등은 Worker 가 즉시 failure 로 처리. */
        data class Http(val code: Int) : Outcome
        data class SizeMismatch(val expected: Long, val actual: Long) : Outcome
        /** 디스크 풀 / 권한 문제 등 — Worker 즉시 failure. */
        data class DiskFull(val cause: IOException) : Outcome
    }

    suspend fun download(
        url: String,
        partFile: java.io.File,
        expectedSize: Long,
        userAgent: String,
        connectTimeoutMs: Int = 30_000,
        readTimeoutMs: Int = 60_000,
        onProgress: suspend (Progress) -> Unit = {},
    ): Outcome = withContext(Dispatchers.IO) {
        // 비정상 크기(서버 파일 변경 등) 의 .part 는 버리고 처음부터.
        if (partFile.exists() && partFile.length() > expectedSize) {
            partFile.delete()
        }
        val startOffset = if (partFile.exists()) partFile.length() else 0L
        if (startOffset == expectedSize) {
            onProgress(Progress(expectedSize, expectedSize))
            return@withContext Outcome.Completed
        }

        // M7-BugFix: 프록시/VPN 환경에서 비-HTTP URL handler 가 등록되면 cast 가 ClassCastException 으로
        // 죽었다. 잘못된 URL 은 Outcome.Network 로 정상 전파.
        val conn = URL(url).openConnection() as? HttpURLConnection
            ?: return@withContext Outcome.Network(IllegalStateException("URL handler is not HTTP: $url"))
        // 코루틴 취소 시 즉시 connection 을 끊어 stalled read 를 풀어준다.
        // (read 안에 갇혀 있으면 ensureActive 가 못 끼어든다 → 사용자 "취소" 가 readTimeout 까지 지연됨.)
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion {
            runCatching { conn.disconnect() }
        }
        try {
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/octet-stream, */*")
            if (startOffset > 0L) {
                conn.setRequestProperty("Range", "bytes=$startOffset-")
            }
            conn.connect()
            val code = conn.responseCode
            val resumed = code == HttpURLConnection.HTTP_PARTIAL
            val okFull = code == HttpURLConnection.HTTP_OK
            if (!resumed && !okFull) return@withContext Outcome.Http(code)

            // 서버가 Range 무시하면 .part 를 버리고 처음부터 받는다.
            val fromScratch = okFull && startOffset > 0L
            if (fromScratch && partFile.exists()) partFile.delete()
            val append = !fromScratch && startOffset > 0L
            var written = if (append) startOffset else 0L

            FileOutputStream(partFile, append).use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n <= 0) break
                        try {
                            out.write(buf, 0, n)
                        } catch (e: IOException) {
                            if (isDiskFull(e)) return@withContext Outcome.DiskFull(e)
                            throw e
                        }
                        written += n
                        onProgress(Progress(written, expectedSize))
                    }
                }
            }

            val actualSize = partFile.length()
            if (actualSize == expectedSize) Outcome.Completed
            else Outcome.SizeMismatch(expected = expectedSize, actual = actualSize)
        } catch (t: CancellationException) {
            throw t
        } catch (t: IOException) {
            if (isDiskFull(t)) Outcome.DiskFull(t) else Outcome.Network(t)
        } catch (t: Throwable) {
            Outcome.Network(t)
        } finally {
            cancelHandle?.dispose()
            conn.disconnect()
        }
    }

    /** ENOSPC 류 에러 식별. Android 는 errno 메시지에 "ENOSPC" 또는 "No space left" 를 포함. */
    private fun isDiskFull(t: Throwable): Boolean {
        val msg = t.message ?: return false
        return msg.contains("ENOSPC", ignoreCase = true) ||
            msg.contains("No space left", ignoreCase = true)
    }
}
