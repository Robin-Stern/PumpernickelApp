import SwiftUI
import Shared
import KMPNativeCoroutinesAsync
import KMPNativeCoroutinesCore
import UIKit

/// D-19-16 — Settings detail screen for Phase 19. Three sections:
///   1) "So funktioniert's" — feature explanation
///   2) "Status" — current permission state + Settings deep-link
///   3) "Early Exits diesen Monat" — used/budget + next-reset date
///
/// Budget data is sourced from WorkoutSessionViewModel.earlyExitBudgetFlow
/// (a @NativeCoroutinesState-annotated StateFlow) rather than directly from
/// EarlyExitTracker.budget (whose raw Kotlinx_coroutines_coreFlow requires a
/// Gradle framework rebuild to gain NativeCoroutines export). Both observe the
/// same SettingsRepository.earlyExits DataStore stream — identical data.
struct WorkoutEnforcementDetailView: View {
    private let permissionController = KoinHelper.shared.getPermissionController()
    // Use WorkoutSessionViewModel to source earlyExitBudget — it exposes the same
    // SettingsRepository.earlyExits stream via @NativeCoroutinesState (earlyExitBudgetFlow).
    private let sessionViewModel = KoinHelper.shared.getWorkoutSessionViewModel()

    @State private var status: LocationPermissionStatus = .notDetermined
    @State private var budget: EarlyExitBudget? = nil

    // BLOCKER-19-5 fix — mirror of Kotlin `EarlyExitTracker.EARLY_EXIT_BUDGET_PER_MONTH`.
    // Update this if the Kotlin constant changes; both values MUST stay in sync.
    private static let earlyExitBudgetPerMonth = 2

    var body: some View {
        Form {
            Section {
                Text("Sobald du dein erstes Set loggst, setzen wir einen ~50m-Radius um deinen Standort. Verlässt du diesen Bereich vor Workout-Ende, wird das Workout abgebrochen und es gibt einen XP-Abzug.")
                    .font(.body)
                    .foregroundColor(.secondary)
            } header: {
                Text("So funktioniert's")
            }

            Section {
                Text(statusBody)
                    .font(.body)
                    .foregroundColor(.secondary)
                Button("In Einstellungen ändern") {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                }
                .foregroundColor(.appAccent)
            } header: {
                Text("Status")
            }

            Section {
                Text(earlyExitBody)
                    .font(.body)
                    .foregroundColor(.secondary)
            } header: {
                Text("Early Exits diesen Monat")
            }
        }
        .navigationTitle("Workout Enforcement")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            async let s: () = refreshStatus()
            async let b: () = observeBudget()
            _ = await (s, b)
        }
    }

    private var statusBody: String {
        switch status {
        case .always: return "Hintergrund-Standortzugriff erlaubt"
        case .whenInUse: return "Nur Zugriff im Vordergrund — Enforcement eingeschränkt"
        case .denied, .restricted: return "Standortzugriff nicht erlaubt — Enforcement deaktiviert"
        default: return "Status wird ermittelt..."
        }
    }

    private var earlyExitBody: String {
        guard let budget = budget else { return "Wird geladen..." }
        // BLOCKER-19-5 fix — Swift-side constant mirrors Kotlin
        // EarlyExitTracker.EARLY_EXIT_BUDGET_PER_MONTH.
        return "\(budget.used) von \(Self.earlyExitBudgetPerMonth) genutzt — Reset am 1. \(nextMonthName(currentYearMonth: budget.yearMonth))"
    }

    @MainActor
    private func refreshStatus() async {
        // suspend fun bridges directly to Swift async (Swift concurrency bridge).
        let value = try? await permissionController.currentLocationStatus()
        if let value { self.status = value }
    }

    @MainActor
    private func observeBudget() async {
        // earlyExitBudgetFlow is @NativeCoroutinesState on WorkoutSessionViewModel —
        // reliable NativeFlow export without requiring a Gradle framework rebuild.
        do {
            for try await value in asyncSequence(for: sessionViewModel.earlyExitBudgetFlow) {
                self.budget = value
            }
        } catch {
            print("Budget observation error: \(error)")
        }
    }

    private func nextMonthName(currentYearMonth: String) -> String {
        let parts = currentYearMonth.split(separator: "-")
        guard parts.count == 2, let year = Int(parts[0]), let month = Int(parts[1]) else { return "" }
        let next = month == 12 ? 1 : month + 1
        let nextYear = month == 12 ? year + 1 : year
        var comps = DateComponents()
        comps.year = nextYear
        comps.month = next
        comps.day = 1
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "de_DE")
        formatter.dateFormat = "LLLL"
        if let date = Calendar.current.date(from: comps) {
            return formatter.string(from: date)
        }
        return ""
    }
}
