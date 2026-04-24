import SwiftUI

struct WorkoutEmptyStateView: View {
    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            VStack(spacing: 16) {
                Image(systemName: "dumbbell.fill")
                    .font(.system(size: 64))
                    .foregroundColor(.secondary)

                Text("No Workouts Yet")
                    .font(.title3.weight(.semibold))

                Text("Start by exploring exercises to build your first workout template.")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)


                Spacer()

                NavigationLink(destination: ExerciseCatalogView()) {
                    Text("Browse Exercises")
                        .font(.body.weight(.semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color.appAccent)
                        .cornerRadius(12)
                }
                .padding(.horizontal, 32)
                .padding(.bottom, 32)
            }
            .navigationTitle("Workout")
        }
    }
}
