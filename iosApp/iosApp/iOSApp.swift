import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Un widget ou une activité en direct touché : kiko://chat/<conversation>.
                .onOpenURL { url in
                    guard url.scheme == "kiko", url.host == "chat" else { return }
                    let id = url.lastPathComponent
                    DeepLinks.shared.open(conversationId: id == "home" ? nil : id, outboxId: nil, type: "widget")
                }
        }
        .onChange(of: scenePhase) { phase in
            AppVisibility.shared.foreground = phase == .active
        }
    }
}
