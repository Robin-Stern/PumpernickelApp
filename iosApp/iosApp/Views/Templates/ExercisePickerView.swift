import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct ExercisePickerView: View {
    let onSelect: (String, String, [MuscleGroup]) -> Void

    private let viewModel = KoinHelper.shared.getExerciseCatalogViewModel()

    @Environment(\.dismiss) private var dismiss

    @State private var exercises: [Exercise] = []
    @State private var searchQuery: String = ""
    @State private var selectedMuscleGroup: MuscleGroup? = nil
    @State private var showAnatomyPicker = false
    @State private var hasShownInitialAnatomy = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                selectedMuscleBar
                exerciseList
            }
            .searchable(
                text: $searchQuery,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: "Search exercises..."
            )
            .onChange(of: searchQuery) { _, newValue in
                viewModel.onSearchQueryChanged(query: newValue)
            }
            .navigationTitle("Add Exercise")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                }
            }
            .sheet(isPresented: $showAnatomyPicker) {
                AnatomyPickerView(
                    selectedGroup: selectedMuscleGroup?.dbName,
                    onConfirm: { groupDbName in
                        let group = MuscleGroup.companion.fromDbName(name: groupDbName)
                        selectedMuscleGroup = group
                        viewModel.onMuscleGroupSelected(group: group)
                    }
                )
                .presentationDetents([.large])
            }
            .task {
                await observeExercises()
            }
            .onAppear {
                if !hasShownInitialAnatomy {
                    hasShownInitialAnatomy = true
                    showAnatomyPicker = true
                }
            }
        }
    }

    // MARK: - Selected Muscle Indicator Bar
    private var selectedMuscleBar: some View {
        HStack(spacing: 8) {
            Button(action: { showAnatomyPicker = true }) {
                Image(systemName: "figure.stand")
                    .font(.system(size: 20))
                    .frame(width: 44, height: 36)
                    .background(Color(UIColor.tertiarySystemBackground))
                    .cornerRadius(20)
            }

            if let group = selectedMuscleGroup {
                Text(group.displayName)
                    .font(.subheadline.weight(.medium))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color.appAccent)
                    .foregroundColor(.white)
                    .cornerRadius(20)

                Spacer()

                Button(action: {
                    selectedMuscleGroup = nil
                    viewModel.onMuscleGroupSelected(group: nil)
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title3)
                        .foregroundColor(.secondary)
                }
            } else {
                Text("All Muscles")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                Spacer()
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    // MARK: - Exercise List (tap-to-select instead of NavigationLink, per D-03)
    private var filteredExercises: [Exercise] {
        guard let group = selectedMuscleGroup else { return exercises }
        return exercises.filter { exercise in
            exercise.primaryMuscles.contains { $0.dbName == group.dbName }
        }
    }

    private var exerciseList: some View {
        let displayed = filteredExercises
        return List {
            if displayed.isEmpty {
                Text("No exercises found")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .listRowBackground(Color.clear)
            } else {
                ForEach(displayed, id: \.id) { exercise in
                    Button(action: {
                        onSelect(exercise.id, exercise.name, exercise.primaryMuscles)
                        dismiss()
                    }) {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(exercise.name)
                                    .font(.body.weight(.semibold))
                                    .foregroundColor(.primary)
                                Text(exercise.primaryMuscles.first?.displayName ?? "")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Image(systemName: "plus.circle")
                                .foregroundColor(.appAccent)
                                .font(.title3)
                        }
                        .frame(minHeight: 40)
                    }
                }
            }
        }
        .listStyle(.plain)
    }

    // MARK: - Flow Observation
    private func observeExercises() async {
        do {
            for try await value in asyncSequence(for: viewModel.exercises) {
                self.exercises = value
            }
        } catch {
            print("ExercisePicker flow observation error: \(error)")
        }
    }
}
