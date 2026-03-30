import SwiftUI
import Shared

struct ExerciseOverviewSheet: View {
    let exercises: [SessionExercise]
    let currentExerciseIndex: Int32
    var onSelect: (Int32) -> Void
    var onMove: (Int, Int) -> Void
    var onSkip: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                // Completed exercises section (non-movable, greyed)
                if currentExerciseIndex > 0 {
                    Section("Completed") {
                        ForEach(0..<Int(currentExerciseIndex), id: \.self) { index in
                            exerciseRow(exercise: exercises[index], style: .completed)
                        }
                    }
                }

                // Current exercise section (non-movable, highlighted)
                Section("Current") {
                    HStack {
                        exerciseRow(exercise: exercises[Int(currentExerciseIndex)], style: .current)
                        Spacer()

                        // Skip button (FLOW-07, D-06) -- only if not the last exercise
                        if Int(currentExerciseIndex) + 1 < exercises.count {
                            Button {
                                onSkip()
                                dismiss()
                            } label: {
                                Text("Skip")
                                    .font(.subheadline.weight(.medium))
                                    .foregroundColor(.orange)
                            }
                            .buttonStyle(.borderless)
                            .accessibilityLabel("Skip current exercise")
                        }
                    }
                }

                // Pending exercises section (movable via drag handles)
                let pendingStart = Int(currentExerciseIndex) + 1
                if pendingStart < exercises.count {
                    Section("Up Next") {
                        ForEach(Array(exercises[pendingStart...].enumerated()), id: \.offset) { relIdx, exercise in
                            Button {
                                onSelect(Int32(pendingStart + relIdx))
                            } label: {
                                exerciseRow(exercise: exercise, style: .pending)
                            }
                            .foregroundColor(.primary)
                            .accessibilityLabel("Jump to \(exercise.exerciseName)")
                        }
                        .onMove { source, destination in
                            if let from = source.first {
                                onMove(from, destination)
                            }
                        }
                    }
                }
            }
            .environment(\.editMode, .constant(.active))
            .navigationTitle("Exercise Order")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                        .accessibilityLabel("Close exercise overview")
                }
            }
        }
    }

    // MARK: - Exercise Row

    private enum ExerciseStyle {
        case completed, current, pending
    }

    private func exerciseRow(exercise: SessionExercise, style: ExerciseStyle) -> some View {
        HStack {
            // Status icon
            switch style {
            case .completed:
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(.green)
            case .current:
                Image(systemName: "play.circle.fill")
                    .foregroundColor(.accentColor)
            case .pending:
                Image(systemName: "circle")
                    .foregroundColor(.secondary)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(exercise.exerciseName)
                    .font(.body.weight(style == .current ? .semibold : .regular))
                    .foregroundColor(style == .completed ? .secondary : .primary)

                let completed = exercise.sets.filter { $0.isCompleted }.count
                Text("\(completed)/\(exercise.targetSets) sets")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
        }
    }
}
