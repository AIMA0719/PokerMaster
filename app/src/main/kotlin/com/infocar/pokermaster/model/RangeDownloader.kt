package com.infocar.pokermaster.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP Range 기반 재진입 가능 다운로더 — v1.1 §5.4.
 *
 *  - partFile 에 기존 바이트가 있으면 `Range: bytes=<len>-` 헤더로 이어받기 시도.
 *  - 서버가 Range 무시(200 OK 응답)하면 partFile 을 삭제하고 처음부터.
 *  - onProgress 는 매 read 마다 호출 — 호출자가 throttling 책임.
 *  - 코루틴 취소(`ensureActive`) 로 사용자 "일시정지/취소" 처리.
 */
object RangeDownloader {

    data class Progress(val downloaded: Long, val total: Long)

    sealed interface Outcome {
        data object Completed : Outcome
        data class Network(val cause: Throwable) : Outcome
        data class Http(val code: Int) : Outcome
        data class SizeMismatch(val expected: Long, val actual: Long) : Outcome
    }

    suspend fun download(
        url: String,
        partFile: java.io.File,
        expectedSize: Long,
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

        val conn = (URL(url).openConnection() as HttpURLConnection)
        try {
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
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
                        out.write(buf, 0, n)
                        written += n
                        onProgress(Progress(written, expectedSize))
                    }
                }
            }

            val actualSize = partFile.length()
            if (actualSize == expectedSize) Outcome.Completed
            else Outcome.SizeMismatch(expected = expectedSize, actual = actualSize)
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (t: Throwable) {
            Outcome.Network(t)
        } finally {
            conn.disconnect()
        }
    }
}
