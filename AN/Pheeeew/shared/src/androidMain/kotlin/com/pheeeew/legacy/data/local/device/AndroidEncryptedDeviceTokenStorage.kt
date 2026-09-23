package com.pheeeew.legacy.data.local.device

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pheeeew.legacy.domain.model.device.RefreshToken
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

class AndroidEncryptedDeviceTokenStorage(
    context: Context,
) : DeviceTokenStorage {
    private val appContext = context.applicationContext

    private var cachedPreferences: SharedPreferences? = null

    private val preferences: SharedPreferences
        get() = cachedPreferences ?: createPreferences().also { cachedPreferences = it }

    override suspend fun getRefreshToken(): com.pheeeew.legacy.domain.model.device.RefreshToken? =
        try {
            preferences.getString(REFRESH_TOKEN_KEY, null)?.let(::RefreshToken)
        } catch (error: GeneralSecurityException) {
            resetCorruptedCredentials()
            null
        } catch (error: SecurityException) {
            resetCorruptedCredentials()
            null
        } catch (error: IOException) {
            resetCorruptedCredentials()
            null
        } catch (error: ClassCastException) {
            resetCorruptedCredentials()
            null
        }

    override suspend fun saveRefreshToken(refreshToken: com.pheeeew.legacy.domain.model.device.RefreshToken) {
        preferences.edit().putString(REFRESH_TOKEN_KEY, refreshToken.value).apply()
    }

    override suspend fun clear() {
        preferences.edit().remove(REFRESH_TOKEN_KEY).apply()
    }

    private fun createPreferences() =
        EncryptedSharedPreferences.create(
            appContext,
            PREFERENCES_NAME,
            MasterKey
                .Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private fun resetCorruptedCredentials() {
        cachedPreferences = null
        appContext.deleteSharedPreferences(PREFERENCES_NAME)
        runCatching {
            KeyStore
                .getInstance("AndroidKeyStore")
                .apply {
                    load(null)
                    deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "device_credentials"
        const val REFRESH_TOKEN_KEY = "refresh_token"
    }
}
