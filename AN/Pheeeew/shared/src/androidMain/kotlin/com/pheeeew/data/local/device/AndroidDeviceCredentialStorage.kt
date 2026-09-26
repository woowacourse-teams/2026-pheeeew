package com.pheeeew.data.local.device

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pheeeew.domain.model.device.DeviceCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Uses the existing backup-excluded encrypted file, with separate records for each environment. */
class AndroidDeviceCredentialStorage(
    context: Context,
    environment: String,
    private val legacyPolicy: LegacyCredentialPolicy = LegacyCredentialPolicy.UNCONFIRMED,
) : DeviceCredentialStorage {
    private val context = context.applicationContext
    private val key = "session_$environment"
    private var preferences: SharedPreferences? = null
    private var writeFailed = false

    init {
        require(environment in setOf("dev", "prod"))
    }

    override suspend fun read(): CredentialRead =
        withContext(Dispatchers.IO) {
            try {
                if (writeFailed) return@withContext CredentialRead.Failure()
                val prefs = preferences()
                prefs.getString(key, null)?.let {
                    return@withContext CredentialRead.Found(Json.decodeFromString<DeviceCredentials>(it))
                }
                val legacy = prefs.getString("refresh_token", null)
                migrateLegacyCredentials(legacy, legacyPolicy) { persist(it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                CredentialRead.Failure()
            }
        }

    override suspend fun write(credentials: DeviceCredentials): Boolean =
        withContext(Dispatchers.IO) {
            try {
                !writeFailed && persist(credentials)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                writeFailed = true
                false
            }
        }

    private fun persist(credentials: DeviceCredentials): Boolean =
        preferences().edit().putString(key, Json.encodeToString(credentials)).commit().also {
            // A failed commit may still update SharedPreferences memory. Never read that as durable.
            if (!it) writeFailed = true
        }

    private fun preferences(): SharedPreferences =
        preferences ?: EncryptedSharedPreferences
            .create(
                context,
                "device_credentials",
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ).also { preferences = it }
}
