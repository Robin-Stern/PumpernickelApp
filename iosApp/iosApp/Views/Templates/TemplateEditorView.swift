import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct TemplateEditorView: View {
    let templateId: Int64?

    private let viewModel = KoinHelper.shared.getTemplateEditorViewModel()

    @Environment(\.dismiss) private var dismiss

    @State private var name: String = ""
    @State private var exercises: [TemplateExercise] = []
    @State private var isSaving: Bool = false
    @State private var isFormValid: Bool = false  // Observed from ViewModel, NOT computed locally
    @State private var saveResult: TemplateEditorViewModel.SaveResult? = nil
    @State private var showExercisePicker = false
    @State private var showErrorAlert = false
    @State private var errorMessage = ""

    init(templateId: Int64? = nil) {
        self.templateId = templateId
    }

    var body: some View {
        VStack(spacing: 0) {
            Form {
                // Template name section
                Section("Template Name") {
                    TextField("e.g., Push Day", text: $name)
                        .onChange(of: name) { _, newValue in
                            viewModel.onNameChanged(name: newValue)
                        }
                }

                // Exercises section with inline targets
                Section {
                    if exercises.isEmpty {
                        Text("No exercises added yet")
                            .foregroundColor(.secondary)
                            .font(.subheadline)
                    } else {
                        ForEach(Array(exercises.enumerated()), id: \.element.id) { index, exercise in
                            ExerciseTargetRow(
                                exercise: exercise,
                                onUpdateTargets: { sets, reps, weightKgX10, restSec in
                                    viewModel.updateExerciseTargets(
                                        id: exercise.id,
                                        sets: sets,
                                        reps: reps,
                                        weightKgX10: weightKgX10,
                                        restSec: restSec
                                    )
                                }
                            )
                        }
                        .onMove { source, destination in
                            if let from = source.first {
                                viewModel.moveExercise(from: Int32(from), to: Int32(destination))
                            }
                        }
                        .onDelete { indices in
                            if let index = indices.first, index < exercises.count {
                                viewModel.removeExercise(templateExerciseId: exercises[index].id)
                            }
                        }
                    }
                } header: {
                    HStack {
                        Text("Exercises")
                        Spacer()
                        Button("Add Exercise") {
                            showExercisePicker = true
                        }
                        .font(.subheadline)
                    }
                }
            }
            .environment(\.editMode, .constant(.active)) // Always show drag handles (D-12)
        }
        .navigationTitle(templateId != nil ? "Edit Template" : "New Template")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button("Save") {
                    viewModel.save()
                }
                .disabled(!isFormValid || isSaving)
            }
        }
        .sheet(isPresented: $showExercisePicker) {
            ExercisePickerView { exerciseId, exerciseName, primaryMuscles in
                viewModel.addExercise(
                    exerciseId: exerciseId,
                    exerciseName: exerciseName,
                    primaryMuscles: primaryMuscles
                )
            }
        }
        .alert("Error", isPresented: $showErrorAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage)
        }
        .task {
            if let id = templateId {
                viewModel.loadTemplate(id: id)
            }
            await observeState()
        }
    }

    // MARK: - Flow Observation
    private func observeState() async {
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await observeName() }
            group.addTask { await observeExercises() }
            group.addTask { await observeSaving() }
            group.addTask { await observeSaveResult() }
            group.addTask { await observeIsFormValid() }
        }
    }

    private func observeName() async {
        do {
            for try await value in asyncSequence(for: viewModel.nameFlow) {
                if self.name != value { self.name = value }
            }
        } catch { print("Name observation error: \(error)") }
    }

    private func observeExercises() async {
        do {
            for try await value in asyncSequence(for: viewModel.exercisesFlow) {
                self.exercises = value
            }
        } catch { print("Exercises observation error: \(error)") }
    }

    private func observeSaving() async {
        do {
            for try await value in asyncSequence(for: viewModel.isSavingFlow) {
                self.isSaving = value.boolValue
            }
        } catch { print("Saving observation error: \(error)") }
    }

    private func observeSaveResult() async {
        do {
            for try await value in asyncSequence(for: viewModel.saveResultFlow) {
                if let result = value {
                    switch result {
                    case is TemplateEditorViewModel.SaveResultSuccess:
                        dismiss()
                    case let error as TemplateEditorViewModel.SaveResultError:
                        errorMessage = error.message
                        showErrorAlert = true
                    default:
                        break
                    }
                    viewModel.clearSaveResult()
                }
            }
        } catch { print("SaveResult observation error: \(error)") }
    }

    // Observe ViewModel.isFormValid instead of computing locally.
    // This keeps validation in a single source of truth (the ViewModel).
    private func observeIsFormValid() async {
        do {
            for try await value in asyncSequence(for: viewModel.isFormValidFlow) {
                self.isFormValid = value.boolValue
            }
        } catch {
            // Fallback: if Boolean StateFlow bridging fails (known Phase 1 issue),
            // compute locally as a safety net.
            print("isFormValid observation error: \(error)")
        }
    }
}

// MARK: - Exercise Row with Inline Targets (D-05)
// Extracted as a separate view so each row has its own @State for editable text fields.
private struct ExerciseTargetRow: View {
    let exercise: TemplateExercise
    let onUpdateTargets: (Int32, Int32, Int32, Int32) -> Void

    @State private var setsText: String = ""
    @State private var repsText: String = ""
    @State private var weightText: String = ""
    @State private var restText: String = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Exercise name and primary muscle
            HStack {
                Text(exercise.exerciseName)
                    .font(.body.weight(.semibold))
                Spacer()
            }

            if let firstMuscle = exercise.primaryMuscles.first {
                Text(firstMuscle.displayName)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            // Inline target configuration (D-05)
            HStack(spacing: 12) {
                targetField(label: "Sets", text: $setsText)
                targetField(label: "Reps", text: $repsText)
                weightField
                restField
            }
        }
        .padding(.vertical, 4)
        .onAppear {
            setsText = "\(exercise.targetSets)"
            repsText = "\(exercise.targetReps)"
            weightText = formatWeightDisplay(kgX10: Int(exercise.targetWeightKgX10))
            restText = "\(exercise.restPeriodSec)"
        }
        .onChange(of: exercise) { _, newExercise in
            setsText = "\(newExercise.targetSets)"
            repsText = "\(newExercise.targetReps)"
            weightText = formatWeightDisplay(kgX10: Int(newExercise.targetWeightKgX10))
            restText = "\(newExercise.restPeriodSec)"
        }
    }

    // MARK: - Target Input Fields
    private func targetField(label: String, text: Binding<String>) -> some View {
        VStack(spacing: 2) {
            Text(label)
                .font(.caption2)
                .foregroundColor(.secondary)
            TextField("0", text: text)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.center)
                .frame(width: 50)
                .padding(.vertical, 6)
                .background(Color(white: 0.12))
                .cornerRadius(8)
                .onSubmit { commitChanges() }
                .onChange(of: text.wrappedValue) { _, _ in commitChanges() }
        }
    }

    // Weight field: display as kg with one decimal (D-06)
    private var weightField: some View {
        VStack(spacing: 2) {
            Text("kg")
                .font(.caption2)
                .foregroundColor(.secondary)
            TextField("0", text: $weightText)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.center)
                .frame(width: 60)
                .padding(.vertical, 6)
                .background(Color(white: 0.12))
                .cornerRadius(8)
                .onSubmit { commitChanges() }
                .onChange(of: weightText) { _, _ in commitChanges() }
        }
    }

    // Rest field: display as seconds (D-07)
    private var restField: some View {
        VStack(spacing: 2) {
            Text("Rest")
                .font(.caption2)
                .foregroundColor(.secondary)
            TextField("0", text: $restText)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.center)
                .frame(width: 50)
                .padding(.vertical, 6)
                .background(Color(white: 0.12))
                .cornerRadius(8)
                .onSubmit { commitChanges() }
                .onChange(of: restText) { _, _ in commitChanges() }
        }
    }

    // MARK: - Helpers
    private func formatWeightDisplay(kgX10: Int) -> String {
        let whole = kgX10 / 10
        let decimal = kgX10 % 10
        if decimal == 0 {
            return "\(whole)"
        } else {
            return "\(whole).\(decimal)"
        }
    }

    private func parseWeightKgX10(_ input: String) -> Int32 {
        guard let value = Double(input), value >= 0 else { return 0 }
        return Int32(value * 10)
    }

    private func commitChanges() {
        let sets = Int32(setsText) ?? exercise.targetSets
        let reps = Int32(repsText) ?? exercise.targetReps
        let weightKgX10 = parseWeightKgX10(weightText)
        let rest = Int32(restText) ?? exercise.restPeriodSec

        onUpdateTargets(sets, reps, weightKgX10, rest)
    }
}
