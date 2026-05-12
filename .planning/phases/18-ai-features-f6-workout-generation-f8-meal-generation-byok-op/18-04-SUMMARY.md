---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: "04"
subsystem: ai-byok-storage
tags: [security, keychain, encrypted-shared-preferences, datastore, koin, expect-actual]
dependency_graph:
  requires:
    - 18-02 (OpenAICompatibleClient suspend-lambda key provider shape)
  provides:
    - SecureKeyStore expect class with writeApiKey/readApiKey/clearApiKey
    - Android actual: EncryptedSharedPreferences (AES256_SIV keys, AES256_GCM values)
    - iOS actual: Keychain Services (kSecClassGenericPassword, PumpernickelApp.AI service)
    - Koin bindings in both PlatformModules
    - SettingsRepository: aiProviderPreset / aiBaseUrl / aiModel Flows + setters
  affects:
    - Plan 05: AiSettingsViewModel reads SecureKeyStore + SettingsRepository AI fields
    - Plan 06: WorkoutAiViewModel reads secureKeyStore.readApiKey() just-in-time
    - Plan 08: RecipeAiViewModel reads secureKeyStore.readApiKey() just-in-time
tech_stack:
  added:
    - androidx.security:security-crypto:1.0.0 (androidMain only)
  patterns:
    - expect class SecureKeyStore mirrors PhotoVault expect/actual shape exactly
    - Android actual: Context-injected, lazy EncryptedSharedPreferences via MasterKey AES256_GCM
    - iOS actual: CFDictionary-based Keychain (SecItemAdd/Update/CopyMatching/Delete), no-arg ctor
    - Koin binding: single<SecureKeyStore> { SecureKeyStore(androidContext()) } / { SecureKeyStore() }
    - SettingsRepository: stringPreferencesKey trio — same pattern as weightUnit, appTheme
key_files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.android.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt
  modified:
    - shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt
    - shared/build.gradle.kts
    - gradle/libs.versions.toml
    - androidApp/src/androidMain/res/xml/data_extraction_rules.xml
    - androidApp/src/androidMain/res/xml/backup_rules.xml
decisions:
  - "Used security-crypto:1.0.0 stable (not 1.1.x alpha) per CLAUDE.md no-alpha directive; all required APIs present in 1.0.0"
  - "iOS actual uses CFDictionary-based cinterop (not Swift bridge) — mirrors existing CFDictionarySetValue usage in BiometricGate.ios.kt; introduced CFDictionarySetValueBridge helper for ergonomic retain-bridging"
  - "write-update-then-add pattern: SecItemUpdate first; fall back to SecItemAdd only on errSecItemNotFound — avoids errSecDuplicateItem on repeated writes"
  - "kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly is intentional: key is device-local (no iCloud sync), available after first unlock (T-18-04-02)"
  - "ai_secrets.xml added to data_extraction_rules.xml + backup_rules.xml (T-18-04-03 gap found during execution and auto-fixed per Rule 2)"
metrics:
  duration: "~15 min"
  completed: "2026-05-07"
  tasks: 3
  files_created: 3
  files_modified: 8
---

# Phase 18 Plan 04: Secure Key Store and Settings — Summary

**One-liner:** BYOK API key storage via expect/actual SecureKeyStore (EncryptedSharedPreferences on Android, Keychain Services on iOS) plus three DataStore AI config fields in SettingsRepository — key never touches DataStore (REQ-AI-06).

## Files Created / Modified

### Created

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt` — expect class with `writeApiKey`, `readApiKey`, `clearApiKey`
- `shared/src/androidMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.android.kt` — EncryptedSharedPreferences actual; lazy prefs initialization; all methods on `Dispatchers.IO`
- `shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt` — Keychain Services actual via cinterop (CFDictionary); no-arg ctor

### Modified

- `shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt` — added `single<SecureKeyStore> { SecureKeyStore(androidContext()) }`
- `shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt` — added `single<SecureKeyStore> { SecureKeyStore() }`
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` — three new key/Flow/setter trios for `ai_provider_preset`, `ai_base_url`, `ai_model`
- `shared/build.gradle.kts` — `implementation(libs.androidx.security.crypto)` in `androidMain.dependencies`
- `gradle/libs.versions.toml` — `androidx-security-crypto = "1.0.0"` version + library entry
- `androidApp/src/androidMain/res/xml/data_extraction_rules.xml` — `ai_secrets.xml` exclusion from cloud-backup + device-transfer (T-18-04-03)
- `androidApp/src/androidMain/res/xml/backup_rules.xml` — `ai_secrets.xml` exclusion from legacy backup (T-18-04-03)

## SecureKeyStore API Surface

```kotlin
expect class SecureKeyStore {
    suspend fun writeApiKey(value: String)   // store key in platform secure store
    suspend fun readApiKey(): String?        // retrieve key; null if not set
    suspend fun clearApiKey()               // delete key
}
```

## Keychain Attribute Summary (iOS)

| Attribute | Value |
|-----------|-------|
| `kSecClass` | `kSecClassGenericPassword` |
| `kSecAttrService` | `"PumpernickelApp.AI"` |
| `kSecAttrAccount` | `"openai.api.key"` |
| `kSecAttrAccessible` | `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` |

The `ThisDeviceOnly` accessibility is intentional: the key does NOT sync to iCloud Keychain (T-18-04-02). It becomes available after the first device unlock after a reboot.

## iOS Implementation Choice

**Chosen: cinterop via CFDictionary-based Keychain API** (not Swift bridge fallback).

Rationale: The existing `BiometricGate.ios.kt` already uses `CFDictionarySetValue` and `CFBridgingRetain` patterns. The new file introduces a private `CFDictionarySetValueBridge` helper to ergonomically bridge NSObject values to CF pointers for dictionary insertion. The `@file:OptIn(ExperimentalForeignApi, BetaInteropApi)` annotation mirrors `PhotoVault.ios.kt` exactly. The Swift bridge fallback was not needed.

## SettingsRepository Delta

Three new key/Flow/setter trios appended to the existing class — pattern matches `weightUnit` and `appTheme` exactly:

| DataStore Key | Flow | Default | Setter |
|---------------|------|---------|--------|
| `ai_provider_preset` | `aiProviderPreset: Flow<String>` | `"openai"` | `setAiProviderPreset(String)` |
| `ai_base_url` | `aiBaseUrl: Flow<String>` | `"https://api.openai.com/v1"` | `setAiBaseUrl(String)` |
| `ai_model` | `aiModel: Flow<String>` | `"gpt-4o-mini"` | `setAiModel(String)` |

The API key is **not** in SettingsRepository — it exists only in `SecureKeyStore` (REQ-AI-06).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Security Mitigation] Added ai_secrets.xml to Android backup exclusion rules (T-18-04-03)**
- **Found during:** Task 1 — threat model review of T-18-04-03
- **Issue:** The plan's threat model (T-18-04-03) requires `ai_secrets.xml` to be excluded from Android Auto Backup. The existing `data_extraction_rules.xml` and `backup_rules.xml` only excluded `progress_pics/` (Phase 17). The `ai_secrets.xml` file was not excluded, leaving the EncryptedSharedPreferences file eligible for cloud backup.
- **Fix:** Added `<exclude domain="sharedpref" path="ai_secrets.xml"/>` to both `data_extraction_rules.xml` (cloud-backup + device-transfer sections) and `backup_rules.xml`.
- **Files modified:** `androidApp/src/androidMain/res/xml/data_extraction_rules.xml`, `androidApp/src/androidMain/res/xml/backup_rules.xml`
- **Commit:** 8953304

## Threat Flags

No new threat surface beyond what is documented in the plan's `<threat_model>`. All T-18-04-01/02/03/04/05 mitigations are in place:
- T-18-04-01: 0 `println` calls in both actuals (verified by acceptance criteria)
- T-18-04-02: `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` set on iOS write
- T-18-04-03: `ai_secrets.xml` excluded from all backup paths (auto-fixed above)
- T-18-04-04: Base URL validation deferred to Plan 05 Settings UI (documented in plan)
- T-18-04-05: EncryptedSharedPreferences is process-local, encrypted at rest

## Self-Check: PASSED

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt` — FOUND
- `shared/src/androidMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.android.kt` — FOUND
- `shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.ios.kt` — FOUND
- Commit 8953304 (Task 1) — FOUND
- Commit e43c61c (Task 2) — FOUND
- Commit fa6efcd (Task 3) — FOUND
