import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

private enum SuggestionType { case cut, maintain, bulk }

struct NutritionGoalsEditorView: View {
    private let viewModel = KoinHelper.shared.getOverviewViewModel()

    @Environment(\.dismiss) private var dismiss

    // Stats inputs — placeholder defaults per UI-SPEC.
    @State private var weightText: String = "80"
    @State private var heightText: String = "180"
    @State private var ageText: String = "30"
    @State private var sex: SharedSex = .male
    @State private var activityLevel: SharedActivityLevel = .moderatelyActive

    // Picker state — defaults from NutritionGoals defaults.
    @State private var kcalValue: Int = 2500
    @State private var proteinValue: Int = 150
    @State private var carbsValue: Int = 300
    @State private var fatValue: Int = 80
    @State private var sugarValue: Int = 50

    @State private var statsExpanded: Bool = true
    @State private var selectedSuggestion: SuggestionType? = nil

    // Live-derived suggestions (recomputed every time stats change).
    private var currentStats: SharedUserPhysicalStats {
        SharedUserPhysicalStats(
            weightKg: Double(weightText) ?? 80.0,
            heightCm: Int32(heightText) ?? 180,
            age: Int32(ageText) ?? 30,
            sex: sex,
            activityLevel: activityLevel
        )
    }

    private var suggestions: SharedTdeeSuggestions {
        SharedTdeeCalculator.shared.suggestions(stats: currentStats)
    }

    var body: some View {
        NavigationStack {
            Form {
                // Section 1 — Stats (collapsible)
                Section {
                    DisclosureGroup("Meine Stats", isExpanded: $statsExpanded) {
                        HStack {
                            TextField("80", text: $weightText)
                                .keyboardType(.decimalPad)
                            Text("kg").foregroundColor(.secondary)
                        }
                        HStack {
                            TextField("180", text: $heightText)
                                .keyboardType(.numberPad)
                            Text("cm").foregroundColor(.secondary)
                        }
                        HStack {
                            TextField("30", text: $ageText)
                                .keyboardType(.numberPad)
                            Text("Jahre").foregroundColor(.secondary)
                        }
                        Picker("Geschlecht", selection: $sex) {
                            Text("Männlich").tag(SharedSex.male)
                            Text("Weiblich").tag(SharedSex.female)
                        }
                        .pickerStyle(.segmented)
                        Picker("Aktivität", selection: $activityLevel) {
                            Text("Bürojob / kaum Bewegung").tag(SharedActivityLevel.sedentary)
                            Text("Leicht aktiv (1–3×/Woche)").tag(SharedActivityLevel.lightlyActive)
                            Text("Mäßig aktiv (3–5×/Woche)").tag(SharedActivityLevel.moderatelyActive)
                            Text("Sehr aktiv (6–7×/Woche)").tag(SharedActivityLevel.veryActive)
                            Text("Extrem aktiv / körperlicher Beruf").tag(SharedActivityLevel.extraActive)
                        }
                    }
                }

                // Section 2 — three suggestion cards
                Section("Vorschlag berechnen") {
                    HStack(spacing: 8) {
                        SuggestionCardView(
                            title: "Defizit",
                            subtitle: "−500 kcal",
                            split: suggestions.cut,
                            isSelected: selectedSuggestion == .cut,
                            onTap: { applySuggestion(.cut) }
                        ).frame(maxWidth: .infinity)
                        SuggestionCardView(
                            title: "Erhalt",
                            subtitle: "TDEE",
                            split: suggestions.maintain,
                            isSelected: selectedSuggestion == .maintain,
                            onTap: { applySuggestion(.maintain) }
                        ).frame(maxWidth: .infinity)
                        SuggestionCardView(
                            title: "Aufbau",
                            subtitle: "+300 kcal",
                            split: suggestions.bulk,
                            isSelected: selectedSuggestion == .bulk,
                            onTap: { applySuggestion(.bulk) }
                        ).frame(maxWidth: .infinity)
                    }
                }

                // Section 3 — picker wheels
                Section("Zielwerte anpassen") {
                    wheelPicker(
                        label: "Kalorien",
                        unit: "kcal",
                        range: Array(stride(from: 800, through: 6000, by: 50)),
                        selection: $kcalValue,
                        onChange: { selectedSuggestion = nil }
                    )
                    wheelPicker(
                        label: "Protein",
                        unit: "g",
                        range: Array(stride(from: 20, through: 400, by: 5)),
                        selection: $proteinValue,
                        onChange: { selectedSuggestion = nil }
                    )
                    wheelPicker(
                        label: "Kohlenhydrate",
                        unit: "g",
                        range: Array(stride(from: 20, through: 700, by: 5)),
                        selection: $carbsValue,
                        onChange: { selectedSuggestion = nil }
                    )
                    wheelPicker(
                        label: "Fett",
                        unit: "g",
                        range: Array(stride(from: 10, through: 250, by: 5)),
                        selection: $fatValue,
                        onChange: { selectedSuggestion = nil }
                    )
                    wheelPicker(
                        label: "Zucker",
                        unit: "g",
                        range: Array(stride(from: 0, through: 200, by: 5)),
                        selection: $sugarValue,
                        onChange: { selectedSuggestion = nil }
                    )
                }
            }
            .navigationTitle("Ernährungsziele")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Abbrechen") { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Ziele speichern") { saveGoals() }
                        .fontWeight(.semibold)
                }
            }
            .task {
                await withTaskGroup(of: Void.self) { group in
                    group.addTask { await observeStats() }
                    group.addTask { await observeGoals() }
                }
            }
        }
    }

    // MARK: - Helpers

    @ViewBuilder
    private func wheelPicker(
        label: String,
        unit: String,
        range: [Int],
        selection: Binding<Int>,
        onChange: @escaping () -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption2).foregroundColor(.secondary)
            Picker(label, selection: selection) {
                ForEach(range, id: \.self) { v in
                    Text("\(v) \(unit)").tag(v)
                }
            }
            .pickerStyle(.wheel)
            .frame(height: 100)
            .onChange(of: selection.wrappedValue) { _, _ in onChange() }
        }
    }

    private func applySuggestion(_ type: SuggestionType) {
        selectedSuggestion = type
        let split: SharedMacroSplit
        switch type {
        case .cut: split = suggestions.cut
        case .maintain: split = suggestions.maintain
        case .bulk: split = suggestions.bulk
        }
        kcalValue = Int(split.kcal)
        proteinValue = Int(split.proteinG)
        carbsValue = Int(split.carbsG)
        fatValue = Int(split.fatG)
        sugarValue = Int(split.sugarG)
    }

    private func saveGoals() {
        let stats = SharedUserPhysicalStats(
            weightKg: Double(weightText) ?? 80.0,
            heightCm: Int32(heightText) ?? 180,
            age: Int32(ageText) ?? 30,
            sex: sex,
            activityLevel: activityLevel
        )
        viewModel.updateUserPhysicalStats(stats: stats)
        let goals = SharedNutritionGoals(
            calorieGoal: Int32(kcalValue),
            proteinGoal: Int32(proteinValue),
            fatGoal: Int32(fatValue),
            carbGoal: Int32(carbsValue),
            sugarGoal: Int32(sugarValue)
        )
        viewModel.updateNutritionGoals(goals: goals)
        dismiss()
    }

    // MARK: - Async Observation

    private func observeStats() async {
        do {
            for try await stats in asyncSequence(for: viewModel.userPhysicalStatsFlow) {
                if let s = stats as? SharedUserPhysicalStats {
                    weightText = String(format: "%.0f", s.weightKg)
                    heightText = "\(s.heightCm)"
                    ageText = "\(s.age)"
                    sex = s.sex
                    activityLevel = s.activityLevel
                    statsExpanded = false   // collapse when stats already stored (D-16-09)
                }
            }
        } catch {
            print("Editor stats observation error: \(error)")
        }
    }

    private func observeGoals() async {
        do {
            for try await goals in asyncSequence(for: viewModel.nutritionGoalsFlow) {
                if let g = goals as? SharedNutritionGoals {
                    kcalValue = Int(g.calorieGoal)
                    proteinValue = Int(g.proteinGoal)
                    fatValue = Int(g.fatGoal)
                    carbsValue = Int(g.carbGoal)
                    sugarValue = Int(g.sugarGoal)
                }
            }
        } catch {
            print("Editor goals observation error: \(error)")
        }
    }
}

// MARK: - SuggestionCardView

private struct SuggestionCardView: View {
    let title: String
    let subtitle: String
    let split: SharedMacroSplit
    let isSelected: Bool
    let onTap: () -> Void

    // Macro ring colors (UI-SPEC §"Color")
    private let calorieColor = Color(red: 1.0, green: 0.42, blue: 0.42)
    private let proteinColor = Color(red: 0.31, green: 0.76, blue: 0.97)
    private let carbsColor   = Color(red: 1.0, green: 0.84, blue: 0.31)
    private let fatColor     = Color(red: 1.0, green: 0.54, blue: 0.40)
    private let sugarColor   = Color(red: 0.73, green: 0.41, blue: 0.78)

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.headline)
                Text(subtitle).font(.caption2).foregroundColor(.secondary)
                Text("\(Int(split.kcal)) kcal")
                    .font(.subheadline)
                    .foregroundColor(calorieColor)
                    .padding(.top, 4)
                macroRow(color: proteinColor, label: "Protein", grams: Int(split.proteinG))
                macroRow(color: carbsColor,   label: "Kohlenh.", grams: Int(split.carbsG))
                macroRow(color: fatColor,     label: "Fett",     grams: Int(split.fatG))
                macroRow(color: sugarColor,   label: "Zucker",   grams: Int(split.sugarG))
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isSelected ? Color.appAccent.opacity(0.08) : Color(.secondarySystemGroupedBackground))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? Color.appAccent : Color(.separator), lineWidth: isSelected ? 2 : 0.5)
            )
            .cornerRadius(12)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func macroRow(color: Color, label: String, grams: Int) -> some View {
        HStack(spacing: 4) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text("\(label) \(grams) g").font(.caption2)
        }
    }
}

// MARK: - Type aliases for Shared module types

private typealias SharedUserPhysicalStats = Shared.UserPhysicalStats
private typealias SharedActivityLevel = Shared.ActivityLevel
private typealias SharedSex = Shared.Sex
private typealias SharedNutritionGoals = Shared.NutritionGoals
private typealias SharedTdeeCalculator = Shared.TdeeCalculator
private typealias SharedTdeeSuggestions = Shared.TdeeSuggestions
private typealias SharedMacroSplit = Shared.MacroSplit
