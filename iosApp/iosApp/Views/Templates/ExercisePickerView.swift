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

    private let muscleGroups: [MuscleGroup] = MuscleGroup.entries

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                filterChipRow
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
        }
    }

    // MARK: - Filter Chips (same as ExerciseCatalogView)
    private var filterChipRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                Button(action: { showAnatomyPicker = true }) {
                    Image(systemName: "figure.stand")
                        .font(.system(size: 20))
                        .frame(width: 44, height: 36)
                        .background(Color(white: 0.12))
                        .cornerRadius(20)
                }

                ForEach(muscleGroups, id: \.self) { group in
                    let isSelected = selectedMuscleGroup == group
                    Button(action: {
                        if isSelected {
                            selectedMuscleGroup = nil
                            viewModel.onMuscleGroupSelected(group: nil)
                        } else {
                            selectedMuscleGroup = group
                            viewModel.onMuscleGroupSelected(group: group)
                        }
                    }) {
                        Text(group.displayName)
                            .font(.subheadline)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(isSelected ? Color(red: 0.4, green: 0.733, blue: 0.416) : Color(white: 0.12))
                            .foregroundColor(isSelected ? .white : .primary)
                            .cornerRadius(20)
                            .overlay(
                                RoundedRectangle(cornerRadius: 20)
                                    .stroke(isSelected ? Color.clear : Color(white: 0.2), lineWidth: 1)
                                )
                    }
                }
            }
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 12)
    }

    // MARK: - Exercise List (tap-to-select instead of NavigationLink, per D-03)
    private var exerciseList: some View {
        List {
            if exercises.isEmpty {
                Text("No exercises found")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .listRowBackground(Color.clear)
            } else {
                ForEach(exercises, id: \.id) { exercise in
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
                                .foregroundColor(Color(red: 0.4, green: 0.733, blue: 0.416))
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
