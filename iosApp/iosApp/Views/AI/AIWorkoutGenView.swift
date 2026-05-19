import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

/// F6 — Workout AI generation flow (D-18-03 / D-18-07 / D-18-12 / D-18-16).
/// Drives `WorkoutAiViewModel` via `WorkoutAiKoinHelper`. State machine:
/// NoKey → Form → Generating → Preview → Saved (or Error along the way).
struct AIWorkoutGenView: View {
    private let viewModel = WorkoutAiKoinHelper().getWorkoutAiViewModel()

    @State private var uiState: WorkoutAiUiState? = nil
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
        .navigationTitle("KI-Workout")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await withTaskGroup(of: Void.self) { group in
                group.addTask { await observeUiState() }
                group.addTask { await observeStreaming() }
                group.addTask { await observeSavedEvent() }
            }
        }
        .onAppear {
            // 260518-eny — always start from a fresh Form when the user pushes
            // this screen, so a leftover Saved/Preview/Error state from the
            // previous navigation cycle cannot "burn" the view.
            // D-21-02 fix (c) — the VM's reset() now guards Preview/Error too,
            // so a notification-driven re-entry on a finished background
            // generation no longer overwrites the visible workout.
            viewModel.reset()
        }
    }

    @ViewBuilder
    private func content(for state: WorkoutAiUiState) -> some View {
        if state is WorkoutAiUiState.NoKey {
            NoKeyBody()
        } else if let form = state as? WorkoutAiUiState.Form {
            FormBody(state: form, viewModel: viewModel)
        } else if let generating = state as? WorkoutAiUiState.Generating {
            GeneratingBody(
                rowCount: Int(generating.skeletonRowCount),
                streamingContent: streamingContent,
                streamingReasoning: streamingReasoning,
                viewModel: viewModel
            )
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
            // 260518-eny — Saved is now a transient state visible for one
            // coroutine tick only (see WorkoutAiViewModel.save). The real
            // dismiss trigger comes from the one-shot savedEvent observed by
            // observeSavedEvent(). Render a neutral ProgressView so any brief
            // visual flicker stays unobtrusive; do NOT call dismiss() here.
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
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

    private func observeStreaming() async {
        do {
            for try await value in asyncSequence(for: viewModel.streamingTextFlow) {
                self.streamingContent = value.content
                self.streamingReasoning = value.reasoning
            }
        } catch {
            print("AIWorkoutGenView streaming observation error: \(error)")
        }
    }

    private func observeSavedEvent() async {
        do {
            for try await _ in asyncSequence(for: viewModel.savedEvent) {
                dismiss()
            }
        } catch {
            print("AIWorkoutGenView savedEvent observation error: \(error)")
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
    let streamingContent: String
    let streamingReasoning: String
    let viewModel: WorkoutAiViewModel

    @State private var elapsedSeconds: Int = 0
    @State private var pulse: Bool = false

    private var hasAnyStream: Bool {
        !streamingContent.isEmpty || !streamingReasoning.isEmpty
    }

    var body: some View {
        VStack(spacing: 16) {
            // Compact header so the streaming box gets the screen real estate.
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
                    Text("\(elapsedSeconds)s · \(rowCount) Übung\(rowCount == 1 ? "" : "en")")
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
                hasAnyStream: hasAnyStream,
                rowCount: rowCount
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

/// Live panel that shows the LLM's streamed text. Reasoning chunks (Qwen QwQ /
/// DeepSeek R1) appear in a "Gedanken" subsection, the actual answer JSON in
/// "Antwort". When nothing has streamed yet (first second or two) a skeleton
/// is shown so the box isn't empty.
private struct StreamingPanel: View {
    let content: String
    let reasoning: String
    let hasAnyStream: Bool
    let rowCount: Int

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
                            ForEach(0..<rowCount, id: \.self) { _ in
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
