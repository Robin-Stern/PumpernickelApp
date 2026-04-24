import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct ExerciseDetailView: View {
    let exerciseId: String

    private let viewModel = KoinHelper.shared.getExerciseDetailViewModel()
    @State private var exercise: Exercise? = nil

    var body: some View {
        Group {
            if let exercise = exercise {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        muscleGroupsSection(exercise)

                        Divider()

                        metadataSection(exercise)

                        Divider()

                        instructionsSection(exercise)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                }
            } else {
                ProgressView()
            }
        }
        .navigationTitle(exercise?.name ?? "Exercise")
        .task {
            viewModel.loadExercise(id: exerciseId)
            await observeExercise()
        }
    }

    // MARK: - Muscle Groups Section

    @ViewBuilder
    private func muscleGroupsSection(_ exercise: Exercise) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Primary Muscles")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                if exercise.primaryMuscles.isEmpty {
                    Text("None")
                        .font(.body)
                        .foregroundColor(.secondary)
                } else {
                    flowLayout(muscles: exercise.primaryMuscles)
                }
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Secondary Muscles")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                if exercise.secondaryMuscles.isEmpty {
                    Text("None")
                        .font(.body)
                        .foregroundColor(.secondary)
                } else {
                    flowLayout(muscles: exercise.secondaryMuscles)
                }
            }
        }
    }

    @ViewBuilder
    private func flowLayout(muscles: [MuscleGroup]) -> some View {
        HStack(spacing: 8) {
            ForEach(muscles, id: \.self) { muscle in
                Text(muscle.displayName)
                    .font(.subheadline)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.appAccent.opacity(0.15))
                    .foregroundColor(.appAccent)
                    .cornerRadius(12)
            }
        }
    }

    // MARK: - Metadata Section

    @ViewBuilder
    private func metadataSection(_ exercise: Exercise) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            metadataRow(label: "Equipment", value: exercise.equipment ?? "None")
            metadataRow(label: "Level", value: exercise.level.capitalized)
            metadataRow(label: "Force", value: exercise.force?.capitalized ?? "None")
            metadataRow(label: "Mechanic", value: exercise.mechanic?.capitalized ?? "None")
            metadataRow(label: "Category", value: exercise.category.capitalized)
        }
    }

    @ViewBuilder
    private func metadataRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .frame(width: 100, alignment: .leading)
            Text(value)
                .font(.body)
        }
    }

    // MARK: - Instructions Section

    @ViewBuilder
    private func instructionsSection(_ exercise: Exercise) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Instructions")
                .font(.title3.weight(.semibold))
                .padding(.bottom, 16)

            if exercise.instructions.isEmpty {
                Text("No instructions available.")
                    .font(.body)
                    .foregroundColor(.secondary)
            } else {
                ForEach(Array(exercise.instructions.enumerated()), id: \.offset) { index, step in
                    HStack(alignment: .top, spacing: 12) {
                        Text("\(index + 1).")
                            .font(.body.weight(.semibold))
                            .foregroundColor(.appAccent)
                            .frame(width: 28, alignment: .trailing)

                        Text(step)
                            .font(.body)
                    }
                    .padding(.bottom, 16)
                }
            }
        }
    }

    // MARK: - Flow Observation

    private func observeExercise() async {
        do {
            for try await value in asyncSequence(for: viewModel.exercise) {
                self.exercise = value
            }
        } catch {
            print("ExerciseDetail flow observation error: \(error)")
        }
    }
}
