import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct CreateExerciseView: View {
    @Environment(\.dismiss) private var dismiss
    private let viewModel = KoinHelper.shared.getCreateExerciseViewModel()

    @State private var name = ""
    @State private var selectedMuscleGroupDbName: String? = nil
    @State private var selectedEquipment: String? = nil
    @State private var selectedCategory: String? = nil
    @State private var equipmentOptions: [String] = []
    @State private var categoryOptions: [String] = []
    @State private var showAnatomyPicker = false
    @State private var showSuccessToast = false
    @State private var showErrorAlert = false
    @State private var errorMessage = ""
    @State private var isSaving = false

    private var isFormValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
            && selectedMuscleGroupDbName != nil
            && selectedEquipment != nil
            && selectedCategory != nil
    }

    var body: some View {
        VStack(spacing: 24) {
            // Name field
            TextField("Exercise name", text: $name)
                .font(.body)
                .padding(16)
                .background(Color(UIColor.tertiarySystemBackground))
                .cornerRadius(8)
                .onChange(of: name) { _, newValue in
                    viewModel.onNameChanged(name: newValue)
                }

            // Muscle group field (tap opens anatomy picker)
            Button(action: { showAnatomyPicker = true }) {
                HStack {
                    Text(selectedMuscleGroupDbName != nil
                        ? displayName(for: selectedMuscleGroupDbName!)
                        : "Select muscle group")
                        .foregroundColor(selectedMuscleGroupDbName != nil ? .primary : .secondary)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundColor(.secondary)
                }
                .font(.body)
                .padding(16)
                .background(Color(UIColor.tertiarySystemBackground))
                .cornerRadius(8)
            }

            // Equipment picker
            HStack {
                Text("Equipment")
                    .font(.body)
                    .foregroundColor(.secondary)
                Spacer()
                Picker("Equipment", selection: $selectedEquipment) {
                    Text("Select equipment type").tag(nil as String?)
                    ForEach(equipmentOptions, id: \.self) { option in
                        Text(option.capitalized).tag(option as String?)
                    }
                }
                .pickerStyle(.menu)
                .onChange(of: selectedEquipment) { _, newValue in
                    if let equipment = newValue {
                        viewModel.onEquipmentSelected(equipment: equipment)
                    }
                }
            }
            .padding(16)
            .background(Color(UIColor.tertiarySystemBackground))
            .cornerRadius(8)

            // Category picker
            HStack {
                Text("Category")
                    .font(.body)
                    .foregroundColor(.secondary)
                Spacer()
                Picker("Category", selection: $selectedCategory) {
                    Text("Select a category").tag(nil as String?)
                    ForEach(categoryOptions, id: \.self) { option in
                        Text(option.capitalized).tag(option as String?)
                    }
                }
                .pickerStyle(.menu)
                .onChange(of: selectedCategory) { _, newValue in
                    if let category = newValue {
                        viewModel.onCategorySelected(category: category)
                    }
                }
            }
            .padding(16)
            .background(Color(UIColor.tertiarySystemBackground))
            .cornerRadius(8)

            Spacer()

            // Create button
            Button(action: {
                isSaving = true
                viewModel.createExercise()
            }) {
                Text("Create Exercise")
                    .font(.body.weight(.semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(isFormValid
                        ? Color.appAccent
                        : Color.appAccent.opacity(0.5))
                    .cornerRadius(12)
            }
            .disabled(!isFormValid || isSaving)
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .navigationTitle("New Exercise")
        .sheet(isPresented: $showAnatomyPicker) {
            AnatomyPickerView(
                selectedGroup: selectedMuscleGroupDbName,
                onConfirm: { groupDbName in
                    selectedMuscleGroupDbName = groupDbName
                    let group = MuscleGroup.companion.fromDbName(name: groupDbName)
                    viewModel.onMuscleGroupSelected(group: group)
                }
            )
            .presentationDetents([.large])
        }
        .overlay(alignment: .top) {
            if showSuccessToast {
                Text("Exercise created")
                    .font(.subheadline)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.ultraThinMaterial)
                    .cornerRadius(8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .padding(.top, 8)
            }
        }
        .animation(.easeInOut, value: showSuccessToast)
        .alert("Error", isPresented: $showErrorAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Couldn't save exercise. Check your input and try again.")
        }
        .task {
            await withTaskGroup(of: Void.self) { group in
                group.addTask { await observeEquipmentOptions() }
                group.addTask { await observeCategoryOptions() }
                group.addTask { await observeSaveResult() }
            }
        }
    }

    // MARK: - Display Helper

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

    // MARK: - Flow Observations

    private func observeEquipmentOptions() async {
        do {
            for try await value in asyncSequence(for: viewModel.equipmentOptions) {
                self.equipmentOptions = value as! [String]
            }
        } catch {
            print("Equipment options flow error: \(error)")
        }
    }

    private func observeCategoryOptions() async {
        do {
            for try await value in asyncSequence(for: viewModel.categoryOptions) {
                self.categoryOptions = value as! [String]
            }
        } catch {
            print("Category options flow error: \(error)")
        }
    }

    private func observeSaveResult() async {
        do {
            for try await result in asyncSequence(for: viewModel.saveResult) {
                if result is CreateExerciseViewModel.SaveResultSuccess {
                    isSaving = false
                    withAnimation {
                        showSuccessToast = true
                    }
                    try? await Task.sleep(nanoseconds: 2_000_000_000)
                    dismiss()
                } else if let error = result as? CreateExerciseViewModel.SaveResultError {
                    isSaving = false
                    errorMessage = error.message
                    showErrorAlert = true
                }
            }
        } catch {
            print("SaveResult flow error: \(error)")
        }
    }
}
