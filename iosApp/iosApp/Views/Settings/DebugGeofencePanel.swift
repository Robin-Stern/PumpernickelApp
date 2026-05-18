#if DEBUG
import SwiftUI
import Shared

/// DEBUG-only Settings panel. Calls `DebugGeofenceProvider.trigger*` via KoinHelper
/// to emit synthetic geofence events for the active workout's region id.
/// Not present in release builds (entire file wrapped in `#if DEBUG`).
struct DebugGeofencePanel: View {
    // The regionId is read from DebugGeofenceProvider.lastRegisteredRegionId (synchronous),
    // which is set by WorkoutSessionViewModel when it calls geofenceProvider.register().
    // Refreshed on appear and whenever buttons are tapped.
    @State private var activeRegionId: String?
    @State private var providerIsDebug: Bool = false

    private func refreshState() {
        let debug = KoinHelper.shared.getDebugGeofenceProvider()
        providerIsDebug = (debug != nil)
        activeRegionId = debug?.lastRegisteredRegionId
    }

    var body: some View {
        Section("DEBUG — Geofence Mock") {
            Text("Region: \(activeRegionId ?? "(no active workout)")")
                .font(.caption)
                .foregroundColor(.secondary)
            HStack(spacing: 8) {
                Button("Enter") { triggerEnter() }
                    .buttonStyle(.borderedProminent)
                    .disabled(activeRegionId == nil || !providerIsDebug)
                Button("Exit") { triggerExit() }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)
                    .disabled(activeRegionId == nil || !providerIsDebug)
                Button("Error") { triggerError() }
                    .buttonStyle(.bordered)
                    .disabled(activeRegionId == nil || !providerIsDebug)
            }
            if !providerIsDebug {
                Text("GeofenceProvider is NOT DebugGeofenceProvider — debug override failed.")
                    .font(.caption2)
                    .foregroundColor(.red)
            }
        }
        .onAppear { refreshState() }
    }

    private func triggerEnter() {
        guard let id = activeRegionId,
              let debug = KoinHelper.shared.getDebugGeofenceProvider() else { return }
        debug.triggerEnter(regionId: id)
    }

    private func triggerExit() {
        guard let id = activeRegionId,
              let debug = KoinHelper.shared.getDebugGeofenceProvider() else { return }
        debug.triggerExit(regionId: id)
    }

    private func triggerError() {
        guard let id = activeRegionId,
              let debug = KoinHelper.shared.getDebugGeofenceProvider() else { return }
        debug.triggerError(regionId: id, message: "Debug-triggered error")
    }
}
#endif
