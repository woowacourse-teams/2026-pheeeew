package com.pheeeew.data.local.device

import com.pheeeew.domain.model.device.DeviceCredentials
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** Preserves Keychain identity across reinstall; does not delete credentials based on an install marker. */
@OptIn(ExperimentalForeignApi::class)
class IosDeviceCredentialStorage(
    environment: String,
    private val legacyPolicy: LegacyCredentialPolicy = LegacyCredentialPolicy.UNCONFIRMED,
) : DeviceCredentialStorage {
    private val account = "session-$environment"

    init {
        require(environment in setOf("dev", "prod"))
    }

    override suspend fun read(): CredentialRead =
        withContext(Dispatchers.Default) {
            try {
                readString(
                    account,
                )?.let { return@withContext CredentialRead.Found(Json.decodeFromString<DeviceCredentials>(it)) }
                val legacy = readString("refresh-token")
                if (legacy == null || legacyPolicy == LegacyCredentialPolicy.BELONGS_TO_OTHER_ENVIRONMENT) {
                    return@withContext CredentialRead.Missing
                }
                if (legacyPolicy == LegacyCredentialPolicy.UNCONFIRMED) return@withContext CredentialRead.Failure(true)
                val migrated = DeviceCredentials(refreshToken = legacy, generation = 1)
                writeString(account, Json.encodeToString(migrated))
                CredentialRead.Found(migrated)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                CredentialRead.Failure()
            }
        }

    override suspend fun write(credentials: DeviceCredentials): Boolean =
        withContext(Dispatchers.Default) {
            try {
                writeString(account, Json.encodeToString(credentials))
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }

    private fun readString(account: String): String? =
        withQuery(account) { query ->
            CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
            memScoped {
                val result = alloc<COpaquePointerVar>()
                val status = SecItemCopyMatching(query, result.ptr)
                if (status == errSecItemNotFound) return@memScoped null
                check(status == 0) { "Keychain read failed" }
                val data: CFDataRef = checkNotNull(result.ptr[0]).reinterpret()
                try {
                    val size = CFDataGetLength(data).toInt()
                    val bytes = checkNotNull(CFDataGetBytePtr(data)).reinterpret<ByteVar>()
                    ByteArray(size) { bytes[it] }.decodeToString(throwOnInvalidSequence = true)
                } finally {
                    CFRelease(data)
                }
            }
        }

    private fun writeString(
        account: String,
        value: String,
    ) {
        val bytes = value.encodeToByteArray()
        val data: CFDataRef =
            checkNotNull(bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong()) })
        try {
            withQuery(account) { query ->
                val attrs = checkNotNull(CFDictionaryCreateMutable(null, 0, null, null))
                try {
                    CFDictionarySetValue(attrs, kSecValueData, data)
                    val status = SecItemUpdate(query, attrs)
                    if (status == errSecItemNotFound) {
                        CFDictionarySetValue(query, kSecValueData, data)
                        CFDictionarySetValue(
                            query,
                            kSecAttrAccessible,
                            kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                        )
                        check(SecItemAdd(query, null) == 0) { "Keychain add failed" }
                    } else {
                        check(status == 0) { "Keychain update failed" }
                    }
                } finally {
                    CFRelease(attrs)
                }
            }
        } finally {
            CFRelease(data)
        }
    }

    private fun <T> withQuery(
        account: String,
        block: (CFMutableDictionaryRef) -> T,
    ): T {
        val query = checkNotNull(CFDictionaryCreateMutable(null, 0, null, null))
        val service =
            checkNotNull(CFStringCreateWithCString(null, "com.pheeeew.device-registration", kCFStringEncodingUTF8))
        val accountString = checkNotNull(CFStringCreateWithCString(null, account, kCFStringEncodingUTF8))
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, service)
            CFDictionarySetValue(query, kSecAttrAccount, accountString)
            return block(query)
        } finally {
            CFRelease(query)
            CFRelease(service)
            CFRelease(accountString)
        }
    }
}
