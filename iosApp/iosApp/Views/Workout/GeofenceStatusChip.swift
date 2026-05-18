import SwiftUI
import Shared

/// D-19-16 — 4-state pill chip displayed in WorkoutSessionView toolbar.
/// Passive (not tappable); state changes via VM observation only.
struct GeofenceStatusChip: View {
    let state: GeofenceUiState

    var body: some View {
        let style = ChipStyle.from(state: state)
        HStack(spacing: 4) {
            Image(systemName: style.symbolName)
                .font(.caption)
            Text(style.label)
                .font(style.useMonospaceDigit ? .subheadline.monospacedDigit() : .caption)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(
            Capsule().fill(style.background)
        )
        .foregroundColor(style.foreground)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Geofence-Status: \(style.accessibilityLabel)")
        .accessibilityValue(style.accessibilityValue)
    }
}

private struct ChipStyle {
    let symbolName: String
    let label: String
    let background: Color
    let foreground: Color
    let useMonospaceDigit: Bool
    let accessibilityLabel: String
    let accessibilityValue: String

    static func from(state: GeofenceUiState) -> ChipStyle {
        switch state {
        case is GeofenceUiState.InZone:
            return ChipStyle(
                symbolName: "location.fill",
                label: "In Zone",
                background: Color.appAccent.opacity(0.15),
                foreground: Color.appAccent,
                useMonospaceDigit: false,
                accessibilityLabel: "In Zone",
                accessibilityValue: ""
            )
        case let grace as GeofenceUiState.GracePeriod:
            let remaining = Int(grace.remainingSeconds)
            let m = remaining / 60
            let s = remaining % 60
            let formatted = String(format: "%d:%02d", m, s)
            return ChipStyle(
                symbolName: "exclamationmark.triangle.fill",
                label: "Grace \(formatted)",
                background: Color.orange.opacity(0.15),
                foreground: .orange,
                useMonospaceDigit: true,
                accessibilityLabel: "Gracetime",
                accessibilityValue: "\(m) Minuten \(s) Sekunden verbleibend"
            )
        case is GeofenceUiState.Exited:
            return ChipStyle(
                symbolName: "xmark.octagon.fill",
                label: "Zone verlassen",
                background: Color.red.opacity(0.15),
                foreground: .red,
                useMonospaceDigit: false,
                accessibilityLabel: "Zone verlassen",
                accessibilityValue: ""
            )
        default:   // Inactive
            return ChipStyle(
                symbolName: "location.slash",
                label: "Inaktiv",
                background: Color(.systemGray5),
                foreground: .secondary,
                useMonospaceDigit: false,
                accessibilityLabel: "Inaktiv",
                accessibilityValue: ""
            )
        }
    }
}
