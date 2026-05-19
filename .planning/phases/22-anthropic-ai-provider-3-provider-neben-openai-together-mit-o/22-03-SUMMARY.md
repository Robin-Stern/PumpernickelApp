---
phase: 22-anthropic-ai-provider-3-provider-neben-openai-together-mit-o
plan: 03
subsystem: data/api (AI inference)
tags: [ai, anthropic, ktor, sse, oauth, multi-provider, phase-22]
requires:
  - "Plan 22-01: Credential sealed class (ApiKey, OAuthToken) at com.pumpernickel.infrastructure.ai"
  - "Plan 22-01: ProviderId enum + SecureKeyStore Multi-Slot expect/actuals"
  - "Phase 18: AiError sealed class, Ktor HttpClient Koin single"
provides:
  - "AnthropicMessagesRequest / AnthropicMessage DTOs (top-level system, max_tokens required)"
  - "AnthropicMessagesResponse / AnthropicContentBlock / AnthropicUsage (buffered path)"
  - "AnthropicContentBlockDeltaFrame + AnthropicTextDelta (SSE delta parsing)"
  - "AnthropicErrorFrame + AnthropicErrorBody (SSE error parsing)"
  - "AnthropicOAuthRefreshRequest/Response (refresh-token grant body)"
  - "AnthropicOAuthClient.refresh(refreshToken, clientId) → claude.ai/oauth/token"
  - "AnthropicClient.chatCompletion (buffered) + chatCompletionStreaming (named SSE) gegen /v1/messages"
  - "ensureFreshCredential: per-request SecureKeyStore.readCredential + pre-flight refresh (< now+60s)"
  - "Typed auth dispatch: Credential.OAuthToken → Authorization: Bearer; Credential.ApiKey → x-api-key"
affects:
  - "Plan 22-04 (AnthropicAiClient port adapter) — wird AnthropicClient als Wire-Layer konsumieren"
  - "Plan 22-05 (OAuthBrowserLauncher) — Plan-04-Wiring übergibt den oauthClientId-Konstruktor-Parameter"
  - "Plan 22-06 (SettingsMigration) — schreibt nach Migration ggf. den Anthropic-Slot, AnthropicClient liest ihn dann"
  - "Plan 22-10 (Tests) — commonTest gegen Sample-SSE-Frames (message_start, content_block_delta, message_stop, error)"
tech-stack:
  added:
    - "io.ktor.client.statement.bodyAsChannel + io.ktor.utils.io.readUTF8Line (already in stack — neu für Anthropic SSE-Frame-Parsing)"
    - "kotlin.time.Clock.System.now().epochSeconds via @OptIn(ExperimentalTime::class) — bereits in SettingsRepositoryImpl etabliert"
  patterns:
    - "Named-event SSE parsing (event:/data: two-line frame pairs) — neu gegenüber OpenAI-Stil 'data: {...}\\n\\n'"
    - "Sealed-class auth-dispatch via when (cred) { is OAuthToken -> Bearer; is ApiKey -> x-api-key }"
    - "Pre-request credential-freshness gate (ensureFreshCredential) — ersetzt request-time-only key-lookup aus Phase 18"
    - "CancellationException-rethrow vor AiError-Catch (1:1 Pattern aus OpenAICompatibleClient)"
key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicMessagesDto.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicOAuthClient.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicClient.kt
decisions:
  - "Single Wrapper-DTOs statt sealed-SSE-Frame-Hierarchy: cheap String-Match auf event:-Line bevor Json.decodeFromString — gnore-cost = 0 für message_start/_stop/ping/message_delta/content_block_start/_stop"
  - "applyAuthHeaders als HttpRequestBuilder-extension (private member-extension): die when-Branch zum Header-Dispatch ist 1 Code-Stelle, beide chatCompletion-Varianten teilen sie via builder lambda"
  - "OAuth-Refresh-Persist: refreshToken-Rotation defensiv (refreshed.refreshToken ?: refresh) — manche OAuth-Provider rotieren nicht, Plan-22-Researcher-D-22-12 erwartet aber Rotation"
  - "ensureFreshCredential.expiresAtEpochSeconds-Check verwendet Clock.System.now().epochSeconds (UNIX seconds), passend zu Credential.OAuthToken-Field-Konvention aus Plan 22-01"
  - "Buffered chatCompletion behält symmetrische Schnittstelle zu OpenAICompatibleClient.chatCompletion — wird in Plan 22-04 vom Port-Adapter ggf. nicht aufgerufen, ist aber für künftige Diagnostik/non-Streaming-Use-Cases vorhanden"
metrics:
  duration: "~3 min"
  completed: "2026-05-19T09:22:46Z"
  tasks: 2/2
  files_created: 3
  commits: 2
---

# Phase 22 Plan 03: Anthropic Messages Wire-Client + OAuth Refresh Summary

**One-liner:** Drei neue `data/api/`-Files realisieren den Anthropic-Wire-Adapter (`/v1/messages` mit top-level `system` + `max_tokens` + `anthropic-version`-Header + named SSE-Events) plus typsicheren `Credential`-Auth-Dispatch und pre-request OAuth-Refresh gegen `claude.ai/oauth/token`.

## What Was Built

### Task 1 — AnthropicMessagesDto.kt (commit `06b70fb`)

Sieben `@Serializable` data classes plus zwei OAuth-Refresh-DTOs:

- **AnthropicMessagesRequest** — top-level `system: String?`, `max_tokens: Int` (non-nullable, Anthropic-Pflicht), `temperature: Double = 0.7`, `stream: Boolean = false`. **Keine** `response_format` / `json_schema` Fields (D-22-10 — bestehende Prompts diktieren Output-Shape via System-Prompt).
- **AnthropicMessage** — `role: String` (Kommentar dokumentiert "user|assistant — NEVER system"), `content: String`.
- **AnthropicContentBlockDeltaFrame + AnthropicTextDelta** — schmaler SSE-DTO für genau den `content_block_delta`-Event-Typ. Alle anderen Frame-Bodies werden vom Parser nicht deserialisiert.
- **AnthropicErrorFrame + AnthropicErrorBody** — `error.type` discriminator (`authentication_error` / `overloaded_error` / etc.) + `error.message`.
- **AnthropicMessagesResponse + AnthropicContentBlock + AnthropicUsage** — non-streaming Response-Shape.
- **AnthropicOAuthRefreshRequest + AnthropicOAuthRefreshResponse** — `grant_type=refresh_token` + `client_id` request / `{ access_token, refresh_token?, expires_in, token_type, scope }` response.

### Task 2 — AnthropicOAuthClient.kt + AnthropicClient.kt (commit `b8820be`)

#### AnthropicOAuthClient

- POST `https://claude.ai/oauth/token` mit `application/json`-Body (`AnthropicOAuthRefreshRequest`).
- Non-2xx → `throw AiError.AuthOrQuota(status)`.
- Jede Throwable (außer `CancellationException` und `AiError`) → `throw AiError.AuthOrQuota(401)`. Kein silent-recover: refresh-Misserfolge zwingen den Anschluss-Pfad in `AnthropicClient.ensureFreshCredential` den OAuth-Slot zu clearen.
- Token-Log nur via `body.take(512)` Truncate — kein `access_token` / `refresh_token` direct in logs.

#### AnthropicClient

Konstruktor-Parameter: `HttpClient`, `SecureKeyStore`, `AnthropicOAuthClient`, `oauthClientId: String`. **Endpoint hardcoded** (D-22-09): `https://api.anthropic.com/v1/messages` als `MESSAGES_ENDPOINT` const im Companion.

| Element                          | Implementation                                                                                                                                                                                                            |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `chatCompletion(request)`        | `client.post(MESSAGES_ENDPOINT) { applyAuthHeaders(cred); header("anthropic-version","2023-06-01"); contentType(Json); timeout(...); setBody(request) }` — buffered Path; non-2xx → mapHttpError; 64KB-Cap → SchemaInvalid |
| `chatCompletionStreaming(...)`   | `preparePost(...).execute { response -> ... }` — Channel-Loop liest UTF-8-Lines, trackt `currentEventType` (event:-Line) und routet `data:`-Payload nach `when (currentEventType)`.                                       |
| `ensureFreshCredential()`        | `secureKeyStore.readCredential(Anthropic) ?: throw AuthOrQuota(401)`; falls OAuth + expiry-Window < 60s → `oauthClient.refresh(...)` + `writeCredential(new)`; Refresh-Fehler → `clearCredential` + `throw AuthOrQuota(401)` |
| `applyAuthHeaders(cred)`         | private `HttpRequestBuilder`-extension: `when (cred) { is OAuthToken -> "Authorization" -> "Bearer ${cred.accessToken}"; is ApiKey -> "x-api-key" -> cred.value }`                                                          |
| `mapHttpError(status, body, ?)`  | 401/403 → `AuthOrQuota(status)`; 5xx → `Provider(status)`; else → `SchemaInvalid("HTTP $status — $excerpt")` (excerpt: `body.take(400).replace("\n", " ")`)                                                                |
| Timeouts                         | `requestTimeoutMillis = 600_000`, `socketTimeoutMillis = 120_000` (symmetrisch zu `OpenAICompatibleClient`)                                                                                                                |
| 64KB-Cap                         | nach Streaming-Ende + nach buffered-decode: `if (length > 64*1024) throw SchemaInvalid("response exceeded 64KB cap")`                                                                                                     |
| `tryDecode<T>(payload)` helper   | private inline reified — schluckt Json-Parse-Failure auf einzelnem Frame (z.B. malformiertes content_block_delta) und gibt `null` zurück → SSE-Loop überspringt das Frame statt zu sterben                                  |

## Anthropic SSE-Frame-Schema (D-22-11)

```
event: message_start
data: {"type":"message_start","message":{...}}

event: content_block_start
data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}

event: content_block_stop
data: {"type":"content_block_stop","index":0}

event: message_delta
data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{...}}

event: message_stop
data: {"type":"message_stop"}

event: error
data: {"type":"error","error":{"type":"overloaded_error","message":"..."}}

event: ping
data: {...}
```

### Welche Events konsumiert / ignoriert werden

| Event-Type             | Aktion in `AnthropicClient.chatCompletionStreaming`                                                                                                                                                             |
| ---------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `content_block_delta`  | `tryDecode<AnthropicContentBlockDeltaFrame>(payload)`; wenn `delta.type == "text_delta"` und `text` nicht leer → `content.append(text)` + `onProgress(content.toString(), "")` (reasoning ist immer leerer String) |
| `error`                | `tryDecode<AnthropicErrorFrame>`; mapping siehe Tabelle unten — throw                                                                                                                                            |
| `message_stop`         | `sawStop = true; return@execute` — verlässt die Channel-Schleife sauber                                                                                                                                          |
| `message_start`        | silent ignore                                                                                                                                                                                                  |
| `content_block_start`  | silent ignore                                                                                                                                                                                                  |
| `content_block_stop`   | silent ignore                                                                                                                                                                                                  |
| `message_delta`        | silent ignore (stop_reason kommt am Ende, wir brauchen ihn nicht)                                                                                                                                              |
| `ping`                 | silent ignore (Anthropic Keep-Alive)                                                                                                                                                                           |
| Comment-Lines / anderes| silent ignore                                                                                                                                                                                                  |

## SSE error-Event → AiError-Mapping

| `error.type`            | AiError-Resultat                                       |
| ----------------------- | ------------------------------------------------------ |
| `*authentication_error` | `AiError.AuthOrQuota(401)`                             |
| `*overloaded_error`     | `AiError.Provider(529)` (Anthropic-spezifischer Status) |
| sonst                   | `AiError.SchemaInvalid("Anthropic stream error: $msg")`|

`endsWith("authentication_error")` / `endsWith("overloaded_error")` — defensiv, falls Anthropic je Prefix variiert (`organization_authentication_error` o.ä.).

## Token-Refresh-Schwelle und Failure-Pfad (D-22-12)

| Bedingung                                                                                            | Aktion                                                                                                                            |
| ---------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `readCredential(Anthropic)` == null                                                                  | `throw AiError.AuthOrQuota(401)`                                                                                                  |
| Credential ist `Credential.ApiKey`                                                                   | return — keine Refresh-Logik nötig                                                                                                |
| Credential ist `Credential.OAuthToken` und `expiresAtEpochSeconds > now + 60`                        | return — token noch frisch                                                                                                        |
| Credential ist OAuth, expired (oder Window-knapp), `refreshToken == null`                            | `clearCredential(Anthropic)` + `throw AuthOrQuota(401)` (D-22-12: User muss neu connecten)                                       |
| Credential ist OAuth, expired, `refreshToken != null`                                                 | `oauthClient.refresh(refreshToken, oauthClientId)` → persistiere neuen `OAuthToken(access, refresh ?: old, now + expires_in)`     |
| Refresh selbst wirft (AuthOrQuota, Network, etc.)                                                    | `clearCredential(Anthropic)` + `throw AuthOrQuota(401)` (Token gilt als invalidiert, UI muss neuen OAuth-Flow anstoßen)            |
| Refresh-Response liefert `refresh_token == null`                                                     | behalte alten `refreshToken` (defensiv, manche Provider rotieren nicht) — die neue `expiresAtEpochSeconds` ist die neue Schranke    |

Schwelle: **60 Sekunden** Sicherheits-Fenster vor Ablauf — kompensiert Clock-Skew + Request-Roundtrip-Zeit.

## oauthClientId-Source

Aktuell **Konstruktor-Parameter** in `AnthropicClient(client, secureKeyStore, oauthClient, oauthClientId, json)`. Wert wird in Plan **22-06 (Bootstrap/Migration)** oder Plan **22-05 (OAuthBrowserLauncher)** als const aus dem Koin-Wiring gesetzt (z.B. `single<AnthropicClient> { AnthropicClient(get(), get(), get(), oauthClientId = "pumpernickel-app-...", ...) }`). Plan 22-03 setzt KEINEN Default — das bleibt der Wiring-Schicht überlassen, damit der Wert in einer einzigen Stelle gepflegt wird (DRY).

## Pflicht-Anthropic-Header & Request-Eigenschaften

| Eigenschaft        | Wert / Pfad                                                       |
| ------------------ | ----------------------------------------------------------------- |
| Endpoint           | `https://api.anthropic.com/v1/messages` (hardcoded — kein override)|
| Pflicht-Header     | `anthropic-version: 2023-06-01`                                   |
| Auth-Header (OAuth)| `Authorization: Bearer <access_token>`                            |
| Auth-Header (Key)  | `x-api-key: <api_key>`                                            |
| Stream-Accept      | `Accept: text/event-stream` (nur in chatCompletionStreaming)      |
| Request-Timeout    | 600 s (10 min)                                                    |
| Socket-Timeout     | 120 s                                                             |
| Body-Cap           | 64 KB nach Decode / nach Stream-Akkumulation                      |

## Backward-Compatibility / Cross-Plan

- **Plan 22-04 (AnthropicAiClient-Port)** wird `AnthropicClient.chatCompletionStreaming` 1:1 wrappen — der `(content, reasoning)`-onProgress-Signatur-Vertrag ist identisch zu `OpenAICompatibleClient.chatCompletionStreaming`, damit der bestehende `AiInferenceClient`-Port (Plan 22-04 noch zu definieren) ohne if/else Provider-Branching auskommt.
- **Plan 22-10 (Tests)** kann den Stream-Parser isoliert per Sample-Lines testen (`message_start`-Line dann `content_block_delta`-Line × N dann `message_stop`-Line) — keine echte HTTPS-Roundtrip nötig.
- **Plan 22-06 (SettingsMigration)** ist nicht-konsumierend: der Anthropic-Slot wird erst durch User-Aktion (OAuth-Flow oder API-Key-Paste) geschrieben, die Migration setzt nur `activeProvider`/`baseUrl`/`model`-Defaults, nicht den Credential-Slot.

## Build / Verification State in diesem Worktree

Pre-existing commonMain compile errors (Wave-1-Transitionszustand, dokumentiert in 22-01-SUMMARY): `AiModule.kt:36`, `AiSettingsViewModel.kt:43/48/52/69`, `RecipeAiViewModel.kt:51`, `WorkoutAiViewModel.kt:105` — alles `readApiKey/writeApiKey/clearApiKey`-Referenzen die Plan 22-04 + 22-07 reparieren. **Keine** dieser Errors referenziert die in Plan 22-03 neu erstellten Anthropic-Files. Plus 1 `AppDatabase`-Roomconstructor-Error, ebenfalls pre-existing.

Verification der neuen Files erfolgte via fixed-string grep (kein Anthropic-Symbol erscheint in der Error-Liste); finale Cross-Plan-Compile-Verifikation gehört in den Wave-2-Merge-Schritt (Plan 22-04 ergänzt den Port-Adapter, Plan 22-07 repariert die Wave-1-Konsumenten).

## Deviations from Plan

None — beide Tasks 1:1 wie in 22-03-PLAN.md spezifiziert ausgeführt. Eine kleine Tooling-Anpassung: `HttpRequestBuilder` wurde explizit importiert (`import io.ktor.client.request.HttpRequestBuilder`) statt als FQ-Name (`io.ktor.client.request.HttpRequestBuilder.applyAuthHeaders`) — semantisch identisch, sauberer Imports-Block.

## Threat Surface Scan

Keine neuen Threat-Flags. Plan-spezifizierte Mitigationen (T-22-01 bis T-22-05) sind alle implementiert:

| Threat ID | Status     | Beweis im Code                                                                                                |
| --------- | ---------- | ------------------------------------------------------------------------------------------------------------- |
| T-22-01   | mitigated  | `applyAuthHeaders` ist private extension; alle `println`-Calls truncate body und loggen nie das Auth-Header-Value |
| T-22-02   | mitigated  | `mapHttpError` truncate body excerpt auf 400 Zeichen + replace newlines; SSE-error → SchemaInvalid mit short msg |
| T-22-03   | mitigated  | Refresh läuft gegen `claude.ai/oauth/token`, Inference gegen `api.anthropic.com/v1/messages` — getrennt        |
| T-22-04   | accepted   | Kein Backoff — User sieht `Provider(529)` als "Anbieter überlastet"; manuelles Retry (Phase-22-Scope)         |
| T-22-05   | mitigated  | `ensureFreshCredential` ruft `secureKeyStore.readCredential` **per Request** auf — keine field-Referenz im Client |

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicMessagesDto.kt`: FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicOAuthClient.kt`: FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicClient.kt`: FOUND
- Commit `06b70fb` (Task 1, DTOs): FOUND in `git log --oneline`
- Commit `b8820be` (Task 2, Client + OAuth): FOUND in `git log --oneline`
- Grep `class AnthropicMessagesRequest` with top-level `system` + `max_tokens` required: PASSED
- Grep `MESSAGES_ENDPOINT = "https://api.anthropic.com/v1/messages"`: PASSED
- Grep `ANTHROPIC_VERSION = "2023-06-01"`: PASSED
- Grep `header(HttpHeaderAuthorization, "Bearer ...")`: PASSED
- Grep `header(HEADER_X_API_KEY, cred.value)`: PASSED
- Grep `content_block_delta` + `message_stop`: PASSED
- Grep `class AnthropicOAuthClient` + `claude.ai/oauth/token`: PASSED
- Compile-error-grep: keine Errors enthalten die Substrings `Anthropic` / `AnthropicClient` / `AnthropicOAuthClient` / `AnthropicMessagesDto`
