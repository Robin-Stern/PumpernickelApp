import SwiftUI

// Stub -- will be fully implemented in Task 2
struct AnatomyFrontView: View {
    let selectedGroup: String?
    let onRegionTapped: (String) -> Void

    var body: some View {
        Rectangle()
            .fill(Color(white: 0.12))
            .aspectRatio(0.56, contentMode: .fit)
    }
}
