import Foundation
import WidgetKit
import ComposeApp
#if canImport(ActivityKit)
import ActivityKit
#endif

/// Le pont entre l'app Kotlin et ce qui n'existe qu'en Swift : WidgetKit (redessiner les widgets)
/// et ActivityKit (activités en direct, et leurs jetons pour les pousser depuis le serveur).
final class KikoBridge: NSObject, NativeWidgets {
    static let shared = KikoBridge()

    /// Une app lancée depuis Xcode reçoit ses notifications par le serveur APNs de développement.
    static var isSandbox: Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }

    func start() {
        IosHooks.shared.widgets = self
        if #available(iOS 16.1, *) {
            LiveActivities.observe()
        }
    }

    func snapshotChanged(json: String) {
        WidgetCenter.shared.reloadAllTimelines()
        if #available(iOS 16.1, *) {
            LiveActivities.sync(KikoSnapshot.decode(json))
        }
    }
}

@available(iOS 16.1, *)
enum LiveActivities {
    private static func register(_ token: Data, kind: String, activity: String) {
        let hex = token.map { String(format: "%02x", $0) }.joined()
        PushRegistry.shared.onToken(token: hex, platform: "ios", kind: kind, activity: activity, sandbox: KikoBridge.isSandbox)
    }

    private static func follow<A: ActivityAttributes>(_ activity: Activity<A>, name: String) {
        Task {
            for await token in activity.pushTokenUpdates {
                register(token, kind: "activity_update", activity: name)
            }
        }
    }

    /// Jetons « push-to-start » (iOS 17.2+) : le serveur lance l'activité même app fermée.
    /// Jetons de mise à jour : le serveur la ferme quand l'appel est fini ou la série sauvée.
    static func observe() {
        if #available(iOS 17.2, *) {
            Task {
                for await token in Activity<CallAttributes>.pushToStartTokenUpdates {
                    register(token, kind: "activity_start", activity: "call")
                }
            }
            Task {
                for await token in Activity<StreakAttributes>.pushToStartTokenUpdates {
                    register(token, kind: "activity_start", activity: "streak")
                }
            }
        }
        Activity<CallAttributes>.activities.forEach { follow($0, name: "call:\($0.attributes.callId)") }
        Activity<StreakAttributes>.activities.forEach { follow($0, name: "streak:\($0.attributes.conversationId)") }
        Task {
            for await activity in Activity<CallAttributes>.activityUpdates {
                follow(activity, name: "call:\(activity.attributes.callId)")
            }
        }
        Task {
            for await activity in Activity<StreakAttributes>.activityUpdates {
                follow(activity, name: "streak:\(activity.attributes.conversationId)")
            }
        }
    }

    /// App ouverte : on lance l'activité du prochain appel s'il commence dans l'heure,
    /// et on ferme celles des appels annulés ou passés.
    static func sync(_ snapshot: KikoSnapshot?) {
        let call = snapshot?.nextCall
        for activity in Activity<CallAttributes>.activities where activity.attributes.callId != call?.callId {
            Task { await activity.end(dismissalPolicy: .immediate) }
        }
        guard ActivityAuthorizationInfo().areActivitiesEnabled, let call else { return }
        let now = Date()
        guard call.start.timeIntervalSince(now) < 3600, call.end > now else { return }
        guard !Activity<CallAttributes>.activities.contains(where: { $0.attributes.callId == call.callId }) else { return }
        let attributes = CallAttributes(
            callId: call.callId,
            conversationId: call.conversationId,
            partnerName: call.partnerName,
            partnerCity: KikoTime.city(call.partnerTimeZone),
            partnerTimeZone: call.partnerTimeZone
        )
        let state = CallAttributes.ContentState(startsAtEpoch: call.startsAt / 1000, minutes: call.minutes, joined: false)
        _ = try? Activity.request(attributes: attributes, contentState: state, pushType: .token)
    }
}
