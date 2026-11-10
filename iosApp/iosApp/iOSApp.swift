import SwiftUI
import FirebaseCore
import Shared

@main
struct iOSApp: App {
    init() {
        FirebaseApp.configure()
        // Подключаем нативный WebRTC к общему коду звонков.
        CallPlatform.shared.engine = WebRTCCallEngine()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}
