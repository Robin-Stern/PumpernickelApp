import SwiftUI
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
}
