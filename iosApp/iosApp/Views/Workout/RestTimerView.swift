import SwiftUI

struct RestTimerView: View {
    let remainingSeconds: Int32
    let totalSeconds: Int32

    var body: some View {
        VStack(spacing: 8) {
            Text("Rest")
                .font(.headline)
                .foregroundColor(.secondary)

            Text("\(remainingSeconds)")
                .font(.system(size: 64, weight: .bold, design: .rounded))
                .monospacedDigit()
                .foregroundColor(remainingSeconds <= 3 ? .red : .primary)

            // Progress indicator
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color(UIColor.systemGray5))
                        .frame(height: 8)

                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color(red: 0.4, green: 0.733, blue: 0.416))
                        .frame(
                            width: totalSeconds > 0
                                ? geo.size.width * CGFloat(remainingSeconds) / CGFloat(totalSeconds)
                                : 0,
                            height: 8
                        )
                        .animation(.linear(duration: 1), value: remainingSeconds)
                }
            }
            .frame(height: 8)
            .padding(.horizontal, 32)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
    }
}
