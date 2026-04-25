package com.infocar.pokermaster.model

import com.infocar.pokermaster.BuildConfig
import com.infocar.pokermaster.core.model.ModelManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 원격 manifest.json + manifest.json.sig 를 fetch → Ed25519 검증 → 파싱.
 *
 * 설계서 §5.4:
 *  - 매니페스트는 수 KB 이므로 in-memory 로드 안전.
 *  - 서명은 별도 파일(.sig): 매니페스트 바이트 전체에 대한 Ed25519 서명 (Base64).
 *  - 서명 불일치 시 매니페스트는 폐기 (신뢰 불가).
 *
 * WorkManager 다운로드 (GGUF 대용량) 는 M4-Phase1c. 여기선 작은 JSON/SIG 만 다루며
 * 직접 HttpURLConnection 사용 — 추가 의존성 0.
 */
class ManifestFetcher(
    // Json.Default 는 `ignoreUnknownKeys = false` — 매니페스트 변조/구버전 스키마 거부.
    private val json: Json = Json.Default,
    private val publicKeyBase64: String = BuildConfig.MODEL_MANIFEST_ED25519_PUBKEY_BASE64,
    private val httpTimeoutMs: Int = 15_000,
) {

    sealed interface Result {
        data class Success(val manifest: ModelManifest) : Result
        data class NetworkFailure(val url: String, val cause: Throwable) : Result
        data class HttpError(val url: String, val code: Int) : Result
        data class SignatureFailure(val detail: Ed25519Verifier.Result) : Result
        data class ParseFailure(val cause: Throwable) : Result
    }

    suspend fun fetch(manifestUrl: String, signatureUrl: String): Result = withContext(Dispatchers.IO) {
        val manifestBytes = when (val o = fetchBytes(manifestUrl)) {
            is FetchOutcome.Ok -> o.bytes
            is FetchOutcome.Net -> return@withContext Result.NetworkFailure(manifestUrl, o.cause)
            is FetchOutcome.Http -> return@withContext Result.HttpError(manifestUrl, o.code)
        }
        val sigBytes = when (val o = fetchBytes(signatureUrl)) {
            is FetchOutcome.Ok -> o.bytes
            is FetchOutcome.Net -> return@withContext Result.NetworkFailure(signatureUrl, o.cause)
            is FetchOutcome.Http -> return@withContext Result.HttpError(signatureUrl, o.code)
        }

        val verifyResult = Ed25519Verifier.verify(
            message = manifestBytes,
            signatureBase64 = String(sigBytes, Charsets.UTF_8),
            publicKeyBase64 = publicKeyBase64,
        )
        if (verifyResult !is Ed25519Verifier.Result.Valid) {
            return@withContext Result.SignatureFailure(verifyResult)
        }

        val manifestText = String(manifestBytes, Charsets.UTF_8)
        val manifest = runCatching { json.decodeFromString(ModelManifest.serializer(), manifestText) }
            .getOrElse { return@withContext Result.ParseFailure(it) }
        Result.Success(manifest)
    }

    private fun fetchBytes(urlString: String): FetchOutcome {
        val url = URL(urlString)
        // M7-BugFix: 프록시/VPN 환경에서 비-HTTP URL handler 가 등록돼 ClassCastException 으로
        // crash 하던 케이스 방어. 잘못된 URL 은 NetworkFailure 로 정상 전파.
        val conn = url.openConnection() as? HttpURLConnection
            ?: return FetchOutcome.Net(IllegalStateException("URL handler is not HTTP: $urlString"))
        return try {
            conn.connectTimeout = httpTimeoutMs
            conn.readTimeout = httpTimeoutMs
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "*/*")
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) return FetchOutcome.Http(code)
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
            }
            FetchOutcome.Ok(out.toByteArray())
        } catch (t: Throwable) {
            FetchOutcome.Net(t)
        } finally {
            conn.disconnect()
        }
    }

    private sealed interface FetchOutcome {
        data class Ok(val bytes: ByteArray) : FetchOutcome
        data class Net(val cause: Throwable) : FetchOutcome
        data class Http(val code: Int) : FetchOutcome
    }
}
