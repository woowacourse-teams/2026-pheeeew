package com.pheeeew.legacy.data.local.device

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.platform.app.InstrumentationRegistry
import com.pheeeew.legacy.domain.model.device.RefreshToken
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.security.KeyStore

class AndroidEncryptedDeviceTokenStorageTest {
    private val context: Context =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext

    @Before
    fun setUp() {
        resetStorage()
    }

    @After
    fun tearDown() {
        resetStorage()
    }

    @Test
    fun restoredEncryptedFileWithoutKeystoreKeyIsDiscardedAndCanBeRecreated() =
        runTest {
            val preferences =
                EncryptedSharedPreferences.create(
                    context,
                    PREFERENCES_NAME,
                    createMasterKey(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            preferences.edit().putString(REFRESH_TOKEN_KEY, "restored-token").commit()
            deleteMasterKey()

            val storage = AndroidEncryptedDeviceTokenStorage(context)

            assertNull(storage.getRefreshToken())

            storage.saveRefreshToken(
                com.pheeeew.legacy.domain.model.device.RefreshToken(
                    "new-token"
                )
            )

            assertEquals(com.pheeeew.legacy.domain.model.device.RefreshToken("new-token"), storage.getRefreshToken())

            val recreatedStorage = AndroidEncryptedDeviceTokenStorage(context)

            assertEquals(com.pheeeew.legacy.domain.model.device.RefreshToken("new-token"), recreatedStorage.getRefreshToken())
        }

    private fun createMasterKey(): MasterKey =
        MasterKey
            .Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private fun resetStorage() {
        context.deleteSharedPreferences(PREFERENCES_NAME)
        deleteMasterKey()
    }

    private fun deleteMasterKey() {
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
