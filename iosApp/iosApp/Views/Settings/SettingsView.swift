import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct SettingsView: View {
    private let viewModel = KoinHelper.shared.getSettingsViewModel()
    private var theme = ThemeManager.shared

    @State private var weightUnit: WeightUnit = .kg
    // D-quick-vn7 — Debug-Modus toggle + Geofence grace-period picker.
    @State private var debugModeEnabled: Bool = true
    @State private var gracePeriodSeconds: Int64 = 300
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Appearance") {
                    Picker("Theme", selection: Binding(
                        get: { theme.themeKey },
                        set: { newValue in
                            theme.applyTheme(newValue)
                            viewModel.setAppTheme(theme: newValue)
                        }
                    )) {
                        Text("System").tag("system")
                        Text("Light").tag("light")
                        Text("Dark").tag("dark")
                    }
                    .pickerStyle(.segmented)
                }

                Section("Accent Color") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4), spacing: 16) {
                        ForEach(ThemeManager.presetColors) { preset in
                            let isSelected = theme.accentColorKey == preset.key
                            Circle()
                                .fill(preset.color)
                                .frame(width: 44, height: 44)
                                .overlay(
                                    Circle()
                                        .strokeBorder(.white, lineWidth: isSelected ? 3 : 0)
                                )
                                .overlay(
                                    isSelected
                                        ? Image(systemName: "checkmark")
                                            .font(.body.weight(.bold))
                                            .foregroundColor(.white)
                                        : nil
                                )
                                .shadow(color: preset.color.opacity(isSelected ? 0.5 : 0), radius: 6)
                                .contentShape(Circle())
                                .onTapGesture {
                                    theme.applyAccentColor(preset.key)
                                    viewModel.setAccentColor(color: preset.key)
                                }
                        }
                    }
                    .padding(.vertical, 8)
                }

                Section("Units") {
                    Picker("Weight Unit", selection: $weightUnit) {
                        Text("Kilograms (kg)").tag(WeightUnit.kg)
                        Text("Pounds (lbs)").tag(WeightUnit.lbs)
                    }
                    .pickerStyle(.segmented)
                    .onChange(of: weightUnit) { _, newValue in
                        viewModel.setWeightUnit(unit: newValue)
                    }
                }

                // D-21: achievement gallery reached from Settings only (NOT from Overview rank strip per D-18).
                Section("Gamification") {
                    NavigationLink {
                        AchievementGalleryView()
                    } label: {
                        Label("Achievements", systemImage: "trophy.fill")
                    }
                }

                // D-19-16: Workout Enforcement settings (Phase 19).
                Section("Training") {
                    NavigationLink {
                        WorkoutEnforcementDetailView()
                    } label: {
                        Label("Workout Enforcement", systemImage: "location.circle.fill")
                    }
                }

                #if DEBUG
                // D-quick-vn7 — user-controllable Debug section (replaces inline panel).
                Section("Debug") {
                    Toggle(isOn: Binding(
                        get: { debugModeEnabled },
                        set: { newValue in
                            debugModeEnabled = newValue
                            viewModel.setDebugModeEnabled(enabled: newValue)
                        }
                    )) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Debug-Modus")
                            Text("Zeigt Debug-Steuerung im Workout-Screen")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }

                    Picker("Grace-Period (Demo)", selection: Binding(
                        get: { gracePeriodSeconds },
                        set: { newValue in
                            gracePeriodSeconds = newValue
                            viewModel.setGracePeriodSeconds(seconds: newValue)
                        }
                    )) {
                        Text("5 Sek.").tag(Int64(5))
                        Text("10 Sek.").tag(Int64(10))
                        Text("30 Sek.").tag(Int64(30))
                        Text("1 Min.").tag(Int64(60))
                        Text("5 Min.").tag(Int64(300))
                    }
                }
                #endif

                // D-18-05: BYOK AI configuration reachable from Settings.
                Section("AI") {
                    NavigationLink {
                        AISettingsView()
                    } label: {
                        Label("KI-Einstellungen", systemImage: "sparkles")
                    }
                }

                Section("Hilfe") {
                    Button {
                        viewModel.setHasSeenTutorial(value: false)
                    } label: {
                        Label("Tutorial erneut anzeigen", systemImage: "questionmark.circle")
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
        .preferredColorScheme(theme.colorScheme)
        .task {
            await observeWeightUnit()
        }
        .task {
            await observeDebugModeEnabled()
        }
        .task {
            await observeGracePeriodSeconds()
        }
    }

    private func observeWeightUnit() async {
        do {
            for try await value in asyncSequence(for: viewModel.weightUnitFlow) {
                self.weightUnit = value
            }
        } catch {
            print("Settings weight unit observation error: \(error)")
        }
    }

    // D-quick-vn7 — observe Debug-Modus toggle. StateFlow<Boolean> bridges as KotlinBoolean.
    private func observeDebugModeEnabled() async {
        do {
            for try await value in asyncSequence(for: viewModel.debugModeEnabledFlow) {
                self.debugModeEnabled = value.boolValue
            }
        } catch {
            print("Settings debug-mode observation error: \(error)")
        }
    }

    // D-quick-vn7 — observe Geofence grace-period (seconds). StateFlow<Long> bridges as KotlinLong.
    private func observeGracePeriodSeconds() async {
        do {
            for try await value in asyncSequence(for: viewModel.gracePeriodSecondsFlow) {
                self.gracePeriodSeconds = value.int64Value
            }
        } catch {
            print("Settings grace-period observation error: \(error)")
        }
    }
}
