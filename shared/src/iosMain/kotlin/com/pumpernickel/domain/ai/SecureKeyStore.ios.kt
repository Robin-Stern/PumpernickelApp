@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pumpernickel.domain.ai

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.numberWithBool
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
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

private const val SERVICE = "PumpernickelApp.AI"
private const val ACCOUNT = "openai.api.key"

actual class SecureKeyStore {

    actual suspend fun writeApiKey(value: String) = withContext(Dispatchers.Default) {
        val data: NSData = value.encodeToByteArray().toNSData()

        // Try update first; if not found, add.
        val updateAttrs = NSMutableDictionary().apply {
            setObject(data, forKey = kSecValueData!! as NSString)
        }
        @Suppress("CAST_NEVER_SUCCEEDS")
        val updateStatus = SecItemUpdate(
            baseQuery() as CFDictionaryRef,
            updateAttrs as CFDictionaryRef
        )
        if (updateStatus == errSecItemNotFound) {
            val addQuery = baseQuery().apply {
                setObject(data, forKey = kSecValueData!! as NSString)
                setObject(
                    kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly!! as NSString,
                    forKey = kSecAttrAccessible!! as NSString
                )
            }
            @Suppress("CAST_NEVER_SUCCEEDS")
            val addStatus = SecItemAdd(addQuery as CFDictionaryRef, null)
            require(addStatus == errSecSuccess) {
                "Keychain SecItemAdd failed (status $addStatus)"
            }
        } else {
            require(updateStatus == errSecSuccess) {
                "Keychain SecItemUpdate failed (status $updateStatus)"
            }
        }
    }

    actual suspend fun readApiKey(): String? = withContext(Dispatchers.Default) {
        memScoped {
            val query = baseQuery().apply {
                setObject(kSecMatchLimitOne!! as NSString, forKey = kSecMatchLimit!! as NSString)
                setObject(NSNumber.numberWithBool(true), forKey = kSecReturnData!! as NSString)
            }
            val resultVar = alloc<CFTypeRefVar>()
            @Suppress("CAST_NEVER_SUCCEEDS")
            val status = SecItemCopyMatching(query as CFDictionaryRef, resultVar.ptr)
            if (status != errSecSuccess) return@withContext null
            val cfData = resultVar.value ?: return@withContext null
            // CFData is toll-free bridged with NSData
            @Suppress("CAST_NEVER_SUCCEEDS")
            val nsData = cfData as NSData
            NSString.create(data = nsData, encoding = NSUTF8StringEncoding) as String?
        }
    }

    actual suspend fun clearApiKey() = withContext(Dispatchers.Default) {
        @Suppress("CAST_NEVER_SUCCEEDS")
        SecItemDelete(baseQuery() as CFDictionaryRef)
        Unit
    }

    private fun baseQuery(): NSMutableDictionary {
        return NSMutableDictionary().apply {
            setObject(kSecClassGenericPassword!! as NSString, forKey = kSecClass!! as NSString)
            setObject(SERVICE, forKey = kSecAttrService!! as NSString)
            setObject(ACCOUNT, forKey = kSecAttrAccount!! as NSString)
        }
    }
}

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
