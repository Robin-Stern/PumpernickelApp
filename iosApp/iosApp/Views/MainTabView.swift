import SwiftUI

struct MainTabView: View {
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                WorkoutEmptyStateView()
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

            PlaceholderTabView(
                iconName: "fork.knife",
                title: "Nutrition",
                message: "Log meals and track your macros. Coming soon."
            )
            .tabItem {
                Image(systemName: "fork.knife")
                Text("Nutrition")
            }
            .tag(2)
        }
        .tint(Color(red: 0.4, green: 0.733, blue: 0.416))
    }
}
