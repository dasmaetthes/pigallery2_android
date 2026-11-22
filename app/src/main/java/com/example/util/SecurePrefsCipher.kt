package com.example.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts sensitive preference values (password, session cookies, CSRF token)
 * using an Android Keystore AES-256-GCM key.
 *
 * Values are stored with the prefix "enc_v1:" followed by Base64(12-byte IV || ciphertext+tag).
 * Legacy plaintext values stored before encryption was introduced are transparently readable
 * and migrated on write/read.
 */
object SecurePrefsCipher {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "pigallery_secure_prefs_aes_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    const val ENCRYPTED_PREFIX = "enc_v1:"

    @Volatile
    private var fallbackTestKey: SecretKey? = null

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) {
                existing
            } else {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            // Fallback for JVM/Robolectric unit test environments where AndroidKeyStore is unavailable
            getOrCreateFallbackKey()
        }
    }

    @Synchronized
    private fun getOrCreateFallbackKey(): SecretKey {
        fallbackTestKey?.let { return it }
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256, SecureRandom())
        return keyGenerator.generateKey().also { fallbackTestKey = it }
    }

    fun isEncrypted(storedValue: String?): Boolean {
        return storedValue != null && storedValue.startsWith(ENCRYPTED_PREFIX)
    }

    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            // If Keystore key was invalidated (e.g., backup restore), regenerate once
            try {
                resetKeystoreKey()
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
                val iv = cipher.iv
                val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
                val combined = ByteArray(iv.size + encrypted.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
                ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
            } catch (_: Exception) {
                ""
            }
        }
    }

    /**
     * Decrypts a stored preference value.
     * Returns Pair(decryptedValue, needsMigrationToEncrypted).
     */
    fun decrypt(storedValue: String?): Pair<String, Boolean> {
        if (storedValue.isNullOrEmpty()) return "" to false
        if (!storedValue.startsWith(ENCRYPTED_PREFIX)) {
            // Legacy plaintext value; caller should migrate it to encrypted form
            return storedValue to true
        }
        return try {
            val payload = Base64.decode(storedValue.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
            if (payload.size <= GCM_IV_LENGTH_BYTES) return "" to false
            val iv = payload.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val cipherBytes = payload.copyOfRange(GCM_IV_LENGTH_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8) to false
        } catch (_: Exception) {
            "" to false
        }
    }

    private fun resetKeystoreKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (_: Exception) {
            // Ignore
        }
    }
}
