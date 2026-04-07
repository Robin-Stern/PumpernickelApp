import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct TemplateEditorView: View {
    let templateId: Int64?

    @State private var viewModel = KoinHelper.shared.getTemplateEditorViewModel()

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
                        ForEach(exercises, id: \.id) { exercise in
                            ExerciseTargetRow(
                                exercise: exercise,
                                onUpdateSetCount: { sets in
                                    viewModel.updateExerciseSetCount(id: exercise.id, sets: sets)
                                },
                                onUpdateReps: { reps in
                                    viewModel.updateExerciseTargets(
                                        id: exercise.id,
                                        sets: exercise.targetSets,
                                        reps: reps,
                                        restSec: exercise.restPeriodSec
                                    )
                                },
                                onUpdateRest: { restSec in
                                    viewModel.updateExerciseRest(id: exercise.id, restSec: restSec)
                                },
                                onUpdateSetReps: { setIdx, reps in
                                    viewModel.updateSetTarget(id: exercise.id, setIndex: setIdx, reps: reps)
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
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") {
                    UIApplication.shared.sendAction(
                        #selector(UIResponder.resignFirstResponder),
                        to: nil, from: nil, for: nil
                    )
                }
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

// MARK: - Exercise Row with Collapsible Per-Set Reps
private struct ExerciseTargetRow: View {
    let exercise: TemplateExercise
    let onUpdateSetCount: (Int32) -> Void
    let onUpdateReps: (Int32) -> Void
    let onUpdateRest: (Int32) -> Void
    let onUpdateSetReps: (Int32, Int32) -> Void // setIndex, reps

    @State private var setsText: String = ""
    @State private var repsText: String = ""
    @State private var restText: String = ""
    @State private var perSetRepsTexts: [String] = []
    @State private var isExpanded: Bool = false

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

            // Default simple view: Sets, Reps, Rest
            HStack(spacing: 12) {
                targetField(label: "Sets", text: $setsText, width: 50, keyboard: .numberPad) {
                    let sets = Int32(setsText) ?? exercise.targetSets
                    if sets > 0 { onUpdateSetCount(sets) }
                }
                targetField(label: "Reps", text: $repsText, width: 50, keyboard: .numberPad) {
                    let reps = Int32(repsText) ?? exercise.targetReps
                    if reps > 0 { onUpdateReps(reps) }
                }
                restField
                Spacer()

                // Expand/collapse button for per-set reps (drop sets)
                Button(action: { withAnimation { isExpanded.toggle() } }) {
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .frame(width: 32, height: 32)
                        .background(Color(white: 0.12))
                        .cornerRadius(8)
                }
            }

            // Collapsible per-set reps (for drop sets)
            if isExpanded {
                let setCount = Int(exercise.targetSets)
                if setCount > 0 {
                    VStack(spacing: 6) {
                        ForEach(0..<setCount, id: \.self) { idx in
                            HStack(spacing: 8) {
                                Text("Set \(idx + 1)")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .frame(width: 44, alignment: .leading)

                                targetField(
                                    label: "Reps",
                                    text: bindingForPerSetReps(at: idx),
                                    width: 50,
                                    keyboard: .numberPad
                                ) { commitSetReps(idx) }
                            }
                        }
                    }
                }
            }
        }
        .padding(.vertical, 4)
        .onAppear { syncFromExercise(exercise) }
        .onChange(of: exercise) { _, newExercise in syncFromExercise(newExercise) }
        .onReceive(NotificationCenter.default.publisher(for: UITextField.textDidBeginEditingNotification)) { notification in
            if let textField = notification.object as? UITextField {
                textField.selectAll(nil)
            }
        }
    }

    // MARK: - Sync state from exercise model

    private func syncFromExercise(_ ex: TemplateExercise) {
        setsText = "\(ex.targetSets)"
        repsText = "\(ex.targetReps)"
        restText = "\(ex.restPeriodSec)"

        let setCount = Int(ex.targetSets)
        var newPerSetReps: [String] = []
        for i in 0..<setCount {
            if let perSet = ex.perSetReps, i < perSet.count {
                newPerSetReps.append("\(perSet[i].int32Value)")
            } else {
                newPerSetReps.append("\(ex.targetReps)")
            }
        }
        perSetRepsTexts = newPerSetReps

        // Auto-expand if per-set reps differ (drop set already configured)
        if ex.perSetReps != nil {
            isExpanded = true
        }
    }

    // MARK: - Bindings for per-set reps

    private func bindingForPerSetReps(at idx: Int) -> Binding<String> {
        Binding(
            get: { perSetRepsTexts.indices.contains(idx) ? perSetRepsTexts[idx] : "0" },
            set: { newValue in
                if perSetRepsTexts.indices.contains(idx) { perSetRepsTexts[idx] = newValue }
            }
        )
    }

    // MARK: - Target Input Fields

    private func targetField(
        label: String,
        text: Binding<String>,
        width: CGFloat,
        keyboard: UIKeyboardType,
        onCommit: @escaping () -> Void
    ) -> some View {
        VStack(spacing: 2) {
            Text(label)
                .font(.caption2)
                .foregroundColor(.secondary)
            TextField("0", text: text)
                .keyboardType(keyboard)
                .multilineTextAlignment(.center)
                .frame(width: width)
                .padding(.vertical, 6)
                .background(Color(white: 0.12))
                .cornerRadius(8)
                .onSubmit { onCommit() }
                .onChange(of: text.wrappedValue) { _, _ in onCommit() }
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
                .onSubmit { commitRest() }
                .onChange(of: restText) { _, _ in commitRest() }
        }
    }

    // MARK: - Commit Helpers

    private func commitSetReps(_ idx: Int) {
        guard idx < perSetRepsTexts.count else { return }
        let reps = Int32(perSetRepsTexts[idx]) ?? 0
        onUpdateSetReps(Int32(idx), reps)
    }

    private func commitRest() {
        let rest = Int32(restText) ?? exercise.restPeriodSec
        onUpdateRest(rest)
    }
}
