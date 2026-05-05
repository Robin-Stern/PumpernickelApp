import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

/// Phase 17 D-17-01 / D-17-02 / D-17-03.
/// Three-button card mounted on the WorkoutFinished surface above the Done
/// button. Non-blocking — Done stays enabled regardless of the card's state.
/// Header copy switches to "Noch ein Foto?" once the user has saved at least
/// one photo. Errors render inline (no toasts, no alerts — D-17-17).
struct ProgressPicturePromptCard: View {
    let workoutId: Int64

    @State private var viewModel: SharedProgressPicturePromptViewModel
    @State private var uiState: SharedPromptUiState?

    init(workoutId: Int64) {
        self.workoutId = workoutId
        _viewModel = State(initialValue: ProgressPicturePromptKoinHelper()
            .getProgressPicturePromptViewModel(workoutId: workoutId))
    }

    var body: some View {
        Group {
            if uiState?.dismissed == true {
                EmptyView()
            } else {
                cardBody
            }
        }
        .task { await observe() }
    }

    private var cardBody: some View {
        let showAddAnother = uiState?.showAddAnother ?? false
        let busy = uiState?.busy ?? false
        let count = Int(uiState?.photoCount ?? 0)

        return VStack(alignment: .leading, spacing: 10) {
            Text(showAddAnother ? "Noch ein Foto?" : "Fortschritts-Foto?")
                .font(.headline)
            Text(showAddAnother
                 ? "Du kannst weitere Fotos zu diesem Workout anhängen."
                 : "Halte deinen Fortschritt fest — Fotos bleiben verschlüsselt auf deinem Gerät.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: 8) {
                Button(action: { viewModel.onTakePhotoClick() }) {
                    Label("Foto aufnehmen", systemImage: "camera")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.appAccent)
                .disabled(busy)

                Button(action: { viewModel.onPickFromLibraryClick() }) {
                    Label("Aus Galerie", systemImage: "photo.on.rectangle")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(busy)
            }

            HStack {
                if busy {
                    ProgressView().scaleEffect(0.8)
                    Text("Speichere …")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                } else if count > 0 {
                    Text("\(count) Foto\(count == 1 ? "" : "s") angehängt")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
                Spacer()
                Button(count > 0 ? "Fertig" : "Überspringen") {
                    viewModel.onSkipClick()
                }
                .buttonStyle(.borderless)
                .disabled(busy)
            }

            if let err = uiState?.error {
                Text(err)
                    .font(.footnote)
                    .foregroundColor(.red)
            }
        }
        .padding(14)
        .background(Color(uiColor: .secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func observe() async {
        do {
            for try await state in asyncSequence(for: viewModel.uiStateFlow) {
                self.uiState = state
            }
        } catch {
            print("ProgressPicturePrompt observation error: \(error)")
        }
    }
}

// MARK: - Shared type aliases

private typealias SharedProgressPicturePromptViewModel = ProgressPicturePromptViewModel
private typealias SharedPromptUiState = PromptUiState
