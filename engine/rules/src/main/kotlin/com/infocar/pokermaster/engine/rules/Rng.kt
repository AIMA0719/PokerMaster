package com.infocar.pokermaster.engine.rules

import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.standardDeck
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Provably Fair RNG (v1.1 §3.5) — 1 핸드 = 1 인스턴스.
 *
 *  - serverSeed: 256bit, 핸드 시작 전 SHA-256(serverSeed) commit 만 공개
 *  - clientSeed: 사용자 입력 또는 자동 256bit
 *  - nonce: handIndex (Long, 단조 증가)
 *  - 셔플은 [init] 에서 1회만 수행되어 [deck] 에 보존. 추가 셔플 호출 불가 (immutable).
 *  - 핸드 종료 후 serverSeed 공개 → 사용자가 SHA-256 비교로 검증.
 *
 * 카드 분배는 [deck] 의 인덱스 순서로만 소비. 같은 핸드에서 추가 카드(번/리플레이스)도
 * 같은 deck 의 다음 인덱스를 사용 — 따라서 한 핸드 안에서 카드 중복은 구조적으로 불가능.
 */
class Rng private constructor(
    val serverSeed: ByteArray,
    val clientSeed: ByteArray,
    val nonce: Long,
    sourceDeck: List<Card>,
) {
    init {
        require(serverSeed.size == SEED_BYTES) { "serverSeed must be $SEED_BYTES bytes (256bit)" }
        require(clientSeed.size == SEED_BYTES) { "clientSeed must be $SEED_BYTES bytes (256bit)" }
        require(nonce >= 0) { "nonce must be non-negative" }
    }

    /**
     * 핸드 시작 시 화면에 노출하는 commit. serverSeed 의 SHA-256.
     * 사용자는 핸드 종료 후 공개된 serverSeed 의 SHA-256 == commit 을 검증.
     */
    val commit: ByteArray by lazy { sha256(serverSeed) }

    /** 결정론적으로 셔플된 덱 (불변). */
    val deck: List<Card> = shuffleOnce(sourceDeck)

    private fun shuffleOnce(source: List<Card>): List<Card> {
        val arr = source.toMutableList()
        val drbg = Sha256Drbg(combinedSeed())
        // Knuth / Modern Fisher-Yates
        for (i in arr.indices.reversed()) {
            val j = drbg.nextIntInclusive(i)
            if (j != i) {
                val tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp
            }
        }
        return arr.toList()
    }

    private fun combinedSeed(): ByteArray {
        val md = MessageDigest.getInstance(SHA256)
        md.update(serverSeed)
        md.update(clientSeed)
        md.update(nonceBytes(nonce))
        return md.digest()
    }

    companion object {
        const val SEED_BYTES = 32
        const val SHA256 = "SHA-256"

        /**
         * 새 Rng 생성. serverSeed 는 SecureRandom 자동 생성.
         * [customClientSeed] 미지정 시 SecureRandom 으로 생성.
         */
        fun create(nonce: Long, customClientSeed: ByteArray? = null): Rng {
            val rnd = SecureRandom()
            val server = ByteArray(SEED_BYTES).also { rnd.nextBytes(it) }
            val client = customClientSeed
                ?.also { require(it.size == SEED_BYTES) { "customClientSeed must be $SEED_BYTES bytes" } }
                ?: ByteArray(SEED_BYTES).also { rnd.nextBytes(it) }
            return Rng(server, client, nonce, standardDeck())
        }

        /** 결정론 시드로 직접 생성 (테스트/디버그/검증용). */
        fun ofSeeds(serverSeed: ByteArray, clientSeed: ByteArray, nonce: Long): Rng =
            Rng(serverSeed, clientSeed, nonce, standardDeck())

        fun sha256(bytes: ByteArray): ByteArray =
            MessageDigest.getInstance(SHA256).digest(bytes)

        /** 사용자 검증: 공개된 serverSeed 의 SHA-256 == commit 인지. */
        fun verifyCommit(serverSeed: ByteArray, commit: ByteArray): Boolean =
            MessageDigest.isEqual(sha256(serverSeed), commit)

        private fun nonceBytes(n: Long): ByteArray {
            val buf = ByteArray(8)
            var v = n
            for (i in 7 downTo 0) {
                buf[i] = (v and 0xFF).toByte()
                v = v ushr 8
            }
            return buf
        }
    }
}

/**
 * SHA-256 기반 DRBG. 시드 → 카운터 증가하며 32바이트 블록 산출.
 * uniform integer 추출은 modulo bias 회피를 위해 rejection sampling 사용.
 */
internal class Sha256Drbg(seed: ByteArray) {
    private val key: ByteArray = seed.copyOf()
    private var counter: Long = 0
    private var pool: ByteArray = ByteArray(0)
    private var poolPos: Int = 0

    private fun refill() {
        val md = MessageDigest.getInstance(Rng.SHA256)
        md.update(key)
        for (i in 7 downTo 0) {
            md.update(((counter ushr (i * 8)) and 0xFF).toByte())
        }
        pool = md.digest()
        poolPos = 0
        counter++
    }

    private fun nextByte(): Int {
        if (poolPos >= pool.size) refill()
        return pool[poolPos++].toInt() and 0xFF
    }

    private fun nextU32(): Long {
        val b0 = nextByte().toLong()
        val b1 = nextByte().toLong()
        val b2 = nextByte().toLong()
        val b3 = nextByte().toLong()
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    /** [0, bound] 균등 정수. rejection sampling 으로 modulo bias 제거. */
    fun nextIntInclusive(bound: Int): Int {
        require(bound in 0 until Int.MAX_VALUE) { "bound out of range" }
        if (bound == 0) return 0
        val range = (bound + 1).toLong()
        val limit = (1L shl 32) - ((1L shl 32) % range)
        while (true) {
            val v = nextU32()
            if (v < limit) return (v % range).toInt()
        }
    }
}
