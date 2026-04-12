import SwiftUI

struct MacroRowView: View {
    let protein: Double
    let fat: Double
    let carbs: Double
    let sugar: Double

    var body: some View {
        HStack(spacing: 8) {
            macroChip(label: "P", value: protein, color: Color(red: 0.9, green: 0.32, blue: 0))
            macroChip(label: "F", value: fat, color: Color(red: 0.76, green: 0.57, blue: 0.03))
            macroChip(label: "KH", value: carbs, color: Color(red: 0.18, green: 0.49, blue: 0.2))
            macroChip(label: "Z", value: sugar, color: Color(red: 0.42, green: 0.11, blue: 0.6))
        }
    }

    private func macroChip(label: String, value: Double, color: Color) -> some View {
        Text("\(label) \(Int(value.rounded()))g")
            .font(.caption.weight(.semibold))
            .foregroundColor(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(color.opacity(0.12))
            .cornerRadius(8)
    }
}
