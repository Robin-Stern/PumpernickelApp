@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pumpernickel.infrastructure.ai

import com.pumpernickel.domain.ai.ApiKeyState
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
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
private const val LEGACY_ACCOUNT = "openai.api.key"
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

actual class SecureKeyStore {

    actual suspend fun writeCredential(
        provider: ProviderId,
        credential: Credential
    ) = withContext(Dispatchers.Default) {
        val (account, payload) = when (credential) {
            is Credential.ApiKey -> provider.apiKeyAccount to credential.value
            is Credential.OAuthToken -> provider.oauthTokenAccount to
                json.encodeToString(Credential.OAuthToken.serializer(), credential)
        }
        writeRaw(account, payload)
        // Mirror Phase-18 semantics — ANY write flips the sentinel to true.
        // AiSettingsViewModel will eventually replace this with per-provider state (Plan 07).
        ApiKeyState.set(true)
    }

    actual suspend fun readCredential(provider: ProviderId): Credential? =
        withContext(Dispatchers.Default) {
            // D-22-01 OAuth-Primary: try OAuth slot first, fall back to API-Key slot.
            val oauthRaw = readRaw(provider.oauthTokenAccount)
            if (oauthRaw != null) {
                return@withContext try {
                    json.decodeFromString(Credential.OAuthToken.serializer(), oauthRaw)
                } catch (t: Throwable) {
                    println("[SecureKeyStore.ios] readCredential OAuth decode failed: $t")
                    null
                }
            }
            val apiKey = readRaw(provider.apiKeyAccount)
            if (apiKey != null) Credential.ApiKey(apiKey) else null
        }

    actual suspend fun clearCredential(provider: ProviderId) =
        withContext(Dispatchers.Default) {
            deleteRaw(provider.apiKeyAccount)
            deleteRaw(provider.oauthTokenAccount)
            // Recompute ApiKeyState after clear.
            ApiKeyState.set(listProvidersBlocking().isNotEmpty())
        }

    actual suspend fun listProviders(): Set<ProviderId> =
        withContext(Dispatchers.Default) {
            listProvidersBlocking()
        }

    actual suspend fun readLegacyApiKey(): String? =
        withContext(Dispatchers.Default) {
            readRaw(LEGACY_ACCOUNT)
        }

    actual suspend fun clearLegacyApiKey() = withContext(Dispatchers.Default) {
        deleteRaw(LEGACY_ACCOUNT)
        Unit
    }

    // --- internal helpers ---

    private fun writeRaw(account: String, value: String) {
        try {
            val data: NSData = value.encodeToByteArray().toNSData()
            val dataPtr: CPointer<*> = data.objcPointer()

            // 1) Try update
            val updateStatus = memScoped {
                val q = baseQueryDict(account)
                val a = makeDict(arrayOf(kSecValueData to dataPtr))
                SecItemUpdate(q, a)
            }

            if (updateStatus == errSecItemNotFound) {
                // 2) Add new
                val accessibility: CPointer<*>? = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
                val addStatus = memScoped {
                    val q = baseQueryDict(
                        account,
                        extras = arrayOf(
                            kSecValueData to dataPtr,
                            kSecAttrAccessible to (accessibility ?: return@memScoped errSecSuccess.toInt())
                        )
                    )
                    SecItemAdd(q, null)
                }
                if (addStatus != errSecSuccess) {
                    println("[SecureKeyStore.ios] SecItemAdd failed (account=$account, status=$addStatus)")
                } else {
                    println("[SecureKeyStore.ios] writeRaw OK (added, account=$account, length=${value.length})")
                }
            } else if (updateStatus != errSecSuccess) {
                println("[SecureKeyStore.ios] SecItemUpdate failed (account=$account, status=$updateStatus)")
            } else {
                println("[SecureKeyStore.ios] writeRaw OK (updated, account=$account, length=${value.length})")
            }
        } catch (t: Throwable) {
            println("[SecureKeyStore.ios] writeRaw crashed (account=$account): $t")
        }
    }

    private fun readRaw(account: String): String? {
        return try {
            memScoped {
                val matchLimit: CPointer<*>? = kSecMatchLimitOne
                val returnTrue: CPointer<*>? = kCFBooleanTrue
                if (matchLimit == null || returnTrue == null) return@memScoped null

                val q = baseQueryDict(
                    account,
                    extras = arrayOf(
                        kSecMatchLimit to matchLimit,
                        kSecReturnData to returnTrue
                    )
                )
                val resultVar = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(q, resultVar.ptr)
                if (status != errSecSuccess) return@memScoped null
                val cfData = resultVar.value ?: return@memScoped null

                @Suppress("UNCHECKED_CAST")
                val cfDataRef = cfData as CFDataRef
                val length = CFDataGetLength(cfDataRef).toInt()
                if (length <= 0) {
                    CFRelease(cfData)
                    return@memScoped null
                }
                val bytePtr = CFDataGetBytePtr(cfDataRef)
                if (bytePtr == null) {
                    CFRelease(cfData)
                    return@memScoped null
                }
                val bytes = ByteArray(length)
                bytes.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), bytePtr, length.convert())
                }
                CFRelease(cfData)
                bytes.decodeToString()
            }
        } catch (t: Throwable) {
            println("[SecureKeyStore.ios] readRaw crashed (account=$account): $t")
            null
        }
    }

    private fun deleteRaw(account: String) {
        try {
            memScoped { SecItemDelete(baseQueryDict(account)) }
        } catch (t: Throwable) {
            println("[SecureKeyStore.ios] deleteRaw crashed (account=$account): $t")
        }
    }

    /** Synchronous variant for use in ApiKeyState recompute. */
    private fun listProvidersBlocking(): Set<ProviderId> =
        ProviderId.entries.filter { p ->
            readRaw(p.apiKeyAccount) != null || readRaw(p.oauthTokenAccount) != null
        }.toSet()

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
        account: String,
        extras: Array<Pair<CPointer<*>?, CPointer<*>?>> = emptyArray()
    ): CFDictionaryRef {
        val cfService: CPointer<*>? = (SERVICE as platform.Foundation.NSString).objcPointer()
        val cfAccount: CPointer<*>? = (account as platform.Foundation.NSString).objcPointer()
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
