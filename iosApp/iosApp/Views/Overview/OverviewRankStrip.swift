import SwiftUI
import Shared

/// D-11 / D-18 compact rank strip. Shown as the first child of OverviewView's
/// scrollable content stack (above the muscle-activity card).
///
/// Two states:
///   - Unranked → lock icon + literal: "Unranked — complete a workout to unlock Silver"
///                (D-11 — copy is LOAD-BEARING; do not localise or rephrase in this phase).
///   - Ranked   → medal icon + rank displayName + "totalXp / nextRankThreshold XP"
///                + ProgressView showing progress toward the next rank.
///
/// This view is passive — it takes the current `RankState` as input. The owner
/// (OverviewView) holds the VM + the @State var + runs the asyncSequence observation.
/// That keeps OverviewView as the single subscription owner, identical to how
/// OverviewView already handles the other Kotlin Flows on its view model.
struct OverviewRankStrip: View {
    let rankState: Shared.RankState

    var body: some View {
        HStack(spacing: 12) {
            if rankState is SharedRankStateUnranked {
                unrankedContent
            } else if let ranked = rankState as? SharedRankStateRanked {
                rankedContent(ranked)
            } else {
                // Defensive — should never hit because RankState is a closed sealed class.
                unrankedContent
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(16)
    }

    // MARK: - Unranked (D-11)

    private var unrankedContent: some View {
        HStack(spacing: 12) {
            Image(systemName: "lock.fill")
                .font(.title2)
                .foregroundColor(.secondary)
            // D-11: exact copy required. Do NOT localise or paraphrase.
            Text("Unranked — complete a workout to unlock Silver")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.leading)
            Spacer()
        }
    }

    // MARK: - Ranked (D-18)

    private func rankedContent(_ ranked: SharedRankStateRanked) -> some View {
        HStack(spacing: 14) {
            // Tier icon — rosette chosen for CSGO-esque feel per D-19 tone.
            Image(systemName: "rosette")
                .font(.system(size: 34, weight: .bold))
                .foregroundColor(tint(for: ranked.currentRank))
                .frame(width: 44, height: 44)

            VStack(alignment: .leading, spacing: 4) {
                Text(ranked.currentRank.displayName)
                    .font(.headline)
                    .foregroundColor(.primary)

                Text(xpLabel(ranked))
                    .font(.caption)
                    .foregroundColor(.secondary)

                ProgressView(value: progress(ranked), total: 1.0)
                    .tint(tint(for: ranked.currentRank))
                    .frame(height: 6)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: - Derived values

    /// "totalXp / nextRankThreshold XP" — or "totalXp XP (MAX)" at Global Elite.
    private func xpLabel(_ r: SharedRankStateRanked) -> String {
        if let next = r.nextRankThreshold {
            // Int64 from Kotlin → need .int64Value from KotlinLong-bridged objects.
            // KotlinLong on Swift side is imported as NSNumber subclass; use .int64Value
            // for the integer rendering. totalXp is non-optional Int64.
            return "\(r.totalXp) / \(next.int64Value) XP"
        } else {
            return "\(r.totalXp) XP (MAX)"
        }
    }

    /// 0.0–1.0 progress from currentRankThreshold to nextRankThreshold.
    /// At max rank (no next), returns 1.0.
    private func progress(_ r: SharedRankStateRanked) -> Double {
        guard let next = r.nextRankThreshold?.int64Value else { return 1.0 }
        let cur = r.currentRankThreshold
        guard next > cur else { return 1.0 }
        let earned = max(0, r.totalXp - cur)
        let span = next - cur
        return min(1.0, Double(earned) / Double(span))
    }

    /// Tier-banded tint matching UnlockModalView.rankTint. Keep both views in
    /// sync — future polish can swap to per-rank gradients.
    private func tint(for rank: SharedRank) -> Color {
        switch rank.tier {
        case 1, 2:    return Color(red: 0.75, green: 0.75, blue: 0.78)  // silver
        case 3, 4, 5: return Color(red: 1.00, green: 0.84, blue: 0.20)  // gold
        case 6, 7:    return Color(red: 0.60, green: 0.80, blue: 1.00)  // guardian blue
        case 8, 9:    return Color(red: 0.73, green: 0.41, blue: 0.78)  // eagle/supreme purple
        default:      return Color(red: 1.00, green: 0.25, blue: 0.25)  // global elite red
        }
    }
}

// MARK: - Shared type aliases
// Note: KMP-Native exports sealed subclasses as nested Swift types (RankState.Ranked),
// not flat top-level names (RankStateRanked). Use Shared.RankState.Ranked.

private typealias SharedRankState = Shared.RankState
private typealias SharedRankStateUnranked = Shared.RankState.Unranked
private typealias SharedRankStateRanked = Shared.RankState.Ranked
private typealias SharedRank = Shared.Rank
