import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

/// BYOK Settings — Phase 22 multi-provider model (D-22-05).
///
/// Wraps the shared AiSettingsViewModel via AiSettingsKoinHelper. Three
/// providers: OpenAI, Together.AI, Anthropic. Anthropic exposes an OAuth
/// primary path plus an API-key fallback. No baseUrl section — all providers
/// use their official endpoints.
struct AISettingsView: View {
    private let viewModel = AiSettingsKoinHelper().getAiSettingsViewModel()

    /// Internal state: holds the active ProviderId's wireName ("openai" /
    /// "together" / "anthropic").
    @State private var providerPreset: String = "openai"
    /// Quick 260518-f2h — true while the .task observer is pushing a value from
    /// the shared StateFlow into the @State. Prevents the Picker's `.onChange`
    /// from re-calling setActiveProvider (which would silently wipe per-provider
    /// state) when the View re-enters and catches up to the actual stored value.
    @State private var isSyncingProviderFromFlow: Bool = false

    /// Per-provider model map keyed by wireName. Populated by modelByProviderFlow.
    @State private var modelByProvider: [String: String] = [:]
    /// Saved model name for the currently active provider.
    @State private var model: String = "gpt-4o-mini"
    @State private var modelDraft: String = ""

    /// Wire names of providers that have credentials stored.
    @State private var connectedProvidersWireNames: Set<String> = []

    @State private var lastError: String? = nil

    @State private var keyDraft: String = ""
    @State private var keyVisible: Bool = false

    @FocusState private var focusedField: Field?
    @Environment(\.dismiss) private var dismiss

    private enum Field { case key, model }

    private static let presetLabels: [(key: String, label: String)] = [
        ("openai", "OpenAI"),
        ("together", "Together.AI"),
        ("anthropic", "Anthropic")
    ]

    // MARK: - Computed

    /// True iff the active provider has any credential stored.
    private var apiKeyConfigured: Bool {
        connectedProvidersWireNames.contains(providerPreset)
    }

    private var isAnthropic: Bool { providerPreset == "anthropic" }

    private var providerLabel: String {
        Self.presetLabels.first(where: { $0.key == providerPreset })?.label ?? providerPreset
    }

    // MARK: - Body

    var body: some View {
        Form {
            providerSection
            apiKeySection
            modelSection
        }
        .navigationTitle("KI-Einstellungen")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Fertig") { saveAllAndDismiss() }
                    .bold()
            }
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Fertig") { commitFocusedField() }
                    .bold()
            }
        }
        .task { await observeProviderPreset() }
        .task { await observeModelByProvider() }
        .task { await observeConnectedProviders() }
        .task { await observeLastError() }
    }

    // MARK: - Sections

    private var providerSection: some View {
        Section {
            Picker("Anbieter", selection: $providerPreset) {
                ForEach(Self.presetLabels, id: \.key) { item in
                    Text(item.label).tag(item.key)
                }
            }
            .pickerStyle(.menu)
            .onChange(of: providerPreset) { _, newValue in
                // Guard against initial flow→@State sync re-firing setActiveProvider
                // on every view re-entry (Quick 260518-f2h root cause pattern).
                if isSyncingProviderFromFlow {
                    isSyncingProviderFromFlow = false
                    return
                }
                if let providerId = ProviderId.companion.fromWireNameOrNull(s: newValue) {
                    viewModel.setActiveProvider(provider: providerId)
                }
                keyDraft = ""
                keyVisible = false
                // Sync model field to the newly selected provider's saved model.
                model = modelByProvider[newValue] ?? defaultModel(for: newValue)
                modelDraft = model
            }
        } header: {
            Text("Anbieter")
        } footer: {
            Text("Beim Wechsel des Anbieters wird der bisherige API-Schlüssel nicht automatisch gelöscht — jeder Anbieter hat seinen eigenen Schlüssel-Slot.")
                .font(.caption2)
        }
    }

    @ViewBuilder
    private var apiKeySection: some View {
        if isAnthropic {
            anthropicApiKeySection
        } else {
            standardApiKeySection
        }
    }

    private var standardApiKeySection: some View {
        Section {
            HStack {
                Group {
                    if keyVisible {
                        TextField("sk-…", text: $keyDraft)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled(true)
                    } else {
                        SecureField("sk-…", text: $keyDraft)
                    }
                }
                .focused($focusedField, equals: .key)
                .submitLabel(.done)
                .onSubmit { saveKeyDraft() }

                Button {
                    keyVisible.toggle()
                } label: {
                    Image(systemName: keyVisible ? "eye.slash" : "eye")
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(keyVisible ? "Schlüssel verbergen" : "Schlüssel zeigen")
            }

            statusRow

            errorRow

            HStack(spacing: 12) {
                Button(action: saveKeyDraft) {
                    Text("Speichern")
                }
                .buttonStyle(.borderedProminent)
                .disabled(keyDraft.trimmingCharacters(in: .whitespaces).isEmpty)

                Button(role: .destructive) {
                    viewModel.disconnect(provider: providerIdForCurrent())
                    keyDraft = ""
                    keyVisible = false
                } label: {
                    Text("Löschen")
                }
                .buttonStyle(.bordered)
                .disabled(!apiKeyConfigured)
            }
        } header: {
            Text("API-Schlüssel · \(providerLabel)")
        } footer: {
            Text("Wird nur lokal im Schlüsselbund abgelegt (kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly). Nie an einen Server gesendet.")
                .font(.caption2)
        }
    }

    private var anthropicApiKeySection: some View {
        Section {
            HStack {
                Group {
                    if keyVisible {
                        TextField("sk-ant-…", text: $keyDraft)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled(true)
                    } else {
                        SecureField("sk-ant-…", text: $keyDraft)
                    }
                }
                .focused($focusedField, equals: .key)
                .submitLabel(.done)
                .onSubmit { saveAnthropicKeyDraft() }

                Button {
                    keyVisible.toggle()
                } label: {
                    Image(systemName: keyVisible ? "eye.slash" : "eye")
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(keyVisible ? "Schlüssel verbergen" : "Schlüssel zeigen")
            }

            statusRow

            errorRow

            HStack(spacing: 12) {
                Button(action: saveAnthropicKeyDraft) {
                    Text("Speichern")
                }
                .buttonStyle(.borderedProminent)
                .disabled(keyDraft.trimmingCharacters(in: .whitespaces).isEmpty)

                Button(role: .destructive) {
                    viewModel.disconnect(provider: ProviderId.anthropic)
                    keyDraft = ""
                    keyVisible = false
                } label: {
                    Text("Löschen")
                }
                .buttonStyle(.bordered)
                .disabled(!apiKeyConfigured)
            }
        } header: {
            Text("API-Schlüssel · Anthropic")
        } footer: {
            Text("API-Key aus console.anthropic.com einfügen. Wird nur lokal im Schlüsselbund gespeichert.")
                .font(.caption2)
        }
    }

    /// Status row shared between standard and Anthropic sections.
    private var statusRow: some View {
        HStack(spacing: 8) {
            Image(systemName: apiKeyConfigured ? "checkmark.seal.fill" : "exclamationmark.triangle")
                .foregroundColor(apiKeyConfigured ? .green : .orange)
            VStack(alignment: .leading, spacing: 2) {
                Text(apiKeyConfigured ? "Verbunden mit \(providerLabel)" : "Kein Schlüssel für \(providerLabel)")
                    .font(.subheadline.weight(.medium))
                    .foregroundColor(apiKeyConfigured ? .green : .orange)
                if !apiKeyConfigured {
                    Text(isAnthropic ? "OAuth-Verbindung oder API-Key oben eintippen." : "Schlüssel oben eintippen und auf Speichern tippen.")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
        }
    }

    /// Error row — shown only when lastError is non-nil.
    @ViewBuilder
    private var errorRow: some View {
        if let error = lastError {
            HStack {
                Text(error)
                    .font(.caption)
                    .foregroundColor(.red)
                Spacer()
                Button("Schließen") { viewModel.clearError() }
                    .font(.caption)
                    .buttonStyle(.borderless)
            }
        }
    }

    private var modelSection: some View {
        Section {
            TextField("gpt-4o-mini", text: $modelDraft)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .focused($focusedField, equals: .model)
                .submitLabel(.done)
                .onSubmit { commitModelDraft() }
            Button("Modell speichern") { commitModelDraft() }
                .disabled(modelDraft.trimmingCharacters(in: .whitespaces).isEmpty
                          || modelDraft == model)

            modelSuggestionsForCurrentProvider
        } header: {
            Text("Modell")
        } footer: {
            Text("Modellname tippen oder einen Vorschlag wählen. Falsche Namen führen zu Timeouts oder leeren Antworten.")
                .font(.caption2)
        }
    }

    @ViewBuilder
    private var modelSuggestionsForCurrentProvider: some View {
        let suggestions = Self.modelSuggestions[providerPreset] ?? []
        if !suggestions.isEmpty {
            DisclosureGroup("Empfohlene Modelle") {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(suggestions, id: \.name) { item in
                        ModelSuggestionRow(name: item.name, note: item.note) {
                            modelDraft = item.name
                            viewModel.setModel(provider: providerIdForCurrent(), model: item.name)
                        }
                    }
                }
                .padding(.vertical, 4)
            }
            .font(.subheadline)
        }
    }

    // MARK: - Actions

    private func saveKeyDraft() {
        let trimmed = keyDraft.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        viewModel.setApiKeyFor(provider: providerIdForCurrent(), key: trimmed)
        keyDraft = ""
        keyVisible = false
        focusedField = nil
    }

    private func saveAnthropicKeyDraft() {
        let trimmed = keyDraft.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        viewModel.setAnthropicApiKey(key: trimmed)
        keyDraft = ""
        keyVisible = false
        focusedField = nil
    }

    private func commitModelDraft() {
        let trimmed = modelDraft.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        viewModel.setModel(provider: providerIdForCurrent(), model: trimmed)
        model = trimmed
        focusedField = nil
    }

    private func commitFocusedField() {
        switch focusedField {
        case .key:
            if isAnthropic { saveAnthropicKeyDraft() } else { saveKeyDraft() }
        case .model:
            commitModelDraft()
        case .none:
            break
        }
        focusedField = nil
    }

    private func saveAllAndDismiss() {
        if !keyDraft.trimmingCharacters(in: .whitespaces).isEmpty {
            if isAnthropic { saveAnthropicKeyDraft() } else { saveKeyDraft() }
        }
        if !modelDraft.trimmingCharacters(in: .whitespaces).isEmpty
            && modelDraft != model {
            commitModelDraft()
        }
        focusedField = nil
        dismiss()
    }

    // MARK: - Helpers

    /// Safe force-unwrap: providerPreset is always one of the three known wireNames.
    private func providerIdForCurrent() -> ProviderId {
        ProviderId.companion.fromWireNameOrNull(s: providerPreset)!
    }

    private func defaultModel(for wireName: String) -> String {
        switch wireName {
        case "together": return "google/gemma-4-31B-it"
        case "anthropic": return "claude-sonnet-4-6"
        default: return "gpt-4o-mini"
        }
    }

    // MARK: - Observers

    private func observeProviderPreset() async {
        do {
            for try await value in asyncSequence(for: viewModel.activeProviderFlow) {
                let wireName = value.wireName
                if wireName != self.providerPreset {
                    self.isSyncingProviderFromFlow = true
                    self.providerPreset = wireName
                }
                // Re-sync the model field to the new provider's saved model.
                self.model = self.modelByProvider[wireName] ?? self.defaultModel(for: wireName)
                self.modelDraft = self.model
            }
        } catch {
            print("AISettingsView activeProvider observation error: \(error)")
        }
    }

    private func observeModelByProvider() async {
        do {
            for try await dict in asyncSequence(for: viewModel.modelByProviderFlow) {
                // KMP bridges Map<ProviderId, String> as NSDictionary keyed by ProviderId NSObject.
                var newMap: [String: String] = [:]
                for (key, val) in dict {
                    if let pid = key as? ProviderId, let modelStr = val as? String {
                        newMap[pid.wireName] = modelStr
                    }
                }
                self.modelByProvider = newMap
                // Update model field for the currently active provider.
                self.model = newMap[self.providerPreset] ?? self.defaultModel(for: self.providerPreset)
                self.modelDraft = self.model
            }
        } catch {
            print("AISettingsView modelByProvider observation error: \(error)")
        }
    }

    private func observeConnectedProviders() async {
        do {
            for try await set in asyncSequence(for: viewModel.connectedProvidersFlow) {
                // KMP bridges Set<ProviderId> as NSSet of ProviderId NSObjects.
                var wireNames: Set<String> = []
                for item in set {
                    if let pid = item as? ProviderId {
                        wireNames.insert(pid.wireName)
                    }
                }
                self.connectedProvidersWireNames = wireNames
            }
        } catch {
            print("AISettingsView connectedProviders observation error: \(error)")
        }
    }

    private func observeLastError() async {
        do {
            for try await value in asyncSequence(for: viewModel.lastErrorFlow) {
                self.lastError = value
            }
        } catch {
            print("AISettingsView lastError observation error: \(error)")
        }
    }
}

// MARK: - Model suggestions per provider (verified May 2026)

private struct ModelSuggestion: Hashable {
    let name: String
    let note: String
}

private extension AISettingsView {
    static let modelSuggestions: [String: [ModelSuggestion]] = [
        "openai": [
            ModelSuggestion(name: "gpt-4o-mini", note: "Günstig · Standard"),
            ModelSuggestion(name: "gpt-4o", note: "Stärker"),
            ModelSuggestion(name: "o4-mini", note: "Reasoning, langsamer")
        ],
        "together": [
            ModelSuggestion(name: "google/gemma-4-31B-it", note: "Empfohlen · 12/12 in Eval"),
            ModelSuggestion(name: "Qwen/Qwen3-235B-A22B-Instruct-2507-tput", note: "Stärkstes Refusal · 6/6 Workout in Eval"),
            ModelSuggestion(name: "openai/gpt-oss-20b", note: "Kostenlos · Reasoning · Recipe unzuverlässig"),
            ModelSuggestion(name: "google/gemma-3-27b-it", note: "Vorgänger · Gemma 4 bevorzugen"),
            ModelSuggestion(name: "google/gemma-2-27b-it", note: "Veraltet · Gemma 4 bevorzugen"),
            ModelSuggestion(name: "meta-llama/Llama-3.3-70B-Instruct-Turbo", note: "Llama · stark allround"),
            ModelSuggestion(name: "deepseek-ai/DeepSeek-V3", note: "DeepSeek · Code/Logik")
        ],
        "anthropic": [
            ModelSuggestion(name: "claude-sonnet-4-6", note: "Empfohlen · Standard"),
            ModelSuggestion(name: "claude-opus-4-7", note: "Stärkstes Reasoning"),
            ModelSuggestion(name: "claude-haiku-4-5", note: "Schnell · günstig")
        ]
    ]
}

private struct ModelSuggestionRow: View {
    let name: String
    let note: String
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(name)
                        .font(.system(.caption, design: .monospaced))
                        .foregroundColor(.primary)
                    Text(note)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                Spacer()
                Image(systemName: "arrow.up.left.and.arrow.down.right")
                    .font(.caption2)
                    .foregroundColor(.accentColor)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
