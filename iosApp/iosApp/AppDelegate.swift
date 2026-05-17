import UIKit
import Shared

/// D-19-04 — UIApplicationDelegate that bootstraps Koin so the single
/// IosGeofenceProvider singleton is constructed (with its location delegate
/// attached) BEFORE SwiftUI's first View renders. When iOS wakes the app
/// because the user crossed a geofence boundary, this method runs with
/// `launchOptions[.location] != nil`. No second location manager is created
/// here — iOS only delivers callbacks to the manager that originally called
/// `startMonitoring` (the Kotlin-side provider's). Resolving the provider
/// via Koin synchronously ensures the delegate is wired before the OS posts
/// the exit event. The provider persists the EXIT to DataStore and emits
/// on its SharedFlow. Wave 3's WorkoutSessionViewModel reconciles the
/// persisted sentinel on resume (D-19-04).
class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Phase 19 WARN-19-2 fix — clear the per-session rationale flag on cold-start
        // so the rationale sheet shows once per app launch (UI-SPEC: "once per workout-try").
        // The @AppStorage("workout.geofence.rationale_shown_session") binding in
        // WorkoutSessionView reads this same UserDefaults key.
        UserDefaults.standard.set(false, forKey: "workout.geofence.rationale_shown_session")

        // 1. Koin must be up before anything else touches the shared graph.
        KoinInitIosKt.doInitKoinIos()

        // 2. DEBUG-only: replace real GeofenceProvider with manual-trigger mock for
        //    Simulator/UAT. Must run BEFORE force-resolving the provider below so the
        //    override binding is in place when getGeofenceProvider() is called.
        #if DEBUG
        KoinHelper.shared.loadDebugGeofenceOverride()
        #endif

        // 3. Force-resolve the GeofenceProvider singleton so its internal delegate is
        //    attached. In release: resolves IosGeofenceProvider (real GPS).
        //    In debug: resolves DebugGeofenceProvider (manual trigger, no GPS needed).
        _ = KoinHelper.shared.getGeofenceProvider()

        // 4. Eagerly initialize the Swift-native LocationPermissionRequester on the main
        //    thread so its CLLocationManager is bound to a runloop-active thread BEFORE
        //    any SwiftUI Task touches .shared from a background dispatcher.
        //    See WorkoutSessionView.swift LocationPermissionRequester for context.
        _ = LocationPermissionRequester.shared

        // 5. The launchOptions[.location] flag is informational — we don't
        //    need to read it because the resolved provider's delegate will
        //    receive `didExitRegion` directly from iOS and forward it both
        //    to the SharedFlow and to PendingGeofenceExitStore.
        return true
    }
}
