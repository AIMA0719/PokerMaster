package com.infocar.pokermaster.model

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.util.Log
import com.infocar.pokermaster.core.model.ModelEntry
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * 모델 파일 디스크 관리 — v1.1 §5.7 "모델 파일 위치 (MAJOR)".
 *
 *  - 저장 위치: `Context.noBackupFilesDir/models/` — Auto Backup 대상에서 제외.
 *  - 파일명은 [ModelEntry.fileName] 기준. 다운로드 중에는 `.part` suffix, 완료 시 rename.
 *  - [verify] 는 파일 크기 + SHA-256 hex 를 매니페스트와 비교. 스트리밍이라 메모리는 8KB 버퍼.
 *  - cold start 마다 800MB 해시를 피하려 (size, mtime, sha) 검증 캐시를 SharedPreferences 에
 *    유지한다. 캐시 적중 시 SHA 계산 없이 즉시 [VerifyResult.Valid].
 */
class ModelStore(private val context: Context) {

    val root: File = File(context.applicationContext.noBackupFilesDir, "models").also {
        if (!it.exists()) it.mkdirs()
    }

    private val verifyCache: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_VERIFY_CACHE, Context.MODE_PRIVATE)

    fun fileFor(entry: ModelEntry): File = File(root, entry.fileName)

    /** 다운로드 중 임시 파일. Range resume 시 existing .part 크기만큼 이어받는다. */
    fun partFileFor(entry: ModelEntry): File = File(root, entry.fileName + ".part")

    fun verify(entry: ModelEntry): VerifyResult {
        val f = fileFor(entry)
        if (!f.isFile) return VerifyResult.Missing
        val actualSize = f.length()
        if (actualSize != entry.sizeBytes) {
            return VerifyResult.SizeMismatch(expected = entry.sizeBytes, actual = actualSize)
        }
        // 캐시 hit: (size, mtime, expected sha) 가 일치하면 SHA 재계산 스킵.
        val cachedSha = verifyCache.getString(cacheKey(entry, "sha"), null)
        val cachedSize = verifyCache.getLong(cacheKey(entry, "size"), -1L)
        val cachedMtime = verifyCache.getLong(cacheKey(entry, "mtime"), -1L)
        if (cachedSha != null
            && cachedSize == actualSize
            && cachedMtime == f.lastModified()
            && cachedSha.equals(entry.sha256, ignoreCase = true)
        ) {
            return VerifyResult.Valid
        }
        val sha = computeSha256Hex(f)
        return if (sha.equals(entry.sha256, ignoreCase = true)) {
            verifyCache.edit().apply {
                putString(cacheKey(entry, "sha"), sha)
                putLong(cacheKey(entry, "size"), actualSize)
                putLong(cacheKey(entry, "mtime"), f.lastModified())
            }.apply()
            VerifyResult.Valid
        } else {
            verifyCache.edit().apply {
                remove(cacheKey(entry, "sha"))
                remove(cacheKey(entry, "size"))
                remove(cacheKey(entry, "mtime"))
            }.apply()
            VerifyResult.HashMismatch(expected = entry.sha256.lowercase(), actual = sha)
        }
    }

    /**
     * Worker 가 .part 단계에서 이미 SHA 검증을 마치고 rename 으로 final 파일을 만든 직후
     * UI 가 사용하는 fast path. final 파일의 크기만 확인하고 캐시에 (size, mtime, sha) 를
     * 기록 — 이후 [verify] 호출은 캐시 히트로 즉시 Valid 반환.
     */
    fun markVerifiedFromWorker(entry: ModelEntry, sha256Hex: String): VerifyResult {
        val f = fileFor(entry)
        if (!f.isFile) return VerifyResult.Missing
        val actualSize = f.length()
        if (actualSize != entry.sizeBytes) {
            return VerifyResult.SizeMismatch(expected = entry.sizeBytes, actual = actualSize)
        }
        if (!sha256Hex.equals(entry.sha256, ignoreCase = true)) {
            return VerifyResult.HashMismatch(expected = entry.sha256.lowercase(), actual = sha256Hex)
        }
        verifyCache.edit().apply {
            putString(cacheKey(entry, "sha"), sha256Hex)
            putLong(cacheKey(entry, "size"), actualSize)
            putLong(cacheKey(entry, "mtime"), f.lastModified())
        }.apply()
        return VerifyResult.Valid
    }

    /**
     * PAD (Play Asset Delivery) install-time 에셋팩에서 모델 추출.
     *
     * install-time 에셋팩은 표준 AssetManager 로 접근 가능. 에셋팩에 GGUF 파일이 있으면
     * 내부 저장소로 복사한다. llama.cpp 는 mmap 기반이라 assets 에서 직접 읽을 수 없기 때문.
     *
     * 디스크 풀 시 [IOException] 을 그대로 던져 호출자가 사용자에게 안내할 수 있도록 한다.
     *
     * @return true: 에셋팩에서 성공적으로 추출됨, false: 에셋팩에 모델 없음 (Debug 빌드 등)
     */
    @Throws(IOException::class)
    fun extractFromAssetPack(entry: ModelEntry, onProgress: (Long, Long) -> Unit = { _, _ -> }): Boolean {
        val target = fileFor(entry)
        if (target.isFile && target.length() == entry.sizeBytes) return true  // 이미 존재

        val assets: AssetManager = context.applicationContext.assets
        return try {
            val inputStream = assets.open(entry.fileName)
            inputStream.use { src ->
                FileOutputStream(target).use { dst ->
                    val buf = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = src.read(buf)
                        if (n <= 0) break
                        dst.write(buf, 0, n)
                        copied += n
                        onProgress(copied, entry.sizeBytes)
                    }
                }
            }
            Log.i("ModelStore", "PAD asset extracted: ${entry.fileName} (${target.length()} bytes)")
            true
        } catch (_: java.io.FileNotFoundException) {
            // 에셋팩에 모델 없음 (Debug 빌드). 정상 — 다운로드 폴백.
            false
        } catch (e: IOException) {
            // 디스크 풀 등 — 부분 파일 정리 후 호출자에게 throw.
            target.delete()
            throw e
        }
    }

    fun deleteInstalled(entry: ModelEntry): Boolean {
        verifyCache.edit().apply {
            remove(cacheKey(entry, "sha"))
            remove(cacheKey(entry, "size"))
            remove(cacheKey(entry, "mtime"))
        }.apply()
        return fileFor(entry).let { if (it.exists()) it.delete() else true }
    }

    fun deletePart(entry: ModelEntry): Boolean = partFileFor(entry).let { if (it.exists()) it.delete() else true }

    sealed interface VerifyResult {
        data object Valid : VerifyResult
        data object Missing : VerifyResult
        data class SizeMismatch(val expected: Long, val actual: Long) : VerifyResult
        data class HashMismatch(val expected: String, val actual: String) : VerifyResult
    }

    companion object {
        private const val PREFS_VERIFY_CACHE = "model_verify_cache"

        private fun cacheKey(entry: ModelEntry, field: String): String = "${entry.id}.$field"

        fun computeSha256Hex(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { ins ->
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
