@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pumpernickel.domain.ai

import kotlinx.cinterop.CFBridgingRelease
import kotlinx.cinterop.CFBridgingRetain
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
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

    actual suspend fun writeApiKey(value: String) = withContext(Dispatchers.IO) {
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: error("Failed to encode API key as UTF-8")

        // Try update first; if not found, add.
        val updateQuery = baseQuery()
        val updateAttrs = CFDictionaryCreateMutable(kCFAllocatorDefault, 1, null, null)
        CFDictionarySetValueBridge(updateAttrs, kSecValueData, data)
        val updateStatus = SecItemUpdate(updateQuery as CFDictionaryRef, updateAttrs as CFDictionaryRef)
        if (updateStatus == errSecItemNotFound) {
            val addQuery = baseQuery()
            CFDictionarySetValueBridge(addQuery, kSecValueData, data)
            CFDictionarySetValueBridge(addQuery, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            val addStatus = SecItemAdd(addQuery as CFDictionaryRef, null)
            require(addStatus == errSecSuccess) { "Keychain SecItemAdd failed (status $addStatus)" }
        } else {
            require(updateStatus == errSecSuccess) { "Keychain SecItemUpdate failed (status $updateStatus)" }
        }
    }

    actual suspend fun readApiKey(): String? = withContext(Dispatchers.IO) {
        memScoped {
            val query = baseQuery()
            CFDictionarySetValueBridge(query, kSecMatchLimit, kSecMatchLimitOne)
            CFDictionarySetValueBridge(query, kSecReturnData, kCFBooleanTrue)

            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
            if (status == errSecItemNotFound) return@withContext null
            if (status != errSecSuccess) return@withContext null

            val cfData = result.value ?: return@withContext null
            val nsData = CFBridgingRelease(cfData) as? NSData ?: return@withContext null
            NSString.create(nsData, NSUTF8StringEncoding) as? String
        }
    }

    actual suspend fun clearApiKey() = withContext(Dispatchers.IO) {
        val query = baseQuery()
        SecItemDelete(query as CFDictionaryRef)
        Unit
    }

    private fun baseQuery(): CFMutableDictionaryRef {
        val dict = CFDictionaryCreateMutable(kCFAllocatorDefault, 4, null, null)
            ?: error("Failed to create Keychain query dictionary")
        CFDictionarySetValueBridge(dict, kSecClass, kSecClassGenericPassword)
        CFDictionarySetValueBridge(dict, kSecAttrService, SERVICE as NSString)
        CFDictionarySetValueBridge(dict, kSecAttrAccount, ACCOUNT as NSString)
        return dict
    }

    /**
     * Bridge helper that retains an Objective-C / NSObject value as a Core
     * Foundation pointer and sets it in the mutable dictionary. The retain is
     * balanced by the dictionary's own release on deallocation, consistent with
     * the CFBridgingRetain usage in other cinterop files in this project.
     */
    private fun CFDictionarySetValueBridge(
        dict: CFMutableDictionaryRef?,
        key: Any?,
        value: Any?
    ) {
        platform.CoreFoundation.CFDictionarySetValue(
            dict,
            CFBridgingRetain(key),
            CFBridgingRetain(value)
        )
    }
}
