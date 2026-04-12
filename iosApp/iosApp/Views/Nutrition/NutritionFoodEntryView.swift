import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct NutritionFoodEntryView: View {
    private let viewModel = KoinHelper.shared.getFoodEntryViewModel()

    @State private var uiState = FoodEntryUiState(
        name: "", calories: "", protein: "", fat: "", carbs: "", sugar: "",
        barcode: "", unit: .gram, errorMessage: nil, successMessage: nil,
        editingFoodId: nil, searchQuery: "", isLookingUp: false, pendingLogFood: nil
    )
    @State private var filteredFoods: [Food] = []
    @State private var showBarcodeScanner = false
    @State private var showLogDialog = false
    @State private var logAmountText = "100"

    @FocusState private var focusedField: Bool

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                entryForm
                savedFoodsList
            }
            .padding()
        }
        .onTapGesture { focusedField = false }
        .navigationTitle(uiState.editingFoodId != nil ? "Bearbeiten" : "Lebensmittel")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Fertig") { focusedField = false }
            }
        }
        .fullScreenCover(isPresented: $showBarcodeScanner) {
            BarcodeScannerView { barcode in
                showBarcodeScanner = false
                viewModel.onEvent(event: FoodEntryEventOnBarcodeScanned(barcode: barcode))
            }
        }
        .sheet(isPresented: $showLogDialog, onDismiss: {
            viewModel.onEvent(event: FoodEntryEventOnDismissLogDialog.shared)
        }) { logAmountSheet }
        .task {
            await withTaskGroup(of: Void.self) { group in
                group.addTask { await observeUiState() }
                group.addTask { await observeFilteredFoods() }
            }
        }
        .onChange(of: uiState.pendingLogFood) { _, newVal in
            if newVal != nil {
                logAmountText = "100"
                showLogDialog = true
            }
        }
    }

    // MARK: - Entry Form
    private var entryForm: some View {
        VStack(spacing: 12) {
            if let error = uiState.errorMessage {
                Text(error)
                    .font(.caption)
                    .foregroundColor(.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            if let success = uiState.successMessage {
                Text(success)
                    .font(.caption)
                    .foregroundColor(.green)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            if uiState.isLookingUp {
                HStack {
                    ProgressView()
                    Text("Suche Produkt…")
                        .foregroundColor(.secondary)
                }
            }

            TextField("Name *", text: Binding(
                get: { uiState.name },
                set: { viewModel.onEvent(event: FoodEntryEventOnNameChanged(value: $0)) }
            ))
            .textFieldStyle(.roundedBorder)
            .submitLabel(.done)
            .focused($focusedField)

            HStack(spacing: 12) {
                numField("Kalorien (kcal) *", value: uiState.calories) { viewModel.onEvent(event: FoodEntryEventOnCaloriesChanged(value: $0)) }
                numField("Protein (g) *", value: uiState.protein) { viewModel.onEvent(event: FoodEntryEventOnProteinChanged(value: $0)) }
            }
            HStack(spacing: 12) {
                numField("Fett (g) *", value: uiState.fat) { viewModel.onEvent(event: FoodEntryEventOnFatChanged(value: $0)) }
                numField("KH (g) *", value: uiState.carbs) { viewModel.onEvent(event: FoodEntryEventOnCarbsChanged(value: $0)) }
            }
            HStack(spacing: 12) {
                numField("Zucker (g) *", value: uiState.sugar) { viewModel.onEvent(event: FoodEntryEventOnSugarChanged(value: $0)) }
                unitPicker
            }

            HStack(spacing: 12) {
                Button {
                    showBarcodeScanner = true
                } label: {
                    Label("Barcode", systemImage: "barcode.viewfinder")
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(Color(.tertiarySystemBackground))
                        .cornerRadius(10)
                }

                if uiState.editingFoodId != nil {
                    Button("Abbrechen") {
                        viewModel.onEvent(event: FoodEntryEventOnCancelEdit.shared)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(Color(.tertiarySystemBackground))
                    .cornerRadius(10)
                }
            }

            Button {
                viewModel.onEvent(event: FoodEntryEventOnSaveClicked.shared)
            } label: {
                Text(uiState.editingFoodId != nil ? "Aktualisieren" : "Speichern")
                    .font(.body.weight(.semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Color.appAccent)
                    .cornerRadius(12)
            }
        }
    }

    private func numField(_ placeholder: String, value: String, onChange: @escaping (String) -> Void) -> some View {
        TextField(placeholder, text: Binding(get: { value }, set: onChange))
            .keyboardType(.decimalPad)
            .textFieldStyle(.roundedBorder)
            .focused($focusedField)
    }

    private var unitPicker: some View {
        Picker("Einheit", selection: Binding(
            get: { uiState.unit },
            set: { viewModel.onEvent(event: FoodEntryEventOnUnitChanged(unit: $0)) }
        )) {
            Text("Gramm").tag(FoodUnit.gram)
            Text("ml").tag(FoodUnit.milliliter)
        }
        .pickerStyle(.segmented)
    }

    // MARK: - Saved Foods
    private var savedFoodsList: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Gespeicherte Lebensmittel")
                .font(.headline)

            TextField("Suchen…", text: Binding(
                get: { uiState.searchQuery },
                set: { viewModel.onEvent(event: FoodEntryEventOnSearchQueryChanged(value: $0)) }
            ))
            .textFieldStyle(.roundedBorder)
            .submitLabel(.done)
            .focused($focusedField)

            if filteredFoods.isEmpty {
                Text("Keine Lebensmittel gefunden.")
                    .foregroundColor(.secondary)
                    .padding(.vertical, 8)
            } else {
                LazyVStack(spacing: 6) {
                    ForEach(filteredFoods, id: \.id) { food in
                        foodRow(food: food)
                    }
                }
            }
        }
    }

    private func foodRow(food: Food) -> some View {
        HStack {
            Button {
                viewModel.onEvent(event: FoodEntryEventOnFoodSelected(food: food))
            } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text(food.name)
                        .font(.body)
                        .foregroundColor(.primary)
                    HStack(spacing: 4) {
                        Text("\(Int(food.calories.rounded())) kcal")
                        MacroRowView(protein: food.protein, fat: food.fat, carbs: food.carbohydrates, sugar: food.sugar)
                    }
                    .font(.caption)
                }
            }
            Spacer()
            Button(role: .destructive) {
                viewModel.onEvent(event: FoodEntryEventOnFoodDeleted(food: food))
            } label: {
                Image(systemName: "trash")
                    .font(.caption)
                    .foregroundColor(.red)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(10)
    }

    // MARK: - Log Amount Sheet
    private var logAmountSheet: some View {
        NavigationStack {
            VStack(spacing: 20) {
                if let food = uiState.pendingLogFood {
                    Text(food.name).font(.headline)
                    Text("\(Int(food.calories.rounded())) kcal / 100\(food.unit == .milliliter ? "ml" : "g")")
                        .foregroundColor(.secondary)
                    TextField("Menge (\(food.unit == .milliliter ? "ml" : "g"))", text: $logAmountText)
                        .keyboardType(.decimalPad)
                        .textFieldStyle(.roundedBorder)
                        .frame(maxWidth: 200)
                        .focused($focusedField)
                    Button("Eintragen") {
                        if let amount = Double(logAmountText) {
                            viewModel.onEvent(event: FoodEntryEventOnConfirmLogAmount(food: food, amount: amount))
                            showLogDialog = false
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.appAccent)
                }
                Spacer()
            }
            .padding(.top, 32)
            .navigationTitle("Menge eingeben")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") {
                        viewModel.onEvent(event: FoodEntryEventOnDismissLogDialog.shared)
                        showLogDialog = false
                    }
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Fertig") { focusedField = false }
                }
            }
        }
        .presentationDetents([.medium])
    }

    // MARK: - Flow Observation
    private func observeUiState() async {
        do {
            for try await value in asyncSequence(for: viewModel.uiStateFlow) {
                self.uiState = value
            }
        } catch {
            print("FoodEntry uiState flow error: \(error)")
        }
    }

    private func observeFilteredFoods() async {
        do {
            for try await value in asyncSequence(for: viewModel.filteredFoodsFlow) {
                self.filteredFoods = value
            }
        } catch {
            print("FoodEntry filteredFoods flow error: \(error)")
        }
    }
}
