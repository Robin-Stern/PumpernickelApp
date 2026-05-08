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
                HStack(spacing: 6) {
                    Image(systemName: apiKeyConfigured ? "checkmark.seal.fill" : "exclamationmark.triangle")
                        .foregroundColor(apiKeyConfigured ? .green : .orange)
                    Text(apiKeyConfigured ? "Schlüssel gespeichert" : "Noch kein Schlüssel gespeichert")
                        .font(.subheadline.weight(.medium))
                        .foregroundColor(apiKeyConfigured ? .green : .orange)
                }

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

            Section {
                TextField("gpt-4o-mini", text: $modelDraft)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .onSubmit {
                        let trimmed = modelDraft.trimmingCharacters(in: .whitespaces)
                        if !trimmed.isEmpty { viewModel.setModel(value: trimmed) }
                    }
                Button("Modell speichern") {
                    let trimmed = modelDraft.trimmingCharacters(in: .whitespaces)
                    guard !trimmed.isEmpty else { return }
                    viewModel.setModel(value: trimmed)
                }
                .disabled(modelDraft.trimmingCharacters(in: .whitespaces).isEmpty
                          || modelDraft == model)

                if providerPreset == "together" {
                    DisclosureGroup("Vorgeschlagene Together-Modelle") {
                        VStack(alignment: .leading, spacing: 6) {
                            ModelSuggestionRow(name: "openai/gpt-oss-20b", note: "Kostenlos · empfohlen") {
                                modelDraft = "openai/gpt-oss-20b"
                                viewModel.setModel(value: "openai/gpt-oss-20b")
                            }
                            ModelSuggestionRow(name: "openai/gpt-oss-120b", note: "Stärker, langsamer") {
                                modelDraft = "openai/gpt-oss-120b"
                                viewModel.setModel(value: "openai/gpt-oss-120b")
                            }
                            ModelSuggestionRow(name: "meta-llama/Llama-3.3-70B-Instruct-Turbo", note: "Bezahlt") {
                                modelDraft = "meta-llama/Llama-3.3-70B-Instruct-Turbo"
                                viewModel.setModel(value: "meta-llama/Llama-3.3-70B-Instruct-Turbo")
                            }
                            ModelSuggestionRow(name: "google/gemma-2-27b-it", note: "Bezahlt") {
                                modelDraft = "google/gemma-2-27b-it"
                                viewModel.setModel(value: "google/gemma-2-27b-it")
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    .font(.subheadline)
                }
            } header: {
                Text("Modell")
            } footer: {
                Text("Modellname tippen oder einen Vorschlag wählen. Falsche Namen führen zu einem Timeout — der Anbieter sucht dann ergebnislos nach dem Modell.")
                    .font(.caption2)
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
                // KMP-NC may emit Swift Bool directly OR a KotlinBoolean wrapper
                // depending on the @NativeCoroutinesState codegen version. Handle
                // both by going through NSNumber bridging which works for either.
                let asBool: Bool = (value as? Bool) ?? ((value as? NSNumber)?.boolValue ?? false)
                self.apiKeyConfigured = asBool
                print("[AISettingsView] apiKeyConfigured emitted: \(asBool)")
            }
        } catch {
            print("AISettingsView apiKeyConfigured observation error: \(error)")
        }
    }
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
