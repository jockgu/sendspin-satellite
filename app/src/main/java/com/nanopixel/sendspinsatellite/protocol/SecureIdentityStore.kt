package com.nanopixel.sendspinsatellite.protocol

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Stores the Sendspin static private key encrypted by the Android Keystore. */
class SecureIdentityStore(context: Context) {
    private val preferences = context.getSharedPreferences("sendspin_identity", Context.MODE_PRIVATE)

    fun getOrCreatePrivateKey(): ByteArray {
        preferences.getString(PRIVATE_KEY, null)?.let { return decrypt(it) }
        return ByteArray(KEY_SIZE).also { SecureRandom().nextBytes(it) }.also { privateKey ->
            preferences.edit().putString(PRIVATE_KEY, encrypt(privateKey)).apply()
        }
    }

    private fun encrypt(plainText: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return encode(cipher.iv) + ":" + encode(cipher.doFinal(plainText))
    }

    private fun decrypt(encoded: String): ByteArray {
        val parts = encoded.split(":", limit = 2)
        require(parts.size == 2) { "Stored Sendspin identity is malformed" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), javax.crypto.spec.GCMParameterSpec(128, decode(parts[0])))
        return cipher.doFinal(decode(parts[1]))
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun encode(value: ByteArray): String =
        android.util.Base64.encodeToString(value, android.util.Base64.NO_WRAP)

    private fun decode(value: String): ByteArray =
        android.util.Base64.decode(value, android.util.Base64.NO_WRAP)

    private companion object {
        const val PRIVATE_KEY = "private_key"
        const val KEY_ALIAS = "sendspin_satellite_identity"
        const val KEY_SIZE = 32
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
