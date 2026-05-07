import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

/// BYOK Settings (D-18-05 / D-18-06 / D-18-07).
///
/// Wraps the shared AiSettingsViewModel via AiSettingsKoinHelper. Provider
/// preset picker resets baseUrl + model to per-preset defaults; baseUrl is
/// disabled unless preset == "custom"; non-HTTPS URLs are rejected with an
/// inline error and the save button stays disabled while the URL is invalid.
/// API key is read/written via Keychain (kSecAttrAccessibleAfterFirstUnlock-
/// ThisDeviceOnly) and never lands in DataStore (REQ-AI-06).
struct AISettingsView: View {
    private let viewModel = AiSettingsKoinHelper().getAiSettingsViewModel()

    @State private var providerPreset: String = "openai"
    @State private var baseUrl: String = "https://api.openai.com/v1"
    @State private var model: String = "gpt-4o-mini"
    @State private var apiKeyConfigured: Bool = false

    @State private var keyDraft: String = ""
    @State private var keyVisible: Bool = false
    @State private var baseUrlDraft: String = ""
    @State private var modelDraft: String = ""
    @State private var didSeedDrafts = false

    @Environment(\.dismiss) private var dismiss

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

    var body: some View {
        Form {
            Section("Anbieter") {
                Picker("Anbieter", selection: $providerPreset) {
                    ForEach(Self.presetLabels, id: \.key) { item in
                        Text(item.label).tag(item.key)
                    }
                }
                .pickerStyle(.menu)
                .onChange(of: providerPreset) { _, newValue in
                    viewModel.setProviderPreset(preset: newValue)
                }
            }

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
                    Button {
                        keyVisible.toggle()
                    } label: {
                        Image(systemName: keyVisible ? "eye.slash" : "eye")
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(keyVisible ? "Schlüssel verbergen" : "Schlüssel zeigen")
                }
                Text(apiKeyConfigured ? "Gespeichert" : "Kein Schlüssel gespeichert")
                    .font(.caption)
                    .foregroundColor(apiKeyConfigured ? .green : .secondary)

                HStack(spacing: 12) {
                    Button {
                        let trimmed = keyDraft.trimmingCharacters(in: .whitespaces)
                        guard !trimmed.isEmpty else { return }
                        viewModel.setApiKey(value: trimmed)
                        keyDraft = ""
                        keyVisible = false
                    } label: {
                        Text("Schlüssel speichern")
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(keyDraft.trimmingCharacters(in: .whitespaces).isEmpty)

                    Button(role: .destructive) {
                        viewModel.clearApiKey()
                        keyDraft = ""
                        keyVisible = false
                    } label: {
                        Text("Schlüssel löschen")
                    }
                    .buttonStyle(.bordered)
                    .disabled(!apiKeyConfigured)
                }
            } header: {
                Text("API-Schlüssel")
            } footer: {
                Text("Der Schlüssel wird nur lokal im Schlüsselbund gespeichert (kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly).")
                    .font(.caption2)
            }

            Section("Basis-URL") {
                TextField("https://api.openai.com/v1", text: $baseUrlDraft)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .keyboardType(.URL)
                    .disabled(!isCustomPreset)
                    .foregroundColor(isCustomPreset ? .primary : .secondary)
                if isCustomPreset && baseUrlInvalid {
                    Text("Nur HTTPS-URLs erlaubt.")
                        .font(.caption)
                        .foregroundColor(.red)
                }
                if isCustomPreset {
                    Button("Basis-URL speichern") {
                        let trimmed = baseUrlDraft.trimmingCharacters(in: .whitespaces)
                        viewModel.setBaseUrl(url: trimmed)
                    }
                    .disabled(baseUrlInvalid || baseUrlDraft.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }

            Section("Modell") {
                TextField("gpt-4o-mini", text: $modelDraft)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                Button("Modell speichern") {
                    let trimmed = modelDraft.trimmingCharacters(in: .whitespaces)
                    guard !trimmed.isEmpty else { return }
                    viewModel.setModel(value: trimmed)
                }
                .disabled(modelDraft.trimmingCharacters(in: .whitespaces).isEmpty
                          || modelDraft == model)
            }
        }
        .navigationTitle("KI-Einstellungen")
        .navigationBarTitleDisplayMode(.inline)
        .task { await observeProviderPreset() }
        .task { await observeBaseUrl() }
        .task { await observeModel() }
        .task { await observeApiKeyConfigured() }
    }

    // MARK: - Observers

    private func observeProviderPreset() async {
        do {
            for try await value in asyncSequence(for: viewModel.providerPresetFlow) {
                self.providerPreset = value
            }
        } catch {
            print("AISettingsView providerPreset observation error: \(error)")
        }
    }

    private func observeBaseUrl() async {
        // Always mirror the flow into the draft. Flow changes are user-driven
        // (preset switch resets baseUrl/model to per-preset defaults — D-18-06),
        // so the field SHOULD visibly update on preset change.
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
                self.apiKeyConfigured = value.boolValue
            }
        } catch {
            print("AISettingsView apiKeyConfigured observation error: \(error)")
        }
    }
}
