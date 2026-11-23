package com.example.gpago_app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL_NAME = "mx.gpago.app/secure_session"
        private const val PREFS_NAME = "gpago_secure_session_prefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "gpago_session_aes_gcm_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_NAME)
            .setMethodCallHandler { call, result ->
                val key = call.argument<String>("key")?.trim()
                if (key.isNullOrEmpty()) {
                    result.error("INVALID_ARGS", "La clave de almacenamiento es obligatoria", null)
                    return@setMethodCallHandler
                }
                when (call.method) {
                    "read" -> result.success(readEncryptedValue(key))
                    "write" -> {
                        val value = call.argument<String>("value")
                        if (value == null) {
                            result.error("INVALID_ARGS", "El valor a resguardar es obligatorio", null)
                            return@setMethodCallHandler
                        }
                        try {
                            writeEncryptedValue(key, value)
                            result.success(null)
                        } catch (_: GeneralSecurityException) {
                            result.error("KEYSTORE_ERROR", "No se pudo cifrar la sesión en KeyStore", null)
                        }
                    }
                    "delete" -> {
                        deleteEncryptedValue(key)
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun readEncryptedValue(key: String): String? {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encoded = prefs.getString(key, null) ?: return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) {
                prefs.edit().remove(key).apply()
                return null
            }
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val cipherText = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, obtenerOLlaveKeyStore(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (_: Exception) {
            prefs.edit().remove(key).apply()
            null
        }
    }

    private fun writeEncryptedValue(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtenerOLlaveKeyStore())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, encoded)
            .apply()
    }

    private fun deleteEncryptedValue(key: String) {
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }

    private fun obtenerOLlaveKeyStore(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
