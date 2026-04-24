package com.infocar.pokermaster.model

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.util.Base64

/**
 * 모델 매니페스트 서명 검증 — v1.1 §5.4.
 *
 *  - minSdk 29 단말을 포함하므로 네이티브 `java.security.Signature("Ed25519")` 대신
 *    BouncyCastle light-weight API 를 직접 사용 (JCE provider 등록 불필요).
 *  - 공개키는 raw 32 bytes 를 Base64 로 인코딩한 문자열. BuildConfig 에 임베드되어
 *    APK 무결성 (서명된 APK 이내에서 불변) 에 의존해 보호된다.
 *  - 서명은 raw 64 bytes 를 Base64 인코딩. 매니페스트 바이트 전체에 대한 서명.
 */
object Ed25519Verifier {

    private const val PUBLIC_KEY_LEN = 32
    private const val SIGNATURE_LEN = 64

    sealed interface Result {
        data object Valid : Result
        data object PublicKeyNotConfigured : Result
        data class MalformedPublicKey(val reason: String) : Result
        data class MalformedSignature(val reason: String) : Result
        data object Invalid : Result
    }

    /**
     * @param message 매니페스트 원본 바이트 (파일 그대로).
     * @param signatureBase64 서명 파일 내용 (공백·개행 trim 허용).
     * @param publicKeyBase64 BuildConfig 에 임베드된 개발자 공개키 Base64.
     */
    fun verify(
        message: ByteArray,
        signatureBase64: String,
        publicKeyBase64: String,
    ): Result {
        if (publicKeyBase64.isBlank()) return Result.PublicKeyNotConfigured

        val pubKeyBytes = runCatching { Base64.getDecoder().decode(publicKeyBase64.trim()) }
            .getOrElse { return Result.MalformedPublicKey("base64 decode: ${it.message}") }
        if (pubKeyBytes.size != PUBLIC_KEY_LEN) {
            return Result.MalformedPublicKey("expected $PUBLIC_KEY_LEN bytes, got ${pubKeyBytes.size}")
        }

        val sigBytes = runCatching { Base64.getDecoder().decode(signatureBase64.trim()) }
            .getOrElse { return Result.MalformedSignature("base64 decode: ${it.message}") }
        if (sigBytes.size != SIGNATURE_LEN) {
            return Result.MalformedSignature("expected $SIGNATURE_LEN bytes, got ${sigBytes.size}")
        }

        val pub = Ed25519PublicKeyParameters(pubKeyBytes, 0)
        val signer = Ed25519Signer().apply {
            init(/* forSigning = */ false, pub)
            update(message, 0, message.size)
        }
        return if (signer.verifySignature(sigBytes)) Result.Valid else Result.Invalid
    }
}
