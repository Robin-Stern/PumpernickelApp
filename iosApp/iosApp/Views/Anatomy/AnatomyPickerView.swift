import SwiftUI

struct AnatomyPickerView: View {
    let selectedGroup: String?
    let onConfirm: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var localSelection: String? = nil

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                HStack(spacing: 16) {
                    AnatomyFrontView(
                        selectedGroup: localSelection,
                        onRegionTapped: { group in localSelection = group }
                    )
                    AnatomyBackView(
                        selectedGroup: localSelection,
                        onRegionTapped: { group in localSelection = group }
                    )
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)

                if let group = localSelection {
                    Text(displayName(for: group))
                        .font(.title3.weight(.semibold))
                } else {
                    Text("Tap a muscle group")
                        .font(.title3)
                        .foregroundColor(.secondary)
                }

                Spacer()

                Button(action: {
                    if let group = localSelection {
                        onConfirm(group)
                        dismiss()
                    }
                }) {
                    Text("Select")
                        .font(.body.weight(.semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(localSelection != nil
                            ? Color(red: 0.4, green: 0.733, blue: 0.416)
                            : Color.gray)
                        .cornerRadius(12)
                }
                .disabled(localSelection == nil)
                .padding(.horizontal, 32)
                .padding(.bottom, 16)
            }
            .navigationTitle("Select Muscle Group")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("", systemImage: "xmark") { dismiss() }
                }
            }
        }
        .onAppear { localSelection = selectedGroup }
    }

    private func displayName(for groupDbName: String) -> String {
        let mapping: [String: String] = [
            "chest": "Chest",
            "shoulders": "Shoulders",
            "biceps": "Biceps",
            "triceps": "Triceps",
            "forearms": "Forearms",
            "traps": "Traps",
            "lats": "Lats",
            "neck": "Neck",
            "quadriceps": "Quadriceps",
            "hamstrings": "Hamstrings",
            "glutes": "Glutes",
            "calves": "Calves",
            "adductors": "Adductors",
            "abdominals": "Abdominals",
            "obliques": "Obliques",
            "lower back": "Lower Back"
        ]
        return mapping[groupDbName] ?? groupDbName.capitalized
    }
}
