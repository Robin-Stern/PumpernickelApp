import SwiftUI
import Shared

@main
struct PumpernickelApp: App {
    init() {
        KoinInitIosKt.doInitKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            MainTabView()
                .preferredColorScheme(.dark)
        }
    }
}
