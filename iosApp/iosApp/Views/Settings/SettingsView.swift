import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct SettingsView: View {
    private let viewModel = KoinHelper.shared.getSettingsViewModel()
    private var theme = ThemeManager.shared

    @State private var weightUnit: WeightUnit = .kg
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
}
