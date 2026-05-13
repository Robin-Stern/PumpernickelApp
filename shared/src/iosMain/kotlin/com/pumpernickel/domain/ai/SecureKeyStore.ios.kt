@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pumpernickel.domain.ai

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.set
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
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
import platform.posix.memcpy

private const val SERVICE = "PumpernickelApp.AI"
private const val ACCOUNT = "openai.api.key"

actual class SecureKeyStore {

    actual suspend fun writeApiKey(value: String) = withContext(Dispatchers.Default) {
        try {
            val data: NSData = value.encodeToByteArray().toNSData()
            val dataPtr: CPointer<*> = data.objcPointer()

            // 1) Try update
            val updateStatus = memScoped {
                val q = baseQueryDict()
                val a = makeDict(arrayOf(kSecValueData to dataPtr))
                SecItemUpdate(q, a)
            }

            if (updateStatus == errSecItemNotFound) {
                // 2) Add new
                val accessibility: CPointer<*>? = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
                val addStatus = memScoped {
                    val q = baseQueryDict(extras = arrayOf(
                        kSecValueData to dataPtr,
                        kSecAttrAccessible to (accessibility ?: return@memScoped errSecSuccess.toInt())
                    ))
                    SecItemAdd(q, null)
                }
                if (addStatus != errSecSuccess) {
                    println("[SecureKeyStore.ios] SecItemAdd failed (status=$addStatus)")
                } else {
                    println("[SecureKeyStore.ios] writeApiKey OK (added, length=${value.length})")
                    ApiKeyState.set(true)
                }
            } else if (updateStatus != errSecSuccess) {
                println("[SecureKeyStore.ios] SecItemUpdate failed (status=$updateStatus)")
            } else {
                println("[SecureKeyStore.ios] writeApiKey OK (updated, length=${value.length})")
                ApiKeyState.set(true)
            }
        } catch (t: Throwable) {
            println("[SecureKeyStore.ios] writeApiKey crashed: $t")
        }
    }

    actual suspend fun readApiKey(): String? = withContext(Dispatchers.Default) {
        try {
            memScoped {
                val matchLimit: CPointer<*>? = kSecMatchLimitOne
                val returnTrue: CPointer<*>? = kCFBooleanTrue
                if (matchLimit == null || returnTrue == null) return@withContext null

                val q = baseQueryDict(extras = arrayOf(
                    kSecMatchLimit to matchLimit,
                    kSecReturnData to returnTrue
                ))
                val resultVar = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(q, resultVar.ptr)
                if (status != errSecSuccess) return@withContext null
                val cfData = resultVar.value ?: return@withContext null

                @Suppress("UNCHECKED_CAST")
                val cfDataRef = cfData as CFDataRef
                val length = CFDataGetLength(cfDataRef).toInt()
                if (length <= 0) {
                    CFRelease(cfData)
                    return@withContext null
                }
                val bytePtr = CFDataGetBytePtr(cfDataRef)
                if (bytePtr == null) {
                    CFRelease(cfData)
                    return@withContext null
                }
                val bytes = ByteArray(length)
                bytes.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), bytePtr, length.convert())
                }
                CFRelease(cfData)
                val str = bytes.decodeToString()
                println("[SecureKeyStore.ios] readApiKey OK (length=${str.length})")
                ApiKeyState.set(true)
                str
            }
        } catch (t: Throwable) {
            println("[SecureKeyStore.ios] readApiKey crashed: $t")
            null
        }
    }

    actual suspend fun clearApiKey() = withContext(Dispatchers.Default) {
        try {
            memScoped { SecItemDelete(baseQueryDict()) }
            ApiKeyState.set(false)
            Unit
        } catch (t: Throwable) {
            println("[SecureKeyStore.ios] clearApiKey crashed: $t")
            Unit
        }
    }

    /**
     * Build the Keychain query dictionary using parallel-array CFDictionaryCreate.
     *
     * Pattern lifted from russhwolf/multiplatform-settings KeychainSettings:
     * the Kotlin/Native runtime can't cast CFStringRef CPointer values to NSString
     * (toll-free bridging is an Obj-C runtime concept the K/N type checker
     * doesn't model), so we keep everything as raw CPointer and let CoreFoundation
     * sort it out at the C boundary.
     */
    private fun MemScope.baseQueryDict(
        extras: Array<Pair<CPointer<*>?, CPointer<*>?>> = emptyArray()
    ): CFDictionaryRef {
        val cfService: CPointer<*>? = (SERVICE as platform.Foundation.NSString).objcPointer()
        val cfAccount: CPointer<*>? = (ACCOUNT as platform.Foundation.NSString).objcPointer()
        val classConst: CPointer<*>? = kSecClassGenericPassword
        val pairs: Array<Pair<CPointer<*>?, CPointer<*>?>> = arrayOf<Pair<CPointer<*>?, CPointer<*>?>>(
            kSecClass to classConst,
            kSecAttrService to cfService,
            kSecAttrAccount to cfAccount
        ) + extras
        return makeDict(pairs)
    }

    private fun MemScope.makeDict(
        pairs: Array<Pair<CPointer<*>?, CPointer<*>?>>
    ): CFDictionaryRef {
        val nonNull = pairs.filter { it.first != null && it.second != null }
        val n = nonNull.size
        val keys = allocArray<COpaquePointerVar>(n)
        val values = allocArray<COpaquePointerVar>(n)
        nonNull.forEachIndexed { i, (k, v) ->
            keys[i] = k as COpaquePointer?
            values[i] = v as COpaquePointer?
        }
        return CFDictionaryCreate(
            kCFAllocatorDefault,
            keys,
            values,
            n.convert(),
            null,
            null
        )!!
    }
}

// MARK: - helpers

/** Get the underlying Obj-C pointer of any NSObject as a generic CPointer. */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
private fun Any.objcPointer(): CPointer<*> {
    val raw = this.objcPtr()
    return kotlinx.cinterop.interpretCPointer<kotlinx.cinterop.CPointed>(raw)!!
}

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
