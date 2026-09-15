package com.pheeeew.data.local.device

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pheeeew.domain.model.device.RefreshToken

class AndroidEncryptedDeviceTokenStorage(
    context: Context,
) : DeviceTokenStorage {
    private val preferences =
        EncryptedSharedPreferences.create(
            context,
            PREFERENCES_NAME,
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    override suspend fun getRefreshToken(): RefreshToken? =
        preferences.getString(REFRESH_TOKEN_KEY, null)?.let(::RefreshToken)

    override suspend fun saveRefreshToken(refreshToken: RefreshToken) {
        preferences.edit().putString(REFRESH_TOKEN_KEY, refreshToken.value).apply()
    }

    override suspend fun clear() {
        preferences.edit().remove(REFRESH_TOKEN_KEY).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "device_credentials"
        const val REFRESH_TOKEN_KEY = "refresh_token"
    }
}
