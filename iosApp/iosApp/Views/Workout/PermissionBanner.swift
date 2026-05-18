import SwiftUI
import Shared
import UIKit

/// D-19-10 + D-19-11 — persistent warning banner shown above the rest-timer
/// section when geofence is degraded. Tap → opens system Settings.
struct PermissionBanner: View {
    enum Variant {
        case whenInUseOnly   // D-19-10
        case denied          // D-19-11
    }

    let variant: Variant

    var body: some View {
        Button(action: openSystemSettings) {
            HStack(spacing: 12) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(.orange)
                    .font(.body)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.body)
                        .foregroundColor(.primary)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundColor(.secondary)
                    .font(.caption)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color(.secondarySystemGroupedBackground))
            .cornerRadius(12)
        }
        .buttonStyle(.plain)
        .accessibilityHint("Doppeltippen, um die App-Einstellungen zu öffnen")
    }

    private var title: String {
        switch variant {
        case .whenInUseOnly: return "Enforcement nur im Vordergrund"
        case .denied: return "Enforcement nicht aktiv"
        }
    }

    private var subtitle: String {
        switch variant {
        case .whenInUseOnly: return "Workout-Enforcement ist nur aktiv, solange die App geöffnet ist. Tippen für vollen Zugriff."
        case .denied: return "Standortzugriff fehlt. Tippen für Einstellungen."
        }
    }

    private func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}
