import UIKit
import UserNotifications
import ComposeApp

/// Notifications côté iPhone : jeton APNs, affichage quand l'app est ouverte, et boutons
/// (répondre depuis l'écran verrouillé, marquer comme lu, merci, bravo) — maquette 23.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        center.setNotificationCategories(Self.categories)
        // Déjà autorisé : on redemande le jeton à chaque lancement (il peut changer).
        center.getNotificationSettings { settings in
            switch settings.authorizationStatus {
            case .authorized, .provisional, .ephemeral:
                DispatchQueue.main.async { application.registerForRemoteNotifications() }
            default:
                break
            }
        }
        KikoBridge.shared.start()
        // PushKit doit être prêt dès le lancement : un appel peut être ce qui a réveillé l'app.
        KikoCalls.shared.start()
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        PushRegistry.shared.onToken(token: token, platform: "ios", kind: "alert", activity: nil, sandbox: KikoBridge.isSandbox)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("Kiko : pas de jeton APNs (\(error.localizedDescription))")
    }

    // L'app est ouverte : la discussion affichée n'a pas besoin de bannière.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let info = notification.request.content.userInfo["kiko"] as? [String: Any]
        if let conv = info?["conversation_id"] as? String, conv == AppVisibility.shared.openConversation {
            completionHandler([])
        } else {
            completionHandler([.banner, .list, .sound])
        }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo["kiko"] as? [String: Any] ?? [:]
        let conv = info["conversation_id"] as? String
        let outbox = (info["outbox_id"] as? NSNumber)?.stringValue ?? info["outbox_id"] as? String
        let type = info["type"] as? String

        // Sonnerie de secours (iPhone sans jeton VoIP) : toucher l'alerte fait sonner l'appel dans l'app.
        if type == "call_ring", let call = info["call_id"] as? String {
            Calls.shared.onIncoming(callId: call.lowercased(), conversationId: conv ?? "", callerName: info["caller_name"] as? String ?? "Kiko")
            completionHandler()
            return
        }

        switch response.actionIdentifier {
        case Action.reply:
            guard let conv, let text = (response as? UNTextInputNotificationResponse)?.userText else { break }
            NotificationActions.shared.reply(conversationId: conv, text: text) { _, _ in completionHandler() }
            return
        case Action.markRead:
            guard let conv else { break }
            NotificationActions.shared.markRead(conversationId: conv) { _, _ in completionHandler() }
            return
        case Action.thank:
            guard let conv else { break }
            NotificationActions.shared.thank(conversationId: conv) { _, _ in completionHandler() }
            return
        case Action.bravo:
            guard let conv else { break }
            let days = (info["streak"] as? NSNumber)?.stringValue ?? ""
            NotificationActions.shared.reply(conversationId: conv, text: "🎉 Bravo pour nos \(days) jours !") { _, _ in completionHandler() }
            return
        case UNNotificationDismissActionIdentifier:
            break
        default:
            // Toucher la notification, ou « Rejoindre » : on ouvre la discussion.
            DeepLinks.shared.open(conversationId: conv, outboxId: outbox, type: type)
        }
        completionHandler()
    }

    enum Action {
        static let reply = "REPLY"
        static let markRead = "MARK_READ"
        static let thank = "THANK"
        static let bravo = "BRAVO"
        static let join = "JOIN"
    }

    /// Les catégories reprennent celles de supabase/functions/_shared/payloads.ts.
    static var categories: Set<UNNotificationCategory> {
        let reply = UNTextInputNotificationAction(identifier: Action.reply, title: "Répondre", options: [],
                                                  textInputButtonTitle: "Envoyer", textInputPlaceholder: "Ta réponse…")
        let markRead = UNNotificationAction(identifier: Action.markRead, title: "Marquer comme lu", options: [])
        let thank = UNNotificationAction(identifier: Action.thank, title: "Merci 🙏", options: [])
        let bravo = UNNotificationAction(identifier: Action.bravo, title: "Envoyer un bravo", options: [])
        let join = UNNotificationAction(identifier: Action.join, title: "Rejoindre", options: [.foreground])
        return [
            UNNotificationCategory(identifier: "MESSAGE", actions: [reply, markRead], intentIdentifiers: [], options: []),
            UNNotificationCategory(identifier: "CORRECTION", actions: [thank, reply], intentIdentifiers: [], options: []),
            UNNotificationCategory(identifier: "REQUEST", actions: [], intentIdentifiers: [], options: []),
            UNNotificationCategory(identifier: "CALL", actions: [join], intentIdentifiers: [], options: []),
            UNNotificationCategory(identifier: "CALL_SOON", actions: [join], intentIdentifiers: [], options: []),
            UNNotificationCategory(identifier: "CALL_RING", actions: [], intentIdentifiers: [], options: []),
            UNNotificationCategory(identifier: "STREAK", actions: [reply], intentIdentifiers: [], options: []),
            UNNotificationCategory(identifier: "MILESTONE", actions: [bravo], intentIdentifiers: [], options: []),
            UNNotificationCategory(identifier: "REMINDER", actions: [], intentIdentifiers: [], options: []),
        ]
    }
}
