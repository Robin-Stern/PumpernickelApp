import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

/// F6 — Workout AI generation flow (D-18-03 / D-18-07 / D-18-12 / D-18-16).
/// Drives `WorkoutAiViewModel` via `WorkoutAiKoinHelper`. State machine:
/// NoKey → Form → Generating → Preview → Saved (or Error along the way).
struct AIWorkoutGenView: View {
    private let viewModel = WorkoutAiKoinHelper().getWorkoutAiViewModel()

    @State private var uiState: WorkoutAiUiState? = nil
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
        .navigationTitle("KI-Workout")
        .navigationBarTitleDisplayMode(.inline)
        .task { await observeUiState() }
    }

    @ViewBuilder
    private func content(for state: WorkoutAiUiState) -> some View {
        if state is WorkoutAiUiState.NoKey {
            NoKeyBody()
        } else if let form = state as? WorkoutAiUiState.Form {
            FormBody(state: form, viewModel: viewModel)
        } else if let generating = state as? WorkoutAiUiState.Generating {
            GeneratingBody(rowCount: Int(generating.skeletonRowCount), viewModel: viewModel)
        } else if let preview = state as? WorkoutAiUiState.Preview {
            FormBody(state: preview.originatingForm, viewModel: viewModel)
                .sheet(isPresented: .constant(true)) {
                    AIPreviewSheet(
                        content: .workout(preview.preview),
                        onSaveAll: { viewModel.save() },
                        onDiscard: { viewModel.discardPreview() }
                    )
                    .interactiveDismissDisabled(true)
                }
        } else if let error = state as? WorkoutAiUiState.Error {
            ErrorBody(error: error.error, viewModel: viewModel)
        } else if state is WorkoutAiUiState.Saved {
            SavedBody().onAppear { dismiss() }
        }
    }

    private func observeUiState() async {
        do {
            for try await value in asyncSequence(for: viewModel.uiStateFlow) {
                self.uiState = value
            }
        } catch {
            print("AIWorkoutGenView uiState observation error: \(error)")
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

// MARK: - Form

private struct FormBody: View {
    let state: WorkoutAiUiState.Form
    let viewModel: WorkoutAiViewModel

    private static let muscleOptions: [MuscleGroup] = [
        .chest, .shoulders, .biceps, .triceps, .forearms, .traps, .lats,
        .neck, .quadriceps, .hamstrings, .glutes, .calves, .adductors,
        .abdominals, .obliques, .lowerBack
    ]

    private var selectedMuscles: Set<MuscleGroup> {
        Set(state.targetMuscles)
    }

    var body: some View {
        Form {
            Section("Zielmuskeln") {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Self.muscleOptions, id: \.self) { muscle in
                            MuscleChip(
                                muscle: muscle,
                                isSelected: selectedMuscles.contains(muscle)
                            ) {
                                toggleMuscle(muscle)
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
            }

            Section("Anzahl Übungen") {
                Stepper(
                    value: Binding(
                        get: { Int(state.exerciseCount) },
                        set: { viewModel.onExerciseCountChanged(count: Int32($0)) }
                    ),
                    in: 1...12
                ) {
                    Text("\(state.exerciseCount) Übungen")
                }
            }

            Section("Aufteilung") {
                Picker("Aufteilung", selection: Binding(
                    get: { state.splitStyle },
                    set: { viewModel.onSplitStyleChanged(split: $0) }
                )) {
                    Text("Einzeln").tag(WorkoutAiSplit.none)
                    Text("Push/Pull/Legs").tag(WorkoutAiSplit.pushPullLegs)
                    Text("Upper/Lower").tag(WorkoutAiSplit.upperLower)
                    Text("Ganzkörper").tag(WorkoutAiSplit.fullBody)
                }
                .pickerStyle(.menu)
            }

            Section {
                Button {
                    viewModel.generate()
                } label: {
                    HStack {
                        Spacer()
                        Image(systemName: "sparkles")
                        Text("Generieren").bold()
                        Spacer()
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(state.targetMuscles.isEmpty)
                .listRowInsets(EdgeInsets(top: 12, leading: 16, bottom: 12, trailing: 16))
            }
        }
    }

    private func toggleMuscle(_ muscle: MuscleGroup) {
        var current = selectedMuscles
        if current.contains(muscle) {
            current.remove(muscle)
        } else {
            current.insert(muscle)
        }
        viewModel.onMusclesChanged(muscles: Array(current))
    }
}

private struct MuscleChip: View {
    let muscle: MuscleGroup
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Text(muscle.displayName)
                .font(.subheadline)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    Capsule().fill(isSelected ? Color.accentColor : Color(.secondarySystemBackground))
                )
                .foregroundColor(isSelected ? .white : .primary)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Generating

private struct GeneratingBody: View {
    let rowCount: Int
    let viewModel: WorkoutAiViewModel

    @State private var elapsedSeconds: Int = 0
    @State private var pulse: Bool = false

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            // Big obvious "thinking" header so the user knows something's happening.
            VStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(Color.accentColor.opacity(pulse ? 0.15 : 0.05))
                        .frame(width: 120, height: 120)
                        .scaleEffect(pulse ? 1.1 : 1.0)
                    Image(systemName: "sparkles")
                        .font(.system(size: 44))
                        .foregroundColor(.accentColor)
                        .symbolEffect(.pulse, options: .repeating, value: pulse)
                }
                Text("KI denkt nach…")
                    .font(.title3.bold())
                Text("\(elapsedSeconds)s")
                    .font(.system(.subheadline, design: .monospaced))
                    .foregroundColor(.secondary)
                Text("Erstelle \(rowCount) Übung\(rowCount == 1 ? "" : "en")…")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            // Subtle skeleton hint underneath
            VStack(spacing: 6) {
                ForEach(0..<rowCount, id: \.self) { _ in
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color(.tertiarySystemBackground))
                        .frame(height: 36)
                        .opacity(0.6)
                }
            }
            .padding(.horizontal, 32)

            Spacer()

            Button(role: .destructive) {
                viewModel.cancel()
            } label: {
                Text("Abbrechen")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .padding(.horizontal, 32)
            .padding(.bottom, 24)
        }
        .task {
            // Pulse animation
            withAnimation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true)) {
                pulse = true
            }
            // Elapsed-second counter — keeps user oriented during 30-90s waits.
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if Task.isCancelled { break }
                elapsedSeconds += 1
            }
        }
    }
}

// MARK: - Error

private struct ErrorBody: View {
    let error: AiError
    let viewModel: WorkoutAiViewModel

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 44))
                .foregroundColor(.orange)
            Text(title(for: error))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
                .font(.headline)

            if let detail = detail(for: error), !detail.isEmpty {
                ScrollView {
                    Text(detail)
                        .font(.system(.caption, design: .monospaced))
                        .foregroundColor(.secondary)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemBackground))
                        .cornerRadius(8)
                }
                .frame(maxHeight: 200)
                .padding(.horizontal, 16)
            }

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

    private func detail(for error: AiError) -> String? {
        if let schema = error as? AiError.SchemaInvalid {
            return schema.detail
        }
        if let provider = error as? AiError.Provider {
            return "HTTP \(provider.httpStatus) — Versuche einen anderen Modellnamen oder warte und probiere es nochmal."
        }
        if let auth = error as? AiError.AuthOrQuota {
            return "HTTP \(auth.httpStatus) — Prüfe API-Schlüssel und Kontingent in den Einstellungen."
        }
        return nil
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
