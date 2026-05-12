---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: 04
type: execute
wave: 2
depends_on: []
files_modified:
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt
  - shared/src/androidMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.android.kt
  - shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt
  - shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt
  - shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt
  - shared/build.gradle.kts
autonomous: true
requirements:
  - REQ-AI-06
  - REQ-AI-08
user_setup: []

must_haves:
  truths:
    - "SecureKeyStore (expect class) exposes suspend writeApiKey, readApiKey, clearApiKey"
    - "Android actual writes to EncryptedSharedPreferences (AES256_SIV keys, AES256_GCM values, MasterKey.DEFAULT_MASTER_KEY_ALIAS)"
    - "iOS actual reads/writes via Keychain Services with kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly + kSecAttrService=PumpernickelApp.AI"
    - "PlatformModule.android.kt binds SecureKeyStore(androidContext()); PlatformModule.ios.kt binds SecureKeyStore() — mirrors PhotoVault pattern"
    - "SettingsRepository exposes 3 new Flows + setters for ai_provider_preset / ai_base_url / ai_model (DataStore — these are configuration, NOT secrets)"
    - "API key never lands in DataStore plaintext or any class field outside SecureKeyStore"
  artifacts:
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt"
      provides: "expect class with three suspend methods"
      contains: "expect class SecureKeyStore"
    - path: "shared/src/androidMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.android.kt"
      provides: "EncryptedSharedPreferences actual"
      contains: "EncryptedSharedPreferences"
    - path: "shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt"
      provides: "Keychain Services actual"
      contains: "kSecClassGenericPassword"
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt"
      provides: "aiProviderPreset / aiBaseUrl / aiModel Flows + setters"
      contains: "aiProviderPreset"
  key_links:
    - from: "PlatformModule.android.kt"
      to: "SecureKeyStore Android actual"
      via: "Koin single + androidContext()"
      pattern: "SecureKeyStore\\(androidContext\\(\\)\\)"
    - from: "PlatformModule.ios.kt"
      to: "SecureKeyStore iOS actual"
      via: "Koin single, no-arg ctor"
      pattern: "SecureKeyStore\\(\\)"
    - from: "SettingsRepository.aiProviderPreset"
      to: "DataStore key ai_provider_preset"
      via: "stringPreferencesKey"
      pattern: "ai_provider_preset"
---

<objective>
Build the BYOK (Bring-Your-Own-API-Key) storage layer and the non-secret AI configuration fields:

1. **SecureKeyStore** — `expect class` in `commonMain` plus Android (EncryptedSharedPreferences) and iOS (Keychain Services) actuals. Single API key only — `writeApiKey`, `readApiKey`, `clearApiKey`. Mirrors the `PhotoVault` `expect/actual` pattern from Phase 17. The API key NEVER lands in DataStore, logs, or any in-memory class field outside this store (REQ-AI-06).

2. **PlatformModule bindings** — Android binds `SecureKeyStore(androidContext())`; iOS binds `SecureKeyStore()` (no-arg ctor matches Phase-17 iOS convention). Both registered as `single<SecureKeyStore>` so DI returns the same instance app-wide.

3. **SettingsRepository extension** — three new `Flow<String>` + setter trios for the *non-secret* AI configuration: `aiProviderPreset`, `aiBaseUrl`, `aiModel`. These ride DataStore alongside theme / weight unit; the API key explicitly does NOT live here.

4. **Gradle dependency** — add `androidx.security:security-crypto` to `androidMain` for `EncryptedSharedPreferences`.

Purpose: REQ-AI-06 (BYOK + Keychain/EncryptedSharedPreferences storage). D-18-06 provides the provider-preset / base-URL / model fields. D-18-07 enables the no-key empty state (downstream VMs read `apiKeyConfigured` from this store).
Output: 3 new SecureKeyStore files, 2 modified PlatformModule files, 1 modified SettingsRepository, 1 modified shared/build.gradle.kts.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/REQUIREMENTS-ai-features.md
@.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-CONTEXT.md
@.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md
@shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.kt
@shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.android.kt
@shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.ios.kt
@shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt
@shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt
@shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt

<interfaces>
PhotoVault expect class precedent (`shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.kt`):
```
expect class PhotoVault {
    suspend fun write(id: String, bytes: ByteArray): String
    suspend fun read(relativePath: String): ByteArray?
    suspend fun delete(relativePath: String)
}
```

PhotoVault Android actual constructor pattern:
```
actual class PhotoVault(private val context: Context) {
    private val rootDir: File by lazy { File(context.filesDir, "progress_pics").apply { mkdirs() } }
    actual suspend fun write(id: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) { ... }
}
```

PhotoVault iOS actual file-level OptIn + memScoped Foundation pattern:
```
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package com.pumpernickel.domain.progresspic
// uses kotlinx.cinterop.{alloc,memScoped,ObjCObjectVar,ptr,value}
// uses platform.Foundation.*
```

PlatformModule.android.kt current binding line 20:
```
single<PhotoVault> { PhotoVault(androidContext()) }
```

PlatformModule.ios.kt current binding line 19:
```
single<PhotoVault> { PhotoVault() }
```

SettingsRepository existing pattern (line 21-26 + 50-69):
```
private val weightUnitKey = stringPreferencesKey("weight_unit")
val weightUnit: Flow<WeightUnit> = dataStore.data.map { ... }
suspend fun setWeightUnit(unit: WeightUnit) { dataStore.edit { ... } }
```

androidx.security:security-crypto API (target version `1.0.0` stable — per CLAUDE.md / project policy "do not use alpha for university project". `EncryptedSharedPreferences` is fully present in 1.0.0; only fall back to a 1.1 prerelease if a specific 1.1 API is required, which it is not):
```
val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
val prefs = EncryptedSharedPreferences.create(
    context, "ai_secrets", masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

iOS Keychain Services (CFDictionary-based; uses CoreFoundation/Security frameworks via cinterop):
- Add: SecItemAdd(query, null) / on errSecDuplicateItem follow with SecItemUpdate
- Read: SecItemCopyMatching(query, valuePtr)
- Delete: SecItemDelete(query)
- Standard query keys: kSecClass, kSecAttrService, kSecAttrAccount, kSecAttrAccessible, kSecValueData, kSecMatchLimit, kSecReturnData
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create SecureKeyStore expect class + Android EncryptedSharedPreferences actual + Koin binding + dep</name>
  <files>
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt,
    shared/src/androidMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.android.kt,
    shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt,
    shared/build.gradle.kts
  </files>
  <read_first>
    shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.kt,
    shared/src/androidMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.android.kt,
    shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt,
    shared/build.gradle.kts,
    .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md
  </read_first>
  <action>
**File 1: shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt** — expect class:

```kotlin
package com.pumpernickel.domain.ai

/**
 * REQ-AI-06 — BYOK API key storage. The key is the ONLY secret managed here;
 * non-secret config (provider preset, base URL, model name) lives in
 * SettingsRepository / DataStore.
 *
 * Implementations:
 * - Android: EncryptedSharedPreferences via androidx.security.crypto
 *   (AES256_SIV keys, AES256_GCM values, MasterKey.DEFAULT_MASTER_KEY_ALIAS).
 * - iOS: Keychain Services with kSecClass=GenericPassword,
 *   kSecAttrService="PumpernickelApp.AI", kSecAttrAccount="openai.api.key",
 *   kSecAttrAccessible=kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly.
 *
 * The key is NEVER returned to logs, NEVER persisted to DataStore, NEVER held
 * as a long-lived field. Callers read it just-in-time per request and let it
 * fall out of scope after the HTTPS call.
 */
expect class SecureKeyStore {
    suspend fun writeApiKey(value: String)
    suspend fun readApiKey(): String?
    suspend fun clearApiKey()
}
```

**File 2: shared/src/androidMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.android.kt** — Android actual:

```kotlin
package com.pumpernickel.domain.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class SecureKeyStore(private val context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    actual suspend fun writeApiKey(value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_API_KEY, value).apply()
    }

    actual suspend fun readApiKey(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_API_KEY, null)
    }

    actual suspend fun clearApiKey() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    companion object {
        private const val PREFS_NAME = "ai_secrets"
        private const val KEY_API_KEY = "openai.api.key"
    }
}
```

**File 3: shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt** — append the SecureKeyStore binding to the existing module. Locate the line that registers `PhotoVault` (`single<PhotoVault> { PhotoVault(androidContext()) }`) and add immediately after it:

```kotlin
import com.pumpernickel.domain.ai.SecureKeyStore
// ... existing imports unchanged
single<SecureKeyStore> { SecureKeyStore(androidContext()) }
```

The full file should now have 4 platform `single<...>` bindings: `PhotoVault`, `PhotoCaptureLauncher`, `BiometricGate`, `SecureKeyStore`. Do NOT remove or reorder existing bindings.

**File 4: shared/build.gradle.kts** — add the `androidx.security:security-crypto` dependency to the `androidMain` source-set. **Use version `1.0.0` (stable)** per CLAUDE.md "What NOT to Use → alpha" guidance for this university project; `EncryptedSharedPreferences` is fully present in 1.0.0. Only escalate to a 1.1 prerelease if a specific 1.1 API is required (it is not for this phase). Locate the existing `androidMain { dependencies { ... } }` block (or `sourceSets.androidMain.dependencies`) and add:

```kotlin
implementation("androidx.security:security-crypto:1.0.0")
```

If the project uses `libs.versions.toml`, add the entry there first:
```toml
[versions]
androidx-security-crypto = "1.0.0"

[libraries]
androidx-security-crypto = { module = "androidx.security:security-crypto", version.ref = "androidx-security-crypto" }
```
and reference it in `shared/build.gradle.kts` via `implementation(libs.androidx.security.crypto)`.

Verify the resulting build by running `./gradlew :shared:assembleDebug` after Task 2 also lands (the iOS actual must exist before the expect class compiles).
  </action>
  <verify>
    <automated>grep -E "expect class SecureKeyStore" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt && grep -E "EncryptedSharedPreferences" shared/src/androidMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.android.kt && grep -E "SecureKeyStore\(androidContext\(\)\)" shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt && grep -E "androidx.security:security-crypto|androidx-security-crypto" shared/build.gradle.kts</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "expect class SecureKeyStore" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt` returns exactly `1`.
    - `grep -c "suspend fun writeApiKey" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt` returns exactly `1`.
    - `grep -c "suspend fun readApiKey" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt` returns exactly `1`.
    - `grep -c "suspend fun clearApiKey" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt` returns exactly `1`.
    - `grep -c "actual class SecureKeyStore" shared/src/androidMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.android.kt` returns exactly `1`.
    - `grep -c "AES256_SIV" shared/src/androidMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.android.kt` returns exactly `1`.
    - `grep -c "AES256_GCM" shared/src/androidMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.android.kt` returns at least `2` (MasterKey scheme + value scheme).
    - `grep -c "withContext(Dispatchers.IO)" shared/src/androidMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.android.kt` returns exactly `3` (one per suspend method).
    - `grep -c "SecureKeyStore(androidContext())" shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt` returns exactly `1`.
    - `grep -c "single<PhotoVault>" shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt` returns exactly `1` (existing binding retained).
    - `grep -ciE "android.security.crypto|androidx-security-crypto|androidx\\.security:security-crypto" shared/build.gradle.kts` returns at least `1`.
    - `grep -ci "println" shared/src/androidMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.android.kt` returns exactly `0` (key never logged).
  </acceptance_criteria>
  <done>SecureKeyStore expect + Android actual exist; Koin binding registered; security-crypto dep declared.</done>
</task>

<task type="auto">
  <name>Task 2: Create iOS Keychain actual + Koin binding</name>
  <files>
    shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt,
    shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt
  </files>
  <read_first>
    shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.ios.kt,
    shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt,
    .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md
  </read_first>
  <action>
**File 1: shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt** — Keychain Services actual via cinterop. Use the canonical generic-password pattern with `kSecAttrService = "PumpernickelApp.AI"`, `kSecAttrAccount = "openai.api.key"`, `kSecAttrAccessible = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`. Wrap reads in `memScoped { ... }` mirroring the existing `PhotoVault.ios.kt` cinterop pattern.

```kotlin
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pumpernickel.domain.ai

import kotlinx.cinterop.CFBridgingRelease
import kotlinx.cinterop.CFBridgingRetain
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
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
        val updateAttrs = CFDictionaryCreateMutable(null, 0, null, null)
        CFDictionarySetValue(updateAttrs, kSecValueData, CFBridgingRetain(data))
        val updateStatus = SecItemUpdate(updateQuery, updateAttrs)
        if (updateStatus == errSecItemNotFound) {
            val addQuery = baseQuery()
            CFDictionarySetValue(addQuery, kSecValueData, CFBridgingRetain(data))
            CFDictionarySetValue(addQuery, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            val addStatus = SecItemAdd(addQuery, null)
            require(addStatus == errSecSuccess) { "Keychain SecItemAdd failed (status $addStatus)" }
        } else {
            require(updateStatus == errSecSuccess) { "Keychain SecItemUpdate failed (status $updateStatus)" }
        }
    }

    actual suspend fun readApiKey(): String? = withContext(Dispatchers.IO) {
        memScoped {
            val query = baseQuery()
            CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
            CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)

            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status == errSecItemNotFound) return@withContext null
            if (status != errSecSuccess) return@withContext null

            val cfData: CPointer<*>? = result.value
            val nsData = CFBridgingRelease(cfData) as? NSData ?: return@withContext null
            (NSString.create(nsData, NSUTF8StringEncoding) as? String)
        }
    }

    actual suspend fun clearApiKey() = withContext(Dispatchers.IO) {
        val query = baseQuery()
        SecItemDelete(query)
        Unit
    }

    private fun baseQuery() = CFDictionaryCreateMutable(null, 0, null, null).also {
        CFDictionarySetValue(it, kSecClass, kSecClassGenericPassword)
        CFDictionarySetValue(it, kSecAttrService, CFBridgingRetain(SERVICE as NSString))
        CFDictionarySetValue(it, kSecAttrAccount, CFBridgingRetain(ACCOUNT as NSString))
    }
}
```

Notes for the executor:
- Keychain cinterop in Kotlin/Native is awkward because `CFDictionarySetValue` takes `CPointer<*>` parameters. The exact signature may require minor adjustments — if the imports above don't compile, refer to the existing `PhotoVault.ios.kt` for the cinterop idioms in use (it's the closest analog file in this project).
- An alternative using a thin Swift bridge in `iosApp/iosApp/SecureKeyStoreBridge.swift` is acceptable if cinterop proves unworkable. The bridge would expose `+ (NSString *)readApiKey;` etc. and the actual class would call through. This is a known fallback and matches several KMP-iOS Keychain patterns in the wild. Document the choice in the task summary.
- `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` is intentional: keys do NOT sync to iCloud Keychain (this-device-only) and require first unlock after boot before they're readable.

**File 2: shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt** — append the binding after the existing `single<PhotoVault> { PhotoVault() }` line:

```kotlin
import com.pumpernickel.domain.ai.SecureKeyStore
// ...
single<SecureKeyStore> { SecureKeyStore() }
```

The full file now has 4 platform `single<...>` bindings: `PhotoVault`, `PhotoCaptureLauncher`, `BiometricGate`, `SecureKeyStore`. Do NOT remove or reorder existing bindings.
  </action>
  <verify>
    <automated>grep -E "actual class SecureKeyStore" shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt && grep -E "kSecClassGenericPassword" shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt && grep -E "kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly" shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt && grep -E "single<SecureKeyStore> \{ SecureKeyStore\(\) \}" shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "actual class SecureKeyStore" shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt` returns exactly `1`.
    - `grep -c "kSecClassGenericPassword" shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt` returns at least `1`.
    - `grep -c "kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly" shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt` returns at least `1`.
    - `grep -c "PumpernickelApp.AI" shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt` returns at least `1`.
    - `grep -c "openai.api.key" shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt` returns at least `1`.
    - `grep -c "SecItemAdd" shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt` returns at least `1`.
    - `grep -c "SecItemDelete" shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt` returns at least `1`.
    - `grep -c "SecItemCopyMatching" shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt` returns at least `1`.
    - `grep -c "single<SecureKeyStore>" shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt` returns exactly `1`.
    - `grep -c "single<PhotoVault>" shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt` returns exactly `1` (existing retained).
    - `grep -ci "println" shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt` returns exactly `0` (key never logged).
  </acceptance_criteria>
  <done>SecureKeyStore.ios.kt compiles via cinterop (or via a Swift bridge if the executor opts for the fallback documented above); Koin binding wired.</done>
</task>

<task type="auto">
  <name>Task 3: Extend SettingsRepository with provider preset / base URL / model fields (DataStore — non-secret only)</name>
  <files>shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt</files>
  <read_first>
    shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt,
    .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-CONTEXT.md
  </read_first>
  <action>
Modify `SettingsRepository.kt`. Append three new key+Flow+setter trios that mirror the existing `weightUnit` pattern (lines 21-26 + 50-69) for the AI configuration fields.

Concrete diff (insert before the closing `}` of the `SettingsRepository` class):

```kotlin
    // D-18-06 — AI configuration. NOT secrets — the API key lives in
    // SecureKeyStore (Keychain on iOS, EncryptedSharedPreferences on Android).
    private val aiProviderPresetKey = stringPreferencesKey("ai_provider_preset")
    private val aiBaseUrlKey = stringPreferencesKey("ai_base_url")
    private val aiModelKey = stringPreferencesKey("ai_model")

    val aiProviderPreset: Flow<String> = dataStore.data.map { prefs ->
        prefs[aiProviderPresetKey] ?: "openai"
    }

    val aiBaseUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[aiBaseUrlKey] ?: "https://api.openai.com/v1"
    }

    val aiModel: Flow<String> = dataStore.data.map { prefs ->
        prefs[aiModelKey] ?: "gpt-4o-mini"
    }

    suspend fun setAiProviderPreset(preset: String) {
        dataStore.edit { prefs -> prefs[aiProviderPresetKey] = preset }
    }

    suspend fun setAiBaseUrl(url: String) {
        dataStore.edit { prefs -> prefs[aiBaseUrlKey] = url }
    }

    suspend fun setAiModel(model: String) {
        dataStore.edit { prefs -> prefs[aiModelKey] = model }
    }
```

Defaults are picked per D-18-06 — `openai` preset, `https://api.openai.com/v1` base URL, `gpt-4o-mini` model. The Settings UI (Plan 05) overrides these when the user picks a different preset.

Do NOT add an API key field here — the key lives ONLY in SecureKeyStore. There MUST NOT be a `private val aiApiKeyKey` or similar in this file.

Do NOT change any existing keys, Flows, or setters.

`stringPreferencesKey` and `edit` imports are already present at the top of the file (used by `weightUnitKey`, `appThemeKey`, etc.) — no new imports needed.
  </action>
  <verify>
    <automated>grep -E "aiProviderPreset" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt && grep -E "aiBaseUrl" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt && grep -E "aiModel" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt && ! grep -E "aiApiKey|api_key" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "ai_provider_preset" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` returns exactly `1`.
    - `grep -c "ai_base_url" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` returns exactly `1`.
    - `grep -c "ai_model" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` returns exactly `1`.
    - `grep -c "val aiProviderPreset: Flow<String>" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` returns exactly `1`.
    - `grep -c "val aiBaseUrl: Flow<String>" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` returns exactly `1`.
    - `grep -c "val aiModel: Flow<String>" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` returns exactly `1`.
    - `grep -c "suspend fun setAiProviderPreset" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` returns exactly `1`.
    - `grep -c "suspend fun setAiBaseUrl" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` returns exactly `1`.
    - `grep -c "suspend fun setAiModel" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` returns exactly `1`.
    - `grep -ciE "api_?key|api\\.key" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` returns exactly `0` (key never persists here per REQ-AI-06).
    - `grep -c "https://api.openai.com/v1" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` returns exactly `1` (default base URL).
    - `grep -c "gpt-4o-mini" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` returns exactly `1` (default model).
    - Existing `val weightUnit` and `val nutritionGoals` declarations are preserved: `grep -c "val weightUnit: Flow<WeightUnit>" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` returns exactly `1`.
  </acceptance_criteria>
  <done>SettingsRepository exposes the three new AI configuration Flows and setters, defaults match D-18-06, and no API-key field leaks into DataStore.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| App memory → persistent storage | API key crosses into long-lived persistence — must be encrypted on both platforms |
| Persistent storage → other apps / users | EncryptedSharedPreferences (Android) and Keychain (iOS) protect the at-rest data |
| Settings UI → DataStore | Provider preset / base URL / model are non-secret; HTTPS-only base URLs enforced at input layer (Plan 05) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-18-04-01 | Information disclosure | API key in logs | mitigate | Acceptance criteria reject `println` in both actuals. Storage uses platform-secure primitives, never DataStore. |
| T-18-04-02 | Information disclosure | API key in iCloud Keychain backup | mitigate | iOS actual sets `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` — this prevents iCloud Keychain sync. |
| T-18-04-03 | Information disclosure | API key in Android backup | mitigate | EncryptedSharedPreferences with `MasterKey` is excluded from Android Auto Backup by default for `androidx.security.crypto` 1.1+ when `dataExtractionRules` is configured. The `dataExtractionRules` and `fullBackupContent` rules from Phase 17 already exclude shared_prefs/ai_secrets — verify by reading `androidApp/src/androidMain/res/xml/data_extraction_rules.xml` and `backup_rules.xml`. If `ai_secrets` is NOT excluded, add an `<exclude domain="sharedpref" path="ai_secrets.xml"/>` entry. (Mitigation belongs in this plan if gap detected.) |
| T-18-04-04 | Tampering | DataStore-stored base URL allows non-HTTPS | mitigate | The Settings UI (Plan 05) validates `https://` prefix before calling `setAiBaseUrl`. This plan does not enforce — the contract is documented and enforced upstream. |
| T-18-04-05 | Elevation of privilege | Other Android apps reading EncryptedSharedPreferences | mitigate | EncryptedSharedPreferences is process-local, encrypted at rest with the device-bound MasterKey. No cross-process access without root. |
</threat_model>

<verification>
- `expect class SecureKeyStore` declared with three suspend methods.
- Android actual uses EncryptedSharedPreferences with AES256_SIV/AES256_GCM and the default MasterKey alias.
- iOS actual uses Keychain Services with the documented service/account/accessibility attributes.
- Both actuals are bound in their respective PlatformModule files.
- SettingsRepository exposes 3 new AI configuration Flows + setters; no API key key leaks into DataStore.
- `androidx.security:security-crypto` is declared in `shared/build.gradle.kts` (or via libs.versions.toml).
- `./gradlew :shared:assembleDebug` succeeds (Android).
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` succeeds (iOS).
- Manual UAT (deferred to Plan 05): write a key, kill the app, relaunch, read the same key back. Clear key, read returns null.
</verification>

<success_criteria>
- BYOK key storage primitive ready for Plan 05's AI Settings VM.
- API key isolated from DataStore (REQ-AI-06).
- Provider preset / base URL / model defaults wired (D-18-06).
- DI is consistent across Android + iOS (mirrors PhotoVault binding shape).
</success_criteria>

<output>
After completion, create `.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-04-SUMMARY.md` with: file list, SecureKeyStore API surface, Keychain attribute summary (service / account / accessibility), iOS implementation choice (cinterop vs Swift bridge), and SettingsRepository delta.
</output>
