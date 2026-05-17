import SwiftUI

struct WorkoutAbortedView: View {
    let workoutName: String
    let durationMillis: Int64
    let loggedSets: Int32
    let penaltyXp: Int32   // negative value
    var onDone: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 72))
                    .foregroundColor(.orange)
                    .accessibilityHidden(true)
                    .padding(.top, 32)

                Text("Workout beendet")
                    .font(.title.weight(.bold))

                Text("Du hast die Trainingszone verlassen. \(loggedSets) Sätze wurden gespeichert, \(abs(Int(penaltyXp))) XP abgezogen.")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)

                VStack(spacing: 12) {
                    SummaryRow(label: "Workout", value: workoutName)
                    SummaryRow(label: "Dauer", value: formatDuration(durationMillis))
                    SummaryRow(label: "Geloggte Sätze", value: "\(loggedSets)")
                    SummaryRow(label: "XP-Abzug", value: "−\(abs(Int(penaltyXp))) XP")
                }
                .padding()
                .background(Color(UIColor.secondarySystemBackground))
                .cornerRadius(16)
                .padding(.horizontal, 32)

                Button("Zur Übersicht") {
                    onDone()
                }
                .font(.body.weight(.semibold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(Color.orange)
                .cornerRadius(12)
                .padding(.horizontal, 32)
                .padding(.bottom, 32)
                .accessibilityLabel("Zur Übersicht zurückkehren")
            }
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
