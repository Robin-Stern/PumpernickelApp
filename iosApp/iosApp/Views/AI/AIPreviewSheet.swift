import SwiftUI
import Shared

/// Bottom-sheet preview for both F6 (Workout) and F8 (Recipe) AI generations.
/// Both branches share the toolbar (Verwerfen / Alle speichern). The body switches
/// on the content enum.
struct AIPreviewSheet: View {
    enum Content {
        case workout(WorkoutAiPreview)
        case recipe(RecipeAiPreview)
    }

    let content: Content
    let onSaveAll: () -> Void
    let onDiscard: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    switch content {
                    case .workout(let preview):
                        WorkoutPreviewBody(preview: preview)
                    case .recipe(let preview):
                        RecipePreviewBody(preview: preview)
                    }
                }
                .padding()
            }
            .navigationTitle("Vorschau")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(role: .destructive) {
                        onDiscard()
                    } label: {
                        Text("Verwerfen")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        onSaveAll()
                    } label: {
                        Text("Alle speichern").bold()
                    }
                }
            }
        }
    }
}

// MARK: - Workout preview

private struct WorkoutPreviewBody: View {
    let preview: WorkoutAiPreview

    var body: some View {
        VStack(spacing: 16) {
            ForEach(Array(preview.templates.enumerated()), id: \.offset) { _, template in
                TemplateCard(template: template)
            }

            if !preview.inlineNewExercises.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Neu generierte Übungen")
                        .font(.headline)
                    ForEach(Array(preview.inlineNewExercises.enumerated()), id: \.offset) { _, ex in
                        HStack {
                            Image(systemName: "sparkles")
                                .foregroundColor(.accentColor)
                            Text(ex.name)
                            Spacer()
                        }
                        .font(.subheadline)
                    }
                }
                .padding(12)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(10)
            }
        }
    }
}

private struct TemplateCard: View {
    let template: StagedTemplate

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(template.name)
                .font(.title3.bold())
            if let desc = template.description_, !desc.isEmpty {
                Text(desc)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Divider()
            ForEach(Array(template.exercises.enumerated()), id: \.offset) { _, ex in
                HStack(alignment: .top, spacing: 8) {
                    Text("\(ex.targetSets)×\(ex.targetReps)")
                        .font(.subheadline.bold())
                        .frame(width: 56, alignment: .leading)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(ex.exerciseName)
                            .font(.subheadline)
                        Text("\(ex.restPeriodSec)s Pause")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                }
            }
        }
        .padding(14)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
}

// MARK: - Recipe preview

private struct RecipePreviewBody: View {
    let preview: RecipeAiPreview

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                Text(preview.recipe.name)
                    .font(.title2.bold())
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("Zutaten").font(.headline)
                ForEach(Array(preview.recipe.ingredients.enumerated()), id: \.offset) { _, ing in
                    HStack {
                        Text("\(Int(ing.amountGrams))g")
                            .font(.subheadline.bold())
                            .frame(width: 60, alignment: .leading)
                        Text(ing.foodName)
                            .font(.subheadline)
                        Spacer()
                    }
                }
            }
            .padding(12)
            .background(Color(.secondarySystemBackground))
            .cornerRadius(10)

            VStack(alignment: .leading, spacing: 6) {
                Text("Schritte").font(.headline)
                ForEach(Array(preview.recipe.steps.enumerated()), id: \.offset) { idx, step in
                    HStack(alignment: .top, spacing: 8) {
                        Text("\(idx + 1).")
                            .font(.subheadline.bold())
                            .frame(width: 24, alignment: .leading)
                        Text(step)
                            .font(.subheadline)
                    }
                }
            }
            .padding(12)
            .background(Color(.secondarySystemBackground))
            .cornerRadius(10)

            VStack(alignment: .leading, spacing: 6) {
                Text("Passt zu deinen Zielen?").font(.headline)
                MacroFitRow(label: "Kalorien", delta: preview.fitsIndicator.deltaKcalPercent)
                MacroFitRow(label: "Protein", delta: preview.fitsIndicator.deltaProteinPercent)
                MacroFitRow(label: "Fett", delta: preview.fitsIndicator.deltaFatPercent)
                MacroFitRow(label: "Kohlenhydrate", delta: preview.fitsIndicator.deltaCarbsPercent)
                MacroFitRow(label: "Zucker", delta: preview.fitsIndicator.deltaSugarPercent)
                Text(preview.fitsIndicator.fitsAll
                     ? "Passt zu deinen Zielen"
                     : "Weicht von deinen Zielen ab")
                    .font(.caption)
                    .foregroundColor(preview.fitsIndicator.fitsAll ? .accentColor : .orange)
                    .padding(.top, 4)
            }
            .padding(12)
            .background(Color(.secondarySystemBackground))
            .cornerRadius(10)

            if !preview.inlineNewFoods.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Neue Lebensmittel").font(.headline)
                    ForEach(Array(preview.inlineNewFoods.enumerated()), id: \.offset) { _, food in
                        HStack {
                            Image(systemName: "sparkles")
                                .foregroundColor(.accentColor)
                            Text(food.name)
                            Spacer()
                            Text("\(Int(food.calories)) kcal/100g")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        .font(.subheadline)
                    }
                }
                .padding(12)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(10)
            }
        }
    }
}

private struct MacroFitRow: View {
    let label: String
    let delta: Double

    private var formatted: String {
        let prefix = delta >= 0 ? "+" : ""
        return "\(prefix)\(String(format: "%.0f", delta))%"
    }

    private var color: Color {
        abs(delta) <= 10 ? .secondary : .orange
    }

    var body: some View {
        HStack {
            Text(label)
            Spacer()
            Text(formatted)
                .foregroundColor(color)
        }
    }
}
