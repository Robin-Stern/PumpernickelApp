import SwiftUI

struct MainTabView: View {
    @State private var selectedTab = 0

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

            PlaceholderTabView(
                iconName: "chart.bar.fill",
                title: "Overview",
                message: "Track your training progress and stats. Coming soon."
            )
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
    }
}
