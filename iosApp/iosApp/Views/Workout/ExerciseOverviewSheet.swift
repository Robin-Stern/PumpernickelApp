import SwiftUI
import Shared

struct ExerciseOverviewSheet: View {
    let exercises: [SessionExercise]
    let currentExerciseIndex: Int32
    var onSelect: (Int32) -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                ForEach(Array(exercises.enumerated()), id: \.offset) { index, exercise in
                    Button {
                        onSelect(Int32(index))
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(exercise.exerciseName)
                                    .font(.body.weight(.semibold))

                                let completed = exercise.sets.filter { $0.isCompleted }.count
                                Text("\(completed)/\(exercise.targetSets) sets")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }

                            Spacer()

                            if Int32(index) == currentExerciseIndex {
                                Image(systemName: "arrow.right.circle.fill")
                                    .foregroundColor(.accentColor)
                            }
                        }
                    }
                    .foregroundColor(.primary)
                }
            }
            .navigationTitle("Exercises")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
    }
}
