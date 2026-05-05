import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

/// Phase 17 D-17-10 / D-17-11 / D-17-12 / D-17-13.
/// LazyVGrid of blurred tiles, one per workout that has at least one photo.
/// Cover image is the most recent photo for that workout, blurred at 24pt
/// (faces unrecognisable). Tap pushes the viewer; auth fires on first
/// composition of the viewer (per-tile every-tap, D-17-14).
struct ProgressGalleryView: View {
    private let viewModel = ProgressGalleryKoinHelper().getProgressGalleryViewModel()

    @State private var uiState: SharedGalleryUiState?
    @State private var pendingWorkoutId: Int64? = nil
    @State private var navigationActive: Bool = false

    private let columns: [GridItem] = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12)
    ]

    var body: some View {
        Group {
            if let state = uiState {
                if state.isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if state.tiles.isEmpty {
                    emptyState
                } else {
                    ScrollView {
                        LazyVGrid(columns: columns, spacing: 12) {
                            ForEach(state.tiles, id: \.workoutId) { tile in
                                NavigationLink(
                                    destination: ProgressViewerView(workoutId: tile.workoutId),
                                    label: { GalleryTileView(tile: tile) }
                                )
                                .buttonStyle(.plain)
                                .simultaneousGesture(TapGesture().onEnded {
                                    viewModel.onTileTapped(workoutId: tile.workoutId)
                                })
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                    }
                }
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle("Fortschritt")
        .navigationBarTitleDisplayMode(.inline)
        .task { await observe() }
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Spacer()
            Image(systemName: "photo.on.rectangle.angled")
                .font(.system(size: 48))
                .foregroundColor(.secondary)
            Text("Noch keine Fortschrittsfotos.")
                .font(.body)
                .foregroundColor(.secondary)
            Text("Schließe ein Workout ab und füge ein Foto hinzu.")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            Spacer()
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func observe() async {
        do {
            for try await state in asyncSequence(for: viewModel.uiStateFlow) {
                self.uiState = state
            }
        } catch {
            print("ProgressGallery observation error: \(error)")
        }
    }
}

// MARK: - Tile

private struct GalleryTileView: View {
    let tile: SharedProgressGalleryTile

    var body: some View {
        ZStack(alignment: .bottom) {
            CoverImage(relativePath: tile.coverRelativePath)
                .blur(radius: 24, opaque: true)  // faces unrecognisable per D-17-13
                .clipShape(RoundedRectangle(cornerRadius: 14))

            VStack(alignment: .leading, spacing: 2) {
                Text("\(formatGermanDate(tile.startTimeMillis)) • \(tile.workoutName)")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                    .lineLimit(1)
                Text(statsLine)
                    .font(.caption2)
                    .foregroundColor(.white)
                if tile.isGoalDay {
                    Text("🍎 Goal day")
                        .font(.caption2)
                        .foregroundColor(.white)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                LinearGradient(
                    colors: [Color.clear, Color.black.opacity(0.7)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .aspectRatio(1, contentMode: .fit)
    }

    private var statsLine: String {
        var parts: [String] = []
        parts.append("\(formatThousand(Int(tile.volumeKg))) kg")
        if tile.prCount > 0 {
            parts.append("🏆 \(tile.prCount)")
        }
        return parts.joined(separator: "  ·  ")
    }

    private func formatGermanDate(_ epochMillis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(epochMillis) / 1000.0)
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "de_DE")
        fmt.dateFormat = "d. MMM"
        return fmt.string(from: date)
    }

    private func formatThousand(_ value: Int) -> String {
        let fmt = NumberFormatter()
        fmt.numberStyle = .decimal
        fmt.locale = Locale(identifier: "de_DE")
        return fmt.string(from: NSNumber(value: value)) ?? "\(value)"
    }
}

// MARK: - Cover image

private struct CoverImage: View {
    let relativePath: String

    @State private var image: UIImage?

    var body: some View {
        Group {
            if let img = image {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFill()
            } else {
                Rectangle()
                    .fill(Color(uiColor: .secondarySystemBackground))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipped()
        .task(id: relativePath) {
            self.image = await Self.loadOffMain(relativePath: relativePath)
        }
    }

    private static func loadOffMain(relativePath: String) async -> UIImage? {
        await Task.detached(priority: .userInitiated) {
            loadVaultImage(relativePath: relativePath)
        }.value
    }
}

// MARK: - File-private vault read

/// Phase 17 photos live at `<Documents>/progress_pics/{uuid}.jpg`
/// (D-17-05). Reading directly via FileManager bypasses the K/N suspend
/// callback bridge for `PhotoVault.read` — same disk path, faster path,
/// no interop noise. Path-traversal guard mirrors PhotoVault.ios.kt's
/// `resolveSafe` helper: relativePath must start with "progress_pics/".
private func loadVaultImage(relativePath: String) -> UIImage? {
    guard relativePath.hasPrefix("progress_pics/") else { return nil }
    guard let docs = try? FileManager.default.url(
        for: .documentDirectory,
        in: .userDomainMask,
        appropriateFor: nil,
        create: false
    ) else { return nil }
    let url = docs.appendingPathComponent(relativePath)
    let standardised = url.standardizedFileURL
    let rootStd = docs.appendingPathComponent("progress_pics", isDirectory: true).standardizedFileURL
    guard standardised.path.hasPrefix(rootStd.path + "/") else { return nil }
    return UIImage(contentsOfFile: standardised.path)
}

// MARK: - Shared type aliases

private typealias SharedGalleryUiState = GalleryUiState
private typealias SharedProgressGalleryTile = ProgressGalleryTile
