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
        .fullScreenCover(
            isPresented: Binding(
                get: { !pendingUnlocks.isEmpty },
                set: { newValue in
                    // Programmatic dismissal (e.g., swipe-down) — pop the head.
                    if !newValue && !pendingUnlocks.isEmpty {
                        pendingUnlocks.removeFirst()
                    }
                }
            )
        ) {
            if let head = pendingUnlocks.first {
                UnlockModalView(event: head) {
                    // Dismiss → pop head. If the queue has more events, the
                    // binding above (`!pendingUnlocks.isEmpty`) remains true and
                    // SwiftUI presents the next one (new UnlockModalView instance,
                    // new .onAppear haptic).
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
