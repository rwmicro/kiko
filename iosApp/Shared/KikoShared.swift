import Foundation
import SwiftUI
#if canImport(ActivityKit)
import ActivityKit
#endif

/// Partagé entre l'app, les widgets et l'extension des notifications (groupe d'apps).
enum KikoGroup {
    static let id = "group.app.kiko"
    static let snapshotKey = "snapshot"
    static var defaults: UserDefaults? { UserDefaults(suiteName: id) }
}

/// Le JSON écrit par l'app Kotlin (composeApp/.../notifications/WidgetSnapshot.kt).
/// Les heures sont en millisecondes depuis 1970.
struct KikoSnapshot: Codable {
    var updatedAt: Double
    var streak: Streak?
    var partners: [Partner]
    var nextCall: Call?

    struct Streak: Codable {
        var conversationId: String
        var partnerName: String
        var days: Int
        var waitingForMe: Bool
    }

    struct Partner: Codable {
        var conversationId: String
        var name: String
        var timeZone: String
        var lastSeen: Double?
        var unread: Int
        var lastMessage: String?
        var lastMessageMine: Bool
        var streak: Int

        func online(at date: Date) -> Bool {
            guard let seen = lastSeen else { return false }
            return date.timeIntervalSince1970 * 1000 - seen < 5 * 60 * 1000
        }
    }

    struct Call: Codable {
        var callId: String
        var conversationId: String
        var partnerName: String
        var partnerTimeZone: String
        var startsAt: Double
        var minutes: Int

        var start: Date { Date(timeIntervalSince1970: startsAt / 1000) }
        var end: Date { start.addingTimeInterval(Double(minutes) * 60) }
    }

    static func decode(_ json: String?) -> KikoSnapshot? {
        guard let data = json?.data(using: .utf8), !data.isEmpty else { return nil }
        return try? JSONDecoder().decode(KikoSnapshot.self, from: data)
    }

    static func load() -> KikoSnapshot? {
        decode(KikoGroup.defaults?.string(forKey: KikoGroup.snapshotKey))
    }

    func save() {
        guard let data = try? JSONEncoder().encode(self), let json = String(data: data, encoding: .utf8) else { return }
        KikoGroup.defaults?.set(json, forKey: KikoGroup.snapshotKey)
    }
}

/// Couleurs du design system (composeApp/.../ui/theme/KikoColors.kt, palette claire).
enum KikoColor {
    static let paper = Color(red: 1.0, green: 0.976, blue: 0.925)
    static let ink = Color(red: 0.165, green: 0.122, blue: 0.102)
    static let mid = Color(red: 0.431, green: 0.384, blue: 0.349)
    static let primary = Color(red: 0.867, green: 0.247, blue: 0.102)
    static let primaryLight = Color(red: 1.0, green: 0.894, blue: 0.839)
    static let sun = Color(red: 0.965, green: 0.725, blue: 0.231)
    static let sunLight = Color(red: 1.0, green: 0.937, blue: 0.761)
    static let sunInk = Color(red: 0.478, green: 0.353, blue: 0.0)
    static let ok = Color(red: 0.055, green: 0.584, blue: 0.580)
    static let okLight = Color(red: 0.851, green: 0.945, blue: 0.937)
    static let okInk = Color(red: 0.039, green: 0.369, blue: 0.365)
    static let tints: [Color] = [
        Color(red: 0.961, green: 0.608, blue: 0.294), Color(red: 0.055, green: 0.584, blue: 0.580),
        Color(red: 0.867, green: 0.247, blue: 0.102), Color(red: 0.788, green: 0.541, blue: 0.071),
        Color(red: 0.549, green: 0.353, blue: 0.620), Color(red: 0.247, green: 0.486, blue: 0.675),
    ]

    /// La même teinte que l'avatar de l'app : le hashCode de Kotlin (String.hashCode de Java), puis abs % 6.
    static func tint(for name: String) -> Color {
        var h: Int32 = 0
        for unit in name.utf16 { h = h &* 31 &+ Int32(unit) }
        let index = Int(h == Int32.min ? 0 : abs(h)) % tints.count
        return tints[index]
    }

    /// Le ciel chez l'autre (ChatSky.kt) : aube 5-8 h, jour 8-18 h, crépuscule 18-21 h, nuit.
    static func sky(hour: Int) -> Color {
        switch hour {
        case 5...7: return Color(red: 0.961, green: 0.604, blue: 0.420)
        case 8...17: return Color(red: 0.427, green: 0.741, blue: 0.933)
        case 18...20: return Color(red: 0.886, green: 0.408, blue: 0.247)
        default: return Color(red: 0.110, green: 0.125, blue: 0.322)
        }
    }
}

enum KikoTime {
    static func clock(_ date: Date, timeZone: String) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "fr_FR")
        f.timeZone = TimeZone(identifier: timeZone) ?? .current
        f.dateFormat = "HH:mm"
        return f.string(from: date)
    }

    static func hour(_ date: Date, timeZone: String) -> Int {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: timeZone) ?? .current
        return cal.component(.hour, from: date)
    }

    static func city(_ timeZone: String) -> String {
        timeZone == "UTC" ? "UTC" : (timeZone.split(separator: "/").last.map(String.init) ?? timeZone).replacingOccurrences(of: "_", with: " ")
    }
}

/// Avatar sans photo : l'initiale sur la teinte du prénom.
struct KikoAvatar: View {
    var name: String
    var size: CGFloat

    var body: some View {
        Circle()
            .fill(KikoColor.tint(for: name))
            .frame(width: size, height: size)
            .overlay(
                Text(String(name.prefix(1)).uppercased())
                    .font(.system(size: size * 0.42, weight: .heavy, design: .rounded))
                    .foregroundColor(.white)
            )
    }
}

#if canImport(ActivityKit)
/// Appel prévu qui approche (maquettes 25, 32, 33, 35). Les noms suivent supabase/functions/_shared/payloads.ts.
@available(iOS 16.1, *)
struct CallAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var startsAtEpoch: Double
        var minutes: Int
        var joined: Bool

        var start: Date { Date(timeIntervalSince1970: startsAtEpoch) }
        var end: Date { start.addingTimeInterval(Double(minutes) * 60) }
    }

    var callId: String
    var conversationId: String
    var partnerName: String
    var partnerCity: String
    var partnerTimeZone: String
}

/// Série qui va se perdre ce soir (maquettes 34 et 36).
@available(iOS 16.1, *)
struct StreakAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var deadlineEpoch: Double
        var streak: Int

        var deadline: Date { Date(timeIntervalSince1970: deadlineEpoch) }
    }

    var conversationId: String
    var partnerName: String
}
#endif

enum KikoLink {
    static func chat(_ conversationId: String?) -> URL {
        URL(string: "kiko://chat/\(conversationId ?? "home")")!
    }
}
