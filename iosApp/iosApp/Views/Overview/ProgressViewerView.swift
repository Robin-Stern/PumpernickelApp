import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

/// Phase 17 D-17-13 / D-17-14.
/// Full-screen black-backed photo carousel for ONE workout's photos.
/// Auth fires on `.onAppear` (per-tile every-tap); paging within the carousel
/// does NOT re-prompt; `.onDisappear` calls `relock()` so the tile re-blurs in
/// the grid. While locked we render the LockedPlaceholder; the un-blurred
/// photo only renders when `state.unlockedWorkoutId != nil`.
struct ProgressViewerView: View {
    let workoutId: Int64

    @State private var viewModel: SharedProgressViewerViewModel
    @State private var uiState: SharedViewerUiState?
    @State private var pageIndex: Int = 0
    @State private var deleteCandidate: SharedProgressPicture? = nil

    init(workoutId: Int64) {
        self.workoutId = workoutId
        _viewModel = State(initialValue: ProgressViewerKoinHelper()
            .getProgressViewerViewModel(workoutId: workoutId))
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            content
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let state = uiState,
               state.unlockedWorkoutId != nil,
               !state.photos.isEmpty,
               pageIndex < state.photos.count {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button(role: .destructive) {
                            deleteCandidate = state.photos[pageIndex]
                        } label: {
                            Label("Foto löschen", systemImage: "trash")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                            .foregroundColor(.white)
                            .accessibilityLabel("Optionen")
                    }
                }
            }
        }
        .toolbarBackground(.hidden, for: .navigationBar)
        .task { await observe() }
        .onAppear { viewModel.requestUnlock() }
        .onDisappear { viewModel.relock() }
        .confirmationDialog(
            "Foto wirklich löschen?",
            isPresented: Binding(
                get: { deleteCandidate != nil },
                set: { if !$0 { deleteCandidate = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Löschen", role: .destructive) {
                if let candidate = deleteCandidate {
                    viewModel.deletePhoto(picture: candidate)
                    pageIndex = max(0, pageIndex - 1)
                }
                deleteCandidate = nil
            }
            Button("Abbrechen", role: .cancel) {
                deleteCandidate = nil
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if let state = uiState {
            if state.busy && state.unlockedWorkoutId == nil {
                ProgressView().tint(.white)
            } else if state.unlockedWorkoutId == nil {
                LockedPlaceholder { viewModel.requestUnlock() }
            } else if state.photos.isEmpty {
                Text("Keine Fotos für dieses Workout.")
                    .foregroundColor(.white)
            } else {
                TabView(selection: $pageIndex) {
                    ForEach(Array(state.photos.enumerated()), id: \.offset) { idx, photo in
                        PhotoPage(photo: photo)
                            .tag(idx)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .always))
                .indexViewStyle(.page(backgroundDisplayMode: .always))
                .onChange(of: state.photos.count) { _, newCount in
                    if pageIndex >= newCount && newCount > 0 {
                        pageIndex = newCount - 1
                    }
                }
            }
        } else {
            ProgressView().tint(.white)
        }
    }

    private func observe() async {
        do {
            for try await state in asyncSequence(for: viewModel.uiStateFlow) {
                self.uiState = state
            }
        } catch {
            print("ProgressViewer observation error: \(error)")
        }
    }
}

// MARK: - Locked placeholder

private struct LockedPlaceholder: View {
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "lock.fill")
                .font(.system(size: 40))
                .foregroundColor(.white.opacity(0.7))
            Text("Tippe, um zu entsperren")
                .font(.headline)
                .foregroundColor(.white)
            Text("Authentifizierung erforderlich.")
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.7))
            Button(action: onRetry) {
                Text("Entsperren")
                    .font(.body.weight(.medium))
                    .foregroundColor(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 10)
                    .background(Color.white.opacity(0.15))
                    .clipShape(Capsule())
            }
            .padding(.top, 8)
            .accessibilityLabel("Entsperren erneut versuchen")
        }
    }
}

// MARK: - Photo page

private struct PhotoPage: View {
    let photo: SharedProgressPicture

    @State private var image: UIImage?

    var body: some View {
        Group {
            if let img = image {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFit()
            } else {
                ProgressView().tint(.white)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task(id: photo.relativePath) {
            self.image = await Self.loadOffMain(relativePath: photo.relativePath)
        }
    }

    private static func loadOffMain(relativePath: String) async -> UIImage? {
        await Task.detached(priority: .userInitiated) {
            loadVaultImage(relativePath: relativePath)
        }.value
    }
}

// MARK: - File-private vault read

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

private typealias SharedProgressViewerViewModel = ProgressViewerViewModel
private typealias SharedViewerUiState = ViewerUiState
private typealias SharedProgressPicture = ProgressPicture
