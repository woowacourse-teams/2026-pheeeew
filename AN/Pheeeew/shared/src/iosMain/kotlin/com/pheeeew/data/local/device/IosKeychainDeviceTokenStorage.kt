package com.pheeeew.data.local.device

import com.pheeeew.domain.model.device.RefreshToken
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFBooleanTrue
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class)
class IosKeychainDeviceTokenStorage : DeviceTokenStorage {
    override suspend fun getRefreshToken(): RefreshToken? =
        read(REFRESH_TOKEN_ACCOUNT)?.let(::RefreshToken)

    override suspend fun saveRefreshToken(refreshToken: RefreshToken) {
        write(REFRESH_TOKEN_ACCOUNT, refreshToken.value)
    }

    override suspend fun clear() {
        delete(REFRESH_TOKEN_ACCOUNT)
    }

    internal fun readString(account: String): String? = read(account)

    internal fun writeString(account: String, value: String) {
        write(account, value)
    }

    private fun read(account: String): String? {
        val query = baseQuery(account)
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val data = memScoped {
            val result = alloc<CPointerVar<ByteVar>>()
            if (SecItemCopyMatching(query, result.ptr.reinterpret()) != 0) null else result.ptr[0]
        } ?: return null
        val dataRef = data as platform.CoreFoundation.CFDataRef
        val pointer = CFDataGetBytePtr(dataRef)?.reinterpret<kotlinx.cinterop.ByteVar>() ?: return null
        val bytes = ByteArray(CFDataGetLength(dataRef).toInt()) { index ->
            pointer.reinterpret<kotlinx.cinterop.ByteVar>()[index]
        }
        return bytes.decodeToString()
    }

    private fun write(account: String, value: String) {
        val data = value.encodeToByteArray().usePinned { bytes ->
            CFDataCreate(null, bytes.addressOf(0).reinterpret(), bytes.get().size.toLong())
        }
        val query = baseQuery(account)
        val updateAttributes = CFDictionaryCreateMutable(null, 0, null, null)
        CFDictionarySetValue(updateAttributes, kSecValueData, data)
        val updateStatus = SecItemUpdate(query, updateAttributes)
        if (updateStatus == errSecItemNotFound) {
            CFDictionarySetValue(query, kSecValueData, data)
            SecItemAdd(query, null)
        }
    }

    private fun delete(account: String) {
        SecItemDelete(baseQuery(account))
    }

    private fun baseQuery(account: String) = CFDictionaryCreateMutable(null, 0, null, null).also { query ->
        CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionarySetValue(query, kSecAttrService, CFStringCreateWithCString(null, SERVICE, kCFStringEncodingUTF8))
        CFDictionarySetValue(query, kSecAttrAccount, CFStringCreateWithCString(null, account, kCFStringEncodingUTF8))
    }

    private companion object {
        const val SERVICE = "com.pheeeew.device-registration"
        const val REFRESH_TOKEN_ACCOUNT = "refresh-token"
    }
}
