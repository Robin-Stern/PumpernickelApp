import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct MainTabView: View {
    @State private var selectedTab = 0
    @State private var pendingUnlocks: [SharedUnlockEvent] = []

    private let gamificationViewModel = GamificationUiKoinHelper().getGamificationViewModel()

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                TemplateListView()
            }
            .tabItem {
                Image(systemName: "dumbbell.fill")
                Text("Workout")
            }
            .tag(0)

            NavigationStack {
                OverviewView()
            }
            .tabItem {
                Image(systemName: "chart.bar.fill")
                Text("Overview")
            }
            .tag(1)

            NavigationStack {
                NutritionDailyLogView()
            }
            .tabItem {
                Image(systemName: "fork.knife")
                Text("Nutrition")
            }
            .tag(2)
        }
        .tint(.appAccent)
        // D-20 queue host. `.fullScreenCover` sits at the TabView root so the modal
        // overlays whichever tab is selected when the engine emits an UnlockEvent.
        // Single pop site: UnlockModalView's onDismiss callback. The binding setter
        // is intentionally a no-op because UnlockModalView delegates dismissal fully
        // via the callback (it does not use @Environment(\.dismiss)); popping in both
        // places silently dropped every second event (CR-01).
        .fullScreenCover(
            isPresented: Binding(
                get: { !pendingUnlocks.isEmpty },
                set: { _ in }
            )
        ) {
            if let head = pendingUnlocks.first {
                UnlockModalView(event: head) {
                    if !pendingUnlocks.isEmpty {
                        pendingUnlocks.removeFirst()
                    }
                }
            }
        }
        .task { await observeUnlocks() }
    }

    // MARK: - Observe unlock events

    private func observeUnlocks() async {
        do {
            for try await event in asyncSequence(for: gamificationViewModel.unlockEvents) {
                // D-20: append to the queue rather than present immediately — this
                // preserves ordering even when multiple events fire within a single
                // save (e.g., rank promotion + achievement tier unlock).
                pendingUnlocks.append(event)
            }
        } catch {
            print("MainTabView unlock observation error: \(error)")
        }
    }
}

// MARK: - Shared type aliases

private typealias SharedUnlockEvent = Shared.UnlockEvent
