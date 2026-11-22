import ActivityKit
import SwiftUI
import WidgetKit

/// Appel prévu (maquettes 25, 32 et 35) : écran verrouillé, îlot compact, déployé et minimal.
/// Sur un iPhone sans îlot, seule la vue de l'écran verrouillé s'affiche (et la pastille de la barre d'état).
struct CallLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: CallAttributes.self) { context in
            CallLockScreen(attributes: context.attributes, state: context.state)
                .activityBackgroundTint(KikoColor.paper)
                .activitySystemActionForegroundColor(KikoColor.ink)
                .widgetURL(KikoLink.chat(context.attributes.conversationId))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 8) {
                        KikoAvatar(name: context.attributes.partnerName, size: 36)
                        VStack(alignment: .leading, spacing: 0) {
                            Text(context.attributes.partnerName).font(.system(size: 15, weight: .bold))
                            Text("Appel prévu").font(.system(size: 12)).foregroundColor(.secondary)
                        }
                    }
                }
                DynamicIslandExpandedRegion(.trailing) {
                    CallTimer(state: context.state)
                        .font(.system(size: 22, weight: .heavy, design: .rounded))
                        .foregroundColor(KikoColor.sun)
                        .frame(maxWidth: 80, alignment: .trailing)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack {
                        Text("\(KikoTime.clock(context.state.start, timeZone: TimeZone.current.identifier)) chez toi · \(KikoTime.clock(context.state.start, timeZone: context.attributes.partnerTimeZone)) à \(context.attributes.partnerCity)")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)
                        Spacer()
                        Link(destination: KikoLink.chat(context.attributes.conversationId)) {
                            Text("Rejoindre")
                                .font(.system(size: 13, weight: .heavy))
                                .padding(.horizontal, 12).padding(.vertical, 6)
                                .background(KikoColor.primary, in: Capsule())
                                .foregroundColor(.white)
                        }
                    }
                }
            } compactLeading: {
                KikoAvatar(name: context.attributes.partnerName, size: 22)
            } compactTrailing: {
                CallTimer(state: context.state)
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .foregroundColor(KikoColor.sun)
                    .frame(maxWidth: 46)
            } minimal: {
                Image(systemName: "phone.fill").foregroundColor(KikoColor.sun)
            }
            .widgetURL(KikoLink.chat(context.attributes.conversationId))
            .keylineTint(KikoColor.sun)
        }
    }
}

/// Le compte à rebours avant l'appel, puis le temps écoulé pendant l'appel.
struct CallTimer: View {
    let state: CallAttributes.ContentState

    var body: some View {
        if state.start > Date() {
            Text(timerInterval: Date()...state.start, countsDown: true)
                .monospacedDigit()
                .multilineTextAlignment(.trailing)
        } else {
            Text(timerInterval: state.start...max(state.end, state.start.addingTimeInterval(60)), countsDown: false)
                .monospacedDigit()
                .multilineTextAlignment(.trailing)
        }
    }
}

struct CallLockScreen: View {
    let attributes: CallAttributes
    let state: CallAttributes.ContentState

    var body: some View {
        HStack(spacing: 12) {
            KikoAvatar(name: attributes.partnerName, size: 46)
            VStack(alignment: .leading, spacing: 2) {
                Text("Appel avec \(attributes.partnerName)")
                    .font(.system(size: 16, weight: .heavy, design: .rounded))
                    .foregroundColor(KikoColor.ink)
                Text("\(KikoTime.clock(state.start, timeZone: TimeZone.current.identifier)) chez toi · \(KikoTime.clock(state.start, timeZone: attributes.partnerTimeZone)) à \(attributes.partnerCity)")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(KikoColor.mid)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 4) {
                CallTimer(state: state)
                    .font(.system(size: 24, weight: .black, design: .rounded))
                    .foregroundColor(KikoColor.primary)
                    .frame(maxWidth: 90, alignment: .trailing)
                Text("REJOINDRE")
                    .font(.system(size: 11, weight: .heavy))
                    .padding(.horizontal, 10).padding(.vertical, 5)
                    .background(KikoColor.primary, in: Capsule())
                    .foregroundColor(.white)
            }
        }
        .padding(14)
    }
}

/// Série en danger (maquettes 34 et 36) : la flamme et le temps qui reste avant minuit (UTC).
struct StreakLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: StreakAttributes.self) { context in
            HStack(spacing: 12) {
                Text("🔥").font(.system(size: 34))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Sauve votre série de \(context.state.streak) jours")
                        .font(.system(size: 16, weight: .heavy, design: .rounded))
                        .foregroundColor(KikoColor.ink)
                    Text("\(context.attributes.partnerName) a écrit aujourd'hui. Il ne manque que toi.")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(KikoColor.mid)
                }
                Spacer()
                Text(timerInterval: Date()...max(Date(), context.state.deadline), countsDown: true)
                    .font(.system(size: 22, weight: .black, design: .rounded))
                    .monospacedDigit()
                    .foregroundColor(KikoColor.sunInk)
                    .frame(maxWidth: 90, alignment: .trailing)
            }
            .padding(14)
            .activityBackgroundTint(KikoColor.sunLight)
            .widgetURL(KikoLink.chat(context.attributes.conversationId))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label("\(context.state.streak) jours", systemImage: "flame.fill")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(KikoColor.sun)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(timerInterval: Date()...max(Date(), context.state.deadline), countsDown: true)
                        .font(.system(size: 20, weight: .heavy, design: .rounded))
                        .monospacedDigit()
                        .frame(maxWidth: 80, alignment: .trailing)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text("Un message à \(context.attributes.partnerName) et la flamme tient.")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.secondary)
                }
            } compactLeading: {
                Image(systemName: "flame.fill").foregroundColor(KikoColor.sun)
            } compactTrailing: {
                Text(timerInterval: Date()...max(Date(), context.state.deadline), countsDown: true)
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .frame(maxWidth: 46)
            } minimal: {
                Image(systemName: "flame.fill").foregroundColor(KikoColor.sun)
            }
            .widgetURL(KikoLink.chat(context.attributes.conversationId))
            .keylineTint(KikoColor.sun)
        }
    }
}
