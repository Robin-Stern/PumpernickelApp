import Foundation
import Shared
import KMPNativeCoroutinesAsync

// KMPNativeCoroutinesAsync (from SPM package https://github.com/rickclephas/KMP-NativeCoroutines)
// provides `asyncSequence(for:)` for observing Kotlin Flows from Swift async/await.
//
// Usage pattern in SwiftUI views:
//   .task {
//       do {
//           for try await value in asyncSequence(for: viewModel.exercises) {
//               self.exercises = value
//           }
//       } catch {
//           print("Flow observation error: \(error)")
//       }
//   }
//
// For @NativeCoroutinesState properties, use asyncSequence(for:) the same way.
// The KMP-NativeCoroutines Gradle plugin generates the necessary Kotlin wrappers
// (e.g., createExerciseCatalogViewModelExercisesNativeFlow) automatically.
