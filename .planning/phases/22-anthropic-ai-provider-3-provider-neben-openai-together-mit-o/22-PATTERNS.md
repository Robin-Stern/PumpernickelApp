# Phase 22: Anthropic AI Provider - Pattern Map

**Mapped:** 2026-05-19
**Files analyzed:** 17 new/modified (10 neu + 7 modifiziert)
**Analogs found:** 16 / 17

---

## File Classification

### Neue Dateien (10)

| Neue Datei | Layer | Rolle | Datenfluss | Analog | Match-Qualität |
|------------|-------|-------|------------|--------|----------------|
| `shared/.../data/api/AnthropicClient.kt` | data | api-client | streaming (SSE) + request-response | `data/api/OpenAICompatibleClient.kt` | exakt |
| `shared/.../data/api/AnthropicMessagesDto.kt` | data | DTO | serialization | `data/api/AiChatDto.kt` | exakt |
| `shared/.../infrastructure/ai/AnthropicAiClient.kt` (Adapter, optional je nach Architektur-Wahl) | infrastructure | adapter | request-response | `infrastructure/ai/OpenAiCompatibleAiClient.kt` | exakt |
| `shared/.../infrastructure/ai/AiClient.kt` (erweitert) ODER neues Interface `AiInferenceClient` | infrastructure | port/interface | request-response | `infrastructure/ai/AiClient.kt` (existiert bereits) | exakt — Interface ist schon da |
| `shared/.../infrastructure/ai/ProviderResolver.kt` (neu) | infrastructure | factory/resolver | event-driven (Flow-basiert) | kein direkter Analog | role-match (Koin factory pattern in `AiModule.kt`) |
| `shared/.../infrastructure/ai/Credential.kt` | infrastructure | model (sealed) | value-class | `domain/ai/AiError.kt` (sealed class) | role-match |
| `shared/.../infrastructure/ai/OAuthBrowserLauncher.kt` (`expect class`) | infrastructure | platform-contract | request-response (suspend) | `infrastructure/progresspic/PhotoCaptureLauncher.kt` | exakt |
| `shared/.../androidMain/.../infrastructure/ai/OAuthBrowserLauncher.android.kt` | infrastructure (android) | platform-actual | CustomTabs callback | `androidMain/.../infrastructure/progresspic/PhotoCaptureLauncher.android.kt` + Holder pattern | role-match |
| `shared/.../iosMain/.../infrastructure/ai/OAuthBrowserLauncher.ios.kt` | infrastructure (ios) | platform-actual | ASWebAuthenticationSession callback | `iosMain/.../infrastructure/progresspic/PhotoCaptureLauncher.ios.kt` | exakt |
| `shared/.../data/repository/SettingsMigration.kt` (neu) | data | one-shot migration | batch | kein direkter Analog (B12 Bug-Wave + Phase 13 retroactive sind closest) | role-match (`RetroactiveWalker.kt` + `retroactiveApplied` sentinel) |

### Modifizierte Dateien (7)

| Bestehende Datei | Was geändert wird |
|------------------|-------------------|
| `shared/.../infrastructure/ai/SecureKeyStore.kt` | Multi-Slot API: `readCredential(ProviderId)` / `writeCredential(...)` + sealed `Credential` |
| `shared/.../iosMain/.../infrastructure/ai/SecureKeyStore.ios.kt` | Account-Slot pro Provider statt fixed `"openai.api.key"` |
| `shared/.../androidMain/.../infrastructure/ai/SecureKeyStore.android.kt` | Multi-Key in `ai_secrets` EncryptedSharedPreferences |
| `shared/.../domain/repository/SettingsRepository.kt` + `data/repository/SettingsRepositoryImpl.kt` | `activeProvider`, `modelByProvider`, `baseUrlByProvider` (deprecate single `aiBaseUrl`/`aiModel`) |
| `shared/.../di/AiModule.kt` | Factory-Resolution `AiClient` basierend auf `activeProvider` |
| `iosApp/iosApp/Info.plist` | `CFBundleURLTypes` mit Redirect-Scheme (z.B. `pumpernickel-oauth`) |
| `androidApp/src/androidMain/AndroidManifest.xml` | `<intent-filter>` an MainActivity mit Redirect-Scheme |
| `androidApp/.../ui/screens/AiSettingsScreen.kt` + `iosApp/iosApp/Views/AI/AISettingsView.swift` + ViewModel | Provider-Liste mit Radio-Toggle + "+ Provider"-Button + Anthropic-Connect-Sheet |

---

## Pattern Assignments

### `shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicClient.kt` (data, streaming-SSE)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt` (exact match — selbe Schicht, selbes Streaming-Pattern, selbe Error-Mapping)

**Imports** (`OpenAICompatibleClient.kt:1-16`):
```kotlin
package com.pumpernickel.data.api

import com.pumpernickel.domain.ai.AiError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
```

**Konstruktor + Auth-Pattern (keyProvider-Lambda)** (`OpenAICompatibleClient.kt:28-32`):
```kotlin
class OpenAICompatibleClient(
    private val client: HttpClient,
    private val keyProvider: suspend () -> String?
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
```

→ AnthropicClient bekommt ANALOG einen `credentialProvider: suspend () -> Credential?` Lambda statt String. Adapter-Logik (Bearer für OAuth vs `x-api-key` für API-Key) lebt im Client.

**Auth-Header-Setup im POST-Aufruf** (`OpenAICompatibleClient.kt:47-55` für buffered, `111-119` für streaming):
```kotlin
val response = client.post(url) {
    header("Authorization", "Bearer $key")
    contentType(ContentType.Application.Json)
    timeout {
        requestTimeoutMillis = 600_000   // 10 min — full stream lifetime for slow free-tier LLMs
        socketTimeoutMillis = 120_000    // 2 min between bytes — generous gap tolerance for slow token streams
    }
    setBody(request)
}
```

→ AnthropicClient ergänzt PFLICHT-Header `anthropic-version: 2023-06-01` und wählt zwischen `Authorization: Bearer ${oauth.access}` (OAuth) vs `x-api-key: $key` (API-Key) basierend auf `Credential`-Variante.

**Streaming SSE Pattern — Schleife über Channel** (`OpenAICompatibleClient.kt:128-160`):
```kotlin
val channel = response.bodyAsChannel()
while (!channel.isClosedForRead) {
    val line = channel.readUTF8Line() ?: break
    if (line.isBlank()) continue
    if (!line.startsWith("data:")) continue

    val payload = line.substring(5).trim()
    if (payload == "[DONE]") {
        sawDone = true
        break
    }

    val chunk = try {
        json.decodeFromString<StreamChunkResponse>(payload)
    } catch (e: Exception) {
        println("[AI] stream skip malformed chunk: ${payload.take(120)}")
        continue
    }
    val delta = chunk.choices.firstOrNull()?.delta ?: continue
    // ... append content + reasoning
    if (changed) {
        onProgress(content.toString(), reasoning.toString())
    }
}
```

→ AnthropicClient muss Anthropic SSE-Format parsen — named events `event: message_start\ndata: {...}\n\n` statt OpenAI `data: {...}`. **Buffer 2 lines** (event-name + data) per Frame. D-22-11 Mapping:
- `event: message_start` → emit start/metadata
- `event: content_block_delta` mit `delta.type=="text_delta"` → akkumuliere `delta.text` + rufe `onProgress(content, reasoning="")`
- `event: message_delta` → ignorieren (enthält `stop_reason`)
- `event: message_stop` → break-Schleife (entspricht `[DONE]`)
- `event: error` → throw `AiError.Provider(...)` mit `data.error.message`

**HTTPS-Gate + URL-Validation** (`OpenAICompatibleClient.kt:182-187`):
```kotlin
private fun validateUrl(url: String) {
    if (url.isBlank()) throw AiError.Network()
    if (!url.startsWith("https://")) {
        throw AiError.SchemaInvalid("Insecure URL rejected (must use https)")
    }
}
```

→ AnthropicClient: BaseURL ist hardcoded `https://api.anthropic.com` (kein User-Override). Validation entfällt, aber 64KB-Cap und Error-Mapping übernehmen.

**Error-Mapping (HTTP-Status → AiError)** (`OpenAICompatibleClient.kt:201-208`):
```kotlin
private fun mapHttpError(statusCode: Int, body: String): AiError {
    val excerpt = body.take(400).replace("\n", " ")
    return when (statusCode) {
        401, 403 -> AiError.AuthOrQuota(statusCode)
        in 500..599 -> AiError.Provider(statusCode)
        else -> AiError.SchemaInvalid("HTTP $statusCode — $excerpt")
    }
}
```

→ AnthropicClient ergänzt: bei 401 mit OAuth-Credential UND Token-Refresh-bereits-versucht-und-failed → spezielle `AiError.AuthOrQuota(401)` mit Hinweis "Bitte neu mit Claude.ai verbinden" (D-22-12 Notification).

**64KB-Cap Pattern** (`OpenAICompatibleClient.kt:66-68, 165-167`): direkt übernehmen.

**Try/catch CancellationException-rethrow** (`OpenAICompatibleClient.kt:70-78, 169-177`): direkt übernehmen.

---

### `shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicMessagesDto.kt` (data, DTO/serialization)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt` (exact match — selbes Schema-Pattern)

**Imports** (`AiChatDto.kt:1-5`):
```kotlin
package com.pumpernickel.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
```

**Request-DTO mit @SerialName für snake_case-Mapping** (`AiChatDto.kt:7-15`):
```kotlin
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false
)
```

→ AnthropicMessagesRequest analog mit Top-Level-`system: String?`, `max_tokens: Int` (Anthropic pflicht!), `stream: Boolean`. Keine `response_format`/`json_schema` — Anthropic hat das nicht. Schema-Enforcement passiert über System-Prompt (D-22-10 bestätigt: Prompts bleiben unverändert).

**Stream-Chunk-DTO** (`AiChatDto.kt:26-43`):
```kotlin
@Serializable
data class StreamChunkResponse(
    val choices: List<StreamChunkChoice> = emptyList()
)

@Serializable
data class StreamChunkChoice(
    val index: Int = 0,
    val delta: StreamChunkDelta = StreamChunkDelta(),
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class StreamChunkDelta(
    val role: String? = null,
    val content: String? = null,
    val reasoning: String? = null
)
```

→ Anthropic-Events haben andere Shapes pro Event-Typ: `MessageStartEvent { message: { id, role, content, model, usage } }`, `ContentBlockDeltaEvent { index, delta: { type, text } }`, `MessageStopEvent {}`, `ErrorEvent { error: { type, message } }`. Eine sealed-Hierarchie ähnlich `AiError` (siehe `domain/ai/AiError.kt:24-30`) ist sinnvoll, oder pragmatisch: eine `JsonElement`-basierte Deserialization mit Discriminator-`type`.

---

### `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AnthropicAiClient.kt` (Adapter, infrastructure)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/OpenAiCompatibleAiClient.kt` (exact match)

**Wichtige Erkenntnis:** Das Interface `AiClient` existiert BEREITS in `infrastructure/ai/AiClient.kt`. CONTEXT.md spricht von einem neuen `AiInferenceClient` Interface — **wahrscheinlich ist das bestehende `AiClient` ausreichend**, gegebenenfalls minimal erweitert. Planner muss entscheiden, ob Interface umbenannt wird oder ob `AiClient` einfach erweitert wird. Der Adapter pro Provider folgt jedenfalls dem `OpenAiCompatibleAiClient`-Muster.

**Imports + Class-Header** (`OpenAiCompatibleAiClient.kt:1-35`):
```kotlin
package com.pumpernickel.infrastructure.ai

import com.pumpernickel.data.api.ChatMessage
import com.pumpernickel.data.api.ChatRequest
import com.pumpernickel.data.api.JsonSchemaSpec
import com.pumpernickel.data.api.OpenAICompatibleClient
import com.pumpernickel.data.api.ResponseFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class OpenAiCompatibleAiClient(
    private val client: OpenAICompatibleClient,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) : AiClient {
    // ...
}
```

→ `AnthropicAiClient(private val client: AnthropicClient) : AiClient` — wrappt den Ktor-Client und mappt `completeJsonSchema/completeJsonObject(systemPrompt, userPrompt)` auf die Anthropic-Messages-API. Da Anthropic kein `json_schema`/`json_object` kennt, wird BEIDE Methoden auf denselben Code-Pfad gemappt (System-Prompt enthält bereits Schema-Anforderung — bestehende Prompts machen das schon, D-22-10).

**completeJsonSchema-Body-Konstruktion** (`OpenAiCompatibleAiClient.kt:37-68`):
```kotlin
override suspend fun completeJsonSchema(
    baseUrl: String,
    model: String,
    systemPrompt: String,
    userPrompt: String,
    schema: AiJsonSchema,
    onProgress: (content: String, reasoning: String) -> Unit
): String {
    val parsedSchema: JsonElement = json.parseToJsonElement(schema.schemaJson)
    val request = ChatRequest(
        model = model,
        messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = userPrompt)
        ),
        responseFormat = ResponseFormat(
            type = "json_schema",
            jsonSchema = JsonSchemaSpec(name = schema.name, schema = parsedSchema, strict = schema.strict)
        ),
        temperature = 0.7,
        maxTokens = 4096
    )
    return client.chatCompletionStreaming(baseUrl, request, onProgress)
}
```

→ Anthropic-Mapping: `systemPrompt` geht in das top-level `system`-Field der Messages-Request, `userPrompt` in `messages: [{role: "user", content: userPrompt}]`. KEINE `system`-Rolle in `messages` (das wäre ein Fehler in Anthropic-API). `temperature = 0.7`, `max_tokens = 4096` übernehmen.

---

### `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AiClient.kt` (Interface — existiert bereits, ggf. anpassen)

**Wichtige Erkenntnis:** Das Interface ist bereits gut abstrahiert (siehe `AiClient.kt:39-91`):
```kotlin
interface AiClient {
    suspend fun completeJsonSchema(
        baseUrl: String, model: String,
        systemPrompt: String, userPrompt: String,
        schema: AiJsonSchema,
        onProgress: (content: String, reasoning: String) -> Unit = { _, _ -> }
    ): String

    suspend fun completeJsonObject(
        baseUrl: String, model: String,
        systemPrompt: String, userPrompt: String,
        onProgress: (content: String, reasoning: String) -> Unit = { _, _ -> }
    ): String
}
```

**Empfehlung für Planner:** AiClient unverändert lassen ODER `baseUrl` optional machen (Anthropic-Adapter ignoriert es einfach). Kein Bedarf für `AiInferenceClient` als neues Interface — der existierende Port ist Provider-agnostisch.

**Vorhandene Pattern, die direkt anwendbar sind:**
- Pure-Kotlin-Surface (kein Ktor, kein Serialization in Port — Doku-Block in `AiClient.kt:6-12`)
- `AiJsonSchema` data class als JSON-Schema-Träger (`AiClient.kt:107-111`) — bleibt nutzbar, Anthropic ignoriert nur das Argument im completeJsonSchema-Aufruf intern.

---

### `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/ProviderResolver.kt` (factory, neu)

**Kein direkter Analog.** Nächste Approximation: Koin-`factory`/`single` mit Lambda-Resolution. Siehe `AiModule.kt:33-43`:

```kotlin
single {
    OpenAICompatibleClient(
        client = get(),
        keyProvider = { get<SecureKeyStore>().readApiKey() }
    )
}
single<AiClient> { OpenAiCompatibleAiClient(get()) }
```

**Empfehlung für Planner (Discretion-Punkt aus CONTEXT.md):**

Option A — Koin-`factory` mit StateFlow-Lookup:
```kotlin
factory<AiClient> {
    val settingsRepo: SettingsRepository = get()
    val active = runBlocking { settingsRepo.activeProvider.first() }  // CAUTION blocking
    when (active) {
        ProviderId.OpenAI, ProviderId.Together -> get<OpenAiCompatibleAiClient>()
        ProviderId.Anthropic -> get<AnthropicAiClient>()
    }
}
```

Option B — eigenes `ProviderResolver` mit observable Flow + `loadKoinModules`-Override on switch. Komplexer, sauberer.

Option C — `AiClient` als facade die intern dispatches:
```kotlin
class DispatchingAiClient(
    private val settings: SettingsRepository,
    private val openai: OpenAiCompatibleAiClient,
    private val anthropic: AnthropicAiClient
) : AiClient {
    override suspend fun completeJsonSchema(...) {
        val provider = settings.activeProvider.first()
        return when (provider) {
            ProviderId.Anthropic -> anthropic.completeJsonSchema(...)
            else -> openai.completeJsonSchema(...)
        }
    }
}
```

→ Option C ist die schlankste und vermeidet runBlocking. Empfehlung in Plan.

**Koin allowOverride ist global aktiv** (siehe `SharedModule.kt:148-156`):
```kotlin
fun initKoin(appDeclaration: KoinApplication.() -> Unit = {}) {
    startKoin {
        allowOverride(true)
        appDeclaration()
        modules(sharedModule + platformModule)
    }
}
```
→ Falls Provider-Switch via Module-Reload geht, ist `allowOverride(true)` bereits vorhanden.

---

### `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/Credential.kt` (sealed model)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt:24-48` (sealed class mit data classes)

**Sealed-Class-Pattern** (`AiError.kt:24-30`):
```kotlin
sealed class AiError : Throwable() {
    object Timeout : AiError()
    data class Network(val detail: String? = null) : AiError()
    data class AuthOrQuota(val httpStatus: Int) : AiError()
    data class Provider(val httpStatus: Int) : AiError()
    data class SchemaInvalid(val detail: String) : AiError()
    object Cancelled : AiError()

    companion object {
        fun fromThrowable(t: Throwable): AiError = when (t) {
            // ... typed dispatch
        }
    }
}
```

→ `Credential` als sealed class:
```kotlin
sealed class Credential {
    data class ApiKey(val value: String) : Credential()
    data class OAuthToken(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtEpochSeconds: Long
    ) : Credential()
}
```

ProviderId-Enum analog zu bestehenden Enum-String-Konvertierungen in `SettingsRepositoryImpl.kt:91-96, 192-209` (Enum.name als Persistenz-Form).

---

### `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/OAuthBrowserLauncher.kt` (`expect class`)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/progresspic/PhotoCaptureLauncher.kt` (exact match — selbes `expect class` mit suspend, return-null-on-cancel-Pattern)

**Common expect class Pattern** (`PhotoCaptureLauncher.kt:15-18`):
```kotlin
expect class PhotoCaptureLauncher {
    suspend fun captureFromCamera(): ByteArray?
    suspend fun pickFromLibrary(): ByteArray?
}
```

→ Common-Surface für OAuthBrowserLauncher:
```kotlin
expect class OAuthBrowserLauncher {
    /**
     * Startet OAuth-PKCE-Flow. Suspends bis Redirect-URL empfangen wird.
     * Returns null wenn User abbricht (Browser dismissed) oder Fehler.
     * Throws auf System-Fehler (kein Browser verfügbar etc.).
     *
     * @param authorizeUrl vollständige authorize-URL inkl. code_challenge,
     *                     state, redirect_uri, client_id
     * @param redirectScheme Scheme-Teil des Redirect (z.B. "pumpernickel-oauth")
     *                       — muss matchen `CFBundleURLTypes` (iOS) /
     *                       `<intent-filter>` (Android)
     * @return ausgepacktes `code`-Parameter aus Redirect-URL, oder null
     */
    suspend fun startAuthFlow(authorizeUrl: String, redirectScheme: String): String?
}
```

---

### `shared/src/iosMain/.../infrastructure/ai/OAuthBrowserLauncher.ios.kt`

**Analog:** `shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/progresspic/PhotoCaptureLauncher.ios.kt` (exact match — selbes Pattern für UIKit-Bridge zu Kotlin suspend)

**File-Header + Imports** (`PhotoCaptureLauncher.ios.kt:1-32`):
```kotlin
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pumpernickel.infrastructure.progresspic

import kotlinx.coroutines.CompletableDeferred
import kotlin.concurrent.Volatile
// ... platform.UIKit imports
import platform.darwin.NSObject
```

**CompletableDeferred + Delegate-Retention-Pattern** (`PhotoCaptureLauncher.ios.kt:60-104`):
```kotlin
actual class PhotoCaptureLauncher {
    @Volatile
    private var currentCameraDelegate: ImagePickerDelegate? = null

    actual suspend fun captureFromCamera(): ByteArray? {
        // ...
        val presenter = PhotoCapturePresenterHolder.current ?: return null
        val deferred = CompletableDeferred<UIImage?>()
        val delegate = ImagePickerDelegate(deferred)
        currentCameraDelegate = delegate
        try {
            val picker = UIImagePickerController()
            picker.sourceType = ...
            picker.delegate = delegate
            presenter.presentViewController(picker, animated = true, completion = null)
            val image = deferred.await()
            // ...
        } finally {
            if (currentCameraDelegate === delegate) currentCameraDelegate = null
        }
    }
}
```

→ Für OAuthBrowserLauncher.ios.kt:
- `ASWebAuthenticationSession` statt `UIImagePickerController`
- `ASWebAuthenticationPresentationContextProviding`-Delegate gehalten via `@Volatile` field (genau wie ImagePickerDelegate hier)
- `CompletableDeferred<String?>` für extrahiertes `code`-Parameter (statt `UIImage?`)
- Session callback-Closure (`completionHandler: { callbackURL, error in ... }`) parsed `callbackURL.query` → liefert `code` ans `deferred.complete(...)`
- Session referenz separat speichern (sonst GC-Risiko während suspend), parallel zum Delegate-Field-Trick (siehe Kommentar `PhotoCaptureLauncher.ios.kt:58-64` "REVIEW M-05")

**PhotoCapturePresenterHolder als Holder-Pattern** (`PhotoCaptureLauncher.ios.kt:40-48`):
```kotlin
object PhotoCapturePresenterHolder {
    @Volatile
    var current: UIViewController? = null
    fun attach(controller: UIViewController) { current = controller }
    fun detach(controller: UIViewController) {
        if (current === controller) current = null
    }
}
```

→ Analog: `OAuthPresenterHolder` für root-UIViewController, gesetzt von Swift via `OAuthBrowserLauncherHelper().attach(viewController)`. `ASWebAuthenticationSession.presentationContextProvider` greift auf diesen zu.

---

### `shared/src/androidMain/.../infrastructure/ai/OAuthBrowserLauncher.android.kt`

**Analog (struktur):** `shared/src/androidMain/.../infrastructure/progresspic/PhotoCaptureLauncher.android.kt` + `PhotoCaptureLauncherHost.kt` (siehe Pfad: `/Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp/shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/progresspic/PhotoCaptureLauncherHost.kt`)

**Pattern** (Holder + ActivityResult-Bridge):
- Eine `OAuthBrowserLauncherHost` Activity (oder Composable mit `rememberLauncherForActivityResult`) startet `CustomTabsIntent`
- Redirect kommt zurück via `<intent-filter>` an MainActivity → `onNewIntent(intent)` extrahiert Redirect-URL
- `CompletableDeferred<String?>` wird in einem App-weiten Holder gehalten
- Plan-Hinweis: SDK-Lib `androidx.browser:browser` ist bereits **NICHT** in `libs.versions.toml` (Researcher muss prüfen) — vermutlich neu aufzunehmen
- Permissions: KEINE neue Permission nötig (CustomTabs läuft im Browser-Prozess)

**AndroidManifest-Erweiterung** (siehe `AndroidManifest.xml:31-40` für bestehende MainActivity):
```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:windowSoftInputMode="adjustResize"
    android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|uiMode|locale|fontScale|density">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
    <!-- NEU: OAuth-Redirect Intent-Filter -->
    <intent-filter android:autoVerify="false">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="pumpernickel-oauth" android:host="callback" />
    </intent-filter>
</activity>
```

→ MainActivity bekommt `onNewIntent(intent: Intent)` Override, der `intent.data?.getQueryParameter("code")` extrahiert und an den Holder delegiert.

---

### `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsMigration.kt` (one-shot migration)

**Kein direkter Analog.** Nächste Approximation:
1. `RetroactiveWalker` in `data/repository/RetroactiveWalker.kt` (mentioned in `.planning/codebase/STRUCTURE.md:489`) — orchestriert one-shot Gamification-Backfill
2. Bestehender retroactive-flag-Pattern in `SettingsRepositoryImpl.kt:177-185`:

```kotlin
override val retroactiveApplied: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[retroactiveAppliedKey] ?: false
}

override suspend fun setRetroactiveApplied(applied: Boolean) {
    dataStore.edit { preferences ->
        preferences[retroactiveAppliedKey] = applied
    }
}
```

→ Migrations-Flag analog:
```kotlin
private val migratedToMultiProviderKey = booleanPreferencesKey("migrated_to_multi_provider")

override val migratedToMultiProvider: Flow<Boolean> = dataStore.data.map {
    it[migratedToMultiProviderKey] ?: false
}

override suspend fun setMigratedToMultiProvider(value: Boolean) {
    dataStore.edit { it[migratedToMultiProviderKey] = value }
}
```

**Migration-Klasse** (D-22-08, eigene Erfindung):
```kotlin
class SettingsMigration(
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: SecureKeyStore  // erweiterte Multi-Slot-Variante
) {
    suspend fun run() {
        if (settingsRepository.migratedToMultiProvider.first()) return  // idempotent

        // 1. Read legacy key
        val legacyKey = secureKeyStore.readLegacyApiKey()  // alter readApiKey()-Pfad
        if (legacyKey != null) {
            val oldBaseUrl = settingsRepository.aiBaseUrl.first()
            val inferredProvider = when {
                oldBaseUrl.contains("together.ai") -> ProviderId.Together
                oldBaseUrl.contains("openrouter.ai") -> ProviderId.OpenAI  // OR oder Custom
                else -> ProviderId.OpenAI
            }
            secureKeyStore.writeCredential(inferredProvider, Credential.ApiKey(legacyKey))
            settingsRepository.setActiveProvider(inferredProvider)
            secureKeyStore.clearLegacyApiKey()  // alten Slot löschen
        }

        settingsRepository.setMigratedToMultiProvider(true)
    }
}
```

**Trigger** (Discretion): `PumpernickelApplication.onCreate` (Android) / `AppDelegate.applicationDidFinishLaunching` (iOS) via Koin `single { SettingsMigration(get(), get()) }` + Kick-off in einem GlobalScope-Launch. Alternativ: lazy beim ersten Aufruf einer Use-Case.

**Test-Strategie:** Migration ist explizit in D-22-13 als Test-Target genannt. commonTest mit fake `SettingsRepository` + fake `SecureKeyStore` — siehe bestehenden Test-Stil in `shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/nutrition/OpenFoodFactsAdapterBrandRankingTest.kt` (test-class pro Unit-of-behaviour, `@Test`-functions klar benannt mit "scenario_expected"-Schema, `kotlin.test.assertEquals/assertTrue`).

---

### `shared/.../infrastructure/ai/SecureKeyStore.kt` + iOS/Android-actuals (MODIFIZIERT)

**Bestehende Common-API** (`SecureKeyStore.kt:19-23`):
```kotlin
expect class SecureKeyStore {
    suspend fun writeApiKey(value: String)
    suspend fun readApiKey(): String?
    suspend fun clearApiKey()
}
```

**Iks gewünschter Stand nach Phase 22:**
```kotlin
expect class SecureKeyStore {
    suspend fun writeCredential(provider: ProviderId, credential: Credential)
    suspend fun readCredential(provider: ProviderId): Credential?
    suspend fun clearCredential(provider: ProviderId)
    suspend fun listProviders(): Set<ProviderId>

    // Legacy migration helpers (private nach Migration):
    suspend fun readLegacyApiKey(): String?
    suspend fun clearLegacyApiKey()
}
```

**Schlüssel-Strategie:**

iOS Keychain (siehe `SecureKeyStore.ios.kt:54-55`):
```kotlin
private const val SERVICE = "PumpernickelApp.AI"
private const val ACCOUNT = "openai.api.key"  // legacy fixed
```

→ Pro Provider eigenes `ACCOUNT`:
- `openai.api.key` (bestehend, bleibt für Backward-Compatibility lesbar)
- `together.api.key`
- `anthropic.api.key`
- `anthropic.oauth.token` (JSON-encoded `Credential.OAuthToken`)

`baseQueryDict(account = ...)` parametrieren statt fixed ACCOUNT.

Android EncryptedSharedPreferences (siehe `SecureKeyStore.android.kt:39-42`):
```kotlin
companion object {
    private const val PREFS_NAME = "ai_secrets"
    private const val KEY_API_KEY = "openai.api.key"
}
```

→ Selber File `ai_secrets`, multiple Keys (`openai.api.key`, `together.api.key`, `anthropic.api.key`, `anthropic.oauth.token`). Direktes Mapping zu iOS Account-Strings.

**Serialisierung von `Credential.OAuthToken`** in JSON über kotlinx.serialization — schreibe `Json.encodeToString(oauthToken)` als String-Value in Keychain/EncryptedSharedPrefs.

**ApiKeyState-Sentinel** (`SecureKeyStore.android.kt:23-37`, `SecureKeyStore.ios.kt:84-91`):
```kotlin
// Android
actual suspend fun writeApiKey(value: String) = withContext(Dispatchers.IO) {
    prefs.edit().putString(KEY_API_KEY, value).apply()
    ApiKeyState.set(true)
}
```

→ ApiKeyState bleibt für bestehende UI-Konsumption (`AiSettingsViewModel.kt:37`) erhalten, signalisiert "irgendein Credential vorhanden". Pro-Provider-Status-StateFlow kann zusätzlich neu eingeführt werden.

---

### `shared/.../domain/repository/SettingsRepository.kt` + Impl (MODIFIZIERT)

**Bestehende AI-Section** (`SettingsRepository.kt:65-71`):
```kotlin
// AI config (D-18-06)
val aiProviderPreset: Flow<String>
val aiBaseUrl: Flow<String>
val aiModel: Flow<String>
suspend fun setAiProviderPreset(preset: String)
suspend fun setAiBaseUrl(url: String)
suspend fun setAiModel(model: String)
```

**Bestehende Impl** (`SettingsRepositoryImpl.kt:236-264`):
```kotlin
// D-18-06 — AI configuration. NOT secrets ...
private val aiProviderPresetKey = stringPreferencesKey("ai_provider_preset")
private val aiBaseUrlKey = stringPreferencesKey("ai_base_url")
private val aiModelKey = stringPreferencesKey("ai_model")

override val aiProviderPreset: Flow<String> = dataStore.data.map { prefs ->
    prefs[aiProviderPresetKey] ?: "openai"
}
// ... setters
```

**Phase-22-Erweiterung:**
```kotlin
// D-22-09
val activeProvider: Flow<ProviderId>
val modelByProvider: Flow<Map<ProviderId, String>>  // ODER pro Provider eigener Flow
val baseUrlByProvider: Flow<Map<ProviderId, String>>
suspend fun setActiveProvider(provider: ProviderId)
suspend fun setModel(provider: ProviderId, model: String)
suspend fun setBaseUrl(provider: ProviderId, url: String)

// Migration-Flag (D-22-08)
val migratedToMultiProvider: Flow<Boolean>
suspend fun setMigratedToMultiProvider(value: Boolean)
```

**Impl-Pattern für Map-basierte Flows** — siehe das `combine`-Pattern in `SettingsRepositoryImpl.kt:152-160` für `nutritionGoals`:
```kotlin
override val nutritionGoals: Flow<NutritionGoals> = combine(
    dataStore.data.map { it[calorieGoalKey]?.toIntOrNull() ?: 2500 },
    dataStore.data.map { it[proteinGoalKey]?.toIntOrNull() ?: 150 },
    // ...
) { cal, pro, fat, carb, sugar -> NutritionGoals(cal, pro, fat, carb, sugar) }
```

→ Für `modelByProvider`:
```kotlin
private val modelOpenAiKey = stringPreferencesKey("model_openai")
private val modelTogetherKey = stringPreferencesKey("model_together")
private val modelAnthropicKey = stringPreferencesKey("model_anthropic")

override val modelByProvider: Flow<Map<ProviderId, String>> = dataStore.data.map { prefs ->
    mapOf(
        ProviderId.OpenAI to (prefs[modelOpenAiKey] ?: "gpt-4o-mini"),
        ProviderId.Together to (prefs[modelTogetherKey] ?: "google/gemma-4-31B-it"),
        ProviderId.Anthropic to (prefs[modelAnthropicKey] ?: "claude-opus-4-7")
    )
}
```

**Deprecation:** Bestehende `aiBaseUrl/aiModel`-Flows bleiben als View über die neuen Map-basierten Flows mit `@Deprecated` Marker — Phase-22-Tasks ändern Use-Cases auf neuen Pfad. Vorhandene UI/VM/Use-Case-Aufrufer (`WorkoutAiUseCase.kt:41-42`, `AiSettingsViewModel.kt:20-29`) werden angepasst.

---

### `shared/.../di/AiModule.kt` (MODIFIZIERT)

**Bestehende Wiring** (`AiModule.kt:26-44`):
```kotlin
val aiModule = module {
    single { AiPromptCatalog() }

    single {
        OpenAICompatibleClient(
            client = get(),
            keyProvider = { get<SecureKeyStore>().readApiKey() }
        )
    }
    single<AiClient> { OpenAiCompatibleAiClient(get()) }
    viewModel { AiSettingsViewModel(get(), get()) }
    // ...
}
```

**Phase-22-Erweiterung:**
```kotlin
val aiModule = module {
    single { AiPromptCatalog() }

    // OpenAI-compat Pfad bleibt unverändert wired
    single {
        OpenAICompatibleClient(
            client = get(),
            keyProvider = {
                val active = get<SettingsRepository>().activeProvider.first()
                when (val cred = get<SecureKeyStore>().readCredential(active)) {
                    is Credential.ApiKey -> cred.value
                    else -> null
                }
            }
        )
    }
    single { OpenAiCompatibleAiClient(get()) }

    // NEU: AnthropicClient mit credentialProvider
    single {
        AnthropicClient(
            client = get(),
            credentialProvider = {
                get<SecureKeyStore>().readCredential(ProviderId.Anthropic)
            }
        )
    }
    single { AnthropicAiClient(get()) }

    // Dispatcher
    single<AiClient> {
        DispatchingAiClient(get(), get(), get())  // settings + openai-adapter + anthropic-adapter
    }

    single { SettingsMigration(get(), get()) }

    viewModel { AiSettingsViewModel(get(), get()) }
    // ... bestehende Use-Cases/VMs unverändert (AiClient-Port)
}
```

---

### `iosApp/iosApp/Info.plist` (MODIFIZIERT)

**Existierender Inhalt** (`Info.plist:4-25`):
```xml
<dict>
    <key>CADisableMinimumFrameDurationOnPhone</key>
    <true/>
    <key>BGTaskSchedulerPermittedIdentifiers</key>
    <array>
        <string>com.pumpernickel.ai_generation</string>
    </array>
    <!-- existing NSCameraUsageDescription etc. -->
    <key>UIBackgroundModes</key>
    <array>
        <string>location</string>
    </array>
</dict>
```

**Erweiterung (NEU)** — `CFBundleURLTypes`:
```xml
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLName</key>
        <string>com.pumpernickel.oauth</string>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>pumpernickel-oauth</string>
        </array>
    </dict>
</array>
```

(Konkrete Schema wird Researcher festlegen — `claude.ai` OAuth-Doc-Recherche).

---

### `androidApp/src/androidMain/AndroidManifest.xml` (MODIFIZIERT)

Siehe oben unter OAuthBrowserLauncher.android.kt — neuer `<intent-filter>` an MainActivity.

---

### Settings-UI (Android + iOS, MODIFIZIERT)

**Android-Analog** (`androidApp/.../ui/screens/AiSettingsScreen.kt:58-138`):
```kotlin
private val PROVIDER_PRESETS = listOf(
    "openai" to "OpenAI",
    "together" to "Together.AI",
    "openrouter" to "OpenRouter",
    "groq" to "Groq",
    "custom" to "Benutzerdefiniert"
)

@Composable
fun AiSettingsScreen(...) {
    // ...
    ExposedDropdownMenuBox(
        expanded = presetExpanded,
        onExpandedChange = { presetExpanded = it }
    ) {
        OutlinedTextField(
            value = PROVIDER_PRESETS.firstOrNull { it.first == providerPreset }?.second ?: providerPreset,
            // ...
        )
        ExposedDropdownMenu(expanded = presetExpanded, onDismissRequest = { presetExpanded = false }) {
            PROVIDER_PRESETS.forEach { (key, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.setProviderPreset(key); presetExpanded = false })
            }
        }
    }
}
```

→ Erweitern:
- Aus dem Dropdown wird eine **Provider-Liste mit Radio-Buttons** (Material `RadioButton` + `Row`-Items per `LazyColumn` oder `Column`)
- "+ Provider hinzufügen"-Button öffnet `AnthropicConnectSheet` (analog `AiPreviewSheet.kt` für Modal-Bottom-Sheets)
- Pro Provider: eigener Modell-Dropdown (gleiche `ExposedDropdownMenuBox`-Mechanik wie heute)
- API-Key-Status-Indikator pro Provider — bestehender Pattern in `AiSettingsScreen.kt:162-183`:
  ```kotlin
  if (apiKeyConfigured) {
      Icon(Icons.Filled.CheckCircle, ...)
      Text("Gespeichert")
  } else {
      Icon(Icons.Filled.Warning, ...)
      Text("Kein Schlüssel gespeichert")
  }
  ```
  → wird pro Provider gerendert (grüner Checkmark = aktiv + verbunden, gelbes Warning = verbunden aber nicht aktiv, etc.)

**iOS-Analog** (`iosApp/iosApp/Views/AI/AISettingsView.swift:82-108`):
```swift
private var providerSection: some View {
    Section {
        Picker("Anbieter", selection: $providerPreset) {
            ForEach(Self.presetLabels, id: \.key) { item in
                Text(item.label).tag(item.key)
            }
        }
        .pickerStyle(.menu)
        .onChange(of: providerPreset) { _, newValue in
            // ...
            viewModel.setProviderPreset(preset: newValue)
        }
    }
}
```

→ Erweitern auf List<ConnectedProvider> mit Radio-Selection (SwiftUI `List` + `selection: Binding`). "+ Provider hinzufügen" als `.toolbar`-Button oder Section-Footer-Button. Connect-Sheet als `.sheet(isPresented:)` mit `AnthropicConnectSheet` (eigene SwiftUI-View).

**Provider-Liste-State-Synchronisation** — siehe iOS Flow-Sync-Pattern (`AISettingsView.swift:74-77`):
```swift
.task { await observeProviderPreset() }
.task { await observeBaseUrl() }
.task { await observeModel() }
.task { await observeApiKeyConfigured() }
```

Pro neuer StateFlow im VM ein eigener `.task` (mit `asyncSequence(for:)` via `KMPNativeCoroutinesAsync`).

---

## Shared Patterns

### Pattern 1 — `expect class` + actual mit Suspend-Bridge

**Source:** `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/progresspic/PhotoCaptureLauncher.kt`
**Apply to:** `OAuthBrowserLauncher.kt`

```kotlin
expect class PhotoCaptureLauncher {
    suspend fun captureFromCamera(): ByteArray?
    suspend fun pickFromLibrary(): ByteArray?
}
```

Common-Surface ist `suspend` + nullable-return-on-cancel. iOS-actual nutzt `CompletableDeferred` + Volatile-Field-Retention für Delegate (`PhotoCaptureLauncher.ios.kt:60-104`). Android-actual nutzt ActivityResult-Bridge (`PhotoCaptureLauncherHost.kt`).

### Pattern 2 — Koin platform-Bindings via `platformModule`

**Source:** `shared/src/iosMain/.../di/PlatformModule.ios.kt:23-43`
**Apply to:** Neues `single<OAuthBrowserLauncher> { OAuthBrowserLauncher() }` (iOS) bzw. `single<OAuthBrowserLauncher> { OAuthBrowserLauncher(androidContext()) }` (Android)

iOS-Pattern:
```kotlin
actual val platformModule: Module = module {
    single<PhotoVault> { PhotoVault() }
    single<PhotoCaptureLauncher> { PhotoCaptureLauncher() }
    single<SecureKeyStore> { SecureKeyStore() }
    // ...
}
```

Android-Pattern (mit `androidContext()`):
```kotlin
actual val platformModule: Module = module {
    single<PhotoVault> { PhotoVault(androidContext()) }
    single<PhotoCaptureLauncher> { PhotoCaptureLauncher(androidContext()) }
    single<SecureKeyStore> { SecureKeyStore(androidContext()) }
    // ...
}
```

### Pattern 3 — iOS KoinHelper für Swift-Konsum

**Source:** `shared/src/iosMain/kotlin/com/pumpernickel/di/AiSettingsKoinHelper.kt`
**Apply to:** Falls für neue Provider-Sektion ein eigener helper benötigt wird; sonst der bestehende `AiSettingsKoinHelper` exposes erweiterten VM.

```kotlin
class AiSettingsKoinHelper {
    fun getAiSettingsViewModel(): AiSettingsViewModel =
        KoinPlatform.getKoin().get()
}
```

### Pattern 4 — @NativeCoroutinesState für Swift-Konsum

**Source:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt:19-37`
**Apply to:** Alle neuen StateFlows im erweiterten AiSettingsViewModel

```kotlin
@NativeCoroutinesState
val providerPreset: StateFlow<String> = settingsRepository.aiProviderPreset
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "openai")
```

### Pattern 5 — Error-Mapping über `AiError`-Sealed-Class

**Source:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt:24-48`
**Apply to:** AnthropicClient verwendet exakt dieselben `AiError`-Subtypen. Mapping in `AnthropicClient.mapHttpError(...)` analog zu `OpenAICompatibleClient.kt:201-208` — KEINE Anthropic-spezifischen Error-Klassen.

### Pattern 6 — HttpClient als Koin-`single`, geteilt zwischen allen Clients

**Source:** `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt:102` + `AiModule.kt:34`
**Apply to:** AnthropicClient nutzt denselben `get<HttpClient>()` — kein zweiter Client.

```kotlin
single { createHttpClient() }
// ...
single {
    OpenAICompatibleClient(
        client = get(),  // <-- der einzige HttpClient
        keyProvider = { ... }
    )
}
```

### Pattern 7 — Test-Stil für commonTest

**Source:** `shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/nutrition/OpenFoodFactsAdapterBrandRankingTest.kt`
**Apply to:** `SettingsMigrationTest.kt`, `AnthropicSseParserTest.kt`, `ProviderResolverTest.kt`

Pattern:
- `import kotlin.test.Test` + `kotlin.test.assertEquals` + `kotlin.test.assertTrue`
- Test-class pro behaviour-unit
- `@Test fun scenario_expectedOutcome()` Naming
- Kommentar-Block über jeder `@Test` erklärt was geprüft wird
- Fake-Implementations werden inline definiert oder in Sibling-File `Fake*.kt`

---

## No Analog Found

Files with no close match in the codebase (planner should use RESEARCH.md + Pattern-Komposition):

| File | Role | Data Flow | Reason / Empfehlung |
|------|------|-----------|---------------------|
| `SettingsMigration.kt` | data | batch / one-shot | Kein expliziter Migration-Coordinator existiert. Nutze `retroactiveApplied`-Flag-Pattern (`SettingsRepositoryImpl.kt:177-185`) als Inspiration; Trigger-Lifecycle ist Discretion. |
| `ProviderResolver` / `DispatchingAiClient` | infrastructure | event-driven dispatch | Keine bestehende Factory-Komponente, die Flow-basiert dispatcht. Plan-Empfehlung: simple Facade `DispatchingAiClient : AiClient` mit `settings.activeProvider.first()` pro Aufruf — vermeidet runBlocking + Koin-Module-Reload. |
| `AnthropicConnectSheet` (Compose + SwiftUI) | UI | request-response | `AiPreviewSheet`-Patterns für Modal-Sheets existieren — aber Connect-Sheet ist eigener Workflow (OAuth-Button vs Key-Paste-Tab). Researcher liefert Anthropic OAuth-Flow-Details. |

---

## Metadata

**Analog-Suchbereich:**
- `shared/src/commonMain/kotlin/com/pumpernickel/data/` (api + repository)
- `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/` (ai + progresspic für expect/actual)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/` (ai + repository)
- `shared/src/commonMain/kotlin/com/pumpernickel/di/`
- `shared/src/iosMain/kotlin/com/pumpernickel/` (PhotoCaptureLauncher.ios.kt, SecureKeyStore.ios.kt, PlatformModule.ios.kt, AiSettingsKoinHelper.kt)
- `shared/src/androidMain/kotlin/com/pumpernickel/`
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt`
- `iosApp/iosApp/Views/AI/AISettingsView.swift`
- `iosApp/iosApp/Info.plist`, `androidApp/src/androidMain/AndroidManifest.xml`
- `shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/nutrition/` (Test-Stil)

**Wichtige Erkenntnisse die das Planning beeinflussen:**

1. **`AiClient` Interface existiert bereits** (`infrastructure/ai/AiClient.kt`, Phase 20 Plan 07 / Smell 4) — CONTEXT.md's `AiInferenceClient`-Bezeichnung kann durch das bestehende `AiClient` ersetzt werden. Keine neue Interface-Datei nötig. Spart Aufwand und respektiert die Phase-20-Refaktorierung.

2. **`SecureKeyStore` liegt unter `infrastructure/ai/` nicht `domain/ai/`** (Phase 20 Refactor) — CONTEXT.md canonical_refs Pfade müssen angepasst werden. Korrekte Pfade:
   - `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.kt`
   - `shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.android.kt`
   - `shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.ios.kt`

3. **`SettingsRepository` interface liegt unter `domain/repository/`** (Phase 20 Refactor) — Implementation unter `data/repository/SettingsRepositoryImpl.kt`. Klassische Interface-Inversion ist nach Phase 20 etabliert.

4. **Android `infrastructure/ai/` statt `feature/ai/`** — Phase 20 Refactor hat Android `feature/` zu `infrastructure/` gefolded (siehe `STRUCTURE.md` § "androidMain package-layout inconsistency"). Aktueller Stand bestätigt: `androidMain/.../infrastructure/ai/SecureKeyStore.android.kt`.

5. **Existing AiClient port abstraction ist Provider-agnostisch** — Use-Cases (`WorkoutAiUseCase.kt:8, 17-23`) injecten bereits `AiClient`, nicht `OpenAICompatibleClient`. Provider-Switch ist im DI machbar OHNE Use-Case-Änderungen — **die existierende Architektur ist phase-22-ready**.

6. **`AiPromptCatalog.workoutSystemPrompt()` / `recipeSystemPrompt()`** liefert die Markdown-Prompts (D-22-10). Adapter `AnthropicAiClient` passt diesen String direkt an `MessagesRequest.system` — kein Prompt-Rewriting.

**Pattern-Extraktions-Datum:** 2026-05-19
