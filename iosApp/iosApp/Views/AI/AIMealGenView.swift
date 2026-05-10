import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

/// F8 — Meal/Recipe AI generation flow (D-18-04 / D-18-08 / D-18-12 / D-18-16).
/// Drives `RecipeAiViewModel` via `RecipeAiKoinHelper`. State machine:
/// Loading → NoKey | RemainingExhausted | Form → Generating → Preview → Saved (or Error).
struct AIMealGenView: View {
    private let viewModel = RecipeAiKoinHelper().getRecipeAiViewModel()

    @State private var uiState: RecipeAiUiState? = nil
    @State private var streamingContent: String = ""
    @State private var streamingReasoning: String = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            if let state = uiState {
                content(for: state)
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle("KI-Mahlzeit")
        .navigationBarTitleDisplayMode(.inline)
        .task { await observeUiState() }
        .task { await observeStreaming() }
        .onAppear { viewModel.onAppearRefresh() }
    }

    @ViewBuilder
    private func content(for state: RecipeAiUiState) -> some View {
        if state is RecipeAiUiState.Loading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if state is RecipeAiUiState.NoKey {
            NoKeyBody()
        } else if let exhausted = state as? RecipeAiUiState.RemainingExhausted {
            RemainingExhaustedBody(remaining: exhausted.remaining)
        } else if let form = state as? RecipeAiUiState.Form {
            FormBody(remaining: form.remaining, viewModel: viewModel)
        } else if state is RecipeAiUiState.Generating {
            GeneratingBody(
                streamingContent: streamingContent,
                streamingReasoning: streamingReasoning,
                viewModel: viewModel
            )
        } else if let preview = state as? RecipeAiUiState.Preview {
            FormBody(remaining: preview.originatingRemaining, viewModel: viewModel)
                .sheet(isPresented: .constant(true)) {
                    AIPreviewSheet(
                        content: .recipe(preview.preview),
                        onSaveAll: { viewModel.save() },
                        onDiscard: { viewModel.discardPreview() }
                    )
                    .interactiveDismissDisabled(true)
                }
        } else if let error = state as? RecipeAiUiState.Error {
            ErrorBody(error: error.error, viewModel: viewModel)
        } else if state is RecipeAiUiState.Saved {
            SavedBody().onAppear { dismiss() }
        }
    }

    private func observeUiState() async {
        do {
            for try await value in asyncSequence(for: viewModel.uiStateFlow) {
                self.uiState = value
            }
        } catch {
            print("AIMealGenView uiState observation error: \(error)")
        }
    }

    private func observeStreaming() async {
        do {
            for try await value in asyncSequence(for: viewModel.streamingTextFlow) {
                self.streamingContent = value.content
                self.streamingReasoning = value.reasoning
            }
        } catch {
            print("AIMealGenView streaming observation error: \(error)")
        }
    }
}

// MARK: - NoKey

private struct NoKeyBody: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "key.slash")
                .font(.system(size: 44))
                .foregroundColor(.secondary)
            Text("Du hast noch keinen API-Schlüssel konfiguriert.")
                .multilineTextAlignment(.center)
            NavigationLink {
                AISettingsView()
            } label: {
                Text("KI-Einstellungen öffnen")
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - RemainingExhausted

private struct RemainingExhaustedBody: View {
    let remaining: RemainingMacros

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "checkmark.seal")
                .font(.system(size: 44))
                .foregroundColor(.green)
            Text("Du hast deine Tagesziele bereits erreicht.")
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            Text("Verbleibend: \(Int(remaining.kcal)) kcal")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Form

private struct FormBody: View {
    let remaining: RemainingMacros
    let viewModel: RecipeAiViewModel

    var body: some View {
        VStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 12) {
                Text("Verbleibende Makros heute")
                    .font(.headline)
                MacroRow(label: "Kalorien", value: "\(Int(remaining.kcal)) kcal")
                MacroRow(label: "Protein", value: "\(Int(remaining.protein)) g")
                MacroRow(label: "Fett", value: "\(Int(remaining.fat)) g")
                MacroRow(label: "Kohlenhydrate", value: "\(Int(remaining.carbs)) g")
                MacroRow(label: "Zucker", value: "\(Int(remaining.sugar)) g")
            }
            .padding(16)
            .background(Color(.secondarySystemBackground))
            .cornerRadius(12)
            .padding(.horizontal, 16)

            Button {
                viewModel.generate()
            } label: {
                HStack {
                    Spacer()
                    Image(systemName: "sparkles")
                    Text("Restliche Makros füllen").bold()
                    Spacer()
                }
            }
            .buttonStyle(.borderedProminent)
            .padding(.horizontal, 16)

            Spacer()
        }
        .padding(.top, 16)
    }
}

private struct MacroRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
            Spacer()
            Text(value)
                .foregroundColor(.secondary)
        }
    }
}

// MARK: - Generating

private struct GeneratingBody: View {
    let streamingContent: String
    let streamingReasoning: String
    let viewModel: RecipeAiViewModel

    @State private var elapsedSeconds: Int = 0
    @State private var pulse: Bool = false

    private var hasAnyStream: Bool {
        !streamingContent.isEmpty || !streamingReasoning.isEmpty
    }

    var body: some View {
        VStack(spacing: 16) {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(Color.accentColor.opacity(pulse ? 0.15 : 0.05))
                        .frame(width: 44, height: 44)
                        .scaleEffect(pulse ? 1.1 : 1.0)
                    Image(systemName: "sparkles")
                        .font(.system(size: 20))
                        .foregroundColor(.accentColor)
                        .symbolEffect(.pulse, options: .repeating, value: pulse)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text("KI denkt nach…")
                        .font(.headline)
                    Text("\(elapsedSeconds)s · Rezept aus Restmakros")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .monospacedDigit()
                }
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)

            StreamingPanel(
                content: streamingContent,
                reasoning: streamingReasoning,
                hasAnyStream: hasAnyStream
            )
            .padding(.horizontal, 16)

            Button(role: .destructive) {
                viewModel.cancel()
            } label: {
                Text("Abbrechen")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .padding(.horizontal, 16)
            .padding(.bottom, 16)
        }
        .task {
            withAnimation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true)) {
                pulse = true
            }
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if Task.isCancelled { break }
                elapsedSeconds += 1
            }
        }
    }
}

private struct StreamingPanel: View {
    let content: String
    let reasoning: String
    let hasAnyStream: Bool

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if hasAnyStream {
                        if !reasoning.isEmpty {
                            sectionHeader("Gedanken", icon: "brain")
                            Text(reasoning)
                                .font(.system(.caption, design: .monospaced))
                                .foregroundColor(.secondary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .id("reasoning-tail-\(reasoning.count)")
                        }
                        if !content.isEmpty {
                            sectionHeader("Antwort", icon: "text.alignleft")
                            Text(content)
                                .font(.system(.caption, design: .monospaced))
                                .foregroundColor(.primary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .id("content-tail-\(content.count)")
                        }
                    } else {
                        VStack(spacing: 6) {
                            ForEach(0..<5, id: \.self) { _ in
                                RoundedRectangle(cornerRadius: 6)
                                    .fill(Color(.tertiarySystemBackground))
                                    .frame(height: 28)
                                    .opacity(0.6)
                            }
                        }
                    }
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(Color(.secondarySystemBackground))
            .cornerRadius(12)
            .onChange(of: content) { _, _ in
                withAnimation { proxy.scrollTo("content-tail-\(content.count)", anchor: .bottom) }
            }
            .onChange(of: reasoning) { _, _ in
                if content.isEmpty {
                    withAnimation { proxy.scrollTo("reasoning-tail-\(reasoning.count)", anchor: .bottom) }
                }
            }
        }
        .frame(maxHeight: .infinity)
    }

    @ViewBuilder
    private func sectionHeader(_ title: String, icon: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.caption2)
            Text(title)
                .font(.caption.weight(.semibold))
        }
        .foregroundColor(.secondary)
    }
}

// MARK: - Error

private struct ErrorBody: View {
    let error: AiError
    let viewModel: RecipeAiViewModel

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 44))
                .foregroundColor(.orange)
            Text(title(for: error))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            if error is AiError.AuthOrQuota {
                NavigationLink {
                    AISettingsView()
                } label: {
                    Text("KI-Einstellungen öffnen")
                }
                .buttonStyle(.borderedProminent)
            } else {
                Button {
                    viewModel.retryFromError()
                } label: {
                    Text("Wiederholen")
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func title(for error: AiError) -> String {
        if error is AiError.Timeout {
            return "Generierung hat zu lange gedauert."
        }
        if error is AiError.Network {
            return "Keine Internetverbindung."
        }
        if error is AiError.AuthOrQuota {
            return "Dein API-Schlüssel ist ungültig oder das Kontingent ist aufgebraucht."
        }
        if error is AiError.Provider {
            return "Der KI-Anbieter hat ein Problem."
        }
        return "Die KI-Antwort war nicht verwertbar."
    }
}

// MARK: - Saved

private struct SavedBody: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 44))
                .foregroundColor(.green)
            Text("Gespeichert")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
