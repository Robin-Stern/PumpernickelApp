import Foundation
import UserNotifications

/// D-19-15 — 5 notification triggers from UI-SPEC. Single category id
/// `workout.geofence` keeps all phase-19 notifications routable for a
/// future user-managed "do not disturb" toggle.
enum GeofenceNotification {
    case exitDetected
    case reEntered
    case graceExpired(loggedSets: Int, penaltyXp: Int)
    case earlyExitWithBudget(remainingAfter: Int)
    case earlyExitWithPenalty(penaltyXp: Int)

    var title: String {
        switch self {
        case .exitDetected: return "Du hast die Zone verlassen"
        case .reEntered: return "Workout läuft weiter"
        case .graceExpired: return "Workout beendet"
        case .earlyExitWithBudget: return "Workout beendet"
        case .earlyExitWithPenalty: return "Workout beendet"
        }
    }

    var body: String {
        switch self {
        case .exitDetected:
            return "5 Minuten um zurückzukommen, sonst wird das Workout abgebrochen."
        case .reEntered:
            return "Willkommen zurück. Weiter geht's."
        case .graceExpired(let logged, let penalty):
            return "Du hast die Trainingszone verlassen. \(logged) Sätze wurden gespeichert, \(penalty) XP abgezogen."
        case .earlyExitWithBudget(let remaining):
            return "Early Exit genutzt — kein XP-Abzug. Verbleibend diesen Monat: \(remaining)."
        case .earlyExitWithPenalty(let penalty):
            return "\(penalty) XP abgezogen für vorzeitiges Beenden."
        }
    }

    static let categoryId = "workout.geofence"
}

extension UNUserNotificationCenter {
    /// Posts a Phase-19 notification. Caller is responsible for having
    /// requested authorization earlier in the flow (Permission Rationale or
    /// first workout-start).
    func postGeofenceNotification(_ notification: GeofenceNotification) {
        let content = UNMutableNotificationContent()
        content.title = notification.title
        content.body = notification.body
        content.sound = .default
        content.categoryIdentifier = GeofenceNotification.categoryId

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil   // immediate
        )
        add(request) { error in
            if let error { print("GeofenceNotification post failed: \(error)") }
        }
    }
}
