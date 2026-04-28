import SwiftUI
import Shared
import SVGPath
import KMPNativeCoroutinesAsync

struct OverviewView: View {
    private let viewModel = KoinHelper.shared.getOverviewViewModel()
    private let gamificationViewModel = GamificationUiKoinHelper().getGamificationViewModel()

    @State private var muscleLoad: [String: SharedTrainingIntensity] = [:]  // groupName -> intensity
    @State private var todayMacros = SharedRecipeMacros(calories: 0, protein: 0, fat: 0, carbs: 0, sugar: 0)
    @State private var goals = SharedNutritionGoals(calorieGoal: 2500, proteinGoal: 150, fatGoal: 80, carbGoal: 300, sugarGoal: 50)
    @State private var isLoading = true
    @State private var showIntensityInfo = false
    @State private var rankState: SharedRankState = SharedRankStateUnranked()
    @State private var bannerVisible: Bool = true
    @State private var showEditor: Bool = false

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // ── Rank Strip (D-11 / D-18) ──
                OverviewRankStrip(rankState: rankState)

                // ── Muscle Activity Section ──
                muscleActivitySection

                // ── Nutrition Goals Banner (D-16-13) ──
                if bannerVisible {
                    NutritionGoalsBannerView(
                        onTap: { showEditor = true },
                        onDismiss: {
                            withAnimation(.easeOut(duration: 0.3)) { bannerVisible = false }
                            viewModel.dismissBanner()
                        }
                    )
                    .transition(.move(edge: .top).combined(with: .opacity))
                }

                // ── Nutrition Rings Section ──
                nutritionRingsSection
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 24)
        }
        .navigationTitle("Übersicht")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: { viewModel.refresh() }) {
                    Image(systemName: "arrow.clockwise")
                }
            }
        }
        .task {
            await withTaskGroup(of: Void.self) { group in
                group.addTask { await observeUiState() }
                group.addTask { await observeRank() }
                group.addTask { await observeBannerVisible() }
            }
        }
        .sheet(isPresented: $showEditor) {
            NutritionGoalsEditorView()
                .presentationDragIndicator(.visible)
        }
    }

    // MARK: - Observe ViewModel

    private func observeUiState() async {
        do {
            for try await state in asyncSequence(for: viewModel.uiStateFlow) {
                isLoading = state.isLoading
                todayMacros = state.todayMacros
                goals = state.nutritionGoals

                // Convert Kotlin Map<MuscleGroup, TrainingIntensity> to Swift dict
                var loadMap: [String: SharedTrainingIntensity] = [:]
                for entry in state.muscleLoad {
                    if let group = entry.key as? SharedMuscleGroup,
                       let intensity = entry.value as? SharedTrainingIntensity {
                        loadMap[group.dbName] = intensity
                    }
                }
                muscleLoad = loadMap
            }
        } catch {
            print("Overview observation error: \(error)")
        }
    }

    private func observeRank() async {
        do {
            for try await state in asyncSequence(for: gamificationViewModel.rankStateFlow) {
                self.rankState = state
            }
        } catch {
            print("Overview rank observation error: \(error)")
        }
    }

    private func observeBannerVisible() async {
        do {
            for try await visible in asyncSequence(for: viewModel.nutritionGoalsBannerVisibleFlow) {
                if let v = visible as? Bool {
                    withAnimation(.easeOut(duration: 0.3)) { bannerVisible = v }
                }
            }
        } catch {
            print("Banner observation error: \(error)")
        }
    }

    // MARK: - Muscle Activity Section

    private var muscleActivitySection: some View {
        VStack(spacing: 16) {
            HStack {
                Text("Muskelbelastung · 7 Tage")
                    .font(.headline)

                Button(action: { showIntensityInfo = true }) {
                    Image(systemName: "info.circle")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
            }

            HStack(spacing: 16) {
                OverviewAnatomyFrontView(muscleLoad: muscleLoad)
                OverviewAnatomyBackView(muscleLoad: muscleLoad)
            }

            // Legend
            HStack(spacing: 16) {
                legendItem(label: "Niedrig", color: Color.red.opacity(0.65))
                legendItem(label: "Mittel", color: Color.yellow.opacity(0.75))
                legendItem(label: "Hoch", color: Color.appAccent)
            }
            .font(.caption)
            .foregroundColor(.secondary)
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(16)
        .alert("RIR-basierte Bewertung", isPresented: $showIntensityInfo) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Jeder Satz wird nach dem RIR-Wert (Reps in Reserve) gewichtet:\n\n• RIR 4+ → 0,5×\n• RIR 2-3 → 1,0×\n• RIR 1 → 1,5×\n• RIR 0 → 2,0×\n\nPrimäre Muskeln: volle Wertung\nSekundäre Muskeln: halbe Wertung\n\nWochenbewertung:\n🔴 Niedrig: Score < 5\n🟡 Mittel: Score 5-12\n🟢 Hoch: Score 13+")
        }
    }

    private func legendItem(label: String, color: Color) -> some View {
        HStack(spacing: 4) {
            Circle()
                .fill(color)
                .frame(width: 10, height: 10)
            Text(label)
        }
    }

    // MARK: - Nutrition Rings Section

    private var nutritionRingsSection: some View {
        VStack(spacing: 20) {
            HStack {
                Spacer()
                Text("Ernährung · Heute")
                    .font(.headline)
                Spacer()
                Button(action: { showEditor = true }) {
                    Image(systemName: "pencil")
                        .foregroundColor(.appAccent)
                }
                .accessibilityLabel("Ziele bearbeiten")
            }

            // Main calorie ring
            CalorieRingView(
                current: todayMacros.calories,
                goal: Double(goals.calorieGoal)
            )
            .frame(width: 180, height: 180)

            // Macro rings row
            HStack(spacing: 0) {
                MacroRingItem(
                    label: "Protein",
                    current: todayMacros.protein,
                    goal: Double(goals.proteinGoal),
                    unit: "g",
                    color: Color(red: 0.31, green: 0.76, blue: 0.97) // light blue
                )
                .frame(maxWidth: .infinity)

                MacroRingItem(
                    label: "Kohlenh.",
                    current: todayMacros.carbs,
                    goal: Double(goals.carbGoal),
                    unit: "g",
                    color: Color(red: 1.0, green: 0.84, blue: 0.31) // yellow
                )
                .frame(maxWidth: .infinity)

                MacroRingItem(
                    label: "Fett",
                    current: todayMacros.fat,
                    goal: Double(goals.fatGoal),
                    unit: "g",
                    color: Color(red: 1.0, green: 0.54, blue: 0.40) // orange
                )
                .frame(maxWidth: .infinity)

                MacroRingItem(
                    label: "Zucker",
                    current: todayMacros.sugar,
                    goal: Double(goals.sugarGoal),
                    unit: "g",
                    color: Color(red: 0.73, green: 0.41, blue: 0.78) // purple
                )
                .frame(maxWidth: .infinity)
            }
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(16)
    }
}

// MARK: - Anatomy Views with Intensity Coloring

private struct OverviewAnatomyFrontView: View {
    let muscleLoad: [String: SharedTrainingIntensity]

    var body: some View {
        GeometryReader { geo in
            let scale = geo.size.width / MuscleRegionPaths.viewBoxWidth
            let scaledHeight = MuscleRegionPaths.viewBoxHeight * scale

            ZStack {
                ForEach(MuscleRegionPaths.frontOutline.indices, id: \.self) { i in
                    svgPath(MuscleRegionPaths.frontOutline[i])
                        .fill(Color(UIColor.systemGray5))
                        .scaleEffect(x: scale, y: scale, anchor: .topLeading)
                }

                ForEach(MuscleRegionPaths.frontRegions) { region in
                    svgPath(region.pathData)
                        .fill(intensityColor(for: region.groupName))
                        .scaleEffect(x: scale, y: scale, anchor: .topLeading)
                }
            }
            .frame(height: scaledHeight)
        }
        .aspectRatio(MuscleRegionPaths.viewBoxWidth / MuscleRegionPaths.viewBoxHeight, contentMode: .fit)
    }

    private func intensityColor(for groupName: String) -> Color {
        guard let intensity = muscleLoad[groupName] else {
            return Color(UIColor.systemGray4)
        }
        switch intensity {
        case .low:      return Color.red.opacity(0.65)
        case .moderate: return Color.yellow.opacity(0.75)
        case .high:     return Color.appAccent
        default:        return Color(UIColor.systemGray4)
        }
    }

    private func svgPath(_ data: String) -> Path {
        (try? Path(svgPath: data)) ?? Path()
    }
}

private struct OverviewAnatomyBackView: View {
    let muscleLoad: [String: SharedTrainingIntensity]

    var body: some View {
        GeometryReader { geo in
            let scale = geo.size.width / MuscleRegionPaths.viewBoxWidth
            let scaledHeight = MuscleRegionPaths.viewBoxHeight * scale

            ZStack {
                ForEach(MuscleRegionPaths.backOutline.indices, id: \.self) { i in
                    svgPath(MuscleRegionPaths.backOutline[i])
                        .fill(Color(UIColor.systemGray5))
                        .scaleEffect(x: scale, y: scale, anchor: .topLeading)
                }

                ForEach(MuscleRegionPaths.backRegions) { region in
                    svgPath(region.pathData)
                        .fill(intensityColor(for: region.groupName))
                        .scaleEffect(x: scale, y: scale, anchor: .topLeading)
                }
            }
            .frame(height: scaledHeight)
        }
        .aspectRatio(MuscleRegionPaths.viewBoxWidth / MuscleRegionPaths.viewBoxHeight, contentMode: .fit)
    }

    private func intensityColor(for groupName: String) -> Color {
        guard let intensity = muscleLoad[groupName] else {
            return Color(UIColor.systemGray4)
        }
        switch intensity {
        case .low:      return Color.red.opacity(0.65)
        case .moderate: return Color.yellow.opacity(0.75)
        case .high:     return Color.appAccent
        default:        return Color(UIColor.systemGray4)
        }
    }

    private func svgPath(_ data: String) -> Path {
        (try? Path(svgPath: data)) ?? Path()
    }
}

// MARK: - Calorie Ring

private struct CalorieRingView: View {
    let current: Double
    let goal: Double

    private var progress: Double {
        guard goal > 0 else { return 0 }
        return min(current / goal, 1.5)
    }

    var body: some View {
        ZStack {
            // Background track
            Circle()
                .stroke(Color.red.opacity(0.15), lineWidth: 16)
                .padding(8)

            // Progress arc
            Circle()
                .trim(from: 0, to: progress)
                .stroke(Color.red, style: StrokeStyle(lineWidth: 16, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .padding(8)
                .animation(.easeInOut(duration: 0.8), value: progress)

            // Center text
            VStack(spacing: 2) {
                Text("\(Int(current))")
                    .font(.title.bold())
                    .foregroundColor(.red)
                Text("/ \(Int(goal)) kcal")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
    }
}

// MARK: - Macro Ring Item

private struct MacroRingItem: View {
    let label: String
    let current: Double
    let goal: Double
    let unit: String
    let color: Color

    private var progress: Double {
        guard goal > 0 else { return 0 }
        return min(current / goal, 1.5)
    }

    var body: some View {
        VStack(spacing: 4) {
            ZStack {
                Circle()
                    .stroke(color.opacity(0.15), lineWidth: 6)
                    .padding(4)

                Circle()
                    .trim(from: 0, to: progress)
                    .stroke(color, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .padding(4)
                    .animation(.easeInOut(duration: 0.8), value: progress)

                Text("\(Int(current))")
                    .font(.caption2.bold())
                    .foregroundColor(color)
            }
            .frame(width: 56, height: 56)

            Text(label)
                .font(.caption2)
                .foregroundColor(.secondary)

            Text("\(Int(current))/\(Int(goal))\(unit)")
                .font(.system(size: 9))
                .foregroundColor(.secondary.opacity(0.7))
        }
    }
}

// MARK: - Nutrition Goals Banner

private struct NutritionGoalsBannerView: View {
    let onTap: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "target")
                .foregroundColor(.appAccent)
            VStack(alignment: .leading, spacing: 2) {
                Text("Persönliche Ziele setzen")
                    .font(.body)
                Text("Berechne deinen Tagesbedarf und passe deine Makros an.")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .foregroundColor(.appAccent)
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Banner ausblenden")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(12)
        .contentShape(Rectangle())
        .onTapGesture { onTap() }
    }
}

// MARK: - Type aliases for Shared module types

private typealias SharedTrainingIntensity = Shared.TrainingIntensity
private typealias SharedMuscleGroup = Shared.MuscleGroup
private typealias SharedRecipeMacros = Shared.RecipeMacros
private typealias SharedNutritionGoals = Shared.NutritionGoals
private typealias SharedRankState = Shared.RankState
private typealias SharedRankStateUnranked = Shared.RankState.Unranked
