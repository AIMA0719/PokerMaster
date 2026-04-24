package com.infocar.pokermaster.model

import android.content.Context
import com.infocar.pokermaster.core.model.ModelEntry
import java.io.File
import java.security.MessageDigest

/**
 * 모델 파일 디스크 관리 — v1.1 §5.7 "모델 파일 위치 (MAJOR)".
 *
 *  - 저장 위치: `Context.noBackupFilesDir/models/` — Auto Backup 대상에서 제외.
 *  - 파일명은 [ModelEntry.fileName] 기준. 다운로드 중에는 `.part` suffix, 완료 시 rename.
 *  - [verify] 는 파일 크기 + SHA-256 hex 를 매니페스트와 비교. 스트리밍이라 메모리는 8KB 버퍼.
 */
class ModelStore(context: Context) {

    val root: File = File(context.applicationContext.noBackupFilesDir, "models").also {
        if (!it.exists()) it.mkdirs()
    }

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
        val sha = computeSha256Hex(f)
        return if (sha.equals(entry.sha256, ignoreCase = true)) VerifyResult.Valid
        else VerifyResult.HashMismatch(expected = entry.sha256.lowercase(), actual = sha)
    }

    fun deleteInstalled(entry: ModelEntry): Boolean = fileFor(entry).let { if (it.exists()) it.delete() else true }

    fun deletePart(entry: ModelEntry): Boolean = partFileFor(entry).let { if (it.exists()) it.delete() else true }

    sealed interface VerifyResult {
        data object Valid : VerifyResult
        data object Missing : VerifyResult
        data class SizeMismatch(val expected: Long, val actual: Long) : VerifyResult
        data class HashMismatch(val expected: String, val actual: String) : VerifyResult
    }

    companion object {
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
