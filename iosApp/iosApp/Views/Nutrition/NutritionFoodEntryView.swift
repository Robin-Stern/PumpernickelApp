import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct NutritionFoodEntryView: View {
    @State private var viewModel = KoinHelper.shared.getFoodEntryViewModel()

    private enum InputMode { case manual, search, barcode }

    @State private var uiState = FoodEntryUiState(
        name: "", calories: "", protein: "", fat: "", carbs: "", sugar: "",
        barcode: "", unit: .gram, errorMessage: nil, successMessage: nil,
        editingFoodId: nil, searchQuery: "", isLookingUp: false, pendingLogFood: nil,
        remoteSearchResults: [], isSearchingRemote: false, remoteSearchError: nil
    )
    @State private var filteredFoods: [Food] = []
    @State private var showBarcodeScanner = false
    @State private var showLogDialog = false
    @State private var logAmountText = "100"
    @State private var inputMode: InputMode = .manual
    @State private var foodToDelete: Food?
    @State private var showDeleteConfirmation = false

    @FocusState private var focusedField: Bool

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Picker("Eingabemodus", selection: $inputMode) {
                    Text("Manuell").tag(InputMode.manual)
                    Text("Suchen").tag(InputMode.search)
                    Text("Barcode").tag(InputMode.barcode)
                }
                .pickerStyle(.segmented)

                switch inputMode {
                case .manual:
                    entryForm
                    savedFoodsList
                case .search:
                    onlineSearchSection
                case .barcode:
                    barcodeSection
                }
            }
            .padding()
        }
        .onTapGesture { focusedField = false }
        .alert("Lebensmittel löschen?", isPresented: $showDeleteConfirmation, presenting: foodToDelete) { food in
            Button("Löschen", role: .destructive) {
                viewModel.onEvent(event: FoodEntryEventOnFoodDeleted(food: food))
            }
            Button("Abbrechen", role: .cancel) {}
        } message: { food in
            Text("Möchtest du '\(food.name)' wirklich dauerhaft löschen?")
        }
        .navigationTitle(uiState.editingFoodId != nil ? "Bearbeiten" : "Lebensmittel")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Fertig") { focusedField = false }
            }
        }
        .fullScreenCover(isPresented: $showBarcodeScanner, onDismiss: {
            inputMode = .manual
        }) {
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

            labeledField("Name *", text: Binding(
                get: { uiState.name },
                set: { viewModel.onEvent(event: FoodEntryEventOnNameChanged(value: $0)) }
            ), keyboard: .default)

            Text("Nährwerte pro 100 g/ml")
                .font(.caption)
                .foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            HStack(spacing: 12) {
                labeledNumField("Kalorien (kcal) *", value: uiState.calories) { viewModel.onEvent(event: FoodEntryEventOnCaloriesChanged(value: $0)) }
                labeledNumField("Protein (g)", value: uiState.protein) { viewModel.onEvent(event: FoodEntryEventOnProteinChanged(value: $0)) }
            }
            HStack(spacing: 12) {
                labeledNumField("Fett (g)", value: uiState.fat) { viewModel.onEvent(event: FoodEntryEventOnFatChanged(value: $0)) }
                labeledNumField("Kohlenhydrate (g)", value: uiState.carbs) { viewModel.onEvent(event: FoodEntryEventOnCarbsChanged(value: $0)) }
            }
            HStack(spacing: 12) {
                labeledNumField("Zucker (g)", value: uiState.sugar) { viewModel.onEvent(event: FoodEntryEventOnSugarChanged(value: $0)) }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Einheit")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    unitPicker
                }
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

    private func labeledField(_ label: String, text: Binding<String>, keyboard: UIKeyboardType) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
            TextField("", text: text)
                .keyboardType(keyboard)
                .textFieldStyle(.roundedBorder)
                .submitLabel(.done)
                .focused($focusedField)
        }
    }

    private func labeledNumField(_ label: String, value: String, onChange: @escaping (String) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
            TextField("0", text: Binding(get: { value }, set: onChange))
                .keyboardType(.decimalPad)
                .textFieldStyle(.roundedBorder)
                .focused($focusedField)
        }
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
                foodToDelete = food
                showDeleteConfirmation = true
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

    // MARK: - Online Search (OpenFoodFacts)
    private var onlineSearchSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Lebensmittel in OpenFoodFacts suchen")
                .font(.headline)

            // B4 root-cause: TextField has `.submitLabel(.search)` but no explicit `.onSubmit`, so Return propagates
            // through SwiftUI's submit chain and re-fires the binding's `set:` closure with the current query.
            // That triggers `FoodEntryEvent.OnSearchQueryChanged` in FoodEntryViewModel (line 125), which sets
            // `remoteSearchResults = emptyList()` synchronously. Because the new query equals the old, the
            // debounced searchFoodsRemote pipeline filters via `distinctUntilChanged()` and never re-fires —
            // so the results stay empty and the UI flips to "Keine Ergebnisse". Fix per D-21-05: attach an
            // explicit neutral `.onSubmit` that only collapses the keyboard.
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.secondary)
                TextField("Suchen", text: Binding(
                    get: { uiState.searchQuery },
                    set: { viewModel.onEvent(event: FoodEntryEventOnSearchQueryChanged(value: $0)) }
                ))
                .textFieldStyle(.roundedBorder)
                .submitLabel(.search)
                .focused($focusedField)
            }

            if uiState.isSearchingRemote {
                HStack { ProgressView(); Text("Suche…").foregroundColor(.secondary) }
            }
            if let error = uiState.remoteSearchError {
                Text(error).foregroundColor(.red).font(.caption)
            }
            if uiState.searchQuery.count < 3 {
                Text("Mindestens 3 Zeichen eingeben.").foregroundColor(.secondary).font(.caption)
            } else if uiState.remoteSearchError == nil && !uiState.isSearchingRemote && uiState.remoteSearchResults.isEmpty {
                Text("Keine Ergebnisse").foregroundColor(.secondary)
            } else {
                LazyVStack(spacing: 6) {
                    ForEach(uiState.remoteSearchResults, id: \.name) { result in
                        remoteFoodCard(result)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Barcode Section
    private var barcodeSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Per Barcode hinzufügen")
                .font(.headline)

            Button {
                showBarcodeScanner = true
            } label: {
                Label("Barcode scannen", systemImage: "barcode.viewfinder")
                    .font(.body.weight(.semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color.appAccent)
                    .cornerRadius(12)
            }

            if uiState.isLookingUp {
                HStack {
                    ProgressView()
                    Text("Wird gesucht…").foregroundColor(.secondary)
                }
            }
            if let error = uiState.errorMessage {
                Text(error).foregroundColor(.red).font(.caption)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func remoteFoodCard(_ result: RemoteFoodResult) -> some View {
        Button {
            viewModel.onEvent(event: FoodEntryEventOnRemoteFoodSelected(result: result))
            inputMode = .manual
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(result.name)
                        .font(.body)
                        .fontWeight(.semibold)
                        .foregroundColor(.primary)
                    Spacer()
                    if let brand = result.brand {
                        Text(brand)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                Text("\(Int(result.calories.rounded())) kcal · E \(Int(result.protein.rounded()))g · F \(Int(result.fat.rounded()))g · KH \(Int(result.carbs.rounded()))g")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .buttonStyle(.plain)
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

                    VStack(spacing: 6) {
                        Text("pro 100 \(food.unit == .milliliter ? "ml" : "g")")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text("\(Int(food.calories.rounded())) kcal")
                            .font(.body.weight(.bold))
                            .foregroundColor(.appAccent)
                        MacroRowView(
                            protein: food.protein,
                            fat: food.fat,
                            carbs: food.carbohydrates,
                            sugar: food.sugar
                        )
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Menge (\(food.unit == .milliliter ? "ml" : "g"))")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        TextField("", text: $logAmountText)
                            .keyboardType(.decimalPad)
                            .textFieldStyle(.roundedBorder)
                            .frame(maxWidth: 200)
                            .focused($focusedField)
                    }

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
