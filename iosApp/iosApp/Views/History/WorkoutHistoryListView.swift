import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct WorkoutHistoryListView: View {
    private let viewModel = KoinHelper.shared.getWorkoutHistoryViewModel()

    @State private var summaries: [WorkoutSummary] = []
    @State private var weightUnit: WeightUnit = .kg

    var body: some View {
        Group {
            if summaries.isEmpty {
                VStack(spacing: 16) {
                    Spacer()
                    Image(systemName: "clock.arrow.circlepath")
                        .font(.system(size: 64))
                        .foregroundColor(Color(white: 0.62))
                    Text("No Workouts Yet")
                        .font(.title3.weight(.semibold))
                    Text("Complete a workout to see your history here.")
                        .font(.body)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                    Spacer()
                }
            } else {
                List(summaries, id: \.id) { summary in
                    NavigationLink(destination: WorkoutHistoryDetailView(workoutId: summary.id)) {
                        historyRow(summary)
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("History")
        .task {
            await withTaskGroup(of: Void.self) { group in
                group.addTask { await observeSummaries() }
                group.addTask { await observeWeightUnit() }
            }
        }
    }

    // Per D-03: date, template name, exercise count, total volume, duration
    private func historyRow(_ summary: WorkoutSummary) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(summary.name)
                    .font(.body.weight(.semibold))
                Spacer()
                Text(formatDate(summary.startTimeMillis))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            HStack {
                Text("\(summary.exerciseCount) exercise\(summary.exerciseCount == 1 ? "" : "s")")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                Text("\u{00B7}")
                    .foregroundColor(.secondary)
                Text(weightUnit.formatVolume(totalVolumeKgX10: summary.totalVolumeKgX10))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                Spacer()
                Text(formatDuration(summary.durationMillis))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }

    // Relative date formatting: Today, Yesterday, or "Mar 27" for older
    private func formatDate(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0)
        let calendar = Calendar.current
        if calendar.isDateInToday(date) {
            return "Today"
        } else if calendar.isDateInYesterday(date) {
            return "Yesterday"
        } else {
            let formatter = DateFormatter()
            formatter.dateFormat = "MMM d"
            return formatter.string(from: date)
        }
    }

    private func formatDuration(_ millis: Int64) -> String {
        let totalSeconds = millis / 1000
        let h = totalSeconds / 3600
        let m = (totalSeconds % 3600) / 60
        if h > 0 {
            return "\(h)h \(m)m"
        }
        return "\(m)m"
    }

    private func observeSummaries() async {
        do {
            for try await value in asyncSequence(for: viewModel.workoutSummariesFlow) {
                self.summaries = value
            }
        } catch {
            print("History summaries observation error: \(error)")
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
