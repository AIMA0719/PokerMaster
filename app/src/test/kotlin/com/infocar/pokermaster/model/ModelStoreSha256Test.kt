package com.infocar.pokermaster.model

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * ModelStore 의 인스턴스 API 는 Android Context 에 의존하므로 JVM unit test 에선
 * companion 유틸([ModelStore.computeSha256Hex]) 만 검증.
 */
class ModelStoreSha256Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `empty file sha256 matches well-known constant`() {
        val f = tempFolder.newFile("empty.bin")
        val hex = ModelStore.computeSha256Hex(f)
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertThat(hex).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    }

    @Test
    fun `abc file sha256 matches NIST test vector`() {
        val f = tempFolder.newFile("abc.bin")
        f.writeBytes("abc".toByteArray(Charsets.UTF_8))
        val hex = ModelStore.computeSha256Hex(f)
        // NIST FIPS 180-4 Appendix A sample:
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertThat(hex).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    @Test
    fun `streaming matches digest for payload larger than buffer`() {
        // 내부 버퍼 8KB — 25KB 데이터로 여러 번 읽기 보장.
        val f = tempFolder.newFile("big.bin")
        val payload = ByteArray(25_000) { i -> (i % 251).toByte() }
        f.writeBytes(payload)

        val actual = ModelStore.computeSha256Hex(f)

        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest(payload).joinToString("") { "%02x".format(it) }
        assertThat(actual).isEqualTo(expected)
    }
}
