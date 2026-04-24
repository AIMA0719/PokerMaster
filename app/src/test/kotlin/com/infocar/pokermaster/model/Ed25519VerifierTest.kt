package com.infocar.pokermaster.model

import com.google.common.truth.Truth.assertThat
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64

class Ed25519VerifierTest {

    private data class Keypair(
        val privateKey: Ed25519PrivateKeyParameters,
        val publicKey: Ed25519PublicKeyParameters,
    )

    private fun keypair(): Keypair {
        val gen = Ed25519KeyPairGenerator().apply {
            init(Ed25519KeyGenerationParameters(SecureRandom()))
        }
        val kp = gen.generateKeyPair()
        return Keypair(
            kp.private as Ed25519PrivateKeyParameters,
            kp.public as Ed25519PublicKeyParameters,
        )
    }

    private fun sign(msg: ByteArray, priv: Ed25519PrivateKeyParameters): ByteArray {
        val signer = Ed25519Signer().apply {
            init(/* forSigning = */ true, priv)
            update(msg, 0, msg.size)
        }
        return signer.generateSignature()
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    @Test
    fun `verify succeeds for correct keypair, message, signature`() {
        val kp = keypair()
        val msg = "hello world".toByteArray()
        val sig = sign(msg, kp.privateKey)

        val result = Ed25519Verifier.verify(
            message = msg,
            signatureBase64 = b64(sig),
            publicKeyBase64 = b64(kp.publicKey.encoded),
        )
        assertThat(result).isEqualTo(Ed25519Verifier.Result.Valid)
    }

    @Test
    fun `verify fails when signature is bit-flipped`() {
        val kp = keypair()
        val msg = "poker manifest v1".toByteArray()
        val sig = sign(msg, kp.privateKey)
        sig[0] = (sig[0].toInt() xor 0x01).toByte()  // 1-bit flip

        val result = Ed25519Verifier.verify(
            message = msg,
            signatureBase64 = b64(sig),
            publicKeyBase64 = b64(kp.publicKey.encoded),
        )
        assertThat(result).isEqualTo(Ed25519Verifier.Result.Invalid)
    }

    @Test
    fun `verify fails when message is tampered`() {
        val kp = keypair()
        val msg = "poker manifest v1".toByteArray()
        val sig = sign(msg, kp.privateKey)
        val tampered = msg.copyOf()
        tampered[tampered.size - 1] = (tampered.last().toInt() xor 0x20).toByte()

        val result = Ed25519Verifier.verify(
            message = tampered,
            signatureBase64 = b64(sig),
            publicKeyBase64 = b64(kp.publicKey.encoded),
        )
        assertThat(result).isEqualTo(Ed25519Verifier.Result.Invalid)
    }

    @Test
    fun `verify reports PublicKeyNotConfigured for empty key`() {
        val result = Ed25519Verifier.verify(
            message = "any".toByteArray(),
            signatureBase64 = b64(ByteArray(64)),
            publicKeyBase64 = "",
        )
        assertThat(result).isEqualTo(Ed25519Verifier.Result.PublicKeyNotConfigured)
    }

    @Test
    fun `verify reports MalformedPublicKey for wrong length`() {
        val result = Ed25519Verifier.verify(
            message = "any".toByteArray(),
            signatureBase64 = b64(ByteArray(64)),
            publicKeyBase64 = b64(ByteArray(16)), // 16 != 32
        )
        assertThat(result).isInstanceOf(Ed25519Verifier.Result.MalformedPublicKey::class.java)
    }

    @Test
    fun `verify reports MalformedSignature for wrong length`() {
        val kp = keypair()
        val result = Ed25519Verifier.verify(
            message = "any".toByteArray(),
            signatureBase64 = b64(ByteArray(32)), // 32 != 64
            publicKeyBase64 = b64(kp.publicKey.encoded),
        )
        assertThat(result).isInstanceOf(Ed25519Verifier.Result.MalformedSignature::class.java)
    }

    @Test
    fun `verify tolerates whitespace around signature base64`() {
        val kp = keypair()
        val msg = "spaces".toByteArray()
        val sig = sign(msg, kp.privateKey)
        val sigBase64 = "\n  " + b64(sig) + "\n"

        val result = Ed25519Verifier.verify(
            message = msg,
            signatureBase64 = sigBase64,
            publicKeyBase64 = b64(kp.publicKey.encoded),
        )
        assertThat(result).isEqualTo(Ed25519Verifier.Result.Valid)
    }
}
