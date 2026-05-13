import SwiftUI
import UIKit
import Shared
import KMPNativeCoroutinesAsync
import CoreLocation

@main
struct PumpernickelApp: App {
    private let locationManager = CLLocationManager()

    init() {
        KoinInitIosKt.doInitKoinIos()
        locationManager.requestWhenInUseAuthorization()
    }

    var body: some Scene {
        WindowGroup {
            AppRootView()
        }
    }
}

private struct AppRootView: View {
    private var theme = ThemeManager.shared
    private let viewModel = KoinHelper.shared.getSettingsViewModel()

    var body: some View {
        MainTabView()
            .preferredColorScheme(theme.colorScheme)
            .tint(.appAccent)
            .task {
                // Gamification: seed achievement_state + retroactive XP replay.
                // Idempotent — safe to call every launch.
                GamificationStartupIos.shared.trigger()

                // Phase 17 D-17-19 — attach root view controller so
                // PhotoCaptureLauncher.ios.kt can present the system camera
                // and library pickers. Without this attach, both pickers
                // resolve to nil immediately and silently no-op.
                attachPhotoCapturePresenter()

                await withTaskGroup(of: Void.self) { group in
                    group.addTask { await observeTheme() }
                    group.addTask { await observeAccentColor() }
                }
            }
    }

    private func observeTheme() async {
        do {
            for try await value in asyncSequence(for: viewModel.appThemeFlow) {
                theme.applyTheme(value)
            }
        } catch {
            print("Theme observation error: \(error)")
        }
    }

    private func observeAccentColor() async {
        do {
            for try await value in asyncSequence(for: viewModel.accentColorFlow) {
                theme.applyAccentColor(value)
            }
        } catch {
            print("Accent color observation error: \(error)")
        }
    }

    /// Walks the active UIWindowScene to find the SwiftUI hosting controller
    /// and registers it with the Kotlin-side photo capture presenter. Brief
    /// delay gives SwiftUI's UIHostingController time to mount in the
    /// window's view hierarchy after first launch (without this delay,
    /// `connectedScenes.first` may not yet be in `.foregroundActive` state).
    private func attachPhotoCapturePresenter() {
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 150_000_000)  // 0.15s
            for _ in 0..<10 {
                if let scene = UIApplication.shared.connectedScenes
                    .compactMap({ $0 as? UIWindowScene })
                    .first(where: { $0.activationState == .foregroundActive })
                    ?? UIApplication.shared.connectedScenes
                    .compactMap({ $0 as? UIWindowScene })
                    .first,
                   let root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController
                            ?? scene.windows.first?.rootViewController
                {
                    PhotoCapturePresenterHolder.shared.attach(controller: root)
                    return
                }
                try? await Task.sleep(nanoseconds: 100_000_000)  // retry every 0.1s
            }
        }
    }
}
