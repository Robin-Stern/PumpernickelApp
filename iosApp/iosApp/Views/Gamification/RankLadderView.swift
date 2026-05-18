import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

/// D-151-11 / D-151-12 rank ladder screen. Lists all 10 CSGO tiers ordered
/// SILVER → GLOBAL_ELITE, each showing tier badge + rank name + threshold XP
/// + a status indicator (PASSED / CURRENT / LOCKED).
///
/// Reached from OverviewView via a NavigationLink on the OverviewRankStrip.
struct RankLadderView: View {
    private let viewModel = RanksAndAchievementsKoinHelper().getRanksAndAchievementsViewModel()

    @State private var uiState: SharedRankLadderUiState? = nil

    var body: some View {
        Group {
            if uiState == nil || uiState!.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, minHeight: 200)
            } else if let state = uiState {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        if state.isUnranked {
                            unrankedHeaderCard
                        }
                        ForEach(rows(from: state), id: \.rank.tier) { row in
                            rankRowCard(row)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 16)
                }
            }
        }
        .navigationTitle("Ranks")
        .navigationBarTitleDisplayMode(.inline)
        .task { await observeUiState() }
    }

    // MARK: - Unranked Header

    private var unrankedHeaderCard: some View {
        HStack(spacing: 12) {
            Image(systemName: "lock.fill")
                .font(.title2)
                .foregroundColor(.secondary)
            // D-151-23 / D-11: load-bearing copy — must match OverviewRankStrip character-for-character.
            Text("Unranked — complete a workout to unlock Silver")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.leading)
            Spacer()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(16)
    }

    // MARK: - Rank Row Card

    private func rankRowCard(_ row: SharedRankRow) -> some View {
        let isLocked = row.status == .locked
        let isCurrent = row.status == .current

        return HStack(spacing: 12) {
            Image(systemName: "rosette")
                .font(.system(size: 30, weight: .bold))
                .foregroundColor(tint(for: row.rank))
                .frame(width: 40, height: 40)

            VStack(alignment: .leading, spacing: 4) {
                Text("#\(row.rank.tier)  \(row.rank.displayName)")
                    .font(.headline)
                    .foregroundColor(.primary)

                Text(formattedXp(row.threshold) + " XP")
                    .font(.caption)
                    .foregroundColor(.secondary)

                HStack(spacing: 4) {
                    Image(systemName: statusIcon(row))
                        .font(.caption2)
                        .foregroundColor(statusColor(row))
                    Text(statusLabel(row))
                        .font(.caption2)
                        .foregroundColor(statusColor(row))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .background(isCurrent
            ? Color.appAccent.opacity(0.12)
            : Color(.secondarySystemGroupedBackground))
        .cornerRadius(16)
        .opacity(isLocked ? 0.45 : 1.0)
    }

    // MARK: - Helpers

    private func statusIcon(_ row: SharedRankRow) -> String {
        switch row.status {
        case .passed:  return "checkmark.circle.fill"
        case .current: return "star.fill"
        default:       return "lock.fill"
        }
    }

    private func statusColor(_ row: SharedRankRow) -> Color {
        switch row.status {
        case .passed, .current: return .appAccent
        default:                return .secondary
        }
    }

    private func statusLabel(_ row: SharedRankRow) -> String {
        switch row.status {
        case .passed:  return "Passed"
        case .current: return "Current rank"
        default:
            // D-151-07: SILVER in Unranked state shows "First workout unlocks"
            if (uiState?.isUnranked == true) && row.rank.tier == 1 {
                return "First workout unlocks"
            }
            if let xp = row.xpToReach {
                return "\(formattedXp(xp.int64Value)) XP to unlock"
            }
            return "Locked"
        }
    }

    private func tint(for rank: SharedRank) -> Color {
        switch rank.tier {
        case 1, 2:    return Color(red: 0.75, green: 0.75, blue: 0.78)
        case 3, 4, 5: return Color(red: 1.00, green: 0.84, blue: 0.20)
        case 6, 7:    return Color(red: 0.60, green: 0.80, blue: 1.00)
        case 8, 9:    return Color(red: 0.73, green: 0.41, blue: 0.78)
        default:      return Color(red: 1.00, green: 0.25, blue: 0.25)
        }
    }

    private func formattedXp(_ value: Int64) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        return formatter.string(from: NSNumber(value: value)) ?? "\(value)"
    }

    // MARK: - List extraction

    private func rows(from state: SharedRankLadderUiState) -> [SharedRankRow] {
        (state.rows as? [SharedRankRow]) ?? state.rows.compactMap { $0 as? SharedRankRow }
    }

    // MARK: - Observer

    private func observeUiState() async {
        do {
            for try await state in asyncSequence(for: viewModel.rankLadderStateFlow) {
                self.uiState = state
            }
        } catch {
            print("RankLadder observation error: \(error)")
        }
    }
}

// MARK: - Type aliases

private typealias SharedRankLadderUiState = RankLadderUiState
private typealias SharedRankRow = RankRow
private typealias SharedRank = Rank
