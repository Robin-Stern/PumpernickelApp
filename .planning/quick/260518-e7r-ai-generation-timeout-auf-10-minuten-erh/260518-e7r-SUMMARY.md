---
phase: quick-260518-e7r
plan: 01
subsystem: data/api
tags: [ai, http, timeout, ktor, okhttp, darwin, ios, android, byok, req-ai-06]
dependency_graph:
  requires: []
  provides:
    - "10-minute total request lifetime ceiling for AI HTTP calls (Workout AI today, Recipe AI next)"
    - "2-minute between-byte gap tolerance on both platforms"
  affects:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.ios.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt
tech-stack:
  added: []
  patterns:
    - "Two-layer HTTP timeout model: smaller of (Ktor HttpTimeout, platform engine timeout) wins — both must move together"
    - "Per-request `timeout {}` override must not be tighter than engine config"
key-files:
  created: []
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.ios.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt
decisions:
  - "Raise to 10 min (600_000 ms) — covers slow free-tier LLMs (Gemma, Qwen QwQ, DeepSeek R1) that can stream for several minutes"
  - "Keep socketTimeoutMillis at 120_000 ms — 2 min of silence already means a dead stream; raising it would only hide hangs, not help slow generations"
  - "Keep Android OkHttp connectTimeout at 30s — TCP handshake is unrelated to slow LLM token generation"
metrics:
  duration: ~4 min
  completed_date: "2026-05-18"
  tasks_completed: 3
  files_modified: 3
---

# Quick-Task 260518-e7r: AI Generation Timeout auf 10 Minuten erhöht — Summary

Single-purpose fix: the AI HTTP timeout ceiling was raised from 5 min (Android) / 3 min (iOS) to a uniform 10 min across all four layers that can cancel a streaming AI request, so slow free-tier LLM generations finish instead of being killed with "Zeitüberschreitung".

## Why the timeout fires before completion

HTTP requests in this app pass through **two independent timeout layers**, and the **smaller of the two cancels the request**:

1. **Ktor `HttpTimeout` plugin** — coroutine-level cancel. Configured engine-wide in `HttpClientFactory.{ios,android}.kt`. Can be overridden per-request via a `timeout {}` block (which `OpenAICompatibleClient` does for AI calls).
2. **Platform engine timeout** — socket-level cancel inside the native HTTP stack:
   - **iOS:** `NSURLSessionConfiguration.timeoutIntervalForRequest` (between-packet) and `timeoutIntervalForResource` (total lifetime).
   - **Android:** OkHttp `readTimeout` / `writeTimeout` (socket reads/writes after handshake).

Before this fix:

| Layer | iOS effective | Android effective |
|-------|--------------:|------------------:|
| Ktor engine-wide | 180 s | 300 s |
| Ktor per-request override (`OpenAICompatibleClient`) | 300 s | 300 s |
| Platform engine (URLSession / OkHttp read+write) | 180 s req / 300 s resource | 300 s read+write |
| **Effective ceiling (smallest)** | **180 s (3 min)** | **300 s (5 min)** |

This matches the user's bug report: Workout AI generation on physical devices was being killed with "Zeitüberschreitung" shortly before the model finished streaming, especially on free-tier providers (Gemma, Qwen QwQ, DeepSeek R1) where end-to-end inference can take several minutes.

## Changes at each of the four sites

All four sites moved together — raising only one has no effect because the smallest wins.

### 1. `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt`
Per-request `timeout {}` block inside **both** `chatCompletion` (line 51) and `chatCompletionStreaming` (line 115):

| Field | Before | After |
|---|---:|---:|
| `requestTimeoutMillis` | 300_000 | **600_000** (10 min) |
| `socketTimeoutMillis` | 120_000 | 120_000 (unchanged) |

Commit: `902c082`

### 2. `shared/src/iosMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.ios.kt`
Ktor engine config + Darwin `NSURLSessionConfiguration`:

| Field | Before | After |
|---|---:|---:|
| Ktor `requestTimeoutMillis` | 180_000 | **600_000** |
| Ktor `socketTimeoutMillis` | *(absent)* | **120_000** *(added for parity with Android)* |
| Darwin `timeoutIntervalForRequest` (seconds) | 180.0 | **600.0** |
| Darwin `timeoutIntervalForResource` (seconds) | 300.0 | **600.0** |

KDoc header updated: previously said "Both are set to 180s here so AI requests have headroom" → now says "Both are set to 600s (10 min) here" and clarifies the per-request override matches.

Note: Darwin values are in **seconds** (NSURLSession API), not milliseconds. Both layers are now equal at 600 s / 600_000 ms.

Commit: `277d98c`

### 3. `shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt`
Ktor engine config + OkHttp engine timeouts:

| Field | Before | After |
|---|---:|---:|
| Ktor `requestTimeoutMillis` | 300_000 | **600_000** |
| Ktor `socketTimeoutMillis` | 120_000 | 120_000 (unchanged) |
| OkHttp `readTimeout` (seconds) | 300 | **600** |
| OkHttp `writeTimeout` (seconds) | 300 | **600** |
| OkHttp `connectTimeout` (seconds) | 30 | 30 (**intentionally unchanged**) |

Inline comment updated from "free-tier inference can run 90-150s" to "up to 10 minutes" and now explicitly states why `connectTimeout` is not extended.

Commit: `ab3f1a9`

## Why `socketTimeoutMillis` stayed at 120_000

`socketTimeoutMillis` is the **between-byte gap** tolerance, not the total request lifetime. A 2-minute gap between bytes already indicates a dead stream — no SSE-streaming LLM goes 2 full minutes between tokens; if it did, the stream is hung and we want to fail fast rather than wait another 8 minutes for the request-level timeout. Raising this would only hide hangs, never help legitimate slow generations.

(The plan also added `socketTimeoutMillis = 120_000` to the iOS factory where it had been absent, bringing iOS to parity with Android.)

## Why Android OkHttp `connectTimeout` stayed at 30s

`connectTimeout` is the TCP handshake timeout — the time to *open* the socket before any HTTP traffic flows. TCP handshake is fast (sub-second on a working network) and is completely unrelated to how long the model takes to generate. A 30 s ceiling here is generous for handshake and still gives a clear "no network" signal quickly. Extending it would only make "you have no internet" feel like "your AI is slow."

## Recipe AI inherits the same ceiling

Recipe AI generation (planned later) uses the **same** `HttpClient` instance and the **same** `OpenAICompatibleClient.chatCompletionStreaming` method, since both feature use cases route through the single shared BYOK client (per D-18-06, REQ-AI-06). No additional changes will be needed when Recipe AI is wired up — it automatically gets the 10-min ceiling.

## Verification

- Static check 1 (no stale 5/3-min refs in `shared/src/`):
  `grep -rn "requestTimeoutMillis = 300_000\|readTimeout(300\|writeTimeout(300\|timeoutIntervalForRequest = 180.0\|requestTimeoutMillis = 180_000" shared/src/` → **no matches** ✓
- Static check 2 (10-min refs at all four sites):
  - `requestTimeoutMillis = 600_000` → **3 files** (common + ios + android) ✓
  - `timeoutIntervalForRequest = 600.0` ✓
  - `timeoutIntervalForResource = 600.0` ✓
  - `readTimeout(600, TimeUnit.SECONDS)` ✓
  - `writeTimeout(600, TimeUnit.SECONDS)` ✓
  - `connectTimeout(30, TimeUnit.SECONDS)` (unchanged) ✓
- Compile check (no errors):
  - `./gradlew :shared:compileAndroidMain` → exit 0 ✓
  - `./gradlew :shared:compileKotlinIosArm64` → exit 0 ✓
  - `./gradlew :shared:compileKotlinIosSimulatorArm64` → exit 0 ✓
  - Only pre-existing warnings remain (`Flow type exposed to ObjC`, deprecated `kotlinx.datetime.Instant` typealias, redundant conversion in `BiometricGate.ios.kt`, etc.) — all unrelated to this change.

## Deviations from Plan

None — plan executed exactly as written. The plan's Android compile task name (`compileDebugKotlinAndroid`) doesn't exist in this Kotlin Multiplatform shared module; the correct task is `compileAndroidMain`. Used the correct task; no code-level deviation.

## Commits

| # | Task | Hash | Files |
|---|------|------|-------|
| 1 | Raise per-request timeouts in OpenAICompatibleClient | `902c082` | OpenAICompatibleClient.kt |
| 2 | Raise iOS Ktor + Darwin URLSession timeouts to 10 min | `277d98c` | HttpClientFactory.ios.kt |
| 3 | Raise Android Ktor + OkHttp engine timeouts to 10 min | `ab3f1a9` | HttpClientFactory.android.kt |

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt` — FOUND, contains `requestTimeoutMillis = 600_000` twice
- File `shared/src/iosMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.ios.kt` — FOUND, contains `requestTimeoutMillis = 600_000` and `timeoutIntervalForRequest = 600.0`
- File `shared/src/androidMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.android.kt` — FOUND, contains `requestTimeoutMillis = 600_000` and `readTimeout(600, TimeUnit.SECONDS)`
- Commit `902c082` — FOUND in git log
- Commit `277d98c` — FOUND in git log
- Commit `ab3f1a9` — FOUND in git log
