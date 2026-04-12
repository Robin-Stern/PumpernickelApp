import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct NutritionRecipeCreationView: View {
    private let viewModel = KoinHelper.shared.getRecipeCreationViewModel()

    @State private var state = RecipeCreationUiState(
        recipeName: "", searchQuery: "", searchResults: [],
        ingredients: [], totals: RecipeMacros(calories: 0, protein: 0, fat: 0, carbs: 0, sugar: 0),
        errorMessage: nil
    )
    @Environment(\.dismiss) private var dismiss
    @FocusState private var focusedField: Bool

    var body: some View {
        Form {
            Section("Rezept") {
                TextField("Rezeptname *", text: Binding(
                    get: { state.recipeName },
                    set: { viewModel.onEvent(event: RecipeCreationEventOnRecipeNameChanged(value: $0)) }
                ))
                .submitLabel(.done)
                .focused($focusedField)
            }

            Section("Lebensmittel suchen") {
                TextField("Suchen…", text: Binding(
                    get: { state.searchQuery },
                    set: { viewModel.onEvent(event: RecipeCreationEventOnSearchQueryChanged(value: $0)) }
                ))
                .submitLabel(.done)
                .focused($focusedField)

                ForEach(state.searchResults, id: \.id) { food in
                    Button {
                        viewModel.onEvent(event: RecipeCreationEventOnFoodSelected(food: food))
                    } label: {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(food.name).foregroundColor(.primary)
                                Text("\(Int(food.calories.rounded())) kcal/100g")
                                    .font(.caption).foregroundColor(.secondary)
                            }
                            Spacer()
                            Image(systemName: "plus.circle")
                                .foregroundColor(.appAccent)
                        }
                    }
                }
            }

            if !state.ingredients.isEmpty {
                Section("Zutaten") {
                    ForEach(Array(state.ingredients.enumerated()), id: \.offset) { index, ingredient in
                        HStack {
                            Text(ingredient.food.name)
                                .lineLimit(1)
                            Spacer()
                            TextField("g", text: Binding(
                                get: { ingredient.amountGrams },
                                set: { viewModel.onEvent(event: RecipeCreationEventOnIngredientAmountChanged(index: Int32(index), value: $0)) }
                            ))
                            .keyboardType(.decimalPad)
                            .frame(width: 70)
                            .textFieldStyle(.roundedBorder)
                            .focused($focusedField)

                            Button {
                                viewModel.onEvent(event: RecipeCreationEventOnIngredientRemoved(index: Int32(index)))
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.red)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                Section("Gesamt") {
                    HStack {
                        Text("\(Int(state.totals.calories.rounded())) kcal")
                            .font(.headline)
                        Spacer()
                        MacroRowView(
                            protein: state.totals.protein,
                            fat: state.totals.fat,
                            carbs: state.totals.carbs,
                            sugar: state.totals.sugar
                        )
                    }
                }
            }

            if let error = state.errorMessage {
                Section {
                    Text(error)
                        .foregroundColor(.red)
                        .font(.caption)
                }
            }

            Section {
                Button {
                    viewModel.onEvent(event: RecipeCreationEventOnSaveClicked.shared)
                } label: {
                    Text("Rezept speichern")
                        .font(.body.weight(.semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(Color.appAccent)
                        .cornerRadius(12)
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            }
        }
        .navigationTitle("Neues Rezept")
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Fertig") { focusedField = false }
            }
        }
        .task {
            await withTaskGroup(of: Void.self) { group in
                group.addTask { await observeCreationState() }
                group.addTask { await observeSavedEvent() }
            }
        }
        .onAppear { viewModel.reset() }
    }

    // MARK: - Flow Observation
    private func observeCreationState() async {
        do {
            for try await value in asyncSequence(for: viewModel.creationStateFlow) {
                self.state = value
            }
        } catch {
            print("RecipeCreation state flow error: \(error)")
        }
    }

    private func observeSavedEvent() async {
        do {
            for try await _ in asyncSequence(for: viewModel.savedEvent) {
                dismiss()
            }
        } catch {
            print("RecipeCreation saved event error: \(error)")
        }
    }
}
