import SwiftUI
import Shared

/// D-19-09 — explains why the app needs Always-Allow before triggering
/// the native iOS system dialog. Backdrop-tap or swipe-down counts as
/// 'Später' (dismiss).
struct PermissionRationaleSheet: View {
    var onActivate: () -> Void
    var onLater: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 24) {
            Spacer().frame(height: 8)
            Text("Workout Enforcement aktivieren?")
                .font(.title2.weight(.bold))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Text("Damit dein Workout auch zählt wenn du das Handy weglegst, brauchen wir Standortzugriff im Hintergrund. Wir tracken nur den ~50m-Radius um dein Gym während aktiver Workouts.")
                .font(.body)
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)
                .padding(.horizontal, 32)

            Text("iOS fragt erst nach Zugriff während der Nutzung — beim nächsten Workout-Start fragen wir dann nach Hintergrund-Zugriff.")
                .font(.caption)
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)
                .padding(.horizontal, 32)

            Spacer()

            VStack(spacing: 12) {
                Button {
                    onActivate()
                    dismiss()
                } label: {
                    Text("Aktivieren")
                        .font(.body.weight(.semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color.appAccent)
                        .cornerRadius(12)
                }
                Button {
                    onLater()
                    dismiss()
                } label: {
                    Text("Später")
                        .font(.body)
                        .foregroundColor(.appAccent)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                }
            }
            .padding(.horizontal, 32)
            .padding(.bottom, 48)
        }
        .presentationDetents([.medium])
    }
}
