package com.jarvis.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the Groq API key as AES-GCM ciphertext using a device-bound Android Keystore key. */
object GroqKeyVault {
    private const val PREFS = "jarvis_groq_api"
    private const val KEY_CIPHERTEXT = "groq_api_key_ciphertext"
    private const val KEY_ALIAS = "jarvis_groq_api_key_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun save(context: Context, key: String) {
        require(key.length >= 20) { "Groq API key looks too short." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + ciphertext
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_CIPHERTEXT, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun read(context: Context): String? {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CIPHERTEXT, null) ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            if (packed.size <= 12) return null
            val iv = packed.copyOfRange(0, 12)
            val ciphertext = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_CIPHERTEXT).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
