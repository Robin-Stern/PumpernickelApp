import SwiftUI
import Shared
import KMPNativeCoroutinesAsync
import UIKit

struct WorkoutSessionView: View {
    var templateId: Int64 = 0
    var templateName: String = ""
    var isResume: Bool = false

    private let viewModel = KoinHelper.shared.getWorkoutSessionViewModel()

    @Environment(\.dismiss) private var dismiss

    @State private var sessionState: WorkoutSessionState = WorkoutSessionState.Idle.shared
    @State private var elapsedSeconds: Int64 = 0
    @State private var showExerciseOverview = false
    @State private var previousPerformance: [String: CompletedExercise] = [:]
    @State private var weightUnit: WeightUnit = .kg

    // Input fields for current set
    @State private var repsInput: String = ""
    @State private var weightInput: String = ""

    // Edit set sheet
    @State private var showEditSheet = false
    @State private var editExerciseIndex: Int32 = 0
    @State private var editSetIndex: Int32 = 0
    @State private var editRepsInput: String = ""
    @State private var editWeightInput: String = ""

    // Track previous rest state for haptic trigger
    @State private var previousRestWasResting = false

    var body: some View {
        Group {
            if let active = sessionState as? WorkoutSessionState.Active {
                activeWorkoutView(active)
            } else if let finished = sessionState as? WorkoutSessionState.Finished {
                WorkoutFinishedView(
                    workoutName: finished.workoutName,
                    durationMillis: finished.durationMillis,
                    totalSets: finished.totalSets,
                    totalExercises: finished.totalExercises,
                    onDone: {
                        viewModel.resetToIdle()
                        dismiss()
                    }
                )
            } else {
                // Idle / loading state
                VStack(spacing: 16) {
                    ProgressView()
                        .scaleEffect(1.5)
                    Text("Starting workout...")
                        .font(.headline)
                        .foregroundColor(.secondary)
                }
            }
        }
        .navigationBarBackButtonHidden(true)
        .sheet(isPresented: $showExerciseOverview) {
            if let active = sessionState as? WorkoutSessionState.Active {
                ExerciseOverviewSheet(
                    exercises: active.exercises,
                    currentExerciseIndex: active.currentExerciseIndex,
                    onSelect: { index in
                        viewModel.jumpToExercise(exerciseIndex: index)
                        showExerciseOverview = false
                    }
                )
            }
        }
        .sheet(isPresented: $showEditSheet) {
            editSetSheet
        }
        .task {
            if isResume {
                viewModel.resumeWorkout()
            } else {
                viewModel.startWorkout(templateId: templateId)
            }
            await withTaskGroup(of: Void.self) { group in
                group.addTask { await observeSessionState() }
                group.addTask { await observeElapsedSeconds() }
                group.addTask { await observePreviousPerformance() }
                group.addTask { await observeWeightUnit() }
            }
        }
    }

    // MARK: - Active Workout View

    @ViewBuilder
    private func activeWorkoutView(_ active: WorkoutSessionState.Active) -> some View {
        let exercises = active.exercises
        let exIdx = Int(active.currentExerciseIndex)
        let setIdx = Int(active.currentSetIndex)
        let currentExercise = exercises[exIdx]
        let restState = active.restState

        ScrollView {
            VStack(spacing: 20) {
                // Header section (WORK-06, WORK-08)
                headerSection(
                    active: active,
                    exercise: currentExercise,
                    exIdx: exIdx,
                    setIdx: setIdx,
                    totalExercises: exercises.count
                )

                // Rest timer or set input
                if let resting = restState as? RestState.Resting {
                    RestTimerView(
                        remainingSeconds: resting.remainingSeconds,
                        totalSeconds: resting.totalSeconds
                    )
                    Button("Skip Rest") {
                        viewModel.skipRest()
                    }
                    .font(.body.weight(.medium))
                    .foregroundColor(.secondary)
                } else if restState is RestState.RestComplete {
                    VStack(spacing: 8) {
                        Text("Rest Complete!")
                            .font(.title3.weight(.semibold))
                            .foregroundColor(Color(red: 0.4, green: 0.733, blue: 0.416))
                    }
                    .padding(.vertical, 12)

                    Button("Continue") {
                        viewModel.skipRest()
                    }
                    .font(.body.weight(.semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Color(red: 0.4, green: 0.733, blue: 0.416))
                    .cornerRadius(12)
                    .padding(.horizontal, 32)
                } else {
                    // Set input (D-09)
                    setInputSection(exercise: currentExercise, setIdx: setIdx)
                }

                // Completed sets for current exercise (D-11)
                completedSetsSection(exercise: currentExercise, exIdx: Int32(exIdx))

                // Finish workout button (D-16)
                let hasCompletedSets = exercises.contains { ex in
                    ex.sets.contains { $0.isCompleted }
                }
                if hasCompletedSets {
                    Button("Finish Workout") {
                        viewModel.finishWorkout()
                    }
                    .font(.body.weight(.semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Color.red.opacity(0.8))
                    .cornerRadius(12)
                    .padding(.horizontal, 32)
                    .padding(.top, 16)
                }
            }
            .padding()
        }
        .navigationTitle(active.templateName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    showExerciseOverview = true
                } label: {
                    Image(systemName: "list.bullet")
                }
            }
        }
    }

    // MARK: - Header Section

    private func headerSection(
        active: WorkoutSessionState.Active,
        exercise: SessionExercise,
        exIdx: Int,
        setIdx: Int,
        totalExercises: Int
    ) -> some View {
        VStack(spacing: 8) {
            HStack {
                Text("Exercise \(exIdx + 1) of \(totalExercises)")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                Spacer()
                Text(formatElapsed(elapsedSeconds))
                    .font(.subheadline.monospacedDigit())
                    .foregroundColor(.secondary)
            }

            Text(exercise.exerciseName)
                .font(.title2.weight(.bold))
                .frame(maxWidth: .infinity, alignment: .leading)

            Text("Set \(setIdx + 1) of \(exercise.targetSets)")
                .font(.headline)
                .foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            // Previous performance (HIST-04, D-08, D-09)
            if let prevExercise = previousPerformance[exercise.exerciseId] {
                let prevText = formatPreviousPerformance(prevExercise)
                if !prevText.isEmpty {
                    Text("Last: \(prevText)")
                        .font(.subheadline)
                        .foregroundColor(.orange)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }

    // MARK: - Set Input Section

    private func setInputSection(exercise: SessionExercise, setIdx: Int) -> some View {
        VStack(spacing: 16) {
            HStack(spacing: 16) {
                VStack(spacing: 4) {
                    Text("Reps")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    TextField("0", text: $repsInput)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.center)
                        .font(.title2.weight(.semibold))
                        .frame(width: 80)
                        .padding(.vertical, 12)
                        .background(Color(UIColor.secondarySystemBackground))
                        .cornerRadius(12)
                }

                VStack(spacing: 4) {
                    Text("Weight (\(weightUnit.label))")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    TextField("0", text: $weightInput)
                        .keyboardType(.decimalPad)
                        .multilineTextAlignment(.center)
                        .font(.title2.weight(.semibold))
                        .frame(width: 100)
                        .padding(.vertical, 12)
                        .background(Color(UIColor.secondarySystemBackground))
                        .cornerRadius(12)
                }
            }

            Button("Complete Set") {
                let reps = Int32(repsInput) ?? 0
                let weightKgX10 = parseWeightKgX10(weightInput)
                viewModel.completeSet(reps: reps, weightKgX10: weightKgX10)
            }
            .font(.body.weight(.semibold))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(Color(red: 0.4, green: 0.733, blue: 0.416))
            .cornerRadius(12)
            .padding(.horizontal, 32)
        }
        .padding(.vertical, 8)
        .onAppear {
            prefillInputs(exercise: exercise, setIdx: setIdx)
        }
        .onChange(of: setIdx) { _, newIdx in
            prefillInputs(exercise: exercise, setIdx: newIdx)
        }
        .onChange(of: exercise.exerciseName) { _, _ in
            prefillInputs(exercise: exercise, setIdx: setIdx)
        }
    }

    // MARK: - Completed Sets Section

    private func completedSetsSection(exercise: SessionExercise, exIdx: Int32) -> some View {
        let completedSets = exercise.sets.filter { $0.isCompleted }
        return Group {
            if !completedSets.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Completed Sets")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.secondary)

                    ForEach(completedSets, id: \.setIndex) { set in
                        WorkoutSetRow(
                            setIndex: set.setIndex,
                            actualReps: set.actualReps?.int32Value ?? 0,
                            actualWeightKgX10: set.actualWeightKgX10?.int32Value ?? 0,
                            isCompleted: set.isCompleted,
                            weightUnit: weightUnit,
                            onTap: {
                                editExerciseIndex = exIdx
                                editSetIndex = set.setIndex
                                editRepsInput = "\(set.actualReps?.int32Value ?? 0)"
                                let w = set.actualWeightKgX10?.int32Value ?? 0
                                editWeightInput = formatWeightInput(w)
                                showEditSheet = true
                            }
                        )
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    // MARK: - Edit Set Sheet (D-11)

    private var editSetSheet: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Text("Edit Set \(editSetIndex + 1)")
                    .font(.title3.weight(.bold))

                HStack(spacing: 16) {
                    VStack(spacing: 4) {
                        Text("Reps")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        TextField("0", text: $editRepsInput)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.center)
                            .font(.title2.weight(.semibold))
                            .frame(width: 80)
                            .padding(.vertical, 12)
                            .background(Color(UIColor.secondarySystemBackground))
                            .cornerRadius(12)
                    }

                    VStack(spacing: 4) {
                        Text("Weight (\(weightUnit.label))")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        TextField("0", text: $editWeightInput)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.center)
                            .font(.title2.weight(.semibold))
                            .frame(width: 100)
                            .padding(.vertical, 12)
                            .background(Color(UIColor.secondarySystemBackground))
                            .cornerRadius(12)
                    }
                }

                Button("Save") {
                    let reps = Int32(editRepsInput) ?? 0
                    let weightKgX10 = parseWeightKgX10(editWeightInput)
                    viewModel.editCompletedSet(
                        exerciseIndex: editExerciseIndex,
                        setIndex: editSetIndex,
                        reps: reps,
                        weightKgX10: weightKgX10
                    )
                    showEditSheet = false
                }
                .font(.body.weight(.semibold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(Color(red: 0.4, green: 0.733, blue: 0.416))
                .cornerRadius(12)
                .padding(.horizontal, 32)

                Spacer()
            }
            .padding()
            .navigationTitle("Edit Set")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        showEditSheet = false
                    }
                }
            }
        }
    }

    // MARK: - Flow Observation

    private func observeSessionState() async {
        do {
            for try await value in asyncSequence(for: viewModel.sessionStateFlow) {
                let newState = value

                // Haptic feedback on rest complete (D-07, WORK-05)
                if let active = newState as? WorkoutSessionState.Active {
                    if active.restState is RestState.RestComplete && previousRestWasResting {
                        let generator = UINotificationFeedbackGenerator()
                        generator.prepare()
                        generator.notificationOccurred(.success)
                    }
                    previousRestWasResting = active.restState is RestState.Resting

                    // Update input fields when cursor changes
                    let exIdx = Int(active.currentExerciseIndex)
                    let setIdx = Int(active.currentSetIndex)
                    if exIdx < active.exercises.count {
                        let exercise = active.exercises[exIdx]
                        if setIdx < exercise.sets.count {
                            let currentSet = exercise.sets[setIdx]
                            if !currentSet.isCompleted {
                                repsInput = "\(currentSet.targetReps)"
                                weightInput = formatWeightInput(currentSet.targetWeightKgX10)
                            }
                        }
                    }
                }

                self.sessionState = newState
            }
        } catch {
            print("SessionState observation error: \(error)")
        }
    }

    private func observeElapsedSeconds() async {
        do {
            for try await value in asyncSequence(for: viewModel.elapsedSecondsFlow) {
                self.elapsedSeconds = value.int64Value
            }
        } catch {
            print("ElapsedSeconds observation error: \(error)")
        }
    }

    private func observePreviousPerformance() async {
        do {
            for try await value in asyncSequence(for: viewModel.previousPerformanceFlow) {
                self.previousPerformance = value
            }
        } catch {
            print("Previous performance observation error: \(error)")
        }
    }

    private func observeWeightUnit() async {
        do {
            for try await value in asyncSequence(for: viewModel.weightUnitFlow) {
                self.weightUnit = value
            }
        } catch {
            print("Weight unit observation error: \(error)")
        }
    }

    // MARK: - Previous Performance Formatting

    private func formatPreviousPerformance(_ exercise: CompletedExercise) -> String {
        let sets = exercise.sets
        if sets.isEmpty { return "" }

        // If all sets have same reps and weight, show compact format: "3x10 @ 50.0 kg"
        let firstSet = sets[0]
        let allSame = sets.allSatisfy {
            $0.actualReps == firstSet.actualReps && $0.actualWeightKgX10 == firstSet.actualWeightKgX10
        }

        if allSame {
            return "\(sets.count)x\(firstSet.actualReps) @ \(weightUnit.formatWeight(kgX10: firstSet.actualWeightKgX10))"
        }

        // Different sets: show each briefly "10x50.0 kg, 8x50.0 kg, 6x50.0 kg"
        return sets.map { set in
            "\(set.actualReps)x\(weightUnit.formatWeight(kgX10: set.actualWeightKgX10))"
        }.joined(separator: ", ")
    }

    // MARK: - Helpers

    private func prefillInputs(exercise: SessionExercise, setIdx: Int) {
        if setIdx < exercise.sets.count {
            let currentSet = exercise.sets[setIdx]
            repsInput = "\(currentSet.targetReps)"
            weightInput = formatWeightInput(currentSet.targetWeightKgX10)
        }
    }

    private func formatWeight(_ kgX10: Int32) -> String {
        return weightUnit.formatWeight(kgX10: kgX10)
    }

    private func formatWeightInput(_ kgX10: Int32) -> String {
        let whole = kgX10 / 10
        let decimal = kgX10 % 10
        return decimal == 0 ? "\(whole)" : "\(whole).\(decimal)"
    }

    private func parseWeightKgX10(_ input: String) -> Int32 {
        guard let value = Double(input), value >= 0 else { return 0 }
        return Int32(value * 10)
    }

    private func formatElapsed(_ seconds: Int64) -> String {
        let h = seconds / 3600
        let m = (seconds % 3600) / 60
        let s = seconds % 60
        if h > 0 {
            return String(format: "%d:%02d:%02d", h, m, s)
        }
        return String(format: "%d:%02d", m, s)
    }
}
