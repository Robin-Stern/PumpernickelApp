import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct SettingsView: View {
    private let viewModel = KoinHelper.shared.getSettingsViewModel()
    private var theme = ThemeManager.shared

    @State private var weightUnit: WeightUnit = .kg
    @State private var selectedTheme: String = "system"
    @State private var selectedAccentColor: String = "green"
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Appearance") {
                    Picker("Theme", selection: $selectedTheme) {
                        Text("System").tag("system")
                        Text("Light").tag("light")
                        Text("Dark").tag("dark")
                    }
                    .pickerStyle(.segmented)
                    .onChange(of: selectedTheme) { _, newValue in
                        theme.applyTheme(newValue)
                        viewModel.setAppTheme(theme: newValue)
                    }
                }

                Section("Accent Color") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4), spacing: 16) {
                        ForEach(ThemeManager.presetColors) { preset in
                            Button {
                                selectedAccentColor = preset.key
                                theme.applyAccentColor(preset.key)
                                viewModel.setAccentColor(color: preset.key)
                            } label: {
                                Circle()
                                    .fill(preset.color)
                                    .frame(width: 44, height: 44)
                                    .overlay(
                                        Circle()
                                            .strokeBorder(.white, lineWidth: selectedAccentColor == preset.key ? 3 : 0)
                                    )
                                    .overlay(
                                        selectedAccentColor == preset.key
                                            ? Image(systemName: "checkmark")
                                                .font(.body.weight(.bold))
                                                .foregroundColor(.white)
                                            : nil
                                    )
                                    .shadow(color: preset.color.opacity(selectedAccentColor == preset.key ? 0.5 : 0), radius: 6)
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
        .task {
            await withTaskGroup(of: Void.self) { group in
                group.addTask { await observeWeightUnit() }
                group.addTask { await observeTheme() }
                group.addTask { await observeAccentColor() }
            }
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

    private func observeTheme() async {
        do {
            for try await value in asyncSequence(for: viewModel.appThemeFlow) {
                self.selectedTheme = value
            }
        } catch {
            print("Settings theme observation error: \(error)")
        }
    }

    private func observeAccentColor() async {
        do {
            for try await value in asyncSequence(for: viewModel.accentColorFlow) {
                self.selectedAccentColor = value
            }
        } catch {
            print("Settings accent color observation error: \(error)")
        }
    }
}
