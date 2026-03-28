import SwiftUI
import Shared

struct WorkoutSetRow: View {
    let setIndex: Int32
    let actualReps: Int32
    let actualWeightKgX10: Int32
    let isCompleted: Bool
    var onTap: (() -> Void)? = nil

    var body: some View {
        Button {
            onTap?()
        } label: {
            HStack {
                Image(systemName: isCompleted ? "checkmark.circle.fill" : "circle")
                    .foregroundColor(isCompleted ? Color(red: 0.4, green: 0.733, blue: 0.416) : .secondary)

                Text("Set \(setIndex + 1)")
                    .font(.body.weight(.medium))

                Spacer()

                Text("\(actualReps) reps")
                    .font(.body)
                    .foregroundColor(.secondary)

                Text(formatWeight(actualWeightKgX10))
                    .font(.body.weight(.medium))
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 12)
            .background(Color(UIColor.secondarySystemBackground))
            .cornerRadius(10)
        }
        .buttonStyle(.plain)
        .disabled(onTap == nil)
    }

    private func formatWeight(_ kgX10: Int32) -> String {
        let whole = kgX10 / 10
        let decimal = kgX10 % 10
        return decimal == 0 ? "\(whole) kg" : "\(whole).\(decimal) kg"
    }
}
