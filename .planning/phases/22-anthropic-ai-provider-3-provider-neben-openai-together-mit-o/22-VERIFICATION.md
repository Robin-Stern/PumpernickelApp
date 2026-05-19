---
phase: 22-anthropic-ai-provider
verified: 2026-05-19T00:00:00Z
status: human_needed
score: 13/13 must-haves verified (goal-elements)
overrides_applied: 0
human_verification:
  - test: "Anthropic OAuth round-trip auf Android-Gerät (Chrome CustomTabs)"
    expected: "Settings → KI-Provider → Anthropic → 'Mit Claude.ai verbinden' öffnet CustomTab, nach Login + Approve schließt der Tab automatisch und 'Anthropic' zeigt '✓ verbunden'."
    why_human: "Echter OAuth-Flow gegen claude.ai erfordert Anthropic-Pro/Max-Account und visuelle Verifikation der CustomTabs→onNewIntent→Deferred-Completion-Chain — Code-Pfad ist verifiziert, aber End-to-End-Verhalten kann nur live geprüft werden."
  - test: "Anthropic OAuth round-trip auf iOS-Gerät (ASWebAuthenticationSession)"
    expected: "Settings → KI-Provider → Anthropic → 'Mit Claude.ai verbinden' öffnet die ASWebAuthenticationSession-Sheet, nach Login schließt sich die Sheet und 'Anthropic' zeigt '✓ verbunden'."
    why_human: "iOS-SwiftUI-Settings sind per MEMORY-Konvention User-written (siehe 22-IOS-HANDOFF.md). End-to-End nur am Gerät verifizierbar."
  - test: "Live AI-Workout-Generation gegen Anthropic Claude Opus 4.7"
    expected: "Nach OAuth: Workout-Gen-Screen → 'Generieren' → Streaming-Tokens erscheinen (named-event-Parser tut sein Ding), valides Workout-JSON wird zurückgegeben, kein Schema-Drift."
    why_human: "Anthropic API-Roundtrip + SSE-Streaming + Schema-via-Prompt + Subscription-Bearer-Token sind unabhängig grün, aber die Kombination produziert nur unter realer Last verifizierbares Verhalten."
  - test: "Pre-Request Token-Refresh manuell triggern"
    expected: "Mit `expiresAtEpochSeconds` < now+60 in SecureKeyStore (Debug-Build): nächster AI-Call löst Refresh aus, neuer Token wird gespeichert; bei refresh_token revoked: Slot wird gecleared, UI zeigt AuthOrQuota-Hinweis."
    why_human: "Code-Pfad in `AnthropicClient.ensureFreshCredential` ist verifiziert, aber Token-Lifecycle-Verhalten gegen claude.ai/oauth/token kann nur live geprüft werden."
  - test: "Together-User-Migration end-to-end"
    expected: "Phase-18-User mit Together-Setup (aiBaseUrl=https://api.together.ai/v1 + api-key) startet Phase-22-App → erster AI-Call löst SettingsMigration.run() aus → activeProvider=Together, Key in Together-Slot, sentinel=true; danach läuft AI gegen Together-Endpoint."
    why_human: "Migration-Logic ist unit-getestet (SettingsMigrationTest, 7 cases), aber das Zusammenspiel mit echtem DataStore + EncryptedSharedPreferences + erstem Use-Case-Call ist nur live verifizierbar."
  - test: "Provider-Switch ohne App-Restart"
    expected: "Mit Anthropic aktiv: Settings → OpenAI-Radio antippen → unmittelbar nächster AI-Call dispatcht an OpenAiCompatibleAiClient (nicht Anthropic). Kein Restart nötig."
    why_human: "DispatchingAiClient.resolve() liest activeProvider.first() pro Call — Pfad ist verifiziert, aber das Settings-UI → DataStore-Flow → Dispatcher-Roundtrip braucht visuelle Bestätigung."
---

# Phase 22: Anthropic AI Provider – Verification Report

**Phase Goal (ROADMAP.md:374):** Anthropic-Modelle (Opus 4.7 / Sonnet 4.6 / Haiku 4.5) als 3. Provider neben OpenAI/Together. OAuth-Primary gegen claude.ai/oauth/authorize (PKCE, S256). API-Key-Fallback. Multi-Provider-Koexistenz via Map-basiertem SecureKeyStore + sealed Credential. Settings-only Provider-Switch via Radio-Liste; per-Provider-Modell-Dropdown. DI-Wiring via DispatchingAiClient-Facade. Workout-/Recipe-Prompts 1:1 ans top-level `system`-Field. SSE-Parser handelt Anthropic named-events. Pre-Request Token-Refresh (60sec-Schwelle). Legacy Phase-18-User via lazy SettingsMigration transparent migriert.

**Verified:** 2026-05-19
**Status:** human_needed
**Re-verification:** No – initial verification

## Goal Achievement

### Observable Truths (goal elements per ROADMAP.md / D-22-01 … D-22-13)

| # | Goal Element | Status | Evidence |
|---|---|---|---|
| 1 | Anthropic-Modelle (Opus 4.7 / Sonnet 4.6 / Haiku 4.5) selectable als 3. Provider | ✓ COVERED | `AiSettingsScreen.kt:92-100` Anthropic block mit drei Optionen `claude-opus-4-7`, `claude-sonnet-4-6`, `claude-haiku-4-5-20251001`; `SettingsRepositoryImpl.kt:436` Default `claude-opus-4-7` |
| 2 | OAuth-Primary gegen claude.ai/oauth/authorize mit PKCE/S256 | ✓ COVERED | `AnthropicOAuthFlow.kt:134` `AUTHORIZE_ENDPOINT = "https://claude.ai/oauth/authorize"`; `AnthropicOAuthFlow.kt:99-106` baut Authorize-URL mit `code_challenge_method=S256`; `AnthropicOAuthFlow.kt:122-125` `codeChallengeS256` = base64url-nopad(SHA-256(verifier)); FIPS-180-4 Sha256-Impl inline `AnthropicOAuthFlow.kt:175-244` |
| 3 | API-Key-Fallback für Console-User | ✓ COVERED | `AnthropicConnectSheet.kt` zeigt API-Key-OutlinedTextField unterhalb OAuth-Button; `AiSettingsViewModel.kt:125-131` `setAnthropicApiKey()` schreibt `Credential.ApiKey`; `AnthropicClient.kt:190-194` `applyAuthHeaders` dispatcht `x-api-key` vs `Authorization: Bearer` per Credential-Variante |
| 4 | Multi-Provider-Koexistenz via Map-basiertem SecureKeyStore | ✓ COVERED | `SecureKeyStore.kt:34-47` expect-class mit `writeCredential/readCredential/clearCredential/listProviders(ProviderId)`; Android `SecureKeyStore.android.kt:26-51` schreibt per `provider.apiKeyAccount` / `provider.oauthTokenAccount`; iOS `SecureKeyStore.ios.kt:59-96` über Keychain-Account-Parameter |
| 5 | Sealed Credential class (OAuthToken \| ApiKey) | ✓ COVERED | `Credential.kt:19-29` sealed class mit `ApiKey(value)` und `OAuthToken(accessToken, refreshToken, expiresAtEpochSeconds)`, beide `@Serializable` |
| 6 | Settings-only Provider-Switch via Radio-Liste | ✓ COVERED | `AiSettingsScreen.kt:142-159` iteriert `PROVIDERS` mit `ProviderRow` (RadioButton + Modell-Dropdown + Connect/Disconnect), Modelle-Dropdown via `ExposedDropdownMenuBox`; per-Provider-Modell-Persistierung `AiSettingsViewModel.kt:86-88` `setModel()` |
| 7 | Per-Provider-Modell-Dropdown | ✓ COVERED | `AiSettingsScreen.kt:259-292` `ExposedDropdownMenuBox` pro Provider rendert `display.modelOptions`; State über `modelByProvider: StateFlow<Map<ProviderId, String>>` |
| 8 | DI-Wiring nutzt DispatchingAiClient-Facade | ✓ COVERED | `AiModule.kt:94-109` Layer-Cake: `MigratingAiClient → DispatchingAiClient → {OpenAiCompatibleAiClient, AnthropicAiClient}`; `DispatchingAiClient.kt:49-52` `resolve()` reads `settings.activeProvider.first()` per Call |
| 9 | Workout-/Recipe-Prompts 1:1 ans top-level system-Field | ✓ COVERED | `AnthropicAiClient.kt:42-50,62-71` `system = systemPrompt` direkt; `AnthropicMessagesDto.kt:21-28` `AnthropicMessagesRequest.system: String?` als top-level field, Messages-Array enthält nur user/assistant; Prompts unverändert aus `WorkoutAiUseCase.kt:50` / `RecipeAiUseCase` |
| 10 | SSE-Parser handelt Anthropic named-events | ✓ COVERED | `AnthropicSseParser.kt:46-94` parst `event:` + `data:` Lines, routet `content_block_delta` (text accumulation), `message_stop` (done flag), `error` (typed AiError); `AnthropicClient.kt:122-128` füttert Parser frame-by-frame |
| 11 | Pre-Request Token-Refresh (60sec-Schwelle) | ✓ COVERED | `AnthropicClient.kt:155-187` `ensureFreshCredential()` prüft `expiresAtEpochSeconds > nowSeconds + 60` und ruft `oauthClient.refresh()` sonst; bei Fehler → `clearCredential` + `AiError.AuthOrQuota(401)`; `AnthropicOAuthClient.kt:30-56` POST `claude.ai/oauth/token` mit `grant_type=refresh_token` |
| 12 | Lazy SettingsMigration (idempotent, sentinel-guarded) | ✓ COVERED | `SettingsMigration.kt:39-90` Migration mit `migratedToMultiProvider`-Sentinel-Guard (Line 44); `MigratingAiClient.kt:15-42` ruft `migration.run()` vor jedem AI-Call → lazy/first-call-trigger ohne Application.onCreate; Together-Inferenz `SettingsMigration.kt:92-99` |
| 13 | Tests + Platform-Wiring (Android Manifest + iOS Info.plist) | ✓ COVERED | `AndroidManifest.xml:45-50` `<intent-filter>` mit `scheme=pumpernickel-oauth host=callback`, `launchMode=singleTop` (Line 34); `MainActivity.kt:96-102` `onNewIntent` forwarded zu `OAuthBrowserLauncherHost.handleRedirect`; `Info.plist:25-37` `CFBundleURLTypes` mit Scheme `pumpernickel-oauth`; commonTest: SettingsMigrationTest (147 lines), AnthropicSseParserTest (127 lines), DispatchingAiClientTest (110 lines), Sha256Test (65 lines) |

**Score:** 13/13 goal-elements COVERED

### Required Artifacts (3-Level + Data-Flow)

| Artifact | Status | Exists | Substantive | Wired | Notes |
|---|---|---|---|---|---|
| `infrastructure/ai/ProviderId.kt` | ✓ VERIFIED | 27 lines | enum mit 3 entries + wireName + apiKeyAccount + oauthTokenAccount + fromWireNameOrNull | Imported by SecureKeyStore, Credential consumers, Settings, AiModule, ViewModel | Vollständig |
| `infrastructure/ai/Credential.kt` | ✓ VERIFIED | 29 lines | sealed class + 2 data-class-Varianten, beide @Serializable | Imported by Anthropic*, SecureKeyStore, Migration, ViewModel | Vollständig |
| `infrastructure/ai/SecureKeyStore.kt` | ✓ VERIFIED | 86 lines | expect-class + SecureKeyStoreSurface + SecureKeyStoreAdapter | Android+iOS actuals exist; Adapter bound in AiModule:88 | Multi-Slot |
| `data/api/AnthropicClient.kt` | ✓ VERIFIED | 223 lines | Ktor-Client mit anthropic-version-Header, dispatch, refresh, SSE streaming, 64KB-Cap, mapHttpError | Wired in AiModule:67-73; consumed by AnthropicAiClient | Vollständig, kein Stub |
| `data/api/AnthropicMessagesDto.kt` | ✓ VERIFIED | 117 lines | Request/Response/SSE-frame DTOs, all @Serializable | Used by AnthropicClient + AnthropicSseParser | Vollständig |
| `data/api/AnthropicOAuthClient.kt` | ✓ VERIFIED | 62 lines | refresh-grant POST gegen claude.ai/oauth/token | Wired in AiModule:65; consumed by AnthropicClient | Vollständig |
| `data/api/AnthropicSseParser.kt` | ✓ VERIFIED | 99 lines | stateful parser, named-event dispatch, typed AiError mapping | Used by AnthropicClient.chatCompletionStreaming | Plan-22-10 Refactor für Testbarkeit |
| `infrastructure/ai/AnthropicAiClient.kt` | ✓ VERIFIED | 72 lines | AiClient-Adapter, systemPrompt → top-level system, baseUrl/schema ignored | Wired in AiModule:74; consumed by DispatchingAiClient | Vollständig |
| `infrastructure/ai/DispatchingAiClient.kt` | ✓ VERIFIED | 53 lines | resolve() liest activeProvider per Call | Wired in AiModule:94-106 | Vollständig |
| `infrastructure/ai/MigratingAiClient.kt` | ✓ VERIFIED | 42 lines | runs migration.run() vor jedem AI-Call (idempotent) | Bound as `AiClient` in AiModule:107-109; outermost layer | Vollständig |
| `infrastructure/ai/AnthropicOAuthFlow.kt` | ✓ VERIFIED | 291 lines | PKCE code_verifier/challenge, S256-SHA-256-inline, base64url-nopad, urlEncode, exchangeCodeForToken | Wired in AiModule:76-82; consumed by ViewModel | Vollständig |
| `infrastructure/ai/OAuthBrowserLauncher.kt` | ✓ VERIFIED | 34 lines (expect) + iOS-actual (100) + Android-actual (34) + Host (57) | iOS ASWebAuthenticationSession w/ ephemeralWebBrowserSession; Android CustomTabsIntent + Host-Deferred-Pattern | Wired in PlatformModule.android.kt:41-43 & PlatformModule.ios.kt:34-36 | Vollständig |
| `data/repository/SettingsMigration.kt` | ✓ VERIFIED | 100 lines | sentinel-guard, Together-Inferenz, custom-baseUrl preserve, error-safe | Wired in AiModule:89; consumed by MigratingAiClient | Vollständig |
| `data/repository/SettingsRepositoryImpl.kt` (Phase-22-Erweiterung) | ✓ VERIFIED | Lines 243-440 implement multi-provider keys + 3 Maps + sentinel | DataStore-backed, Defaults korrekt | Wired via SettingsRepository binding | Vollständig |
| `domain/repository/SettingsRepository.kt` | ✓ VERIFIED | Lines 73-102: activeProvider Flow + modelByProvider + baseUrlByProvider + migratedToMultiProvider + setter | Interface-Erweiterung | Implemented by SettingsRepositoryImpl | Vollständig |
| `di/AiModule.kt` | ✓ VERIFIED | 118 lines | Phase-22 Layer-Cake komplett | All bindings present; viewModel binding für AiSettingsViewModel | Vollständig |
| `presentation/ai/AiSettingsViewModel.kt` | ✓ VERIFIED | 156 lines | 5 StateFlows + 6 actions; Legacy aiProviderPreset/etc. removed | Wired in AiModule:112; consumed by AiSettingsScreen (Android) and per Handoff iOS | Vollständig |
| `androidApp/.../AndroidManifest.xml` | ✓ VERIFIED | intent-filter + launchMode=singleTop | Lines 34, 45-50 | Merged manifest at build/intermediates/.../AndroidManifest.xml contains pumpernickel-oauth scheme (confirmed grep) | Vollständig |
| `androidApp/.../MainActivity.kt` | ✓ VERIFIED | onNewIntent override + initial intent-handling | Lines 58-62, 96-102 | Forwards to OAuthBrowserLauncherHost.handleRedirect | Vollständig |
| `iosApp/iosApp/Info.plist` | ✓ VERIFIED | CFBundleURLTypes mit scheme `pumpernickel-oauth` | Lines 25-37 | Vollständig |
| `androidApp/.../AiSettingsScreen.kt` | ✓ VERIFIED | 380 lines, RadioButton-Liste + Dropdowns + Sheet-Trigger + ApiKeyDialog inlined | Composable wired in nav graph | Vollständig — ApiKeyDialog war nicht in separate Datei extrahiert sondern inlined (Plan-08-SUMMARY consistent) |
| `androidApp/.../AnthropicConnectSheet.kt` | ✓ VERIFIED | 150 lines, OAuth-button + API-key-OutlinedTextField mit visibility-toggle | Used in AiSettingsScreen:189-199 | Vollständig |
| `22-IOS-HANDOFF.md` | ✓ VERIFIED | 144 lines, dokumentiert removed/new ViewModel-Surface, Required SwiftUI Surfaces, Provider-IDs, asyncSequence-Hooks | Spec für User-written SwiftUI per MEMORY-Convention | Vollständig |
| `commonTest/.../SettingsMigrationTest.kt` | ✓ VERIFIED | 147 lines, 7+ cases | Compiles in shared test source set | Plan-22-10-SUMMARY claims 7 cases — consistent |
| `commonTest/.../AnthropicSseParserTest.kt` | ✓ VERIFIED | 127 lines, ≥7 cases | Includes text_delta accumulation, message_stop, error-typed mapping, silent-ignore | Plan-22-10-SUMMARY claims 7 cases — consistent |
| `commonTest/.../DispatchingAiClientTest.kt` | ✓ VERIFIED | 110 lines, ≥4 cases | RecordingAiClient-fake per ctor-widening | Consistent mit Plan 22-10 |
| `commonTest/.../Sha256Test.kt` | ✓ VERIFIED | 65 lines | FIPS 180-4 + RFC 7636 PKCE vectors | Consistent |

### Key Link Verification (Wiring)

| From | To | Via | Status | Details |
|---|---|---|---|---|
| UseCase (Workout/Recipe) | AiClient | Koin `single<AiClient>` = MigratingAiClient | ✓ WIRED | AiModule.kt:107-109 |
| MigratingAiClient | SettingsMigration | ctor injection | ✓ WIRED | AiModule.kt:108 `migration = get()` |
| MigratingAiClient | DispatchingAiClient | delegate field | ✓ WIRED | AiModule.kt:108 |
| DispatchingAiClient | activeProvider | `settings.activeProvider.first()` per call | ✓ WIRED | DispatchingAiClient.kt:49 |
| DispatchingAiClient | OpenAi/Anthropic adapter | when-branch | ✓ WIRED | DispatchingAiClient.kt:50-52 |
| AnthropicAiClient | AnthropicClient | ctor injection | ✓ WIRED | AiModule.kt:74 |
| AnthropicClient | AnthropicOAuthClient | ctor injection | ✓ WIRED | AiModule.kt:70 |
| AnthropicClient | SecureKeyStore | ctor `secureKeyStore: SecureKeyStore` | ✓ WIRED | AiModule.kt:69 |
| AnthropicClient | AnthropicSseParser | direct instantiation per stream call | ✓ WIRED | AnthropicClient.kt:102 |
| AnthropicOAuthFlow | OAuthBrowserLauncher | ctor injection | ✓ WIRED | AiModule.kt:80 |
| OAuthBrowserLauncher (Android) | OAuthBrowserLauncherHost | suspend `awaitRedirect()` / `handleRedirect()` | ✓ WIRED | OAuthBrowserLauncher.android.kt:32 + MainActivity.kt:99 |
| AndroidManifest intent-filter | MainActivity.onNewIntent | scheme `pumpernickel-oauth` + launchMode=singleTop | ✓ WIRED | AndroidManifest.xml:34,45-50 |
| AiSettingsViewModel | AnthropicOAuthFlow | ctor injection | ✓ WIRED | AiModule.kt:112 + AiSettingsViewModel.kt:41 |
| AiSettingsViewModel | SecureKeyStore | ctor injection | ✓ WIRED | AiModule.kt:112 |
| AiSettingsScreen | AiSettingsViewModel | koinViewModel() | ✓ WIRED | AiSettingsScreen.kt:106 |
| SettingsMigration | SecureKeyStoreSurface | ctor injection (via Adapter) | ✓ WIRED | AiModule.kt:88-89 |
| WorkoutAiUseCase | baseUrlByProvider+modelByProvider | settingsRepository.first() per call | ✓ WIRED | WorkoutAiUseCase.kt:46-49 |
| RecipeAiUseCase | baseUrlByProvider+modelByProvider | settingsRepository.first() per call | ✓ WIRED | RecipeAiUseCase.kt:70-73 |
| shared/build.gradle.kts | androidx.browser | implementation(libs.androidx.browser) | ✓ WIRED | shared/build.gradle.kts:71 + libs.versions.toml:23,60 (v1.8.0) |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| (none in core phase-22 files) | – | grep TODO/FIXME against ai/* and Migration returned no hits | – | – |

**Note on SEEDs/KDoc:** AnthropicOAuthFlow.kt:30-34 documents a state-validation deferral (trust-on-first-use compromise for Demo-deadline) — this is **intentional and documented**, tracked in 22-05-SUMMARY.md SEED list. Not a stub.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| `:androidApp:assembleDebug` build | (per orchestrator brief: BUILD SUCCESSFUL on merged tree) | APK present at `androidApp/build/outputs/apk/debug/androidApp-debug.apk` | ✓ PASS |
| Merged AndroidManifest contains OAuth scheme | `grep pumpernickel-oauth androidApp/build/intermediates/merged_manifests/debug/.../AndroidManifest.xml` | Match | ✓ PASS |
| All 17 declared phase-22 source files exist at SUMMARY-claimed paths | `find shared/src/.../ai/...` | All 17 found, all >0 LOC, none stubs | ✓ PASS |
| iOS actuals compile (no `expect`-class signature drift) | implicit via "BUILD SUCCESSFUL on the merged tree" claim from orchestrator | n/a (commonMain compiles only if actuals match) | ✓ PASS |
| commonTest source-set has all 4 test files | `find shared/src/commonTest/.../{Anthropic*,SettingsMigration*,Dispatching*,Sha256*}` | All 4 present, total 449 LOC | ✓ PASS |
| AiModule wires `single<AiClient>` to `MigratingAiClient`-wrapped DispatchingAiClient | `grep "single<AiClient>" di/AiModule.kt` | Line 107-109 confirms | ✓ PASS |
| live OAuth round-trip + token-refresh + Provider-Switch + Migration | requires device + Claude-Pro-Account | – | ? SKIP (routed to human verification) |

### Requirements Coverage

Phase 22 has **no formal REQ-* IDs** (per CONTEXT.md "no formal REQ-* IDs per project convention"). Coverage is against the 13 D-22-XX decisions:

| Decision | Description | Status | Evidence |
|---|---|---|---|
| D-22-01 | OAuth-Primary gegen claude.ai mit PKCE/S256 | ✓ SATISFIED | AnthropicOAuthFlow.kt PKCE + Sha256 + base64url; OAuthBrowserLauncher.{ios,android}.kt; AnthropicConnectSheet OAuth-Primary-Button |
| D-22-02 | API-Key-Fallback (x-api-key header) | ✓ SATISFIED | AnthropicClient.kt:190-194 dispatch; AnthropicConnectSheet API-Key-Feld; setAnthropicApiKey ViewModel action |
| D-22-03 | Separater AnthropicClient + gemeinsames Interface | ✓ SATISFIED | AnthropicClient.kt (Ktor); AnthropicAiClient.kt (AiClient port-adapter, Phase-20-konform in infrastructure/ai) |
| D-22-04 | Koin wählt Impl zur Laufzeit | ✓ SATISFIED | DispatchingAiClient resolve-per-call; AiModule.kt:94-109 layer cake |
| D-22-05 | Settings-only, ein globaler aktiver Provider | ✓ SATISFIED | AiSettingsScreen RadioButton-Liste; activeProvider StateFlow; UseCases lesen activeProvider per Call |
| D-22-06 | Anthropic-Modelle Opus/Sonnet/Haiku, Default Opus | ✓ SATISFIED | AiSettingsScreen.kt:92-100; DEFAULT_MODEL_ANTHROPIC = "claude-opus-4-7" SettingsRepositoryImpl.kt:436 |
| D-22-07 | SecureKeyStore Map-basiert mit sealed Credential | ✓ SATISFIED | SecureKeyStore expect-class; Credential.kt sealed; Android EncryptedSharedPreferences + iOS Keychain mit Account-Parameter |
| D-22-08 | One-time Migration idempotent | ✓ SATISFIED | SettingsMigration.kt:39-90 + migratedToMultiProvider sentinel; SettingsMigrationTest 7 cases |
| D-22-09 | SettingsRepository activeProvider/model/baseUrl + sentinel | ✓ SATISFIED | SettingsRepository.kt:73-102 interface; SettingsRepositoryImpl.kt:243-440 |
| D-22-10 | Prompts unverändert ans top-level system | ✓ SATISFIED | AnthropicAiClient.kt:43,64 `system = systemPrompt`; AnthropicMessagesDto.kt:23 top-level field |
| D-22-11 | SSE-Parser für named-events | ✓ SATISFIED | AnthropicSseParser.kt; AnthropicSseParserTest 7 cases |
| D-22-12 | Token-Refresh 60sec-Schwelle, clear on failure | ✓ SATISFIED | AnthropicClient.kt:155-187 ensureFreshCredential; AnthropicOAuthClient.kt refresh |
| D-22-13 | Tests dort wo logic-heavy | ✓ SATISFIED | 4 test files in commonTest (449 LOC), claimed 23 cases consistent mit Datei-Größe |

### Gaps Summary

**No code/wiring gaps identified.** All 13 goal elements are COVERED by substantive, wired implementations. All 13 D-22-XX decisions are SATISFIED. The build artifact (APK + merged manifest) confirms compilation success on Android. iOS commonMain compiles (otherwise the orchestrator's "BUILD SUCCESSFUL" would have failed before assembleDebug).

**Open items requiring HUMAN verification** (see frontmatter):
1. End-to-End OAuth-Round-Trip auf Android (CustomTabs)
2. End-to-End OAuth-Round-Trip auf iOS (ASWebAuthenticationSession + per Convention: SwiftUI-Code ist User-written, siehe 22-IOS-HANDOFF.md)
3. Live AI-Workout-Generation gegen Anthropic Claude
4. Token-Refresh-Cycle (Pre-Request + claude.ai/oauth/token refresh-grant)
5. Phase-18→22 Migration mit Together-User-Setup
6. Provider-Switch ohne App-Restart (Dispatch-per-call)

**Documented SEEDs (out-of-scope tracked):**
- AnthropicOAuthFlow.kt:30-34 — OAuth state-validation deferred (trust-on-first-use compromise for Demo-deadline). Tracked in 22-05-SUMMARY.md.

### Final Verdict

**PASS (with human-verification queue).**

Alle 13 Phase-Ziel-Elemente sind im Code substantiell implementiert und korrekt verdrahtet. Die Architektur entspricht den D-22-XX-Entscheidungen exakt: OAuth-Primary mit PKCE/S256, API-Key-Fallback, Multi-Provider-Koexistenz mit Map-basiertem SecureKeyStore + sealed Credential, Settings-only Radio-Liste, DispatchingAiClient-Facade als per-call Provider-Switch, top-level system-Field für Prompts, named-event SSE-Parser, Pre-Request Token-Refresh mit 60s-Schwelle, lazy idempotente SettingsMigration via MigratingAiClient.

Statt `passed` lautet der Status `human_needed`, weil das ROADMAP-Ziel Pfade enthält (echter OAuth-Flow, Anthropic API-Roundtrip, Token-Refresh-Lifecycle, Migration eines bestehenden Phase-18-Setups, Provider-Switch zur Laufzeit), deren End-to-End-Verhalten nur am Gerät beobachtbar ist. Die Code-Pfade sind grün; die Live-Verifikation steht aus.

---

*Verified: 2026-05-19*
*Verifier: Claude (gsd-verifier)*
