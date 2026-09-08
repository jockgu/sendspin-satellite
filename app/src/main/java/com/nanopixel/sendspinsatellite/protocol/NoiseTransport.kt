package com.nanopixel.sendspinsatellite.protocol

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters

/** Minimal responder implementation of Sendspin's Noise_KKpsk2_25519_ChaChaPoly_SHA256. */
class NoiseTransport private constructor(
    private val sendingKey: ByteArray,
    private val receivingKey: ByteArray,
) {
    private var sendingNonce = 0L
    private var receivingNonce = 0L

    fun encrypt(plaintext: ByteArray): ByteArray =
        aead(true, sendingKey, sendingNonce++, ByteArray(0), plaintext)

    fun decrypt(ciphertext: ByteArray): ByteArray =
        aead(false, receivingKey, receivingNonce++, ByteArray(0), ciphertext)

    companion object {
        const val SUITE = "25519_ChaChaPoly_SHA256"
        private const val PROTOCOL_NAME = "Noise_KKpsk2_25519_ChaChaPoly_SHA256"

        fun createResponder(
            localPrivateKey: ByteArray,
            remotePublicKey: ByteArray,
            prologue: ByteArray,
            serverMessageOne: ByteArray,
            psk: ByteArray,
            legacyEphemeralKeyMix: Boolean = false,
        ): HandshakeResult {
            require(localPrivateKey.size == KEY_LENGTH)
            require(remotePublicKey.size == KEY_LENGTH)
            require(psk.size == KEY_LENGTH)

            val state = SymmetricState(PROTOCOL_NAME.toByteArray(StandardCharsets.UTF_8))
            state.mixHash(prologue)

            // KK pre-messages: initiator static key then responder static key.
            state.mixHash(remotePublicKey)
            val local = X25519PrivateKeyParameters(localPrivateKey, 0)
            state.mixHash(local.generatePublicKey().encoded)

            var cursor = 0
            val remoteEphemeral = serverMessageOne.copyOfRange(cursor, cursor + KEY_LENGTH)
            cursor += KEY_LENGTH
            state.mixHash(remoteEphemeral)
            if (legacyEphemeralKeyMix) state.mixKey(remoteEphemeral)

            state.mixKey(agreement(local, remoteEphemeral)) // es, responder side.
            state.mixKey(agreement(local, remotePublicKey)) // ss.
            val firstPayload = state.decryptAndHash(serverMessageOne.copyOfRange(cursor, serverMessageOne.size))

            val localEphemeral = X25519PrivateKeyParameters(SecureRandom())
            val response = ArrayList<Byte>()
            response.addAll(localEphemeral.generatePublicKey().encoded.toList())
            state.mixHash(localEphemeral.generatePublicKey().encoded)
            if (legacyEphemeralKeyMix) state.mixKey(localEphemeral.generatePublicKey().encoded)
            state.mixKey(agreement(localEphemeral, remoteEphemeral)) // ee.
            state.mixKey(agreement(localEphemeral, remotePublicKey)) // se, responder side.
            state.mixKeyAndHash(psk)
            response.addAll(state.encryptAndHash("{}".toByteArray(StandardCharsets.UTF_8)).toList())

            val (first, second) = state.split()
            // Noise assigns responder send=second and receive=first.
            return HandshakeResult(firstPayload, response.toByteArray(), NoiseTransport(second, first))
        }

        fun pskId(psk: ByteArray): String = base64Url(
            sha256("sendspin-psk-id-v1".toByteArray(StandardCharsets.UTF_8) + psk),
        )

        fun sentinelPsk(): ByteArray = sha256("sendspin-sentinel-psk-v1".toByteArray(StandardCharsets.UTF_8))

        fun base64Url(bytes: ByteArray): String =
            android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)

        fun fromBase64Url(value: String): ByteArray =
            android.util.Base64.decode(value, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)

        private fun agreement(privateKey: X25519PrivateKeyParameters, publicKey: ByteArray): ByteArray =
            ByteArray(KEY_LENGTH).also { privateKey.generateSecret(X25519PublicKeyParameters(publicKey, 0), it, 0) }

        private fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

        private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(key, "HmacSHA256"))
                doFinal(data)
            }

        private fun hkdf(chainingKey: ByteArray, input: ByteArray, outputs: Int): List<ByteArray> {
            val temporaryKey = hmac(chainingKey, input)
            var previous = ByteArray(0)
            return (1..outputs).map { index ->
                previous = hmac(temporaryKey, previous + byteArrayOf(index.toByte()))
                previous
            }
        }

        private fun aead(
            encrypt: Boolean,
            key: ByteArray,
            nonce: Long,
            additionalData: ByteArray,
            input: ByteArray,
        ): ByteArray {
            val nonceBytes = ByteArray(12)
            for (index in 0 until 8) nonceBytes[index + 4] = (nonce ushr (index * 8)).toByte()
            val cipher = ChaCha20Poly1305()
            cipher.init(encrypt, AEADParameters(KeyParameter(key), 128, nonceBytes, additionalData))
            val output = ByteArray(cipher.getOutputSize(input.size))
            val length = cipher.processBytes(input, 0, input.size, output, 0)
            return try {
                output.copyOf(length + cipher.doFinal(output, length))
            } catch (exception: InvalidCipherTextException) {
                throw IllegalArgumentException("Noise authentication failed", exception)
            }
        }

        private const val KEY_LENGTH = 32
    }

    data class HandshakeResult(
        val serverPayload: ByteArray,
        val response: ByteArray,
        val transport: NoiseTransport,
    )

    private class SymmetricState(protocolName: ByteArray) {
        private var chainingKey = sha256(protocolName)
        private var handshakeHash = chainingKey.copyOf()
        private var cipherKey: ByteArray? = null
        private var nonce = 0L

        fun mixHash(data: ByteArray) {
            handshakeHash = sha256(handshakeHash + data)
        }

        fun mixKey(input: ByteArray) {
            val outputs = hkdf(chainingKey, input, 2)
            chainingKey = outputs[0]
            cipherKey = outputs[1]
            nonce = 0L
        }

        fun mixKeyAndHash(input: ByteArray) {
            val outputs = hkdf(chainingKey, input, 3)
            chainingKey = outputs[0]
            mixHash(outputs[1])
            cipherKey = outputs[2]
            nonce = 0L
        }

        fun decryptAndHash(ciphertext: ByteArray): ByteArray {
            val plaintext = cipherKey?.let { aead(false, it, nonce++, handshakeHash, ciphertext) } ?: ciphertext
            mixHash(ciphertext)
            return plaintext
        }

        fun encryptAndHash(plaintext: ByteArray): ByteArray {
            val ciphertext = cipherKey?.let { aead(true, it, nonce++, handshakeHash, plaintext) } ?: plaintext
            mixHash(ciphertext)
            return ciphertext
        }

        fun split(): Pair<ByteArray, ByteArray> = hkdf(chainingKey, ByteArray(0), 2).let { it[0] to it[1] }
    }
}
