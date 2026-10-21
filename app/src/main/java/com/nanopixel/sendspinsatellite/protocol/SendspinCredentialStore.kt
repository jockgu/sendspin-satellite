package com.nanopixel.sendspinsatellite.protocol

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted, app-private storage for sendspin-cpp's opaque identity and pairing blobs. */
internal class SendspinCredentialStore(context: Context) {
    private val directory = File(context.applicationContext.noBackupFilesDir, DIRECTORY_NAME)

    @Synchronized
    fun loadBlob(key: String): ByteArray? {
        val file = fileFor(key)
        if (!file.exists()) return null
        return try {
            if (file.length() > MAX_STORED_BLOB_SIZE) error("Credential blob is too large")
            val stored = AtomicFile(file).openRead().use { it.readBytes() }
            if (stored.size < 1 + GCM_IV_SIZE + GCM_TAG_SIZE || stored[0] != FORMAT_VERSION) {
                return byteArrayOf()
            }
            val iv = stored.copyOfRange(1, 1 + GCM_IV_SIZE)
            val cipherText = stored.copyOfRange(1 + GCM_IV_SIZE, stored.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
            cipher.doFinal(cipherText)
        } catch (_: Exception) {
            // An unreadable credential store must never be treated as a fresh pairing identity.
            byteArrayOf()
        }
    }

    @Synchronized
    fun saveBlob(key: String, bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) {
            return false
        }
        var output: FileOutputStream? = null
        val atomic = AtomicFile(fileFor(key))
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv
            val cipherText = cipher.doFinal(bytes)
            output = atomic.startWrite()
            output.write(byteArrayOf(FORMAT_VERSION))
            output.write(iv)
            output.write(cipherText)
            atomic.finishWrite(output)
            output = null
            true
        } catch (_: Exception) {
            output?.let(atomic::failWrite)
            false
        }
    }

    private fun fileFor(key: String): File {
        check(key.isNotBlank())
        if (!directory.exists() && !directory.mkdirs()) error("Unable to create credential directory")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
        return File(directory, "$digest.blob")
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "sendspin_credentials"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "sendspin_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
        const val GCM_IV_SIZE = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_SIZE = GCM_TAG_BITS / 8
        const val MAX_STORED_BLOB_SIZE = 4096L
    }
}
