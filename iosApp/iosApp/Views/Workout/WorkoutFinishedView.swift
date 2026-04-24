import SwiftUI

struct WorkoutFinishedView: View {
    let workoutName: String
    let durationMillis: Int64
    let totalSets: Int32
    let totalExercises: Int32
    var onDone: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 72))
                .foregroundColor(.appAccent)
                .accessibilityHidden(true)

            Text("Workout Complete!")
                .font(.title.weight(.bold))

            VStack(spacing: 12) {
                SummaryRow(label: "Workout", value: workoutName)
                SummaryRow(label: "Duration", value: formatDuration(durationMillis))
                SummaryRow(label: "Exercises", value: "\(totalExercises)")
                SummaryRow(label: "Sets", value: "\(totalSets)")
            }
            .padding()
            .background(Color(UIColor.secondarySystemBackground))
            .cornerRadius(16)
            .padding(.horizontal, 32)

            Spacer()

            Button("Done") {
                onDone()
            }
            .font(.body.weight(.semibold))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(Color.appAccent)
            .cornerRadius(12)
            .padding(.horizontal, 32)
            .padding(.bottom, 32)
            .accessibilityLabel("Close workout summary")
        }
    }

    private func formatDuration(_ millis: Int64) -> String {
        let totalSeconds = millis / 1000
        let h = totalSeconds / 3600
        let m = (totalSeconds % 3600) / 60
        let s = totalSeconds % 60
        if h > 0 { return String(format: "%dh %02dm", h, m) }
        return String(format: "%dm %02ds", m, s)
    }
}

private struct SummaryRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .fontWeight(.semibold)
        }
        .accessibilityElement(children: .combine)
    }
}
