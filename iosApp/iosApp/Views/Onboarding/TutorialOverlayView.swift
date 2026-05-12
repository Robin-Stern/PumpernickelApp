import SwiftUI
import UIKit

private struct TutorialPage {
    let title: String
    let body: String
    let emoji: String
}

private let pages: [TutorialPage] = [
    TutorialPage(
        title: "Willkommen bei Pumpernickel",
        body: "Dein persönlicher Fitness-Begleiter für strukturiertes Krafttraining.",
        emoji: "💪"
    ),
    TutorialPage(
        title: "Die drei Bereiche",
        body: "🏋️ Training — Vorlagen erstellen und Workouts loggen\n\n📊 Übersicht — Muskelbelastung und Tagesziele auf einen Blick\n\n🥦 Ernährung — Tagebuch, Lebensmittel und Rezepte verwalten",
        emoji: "📱"
    ),
    TutorialPage(
        title: "Training starten",
        body: "Wähle eine Vorlage im Training-Tab und logge Satz für Satz dein Gewicht und deine Wiederholungen.",
        emoji: "🏋️"
    ),
    TutorialPage(
        title: "Wischen zum Löschen",
        body: "Wische Einträge nach links zum Löschen — in Templates, Rezepten und Tagebuch.",
        emoji: "👈"
    ),
    TutorialPage(
        title: "Wischen zum Favorisieren",
        body: "Wische ein Rezept nach rechts, um es als Favorit zu markieren.",
        emoji: "⭐"
    ),
    TutorialPage(
        title: "Lebensmittel verwalten",
        body: "Im Ernährung-Tab kannst du Lebensmittel mit Nährwerten anlegen und deinem Tagesprotokoll hinzufügen.",
        emoji: "🥦"
    ),
    TutorialPage(
        title: "Rezepte erstellen",
        body: "Kombiniere mehrere Lebensmittel zu einem Rezept — die Makros werden automatisch berechnet.",
        emoji: "📖"
    ),
    TutorialPage(
        title: "Barcode scannen",
        body: "Beim Erfassen eines Lebensmittels kannst du den Barcode scannen, um Nährwerte automatisch zu laden.",
        emoji: "📷"
    ),
    TutorialPage(
        title: "Los geht's!",
        body: "Du kannst dieses Tutorial jederzeit in den Einstellungen erneut aufrufen.",
        emoji: "🚀"
    )
]

struct TutorialOverlayView: View {
    let onFinished: () -> Void

    @State private var currentPage = 0

    private var isLastPage: Bool { currentPage == pages.count - 1 }

    var body: some View {
        ZStack(alignment: .bottom) {
            TabView(selection: $currentPage) {
                ForEach(pages.indices, id: \.self) { index in
                    pageView(pages[index])
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .animation(.easeInOut, value: currentPage)

            VStack(spacing: 0) {
                // Page dots
                HStack(spacing: 8) {
                    ForEach(pages.indices, id: \.self) { index in
                        Circle()
                            .fill(index == currentPage ? Color.appAccent : Color(.systemGray4))
                            .frame(width: index == currentPage ? 10 : 8,
                                   height: index == currentPage ? 10 : 8)
                            .animation(.easeInOut(duration: 0.2), value: currentPage)
                    }
                }
                .padding(.bottom, 24)

                // Buttons
                if isLastPage {
                    Button(action: onFinished) {
                        Text("Jetzt starten")
                            .font(.body.weight(.semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(Color.appAccent)
                            .cornerRadius(14)
                    }
                } else {
                    Button {
                        withAnimation { currentPage += 1 }
                    } label: {
                        Text("Weiter")
                            .font(.body.weight(.semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(Color.appAccent)
                            .cornerRadius(14)
                    }

                    Button(action: onFinished) {
                        Text("Überspringen")
                            .font(.body)
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                    }
                }
            }
            .padding(.horizontal, 32)
            .padding(.bottom, 48)
        }
        .background(Color(.systemBackground))
        .edgesIgnoringSafeArea(.all)
    }

    private func pageView(_ page: TutorialPage) -> some View {
        VStack(spacing: 0) {
            Spacer()
            Text(page.emoji)
                .font(.system(size: 80))
            Spacer().frame(height: 32)
            Text(page.title)
                .font(.title2.weight(.bold))
                .multilineTextAlignment(.center)
            Spacer().frame(height: 16)
            Text(page.body)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .lineSpacing(4)
            Spacer()
            // Reserve space for the fixed bottom controls
            Spacer().frame(height: 180)
        }
        .padding(.horizontal, 32)
    }
}
