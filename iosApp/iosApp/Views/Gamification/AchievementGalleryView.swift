import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

/// D-21 achievement gallery. Reached from SettingsView via NavigationLink.
/// Observes AchievementGalleryViewModel.uiStateFlow and renders a 2-column
/// LazyVGrid grouped by Category in the fixed order:
///   Volume → Consistency → PR Hunter → Variety ("Exercise Variety" label).
///
/// Locked tiles: 0.45 opacity, lock SF Symbol, "currentProgress / threshold" footer.
/// Unlocked tiles: full opacity, trophy SF Symbol, tier-coloured border,
///                 "Unlocked YYYY-MM-DD" footer.
struct AchievementGalleryView: View {
    private let viewModel = AchievementGalleryKoinHelper().getAchievementGalleryViewModel()

    @State private var uiState: SharedAchievementGalleryUiState?

    // Fixed category presentation order per D-14 / UAT test 9.
    private let categoryOrder: [SharedCategory] = [
        .volume,
        .consistency,
        .prHunter,
        .variety
    ]

    private let columns: [GridItem] = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12)
    ]

    var body: some View {
        ScrollView {
            if let state = uiState, !state.isLoading {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(categoryOrder, id: \.self) { category in
                        let tiles = tiles(for: category, in: state)
                        if !tiles.isEmpty {
                            categoryHeader(category)
                                .gridCellColumns(2)

                            ForEach(tiles, id: \.id) { tile in
                                AchievementTileView(tile: tile)
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, minHeight: 200)
            }
        }
        .navigationTitle("Achievements")
        .navigationBarTitleDisplayMode(.inline)
        .task { await observeUiState() }
    }

    // MARK: - Observe

    private func observeUiState() async {
        do {
            for try await state in asyncSequence(for: viewModel.uiStateFlow) {
                self.uiState = state
            }
        } catch {
            print("AchievementGallery observation error: \(error)")
        }
    }

    // MARK: - Category header

    private func categoryHeader(_ category: SharedCategory) -> some View {
        HStack {
            Text(label(for: category))
                .font(.title3.bold())
                .foregroundColor(.primary)
            Spacer()
        }
        .padding(.top, 12)
        .padding(.bottom, 4)
    }

    private func label(for category: SharedCategory) -> String {
        switch category {
        case .volume:       return "Volume"
        case .consistency:  return "Consistency"
        case .prHunter:     return "PR Hunter"
        case .variety:      return "Exercise Variety"
        default:            return "—"
        }
    }

    // MARK: - Tile extraction from Kotlin Map

    /// Kotlin Map<Category, List<AchievementTile>> surfaces as
    /// `[AnyHashable: [SharedAchievementTile]]` in Swift. Match
    /// OverviewView.swift lines 49–55 idiom for key extraction.
    private func tiles(for category: SharedCategory, in state: SharedAchievementGalleryUiState) -> [SharedAchievementTile] {
        for entry in state.tilesByCategory {
            if let key = entry.key as? SharedCategory, key == category,
               let value = entry.value as? [SharedAchievementTile] {
                return value
            }
        }
        return []
    }
}

// MARK: - Tile

private struct AchievementTileView: View {
    let tile: SharedAchievementTile

    private var isUnlocked: Bool { tile.unlockedAtMillis != nil }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: isUnlocked ? "trophy.fill" : "lock.fill")
                    .font(.title2)
                    .foregroundColor(isUnlocked ? tierColor : .secondary)
                Spacer()
                Text(tierLabel)
                    .font(.caption.bold())
                    .foregroundColor(isUnlocked ? tierColor : .secondary)
            }

            Text(tile.displayName)
                .font(.subheadline.bold())
                .foregroundColor(.primary)
                .lineLimit(2)

            Text(tile.flavourCopy)
                .font(.caption)
                .foregroundColor(.secondary)
                .lineLimit(3)

            Spacer(minLength: 4)

            Text(footerText)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .padding(12)
        .frame(maxWidth: .infinity, minHeight: 140, alignment: .topLeading)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(isUnlocked ? tierColor : .clear, lineWidth: isUnlocked ? 2 : 0)
        )
        .opacity(isUnlocked ? 1.0 : 0.45)
    }

    private var tierColor: Color {
        switch tile.tier {
        case .bronze: return Color(red: 0.80, green: 0.50, blue: 0.20)  // #CD7F32
        case .silver: return Color(red: 0.75, green: 0.75, blue: 0.75)  // #C0C0C0
        case .gold:   return Color(red: 1.00, green: 0.84, blue: 0.00)  // #FFD700
        default:      return .secondary
        }
    }

    private var tierLabel: String {
        switch tile.tier {
        case .bronze: return "BRONZE"
        case .silver: return "SILVER"
        case .gold:   return "GOLD"
        default:      return "—"
        }
    }

    private var footerText: String {
        if let millis = tile.unlockedAtMillis {
            // Unlocked YYYY-MM-DD
            let date = Date(timeIntervalSince1970: TimeInterval(truncating: millis) / 1000.0)
            let fmt = DateFormatter()
            fmt.dateFormat = "yyyy-MM-dd"
            return "Unlocked \(fmt.string(from: date))"
        } else {
            return "\(tile.currentProgress) / \(tile.threshold)"
        }
    }
}

// MARK: - Shared type aliases

private typealias SharedCategory = Shared.Category
private typealias SharedTier = Shared.Tier
private typealias SharedAchievementTile = Shared.AchievementTile
private typealias SharedAchievementGalleryUiState = Shared.AchievementGalleryUiState
