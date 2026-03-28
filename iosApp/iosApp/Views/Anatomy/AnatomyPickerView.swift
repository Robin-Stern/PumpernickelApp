import SwiftUI

// Stub -- will be fully implemented in Task 2
struct AnatomyPickerView: View {
    let selectedGroup: String?
    let onConfirm: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Text("Select Muscle Group")
                .navigationTitle("Select Muscle Group")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("", systemImage: "xmark") { dismiss() }
                    }
                }
        }
    }
}
