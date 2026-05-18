import SwiftUI

/// D-19-08 — two confirm-dialog variants. Picks copy + button role based on
/// the current EarlyExitBudget snapshot at trigger time.
struct EarlyExitDialogConfig {
    /// Total budget (constant, EARLY_EXIT_BUDGET_PER_MONTH = 2).
    let total: Int
    /// Remaining BEFORE this consumption.
    let remaining: Int
    /// Calculated penalty XP for the "no budget" branch.
    let penaltyXp: Int

    var hasBudget: Bool { remaining >= 1 }

    var title: String {
        hasBudget ? "Workout frühzeitig beenden?" : "Workout abbrechen?"
    }

    var message: String {
        if hasBudget {
            return "Verbraucht 1 von \(remaining) Early Exits diesen Monat."
        } else {
            return "Du hast diesen Monat keine Early Exits mehr. Frühzeitiges Beenden kostet \(penaltyXp) XP. Fortsetzen?"
        }
    }

    var primaryButtonLabel: String {
        hasBudget ? "Frühzeitig beenden" : "Trotzdem beenden"
    }

    var primaryButtonRole: ButtonRole? {
        hasBudget ? nil : .destructive
    }
}
