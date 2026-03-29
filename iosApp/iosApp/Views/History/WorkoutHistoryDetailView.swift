import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct WorkoutHistoryDetailView: View {
    let workoutId: Int64

    private let viewModel = KoinHelper.shared.getWorkoutHistoryViewModel()

    @State private var workout: CompletedWorkout? = nil
    @State private var weightUnit: WeightUnit = .kg

    var body: some View {
        Group {
            if let workout = workout {
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        // Header: name, date, duration, total volume (per D-06)
                        detailHeader(workout)

                        // Exercise sections (per D-07)
                        ForEach(Array(workout.exercises.enumerated()), id: \.offset) { _, exercise in
                            exerciseSection(exercise)
                        }
                    }
                    .padding()
                }
            } else {
                VStack {
                    ProgressView()
                    Text("Loading...")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
            }
        }
        .navigationTitle("Workout Detail")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            viewModel.loadWorkoutDetail(workoutId: workoutId)
            await withTaskGroup(of: Void.self) { group in
                group.addTask { await observeDetail() }
                group.addTask { await observeWeightUnit() }
            }
        }
        .onDisappear {
            viewModel.clearDetail()
        }
    }

    private func detailHeader(_ workout: CompletedWorkout) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(workout.name)
                .font(.title2.weight(.bold))

            HStack {
                Text(formatDate(workout.startTimeMillis))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                Spacer()
                Text(formatDuration(workout.durationMillis))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }

            let totalVolume = calculateTotalVolume(workout)
            Text("Total Volume: \(weightUnit.formatVolume(totalVolumeKgX10: totalVolume))")
                .font(.subheadline.weight(.medium))
                .foregroundColor(.secondary)

            Divider()
        }
    }

    // Per D-07: exercise name as section header, sets listed with set number, reps, weight
    private func exerciseSection(_ exercise: CompletedExercise) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(exercise.exerciseName)
                .font(.headline)

            ForEach(Array(exercise.sets.enumerated()), id: \.offset) { _, set in
                HStack {
                    Text("Set \(set.setIndex + 1)")
                        .font(.body)
                        .frame(width: 60, alignment: .leading)
                    Text("\(set.actualReps) reps")
                        .font(.body)
                        .foregroundColor(.secondary)
                    Spacer()
                    Text(weightUnit.formatWeight(kgX10: set.actualWeightKgX10))
                        .font(.body.weight(.medium))
                }
                .padding(.vertical, 4)
                .padding(.horizontal, 12)
                .background(Color(UIColor.secondarySystemBackground))
                .cornerRadius(8)
            }
        }
    }

    // Per D-05: sum of (actualReps * actualWeightKgX10) across all sets
    private func calculateTotalVolume(_ workout: CompletedWorkout) -> Int64 {
        var total: Int64 = 0
        for exercise in workout.exercises {
            for set in exercise.sets {
                total += Int64(set.actualReps) * Int64(set.actualWeightKgX10)
            }
        }
        return total
    }

    private func formatDate(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0)
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }

    private func formatDuration(_ millis: Int64) -> String {
        let totalSeconds = millis / 1000
        let h = totalSeconds / 3600
        let m = (totalSeconds % 3600) / 60
        if h > 0 {
            return "\(h)h \(m)m"
        }
        return "\(m) min"
    }

    private func observeDetail() async {
        do {
            for try await value in asyncSequence(for: viewModel.workoutDetailFlow) {
                self.workout = value
            }
        } catch {
            print("Workout detail observation error: \(error)")
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
}
