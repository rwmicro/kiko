import SwiftUI
import WidgetKit

/// Les widgets iPhone des maquettes 27 à 29, et les activités en direct des maquettes 25 et 32 à 36.
/// Ils lisent le JSON que l'app écrit dans le groupe d'apps ; aucun code Kotlin n'est chargé ici
/// (une extension de widget a peu de mémoire).
@main
struct KikoWidgetBundle: WidgetBundle {
    var body: some Widget {
        StreakWidget()
        PartnersWidget()
        NextCallWidget()
        CallLiveActivity()
        StreakLiveActivity()
    }
}

struct KikoEntry: TimelineEntry {
    let date: Date
    let snapshot: KikoSnapshot?
}

/// Une entrée tous les quarts d'heure pendant deux heures : les heures locales avancent toutes seules.
struct KikoProvider: TimelineProvider {
    func placeholder(in context: Context) -> KikoEntry { KikoEntry(date: Date(), snapshot: .sample) }

    func getSnapshot(in context: Context, completion: @escaping (KikoEntry) -> Void) {
        completion(KikoEntry(date: Date(), snapshot: KikoSnapshot.load() ?? (context.isPreview ? .sample : nil)))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<KikoEntry>) -> Void) {
        let snapshot = KikoSnapshot.load()
        let now = Date()
        let start = Calendar.current.dateInterval(of: .minute, for: now)?.start ?? now
        let entries = (0..<8).map { KikoEntry(date: start.addingTimeInterval(Double($0) * 900), snapshot: snapshot) }
        completion(Timeline(entries: entries, policy: .atEnd))
    }
}

extension KikoSnapshot {
    static let sample = KikoSnapshot(
        updatedAt: Date().timeIntervalSince1970 * 1000,
        streak: Streak(conversationId: "ayu", partnerName: "Ayu", days: 6, waitingForMe: true),
        partners: [
            Partner(conversationId: "ayu", name: "Ayu", timeZone: "Asia/Jakarta", lastSeen: Date().timeIntervalSince1970 * 1000, unread: 2, lastMessage: "Besok kita telepon?", lastMessageMine: false, streak: 6),
            Partner(conversationId: "putri", name: "Putri", timeZone: "Asia/Makassar", lastSeen: nil, unread: 0, lastMessage: nil, lastMessageMine: true, streak: 2),
            Partner(conversationId: "budi", name: "Budi", timeZone: "Asia/Jayapura", lastSeen: nil, unread: 0, lastMessage: nil, lastMessageMine: false, streak: 0),
        ],
        nextCall: Call(callId: "c", conversationId: "ayu", partnerName: "Ayu", partnerTimeZone: "Asia/Jakarta", startsAt: (Date().timeIntervalSince1970 + 540) * 1000, minutes: 15)
    )
}

extension View {
    /// iOS 17 demande le fond via containerBackground ; avant, un simple fond.
    @ViewBuilder
    func kikoBackground(_ color: Color) -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            containerBackground(color, for: .widget)
        } else {
            background(color)
        }
    }
}

// MARK: - Série à deux (petit, et écran verrouillé)

struct StreakWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "KikoStreak", provider: KikoProvider()) { entry in
            StreakView(entry: entry)
        }
        .configurationDisplayName("Série à deux")
        .description("Ta plus longue série, et qui attend ton message.")
        .supportedFamilies([.systemSmall, .accessoryCircular, .accessoryInline])
    }
}

struct StreakView: View {
    @Environment(\.widgetFamily) var family
    let entry: KikoEntry

    var body: some View {
        let streak = entry.snapshot?.streak
        switch family {
        case .accessoryInline:
            Text(streak.map { "🔥 \($0.days) jours avec \($0.partnerName)" } ?? "Kiko")
                .widgetURL(KikoLink.chat(streak?.conversationId))
        case .accessoryCircular:
            ZStack {
                AccessoryWidgetBackground()
                VStack(spacing: 0) {
                    Image(systemName: "flame.fill").font(.system(size: 12))
                    Text("\(streak?.days ?? 0)").font(.system(size: 20, weight: .heavy, design: .rounded))
                }
            }
            .widgetURL(KikoLink.chat(streak?.conversationId))
            .kikoBackground(.clear)
        default:
            VStack(spacing: 2) {
                Text("🔥").font(.system(size: 26))
                if let streak {
                    Text("\(streak.days)")
                        .font(.system(size: 46, weight: .black, design: .rounded))
                        .foregroundColor(KikoColor.sunInk)
                    Text(streak.waitingForMe ? "\(streak.partnerName) attend ton message" : "série avec \(streak.partnerName)")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(KikoColor.ink)
                        .multilineTextAlignment(.center)
                } else {
                    Text("Écris à un partenaire pour lancer une série")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(KikoColor.sunInk)
                        .multilineTextAlignment(.center)
                }
            }
            .padding(6)
            .widgetURL(KikoLink.chat(streak?.conversationId))
            .kikoBackground(KikoColor.sunLight)
        }
    }
}

// MARK: - Partenaires et leur heure (moyen, et écran verrouillé)

struct PartnersWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "KikoPartners", provider: KikoProvider()) { entry in
            PartnersView(entry: entry)
        }
        .configurationDisplayName("Partenaires")
        .description("Qui est en ligne, avec son heure locale et son ciel.")
        .supportedFamilies([.systemMedium, .accessoryRectangular])
    }
}

struct PartnersView: View {
    @Environment(\.widgetFamily) var family
    let entry: KikoEntry

    var body: some View {
        let partners = entry.snapshot?.partners ?? []
        if family == .accessoryRectangular {
            let p = partners.first
            VStack(alignment: .leading, spacing: 1) {
                Text(p.map { "\($0.name)\($0.online(at: entry.date) ? " · en ligne" : "")" } ?? "Kiko")
                    .font(.system(size: 14, weight: .bold))
                if let p {
                    Text("\(KikoTime.clock(entry.date, timeZone: p.timeZone)) chez \(p.name)\(p.unread > 0 ? " · \(p.unread) message\(p.unread > 1 ? "s" : "")" : "")")
                        .font(.system(size: 12))
                    if let last = p.lastMessage, !p.lastMessageMine {
                        Text(last).font(.system(size: 12)).lineLimit(1)
                    }
                }
            }
            .widgetURL(KikoLink.chat(p?.conversationId))
            .kikoBackground(.clear)
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Text(partners.contains { $0.online(at: entry.date) } ? "En ligne maintenant" : "Tes partenaires")
                    .font(.system(size: 12, weight: .heavy))
                    .foregroundColor(KikoColor.mid)
                if partners.isEmpty {
                    Text("Salue quelqu'un dans Kiko pour le voir ici.")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(KikoColor.ink)
                }
                HStack(spacing: 10) {
                    ForEach(partners.prefix(4), id: \.conversationId) { p in
                        Link(destination: KikoLink.chat(p.conversationId)) {
                            VStack(spacing: 4) {
                                ZStack(alignment: .bottomTrailing) {
                                    KikoAvatar(name: p.name, size: 40)
                                    if p.online(at: entry.date) {
                                        Circle().fill(KikoColor.ok).frame(width: 11, height: 11)
                                            .overlay(Circle().stroke(Color.white, lineWidth: 2))
                                    }
                                }
                                Text(p.name).font(.system(size: 12, weight: .bold)).foregroundColor(KikoColor.ink).lineLimit(1)
                                HStack(spacing: 3) {
                                    Circle().fill(KikoColor.sky(hour: KikoTime.hour(entry.date, timeZone: p.timeZone))).frame(width: 7, height: 7)
                                    Text(KikoTime.clock(entry.date, timeZone: p.timeZone))
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundColor(KikoColor.mid)
                                }
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .kikoBackground(KikoColor.paper)
        }
    }
}

// MARK: - Prochain appel

struct NextCallWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "KikoNextCall", provider: KikoProvider()) { entry in
            NextCallView(entry: entry)
        }
        .configurationDisplayName("Prochain appel")
        .description("L'appel prévu, avec l'heure chez toi et chez l'autre.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct NextCallView: View {
    @Environment(\.widgetFamily) var family
    let entry: KikoEntry

    var body: some View {
        let call = entry.snapshot?.nextCall.flatMap { $0.end > entry.date ? $0 : nil }
        VStack(alignment: .leading, spacing: 4) {
            Text("PROCHAIN APPEL").font(.system(size: 11, weight: .heavy)).foregroundColor(KikoColor.okInk)
            if let call {
                Text(call.start > entry.date ? "Appel avec \(call.partnerName)" : "Appel avec \(call.partnerName) · maintenant")
                    .font(.system(size: 16, weight: .heavy, design: .rounded))
                    .foregroundColor(KikoColor.ink)
                if call.start > entry.date {
                    Text(call.start, style: .relative)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(KikoColor.okInk)
                }
                Text("\(KikoTime.clock(call.start, timeZone: TimeZone.current.identifier)) chez toi · \(KikoTime.clock(call.start, timeZone: call.partnerTimeZone)) à \(KikoTime.city(call.partnerTimeZone))")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(KikoColor.mid)
                    .lineLimit(family == .systemSmall ? 3 : 1)
            } else {
                Text("Aucun appel prévu").font(.system(size: 15, weight: .heavy)).foregroundColor(KikoColor.ink)
                Text("Propose un appel dans une discussion.").font(.system(size: 12)).foregroundColor(KikoColor.mid)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .widgetURL(KikoLink.chat(call?.conversationId))
        .kikoBackground(KikoColor.okLight)
    }
}
