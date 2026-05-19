# Phase 22: Anthropic AI Provider - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-19
**Phase:** 22-anthropic-ai-provider-3-provider-neben-openai-together-mit-o
**Areas discussed:** Anthropic-Auth-Strategie, Client-Architektur, Provider/Modell-Picker UX, Multi-Provider-Koexistenz

---

## Anthropic-Auth-Strategie

### Sub-Frage 1: Welche Auth-Strategie für Anthropic?

| Option | Description | Selected |
|--------|-------------|----------|
| Claude Pro/Max OAuth (PKCE) | OAuth-Flow nachbauen wie Claude Code: ASWebAuthenticationSession (iOS) / CustomTabs (Android) → claude.ai/oauth/authorize → Bearer-Token gegen Subscription | (implizit ✓ via User-Rückfrage) |
| API-Key mit besserer UX | Direkte Anthropic-API mit x-api-key, Paste-Sheet mit Live-Validation | |
| Beides anbieten | OAuth primary + API-Key secondary | |

**User's Rückfrage:** "geht api key auch mit nur claude pro oder max 5x? habe keine api tokens"

**Antwort (Claude):** Nein — Claude Pro/Max und Console-API-Keys sind getrennte Billing-Streams. Pro/Max-Subscription wird **nur** über den OAuth-Flow (wie Claude Code) konsumiert. `api.anthropic.com` mit `x-api-key` braucht separates Konto + API-Credits.

**Konsequenz:** OAuth ist der einzige praktikable Pfad für User mit Pro/Max-Subscription.

### Sub-Frage 2: Soll der API-Key-Pfad trotzdem als Backup im UI erscheinen?

| Option | Description | Selected |
|--------|-------------|----------|
| Nur OAuth (Pro/Max) | Connect-Screen zeigt nur OAuth-Button. Console-User kriegen Error. | |
| OAuth + API-Key als Fallback | OAuth als Empfehlung/Default-Button, API-Key-Paste als Secondary-Action. Zwei Storage-Pfade, zwei Header-Strategien. | ✓ |

**User's choice:** OAuth + API-Key als Fallback
**Notes:** Zukunftssicher für spätere Console-User, Mehraufwand im Client trivial (nur Header-Wechsel).

---

## Client-Architektur

### Wie integrieren wir Anthropic in den bestehenden Client-Layer?

| Option | Description | Selected |
|--------|-------------|----------|
| Separater AnthropicClient + gemeinsames Interface | Interface AiInferenceClient mit chatCompletion + chatCompletionStreaming. OpenAICompatibleClient + AnthropicClient implementieren es separat. Koin wählt Impl zur Laufzeit. | ✓ |
| Anthropic-Adapter über OpenAI-Schema | Wrapper normalisiert Anthropic-API zu OpenAI-Chat-Completion-Format. Weniger Code-Duplikation aber tricky Mapping (Streaming-Events, system-handling). | |
| Sealed Class AiProvider mit Strategy-Pattern | sealed interface AiProvider mit Impls. Exhaustive when-branches, hilft bei 4. Provider. | |

**User's choice:** Separater AnthropicClient + gemeinsames Interface
**Notes:** Begründung implizit aus der Tabelle: Anthropic Messages-API ist nicht OpenAI-kompatibel (top-level system, andere SSE-Events, anthropic-version Header). Sauberer Pfad ohne Mapping-Bugs.

---

## Provider/Modell-Picker UX

### Wo lebt die Provider/Modell-Auswahl in der UI?

| Option | Description | Selected |
|--------|-------------|----------|
| Settings-only (1 globaler Aktiv-Provider) | Settings → AI-Provider: verbundene Provider als Liste mit Radio-Toggle, pro Provider ein Modell-Dropdown. Genau EIN aktiver Provider+Modell global. Kein Picker in Gen-Screens. | ✓ |
| Per-Action-Picker im Gen-Screen | Jeder AI-Gen-Screen hat oben kompakten Provider+Modell-Picker. Letzte Wahl pro Screen gemerkt. | |
| Default-pro-Task in Settings + Override im Gen-Screen | Separate Defaults für Workout-Gen und Recipe-Gen + Override-Sheet im Gen-Screen. | |

**User's choice:** Settings-only (1 globaler Aktiv-Provider) — Preview-Mock akzeptiert
**Notes:** Cleaner Gen-Screen, simple Settings-Switching. Matched User-Vision aus Memory `[[ai-provider-extension]]`.

---

## Multi-Provider-Koexistenz

### Wie strukturieren wir Storage + Migration der bestehenden OpenAI-Settings?

| Option | Description | Selected |
|--------|-------------|----------|
| Migrieren: bestehender openai.api.key wird zu ersten Eintrag in neuer Provider-Map | SecureKeyStore zu Map<ProviderId, Credential>. One-time Migration beim ersten Start: alter Key in neue Struktur, alter Slot gelöscht, Flag in DataStore. User merkt nichts. | ✓ |
| Wegwerfen — Demo-App, keine Migration | Reset auf neue Struktur, User muss OpenAI-Key neu paste'n. Spart Code, aber User-Reibung. | |
| Komplett neue Storage-Klasse + alten Key liegen lassen | Neue ProviderCredentialStore, alter Key bleibt als Fallback. Technical-Debt. | |

**User's choice:** Migrieren
**Notes:** Nutzerfreundlichste Variante. Migration muss idempotent sein + Unit-Tests in commonTest.

---

## Claude's Discretion

(Vom Workflow Claude überlassen — siehe CONTEXT.md `<decisions>` "Claude's Discretion"-Sektion)

- Naming der neuen Interfaces (`AiInferenceClient` vs Alternativen)
- Genaue Koin-Wiring-Strategie für Provider-Switch
- File-Layout: Interface in `domain/ai/` oder `data/api/` (Dependency-Rule beachten)
- Migration-Trigger: Application.onCreate vs lazy beim ersten Use-Case-Call
- UI-Polish-Details (Logos, Sheet-Animationen)
- Modell-Reihenfolge im Dropdown

## Deferred Ideas

Siehe CONTEXT.md `<deferred>`-Sektion — wichtigste:
- Per-Task-Defaults und Per-Action-Picker (bewusst gegen entschieden, kann in Folge-Phase nachgereicht werden)
- Anthropic-spezifische Features (Thinking, Citations, Cache-Control)
- Prompt-Optimierung für Anthropic
- Together-AI als eigener "verbundener Provider" mit dediziertem Slot
- Token-Refresh-Background-Job (WorkManager / BGTaskScheduler)
- OAuth für andere Provider
