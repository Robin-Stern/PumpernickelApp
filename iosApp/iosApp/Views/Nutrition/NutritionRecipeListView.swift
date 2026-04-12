import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct NutritionRecipeListView: View {
    private let viewModel = KoinHelper.shared.getRecipeListViewModel()

    @State private var recipes: [Recipe] = []
    @State private var foods: [Food] = []
    @State private var expandedRecipeIds: Set<String> = []

    var body: some View {
        Group {
            if recipes.isEmpty {
                emptyState
            } else {
                recipeList
            }
        }
        .navigationTitle("Rezepte")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                NavigationLink(destination: NutritionRecipeCreationView()) {
                    Image(systemName: "plus")
                }
            }
        }
        .task {
            await withTaskGroup(of: Void.self) { group in
                group.addTask { await observeRecipes() }
                group.addTask { await observeFoods() }
            }
        }
        .onAppear { viewModel.refresh() }
    }

    // MARK: - Empty State
    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "book")
                .font(.system(size: 48))
                .foregroundColor(.secondary)
            Text("Noch keine Rezepte")
                .font(.title3.weight(.semibold))
            Text("Tippe auf + um ein Rezept zu erstellen.")
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Recipe List
    private var recipeList: some View {
        List {
            ForEach(recipes, id: \.id) { recipe in
                recipeCard(recipe: recipe)
                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            viewModel.onEvent(event: RecipeListEventOnRecipeDeleted(recipe: recipe))
                        } label: {
                            Label("Löschen", systemImage: "trash")
                        }
                    }
                    .swipeActions(edge: .leading) {
                        Button {
                            viewModel.onEvent(event: RecipeListEventOnRecipeFavoriteToggled(recipe: recipe))
                        } label: {
                            Label(
                                recipe.isFavorite ? "Entfernen" : "Favorit",
                                systemImage: recipe.isFavorite ? "star.slash" : "star.fill"
                            )
                        }
                        .tint(recipe.isFavorite ? .gray : .yellow)
                    }
            }
        }
        .listStyle(.plain)
    }

    private func recipeCard(recipe: Recipe) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                if recipe.isFavorite {
                    Image(systemName: "star.fill")
                        .foregroundColor(.yellow)
                        .font(.caption)
                }
                Text(recipe.name)
                    .font(.body.weight(.semibold))
                Spacer()
                let macros = viewModel.calculateMacros(recipe: recipe)
                Text("\(Int(macros.calories.rounded())) kcal")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }

            let macros = viewModel.calculateMacros(recipe: recipe)
            MacroRowView(protein: macros.protein, fat: macros.fat, carbs: macros.carbs, sugar: macros.sugar)

            Button {
                withAnimation(.easeInOut(duration: 0.25)) {
                    if expandedRecipeIds.contains(recipe.id) {
                        expandedRecipeIds.remove(recipe.id)
                    } else {
                        expandedRecipeIds.insert(recipe.id)
                    }
                }
            } label: {
                Text(expandedRecipeIds.contains(recipe.id) ? "▲ Zutaten ausblenden" : "▼ \(recipe.ingredients.count) Zutaten")
                    .font(.caption)
                    .foregroundColor(.appAccent)
            }
            .buttonStyle(.plain)

            if expandedRecipeIds.contains(recipe.id) {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(recipe.ingredients, id: \.foodId) { ingredient in
                        let foodName = foods.first(where: { $0.id == ingredient.foodId })?.name ?? "Unbekannt"
                        Text("· \(foodName) — \(Int(ingredient.amountGrams.rounded()))g")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                .padding(.leading, 8)
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }

    // MARK: - Flow Observation
    private func observeRecipes() async {
        do {
            for try await value in asyncSequence(for: viewModel.recipesFlow) {
                self.recipes = value
            }
        } catch {
            print("RecipeList recipes flow error: \(error)")
        }
    }

    private func observeFoods() async {
        do {
            for try await value in asyncSequence(for: viewModel.foodsFlow) {
                self.foods = value
            }
        } catch {
            print("RecipeList foods flow error: \(error)")
        }
    }
}
