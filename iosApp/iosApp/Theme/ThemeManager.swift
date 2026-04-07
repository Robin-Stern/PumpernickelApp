import SwiftUI

@Observable
class ThemeManager {
    static let shared = ThemeManager()

    var accentColor: Color = Color(red: 0.4, green: 0.733, blue: 0.416)
    var colorScheme: ColorScheme? = nil // nil = system
    var accentColorKey: String = "green"
    var themeKey: String = "system"

    struct PresetColor: Identifiable {
        let key: String
        let name: String
        let color: Color
        var id: String { key }
    }

    static let presetColors: [PresetColor] = [
        PresetColor(key: "green", name: "Green", color: Color(red: 0.4, green: 0.733, blue: 0.416)),
        PresetColor(key: "blue", name: "Blue", color: .blue),
        PresetColor(key: "indigo", name: "Indigo", color: .indigo),
        PresetColor(key: "purple", name: "Purple", color: .purple),
        PresetColor(key: "pink", name: "Pink", color: .pink),
        PresetColor(key: "red", name: "Red", color: .red),
        PresetColor(key: "orange", name: "Orange", color: .orange),
        PresetColor(key: "teal", name: "Teal", color: .teal),
    ]

    func applyTheme(_ theme: String) {
        guard theme != themeKey else { return }
        themeKey = theme
        switch theme {
        case "dark": colorScheme = .dark
        case "light": colorScheme = .light
        default: colorScheme = nil
        }
    }

    func applyAccentColor(_ key: String) {
        guard key != accentColorKey else { return }
        accentColorKey = key
        accentColor = Self.presetColors.first { $0.key == key }?.color
            ?? Color(red: 0.4, green: 0.733, blue: 0.416)
    }
}
