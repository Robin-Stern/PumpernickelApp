import SwiftUI
import Shared

/// D-19 celebratory unlock modal.
/// Host (MainTabView) presents this via `.fullScreenCover` when `pendingUnlocks.first != nil`.
/// On appear fires a success haptic (matches the workout set-completion haptic at
/// WorkoutSessionView.swift line 421 / 701–703 per PATTERNS §"Haptic feedback convention").
///
/// Supports both UnlockEvent subclasses (RankPromotion, AchievementTierUnlocked).
struct UnlockModalView: View {
    let event: Shared.UnlockEvent
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.9).ignoresSafeArea()

            VStack(spacing: 20) {
                Spacer()

                Image(systemName: iconName)
                    .font(.system(size: 72, weight: .bold))
                    .foregroundColor(accentColor)

                Text(title)
                    .font(.largeTitle.bold())
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)

                if let subtitle = subtitle {
                    Text(subtitle)
                        .font(.headline)
                        .foregroundColor(.white.opacity(0.9))
                }

                Text(flavourCopy)
                    .font(.body)
                    .foregroundColor(.white.opacity(0.75))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)

                if let totalXpText = totalXpText {
                    Text(totalXpText)
                        .font(.title3.bold())
                        .foregroundColor(.appAccent)
                        .padding(.top, 8)
                }

                Spacer()

                Button(action: onDismiss) {
                    Text("Dismiss")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color.appAccent)
                        .cornerRadius(12)
                }
                .padding(.horizontal, 32)
                .padding(.bottom, 40)
            }
            .padding(.top, 60)
        }
        .onAppear {
            // D-19 haptic — success feedback. Mirrors WorkoutSessionView.swift line 701–703.
            let generator = UINotificationFeedbackGenerator()
            generator.prepare()
            generator.notificationOccurred(.success)
        }
    }

    // MARK: - Derived presentation

    private var iconName: String {
        if event is SharedUnlockEventRankPromotion {
            return "medal.fill"            // CSGO-style rank metaphor per D-19 tone
        } else {
            return "trophy.fill"           // achievement
        }
    }

    private var accentColor: Color {
        if let promo = event as? SharedUnlockEventRankPromotion {
            return rankTint(promo.toRank)
        } else if let ach = event as? SharedUnlockEventAchievementTierUnlocked {
            return tierTint(ach.tier)
        }
        return .appAccent
    }

    private var title: String {
        if let promo = event as? SharedUnlockEventRankPromotion {
            return "Promoted to \(promo.toRank.displayName)"
        } else if let ach = event as? SharedUnlockEventAchievementTierUnlocked {
            return ach.displayName
        }
        return "Unlocked"
    }

    private var subtitle: String? {
        if let ach = event as? SharedUnlockEventAchievementTierUnlocked {
            return "\(tierLabel(ach.tier)) Tier"
        }
        return nil
    }

    private var flavourCopy: String {
        if let promo = event as? SharedUnlockEventRankPromotion {
            return promo.flavourCopy
        } else if let ach = event as? SharedUnlockEventAchievementTierUnlocked {
            return ach.flavourCopy
        }
        return ""
    }

    private var totalXpText: String? {
        if let promo = event as? SharedUnlockEventRankPromotion {
            return "\(promo.totalXp) XP"
        }
        return nil
    }

    private func rankTint(_ rank: SharedRank) -> Color {
        // Map Rank.tier (1-based) to a CSGO-ish colour. Not per-rank gradients —
        // Claude's discretion per D-19; can be polished later.
        switch rank.tier {
        case 1, 2:  return Color(red: 0.75, green: 0.75, blue: 0.78)  // silver
        case 3, 4, 5: return Color(red: 1.00, green: 0.84, blue: 0.20)  // gold
        case 6, 7:  return Color(red: 0.60, green: 0.80, blue: 1.00)  // guardian blue
        case 8, 9:  return Color(red: 0.73, green: 0.41, blue: 0.78)  // eagle/supreme purple
        default:    return Color(red: 1.00, green: 0.25, blue: 0.25)  // global elite red
        }
    }

    private func tierTint(_ tier: SharedTier) -> Color {
        switch tier {
        case .bronze: return Color(red: 0.80, green: 0.50, blue: 0.20)  // #CD7F32
        case .silver: return Color(red: 0.75, green: 0.75, blue: 0.75)  // #C0C0C0
        case .gold:   return Color(red: 1.00, green: 0.84, blue: 0.00)  // #FFD700
        default:      return .appAccent
        }
    }

    private func tierLabel(_ tier: SharedTier) -> String {
        switch tier {
        case .bronze: return "Bronze"
        case .silver: return "Silver"
        case .gold:   return "Gold"
        default:      return "—"
        }
    }
}

// MARK: - Shared type aliases
// Note: KMP-Native exports sealed subclasses as nested Swift types (UnlockEvent.RankPromotion),
// not flat top-level names (UnlockEventRankPromotion). Use Shared.UnlockEvent.RankPromotion.

private typealias SharedUnlockEvent = Shared.UnlockEvent
private typealias SharedUnlockEventRankPromotion = Shared.UnlockEvent.RankPromotion
private typealias SharedUnlockEventAchievementTierUnlocked = Shared.UnlockEvent.AchievementTierUnlocked
private typealias SharedRank = Shared.Rank
private typealias SharedTier = Shared.Tier
