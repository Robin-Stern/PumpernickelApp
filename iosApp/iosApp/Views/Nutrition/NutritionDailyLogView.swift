import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct NutritionDailyLogView: View {
    private let viewModel = KoinHelper.shared.getDailyLogViewModel()

    @State private var uiState: DailyLogUiState?

    // Sheets
    @State private var showFoodPicker = false
    @State private var showAmountDialog = false
    @State private var showAdHocDialog = false
    @State private var showBarcodeScanner = false
    @State private var pendingBarcodeScanner = false

    // Amount dialog
    @State private var amountText = "100"

    // Ad-hoc dialog
    @State private var adHocName = ""
    @State private var adHocCalories = ""
    @State private var adHocProtein = ""
    @State private var adHocFat = ""
    @State private var adHocCarbs = ""
    @State private var adHocSugar = ""
    @State private var adHocAmount = "100"

    // Keyboard
    @FocusState private var focusedField: Bool

    var body: some View {
        VStack(spacing: 0) {
            if let state = uiState {
                dateNavigator(state: state)

                ScrollView {
                    VStack(spacing: 16) {
                        dailyTotalsCard(state: state)
                        entriesList(state: state)
                    }
                    .padding(.horizontal)
                    .padding(.top, 12)
                }

                bottomBar
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle("Tagebuch")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                NavigationLink(destination: NutritionFoodEntryView()) {
                    Image(systemName: "plus.square.on.square")
                }
            }
        }
        .sheet(isPresented: $showFoodPicker, onDismiss: {
            viewModel.dismissAddPicker()
            if pendingBarcodeScanner {
                pendingBarcodeScanner = false
                showBarcodeScanner = true
            }
        }) { foodPickerSheet }
        .sheet(isPresented: $showAmountDialog, onDismiss: {
            viewModel.clearPendingFood()
        }) { amountDialogSheet }
        .sheet(isPresented: $showAdHocDialog, onDismiss: {
            viewModel.dismissAdHocDialog()
            resetAdHocFields()
        }) { adHocDialogSheet }
        .fullScreenCover(isPresented: $showBarcodeScanner) {
            BarcodeScannerView { barcode in
                showBarcodeScanner = false
                viewModel.onBarcodeScanned(barcode: barcode)
            }
        }
        .task { await observeUiState() }
        .onChange(of: uiState?.pendingFood) { _, newVal in
            if newVal != nil {
                amountText = "100"
                showAmountDialog = true
            }
        }
        .onChange(of: uiState?.showAddPicker) { _, newVal in
            showFoodPicker = newVal ?? false
        }
        .onChange(of: uiState?.showAdHocDialog) { _, newVal in
            showAdHocDialog = newVal ?? false
        }
    }

    // MARK: - Date Navigator
    private func dateNavigator(state: DailyLogUiState) -> some View {
        HStack {
            Button { viewModel.goPreviousDay() } label: {
                Image(systemName: "chevron.left")
            }
            Spacer()
            Button { viewModel.goToday() } label: {
                Text(formatDate(state.selectedDate))
                    .font(.headline)
            }
            .foregroundColor(.primary)
            Spacer()
            Button { viewModel.goNextDay() } label: {
                Image(systemName: "chevron.right")
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
    }

    private func formatDate(_ date: Kotlinx_datetimeLocalDate) -> String {
        let calendar = Calendar.current
        let components = DateComponents(year: Int(date.year), month: Int(date.month.ordinal + 1), day: Int(date.day))
        if let nativeDate = calendar.date(from: components) {
            let formatter = DateFormatter()
            formatter.dateStyle = .medium
            formatter.locale = Locale(identifier: "de_DE")
            return formatter.string(from: nativeDate)
        }
        return "\(date.day).\(date.month.ordinal + 1).\(date.year)"
    }

    // MARK: - Daily Totals
    private func dailyTotalsCard(state: DailyLogUiState) -> some View {
        VStack(spacing: 8) {
            Text("\(Int(state.totals.calories.rounded())) kcal")
                .font(.title2.weight(.bold))
            MacroRowView(
                protein: state.totals.protein,
                fat: state.totals.fat,
                carbs: state.totals.carbs,
                sugar: state.totals.sugar
            )
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }

    // MARK: - Entries List
    private func entriesList(state: DailyLogUiState) -> some View {
        Group {
            if state.entries.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "tray")
                        .font(.title)
                        .foregroundColor(.secondary)
                    Text("Noch keine Einträge an diesem Tag.")
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 40)
            } else {
                LazyVStack(spacing: 8) {
                    ForEach(state.entries, id: \.id) { entry in
                        entryRow(entry: entry)
                    }
                }
            }
        }
    }

    private func entryRow(entry: ConsumptionEntry) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(entry.name)
                    .font(.body.weight(.medium))
                Text("\(Int(entry.amount.rounded()))g · \(Int((entry.caloriesPer100 * entry.amount / 100).rounded())) kcal")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Spacer()
            Button(role: .destructive) {
                viewModel.delete(id: entry.id)
            } label: {
                Image(systemName: "trash")
                    .font(.caption)
            }
        }
        .padding()
        .background(Color(.secondarySystemBackground))
        .cornerRadius(10)
    }

    // MARK: - Bottom Bar
    private var bottomBar: some View {
        HStack(spacing: 16) {
            Button {
                viewModel.openAddPicker()
            } label: {
                Label("Eintragen", systemImage: "plus.circle.fill")
                    .font(.body.weight(.semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Color.appAccent)
                    .cornerRadius(12)
            }

            NavigationLink(destination: NutritionRecipeListView()) {
                Label("Rezepte", systemImage: "book.fill")
                    .font(.body.weight(.semibold))
                    .foregroundColor(.appAccent)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Color.appAccent.opacity(0.12))
                    .cornerRadius(12)
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 12)
    }

    // MARK: - Food Picker Sheet
    private var foodPickerSheet: some View {
        NavigationStack {
            List {
                if let foods = uiState?.foods, !foods.isEmpty {
                    ForEach(foods, id: \.id) { food in
                        Button {
                            viewModel.selectFoodForEntry(food: food)
                            showFoodPicker = false
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(food.name)
                                    .font(.body)
                                    .foregroundColor(.primary)
                                Text("\(Int(food.calories.rounded())) kcal/100\(food.unit == .milliliter ? "ml" : "g")")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                } else {
                    Text("Keine Lebensmittel vorhanden.")
                        .foregroundColor(.secondary)
                }
            }
            .navigationTitle("Lebensmittel wählen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .bottomBar) {
                    Button {
                        pendingBarcodeScanner = true
                        showFoodPicker = false
                    } label: {
                        Label("Barcode", systemImage: "barcode.viewfinder")
                    }
                    Spacer()
                    Button {
                        showFoodPicker = false
                        viewModel.openAdHocDialog()
                    } label: {
                        Label("Freier Eintrag", systemImage: "square.and.pencil")
                    }
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") {
                        viewModel.dismissAddPicker()
                        showFoodPicker = false
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: - Amount Dialog
    private var amountDialogSheet: some View {
        NavigationStack {
            VStack(spacing: 20) {
                if let food = uiState?.pendingFood {
                    Text(food.name)
                        .font(.headline)

                    Text("\(Int(food.calories.rounded())) kcal / 100\(food.unit == .milliliter ? "ml" : "g")")
                        .foregroundColor(.secondary)

                    TextField("Menge (\(food.unit == .milliliter ? "ml" : "g"))", text: $amountText)
                        .keyboardType(.decimalPad)
                        .textFieldStyle(.roundedBorder)
                        .frame(maxWidth: 200)
                        .focused($focusedField)

                    Button("Eintragen") {
                        if let amount = Double(amountText) {
                            viewModel.confirmLog(food: food, amount: amount)
                            showAmountDialog = false
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
                        viewModel.clearPendingFood()
                        showAmountDialog = false
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

    // MARK: - Ad-hoc Dialog
    private var adHocDialogSheet: some View {
        NavigationStack {
            Form {
                Section("Lebensmittel (pro 100g)") {
                    TextField("Name", text: $adHocName)
                    TextField("Kalorien (kcal)", text: $adHocCalories)
                        .keyboardType(.decimalPad)
                    TextField("Protein (g)", text: $adHocProtein)
                        .keyboardType(.decimalPad)
                    TextField("Fett (g)", text: $adHocFat)
                        .keyboardType(.decimalPad)
                    TextField("Kohlenhydrate (g)", text: $adHocCarbs)
                        .keyboardType(.decimalPad)
                    TextField("Zucker (g)", text: $adHocSugar)
                        .keyboardType(.decimalPad)
                }
                Section("Menge") {
                    TextField("Menge (g)", text: $adHocAmount)
                        .keyboardType(.decimalPad)
                }
                Section {
                    Button("Eintragen") {
                        let cal = Double(adHocCalories) ?? 0
                        let pro = Double(adHocProtein) ?? 0
                        let fat = Double(adHocFat) ?? 0
                        let carbs = Double(adHocCarbs) ?? 0
                        let sugar = Double(adHocSugar) ?? 0
                        let amount = Double(adHocAmount) ?? 100
                        viewModel.confirmAdHocLog(
                            name: adHocName.isEmpty ? "Freier Eintrag" : adHocName,
                            caloriesPer100: cal, proteinPer100: pro,
                            fatPer100: fat, carbsPer100: carbs,
                            sugarPer100: sugar, unit: .gram, amount: amount
                        )
                        showAdHocDialog = false
                        resetAdHocFields()
                    }
                    .frame(maxWidth: .infinity)
                    .buttonStyle(.borderedProminent)
                    .tint(.appAccent)
                }
            }
            .navigationTitle("Freier Eintrag")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") {
                        viewModel.dismissAdHocDialog()
                        showAdHocDialog = false
                        resetAdHocFields()
                    }
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Fertig") { focusedField = false }
                }
            }
        }
    }

    private func resetAdHocFields() {
        adHocName = ""
        adHocCalories = ""
        adHocProtein = ""
        adHocFat = ""
        adHocCarbs = ""
        adHocSugar = ""
        adHocAmount = "100"
    }

    // MARK: - Flow Observation
    private func observeUiState() async {
        do {
            for try await value in asyncSequence(for: viewModel.uiStateFlow) {
                self.uiState = value
            }
        } catch {
            print("DailyLog flow observation error: \(error)")
        }
    }
}
