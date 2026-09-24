package com.personalip.app.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 加密存储 API Key（使用 androidx.security EncryptedSharedPreferences，
 * 基于 AndroidKeyStore + Tink）。其他非敏感的 baseUrl/model 走 [AppDataStore]。
 */
@Singleton
class SecurePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun setApiKey(key: String?) {
        val editor = prefs.edit()
        if (key.isNullOrBlank()) editor.remove(KEY_API_KEY) else editor.putString(KEY_API_KEY, key)
        editor.apply()
    }

    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    private companion object {
        const val FILE_NAME = "secure_prefs"
        const val KEY_API_KEY = "ai_api_key"
    }
}
