import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

/// BYOK Settings (D-18-05 / D-18-06 / D-18-07).
///
/// Wraps the shared AiSettingsViewModel via AiSettingsKoinHelper. Selecting a
/// provider preset resets baseUrl + model AND clears the previously saved key
/// (an OpenAI key won't work on Together — leaving it around with a green
/// "saved" indicator was misleading). Per provider the user is shown a list
/// of known-good model names that auto-fill on tap.
struct AISettingsView: View {
    private let viewModel = AiSettingsKoinHelper().getAiSettingsViewModel()

    @State private var providerPreset: String = "openai"
    /// Quick 260518-f2h — true while the .task observer is pushing a value from
    /// the shared StateFlow into the @State. Prevents the Picker's `.onChange`
    /// from re-calling setProviderPreset (which silently clears the API key) when
    /// the View re-enters and catches up to the actual stored value.
    @State private var isSyncingProviderFromFlow: Bool = false
    @State private var baseUrl: String = "https://api.openai.com/v1"
    @State private var model: String = "gpt-4o-mini"
    @State private var apiKeyConfigured: Bool = false

    @State private var keyDraft: String = ""
    @State private var keyVisible: Bool = false
    @State private var baseUrlDraft: String = ""
    @State private var modelDraft: String = ""

    @FocusState private var focusedField: Field?
    @Environment(\.dismiss) private var dismiss

    private enum Field { case key, baseUrl, model }

    private static let presetLabels: [(key: String, label: String)] = [
        ("openai", "OpenAI"),
        ("together", "Together.AI"),
        ("openrouter", "OpenRouter"),
        ("groq", "Groq"),
        ("custom", "Benutzerdefiniert")
    ]

    private var isCustomPreset: Bool { providerPreset == "custom" }

    private var baseUrlInvalid: Bool {
        let trimmed = baseUrlDraft.trimmingCharacters(in: .whitespaces)
        return !trimmed.isEmpty && !trimmed.lowercased().hasPrefix("https://")
    }

    private var providerLabel: String {
        Self.presetLabels.first(where: { $0.key == providerPreset })?.label ?? providerPreset
    }

    var body: some View {
        Form {
            providerSection
            apiKeySection
            baseUrlSection
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
        .task { await observeBaseUrl() }
        .task { await observeModel() }
        .task { await observeApiKeyConfigured() }
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
                // Guard against the initial flow→@State sync re-firing setProviderPreset on
                // every view re-entry — that would clearApiKey() and silently wipe the saved-state
                // indicator even though the key is still in keychain (Quick 260518-f2h root cause).
                if isSyncingProviderFromFlow {
                    isSyncingProviderFromFlow = false
                    return
                }
                viewModel.setProviderPreset(preset: newValue)
                keyDraft = ""
                keyVisible = false
            }
        } header: {
            Text("Anbieter")
        } footer: {
            Text("Beim Wechsel des Anbieters wird der bisherige API-Schlüssel automatisch gelöscht — Schlüssel sind anbieter-spezifisch.")
                .font(.caption2)
        }
    }

    private var apiKeySection: some View {
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

            HStack(spacing: 8) {
                Image(systemName: apiKeyConfigured ? "checkmark.seal.fill" : "exclamationmark.triangle")
                    .foregroundColor(apiKeyConfigured ? .green : .orange)
                VStack(alignment: .leading, spacing: 2) {
                    Text(apiKeyConfigured ? "Schlüssel für \(providerLabel) gespeichert" : "Kein Schlüssel für \(providerLabel)")
                        .font(.subheadline.weight(.medium))
                        .foregroundColor(apiKeyConfigured ? .green : .orange)
                    if !apiKeyConfigured {
                        Text("Schlüssel oben eintippen und auf Speichern tippen.")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                }
            }

            HStack(spacing: 12) {
                Button(action: saveKeyDraft) {
                    Text("Speichern")
                }
                .buttonStyle(.borderedProminent)
                .disabled(keyDraft.trimmingCharacters(in: .whitespaces).isEmpty)

                Button(role: .destructive) {
                    viewModel.clearApiKey()
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

    private var baseUrlSection: some View {
        Section {
            TextField("https://api.openai.com/v1", text: $baseUrlDraft)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .keyboardType(.URL)
                .focused($focusedField, equals: .baseUrl)
                .disabled(!isCustomPreset)
                .foregroundColor(isCustomPreset ? .primary : .secondary)
                .onSubmit { commitBaseUrlDraft() }
            if isCustomPreset && baseUrlInvalid {
                Text("Nur HTTPS-URLs erlaubt.")
                    .font(.caption)
                    .foregroundColor(.red)
            }
            if isCustomPreset {
                Button("Basis-URL speichern") { commitBaseUrlDraft() }
                    .disabled(baseUrlInvalid || baseUrlDraft.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        } header: {
            Text("Basis-URL")
        } footer: {
            if !isCustomPreset {
                Text("Bei Standard-Anbietern fest auf den offiziellen Endpunkt gesetzt.")
                    .font(.caption2)
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
                            viewModel.setModel(value: item.name)
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
        viewModel.setApiKey(value: trimmed)
        keyDraft = ""
        keyVisible = false
        focusedField = nil
    }

    private func commitBaseUrlDraft() {
        let trimmed = baseUrlDraft.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty, !baseUrlInvalid else { return }
        viewModel.setBaseUrl(url: trimmed)
        focusedField = nil
    }

    private func commitModelDraft() {
        let trimmed = modelDraft.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        viewModel.setModel(value: trimmed)
        focusedField = nil
    }

    private func commitFocusedField() {
        switch focusedField {
        case .key: saveKeyDraft()
        case .baseUrl: commitBaseUrlDraft()
        case .model: commitModelDraft()
        case .none: break
        }
        focusedField = nil
    }

    private func saveAllAndDismiss() {
        if !keyDraft.trimmingCharacters(in: .whitespaces).isEmpty {
            saveKeyDraft()
        }
        if isCustomPreset
            && !baseUrlDraft.trimmingCharacters(in: .whitespaces).isEmpty
            && !baseUrlInvalid
            && baseUrlDraft != baseUrl {
            commitBaseUrlDraft()
        }
        if !modelDraft.trimmingCharacters(in: .whitespaces).isEmpty
            && modelDraft != model {
            commitModelDraft()
        }
        focusedField = nil
        dismiss()
    }

    // MARK: - Observers

    private func observeProviderPreset() async {
        do {
            for try await value in asyncSequence(for: viewModel.providerPresetFlow) {
                if value != self.providerPreset {
                    self.isSyncingProviderFromFlow = true
                    self.providerPreset = value
                }
            }
        } catch {
            print("AISettingsView providerPreset observation error: \(error)")
        }
    }

    private func observeBaseUrl() async {
        do {
            for try await value in asyncSequence(for: viewModel.baseUrlFlow) {
                self.baseUrl = value
                self.baseUrlDraft = value
            }
        } catch {
            print("AISettingsView baseUrl observation error: \(error)")
        }
    }

    private func observeModel() async {
        do {
            for try await value in asyncSequence(for: viewModel.modelFlow) {
                self.model = value
                self.modelDraft = value
            }
        } catch {
            print("AISettingsView model observation error: \(error)")
        }
    }

    private func observeApiKeyConfigured() async {
        do {
            for try await value in asyncSequence(for: viewModel.apiKeyConfiguredFlow) {
                let asBool: Bool = (value as? Bool) ?? ((value as? NSNumber)?.boolValue ?? false)
                self.apiKeyConfigured = asBool
                print("[AISettingsView] apiKeyConfigured emitted: \(asBool)")
            }
        } catch {
            print("AISettingsView apiKeyConfigured observation error: \(error)")
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
        "openrouter": [
            ModelSuggestion(name: "openai/gpt-oss-20b:free", note: "Kostenlos · Reasoning · Recipe unzuverlässig"),
            ModelSuggestion(name: "meta-llama/llama-3.3-70b-instruct", note: "Stark, allround"),
            ModelSuggestion(name: "deepseek/deepseek-chat", note: "Stark bei Code/Logik")
        ],
        "groq": [
            ModelSuggestion(name: "llama-3.3-70b-versatile", note: "Standard, sehr schnell"),
            ModelSuggestion(name: "llama-3.1-8b-instant", note: "Klein, sehr schnell"),
            ModelSuggestion(name: "openai/gpt-oss-20b", note: "Reasoning · Recipe unzuverlässig")
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
