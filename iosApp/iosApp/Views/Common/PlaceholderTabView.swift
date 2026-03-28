import SwiftUI

struct PlaceholderTabView: View {
    let iconName: String
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 16) {
            Spacer()

            Image(systemName: iconName)
                .font(.system(size: 64))
                .foregroundColor(Color(white: 0.62))

            Text(title)
                .font(.title3.weight(.semibold))

            Text(message)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Spacer()
        }
    }
}
