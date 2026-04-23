package com.infocar.pokermaster.engine.rules

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.Card
import org.junit.jupiter.api.Test

/**
 * Provably Fair RNG 테스트 (v1.1 §3.5).
 *  - 256bit seed
 *  - SHA-256 commit/reveal
 *  - Knuth shuffle (52장 보존, 균등성 통계)
 *  - 동일 시드 → 동일 deck (재현성)
 *  - 1핸드 1 인스턴스 — deck 은 init 1회 셔플 후 불변
 */
class RngTest {

    // ---------- 시드 길이 검증 ----------
    @Test fun rejects_wrong_seed_size() {
        val ex = runCatching {
            Rng.ofSeeds(ByteArray(16), ByteArray(32), 0L)
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test fun rejects_negative_nonce() {
        val ex = runCatching {
            Rng.ofSeeds(ByteArray(32), ByteArray(32), -1L)
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test fun create_generates_256bit_seeds() {
        val rng = Rng.create(nonce = 0L)
        assertThat(rng.serverSeed.size).isEqualTo(32)
        assertThat(rng.clientSeed.size).isEqualTo(32)
    }

    // ---------- commit/reveal ----------
    @Test fun commit_is_sha256_of_server_seed() {
        val rng = Rng.create(nonce = 0L)
        val expected = Rng.sha256(rng.serverSeed)
        assertThat(rng.commit.toList()).isEqualTo(expected.toList())
    }

    @Test fun verify_commit_passes_for_real_seed() {
        val rng = Rng.create(nonce = 0L)
        assertThat(Rng.verifyCommit(rng.serverSeed, rng.commit)).isTrue()
    }

    @Test fun verify_commit_fails_for_tampered_seed() {
        val rng = Rng.create(nonce = 0L)
        val tampered = rng.serverSeed.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertThat(Rng.verifyCommit(tampered, rng.commit)).isFalse()
    }

    // ---------- 셔플 정확성 ----------
    @Test fun deck_preserves_all_52_cards() {
        val rng = Rng.create(nonce = 0L)
        assertThat(rng.deck).hasSize(52)
        assertThat(rng.deck.toSet()).hasSize(52)
    }

    @Test fun deck_is_deterministic_for_same_seed() {
        val server = ByteArray(32) { it.toByte() }
        val client = ByteArray(32) { (it + 100).toByte() }
        val a = Rng.ofSeeds(server, client, 7L).deck
        val b = Rng.ofSeeds(server, client, 7L).deck
        assertThat(a).isEqualTo(b)
    }

    @Test fun different_nonce_gives_different_deck() {
        val server = ByteArray(32) { it.toByte() }
        val client = ByteArray(32) { (it + 100).toByte() }
        val a = Rng.ofSeeds(server, client, 1L).deck
        val b = Rng.ofSeeds(server, client, 2L).deck
        assertThat(a).isNotEqualTo(b)
    }

    @Test fun different_server_seed_gives_different_deck() {
        val client = ByteArray(32) { 1 }
        val a = Rng.ofSeeds(ByteArray(32) { 1 }, client, 0L).deck
        val b = Rng.ofSeeds(ByteArray(32) { 2 }, client, 0L).deck
        assertThat(a).isNotEqualTo(b)
    }

    @Test fun different_client_seed_gives_different_deck() {
        val server = ByteArray(32) { 1 }
        val a = Rng.ofSeeds(server, ByteArray(32) { 1 }, 0L).deck
        val b = Rng.ofSeeds(server, ByteArray(32) { 2 }, 0L).deck
        assertThat(a).isNotEqualTo(b)
    }

    // ---------- 균등성 (chi-squared lite): 첫 카드 분포 ----------
    @Test fun first_card_distribution_is_roughly_uniform() {
        val firstCards = mutableMapOf<Card, Int>()
        repeat(5_200) {
            val rng = Rng.create(nonce = it.toLong())
            val first = rng.deck[0]
            firstCards[first] = (firstCards[first] ?: 0) + 1
        }
        assertThat(firstCards.keys).hasSize(52)
        firstCards.values.forEach { count ->
            assertThat(count).isAtLeast(40)
            assertThat(count).isAtMost(200)
        }
    }

    // ---------- DRBG 균등성 (modulo bias 회피) ----------
    @Test fun drbg_uniform_int_no_bias_for_small_bound() {
        val drbg = Sha256Drbg(ByteArray(32) { 42 })
        val counts = IntArray(7)
        repeat(70_000) {
            counts[drbg.nextIntInclusive(6)]++
        }
        counts.forEach { c ->
            assertThat(c).isAtLeast(9500)
            assertThat(c).isAtMost(10500)
        }
    }

    @Test fun drbg_zero_bound_returns_zero() {
        val drbg = Sha256Drbg(ByteArray(32))
        repeat(100) { assertThat(drbg.nextIntInclusive(0)).isEqualTo(0) }
    }
}
