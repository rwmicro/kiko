import Intents
import UIKit
import UserNotifications
import WidgetKit

/// Enrichit les notifications avant leur affichage (maquette 22) : un message qui vient de
/// quelqu'un devient une « notification de communication », avec son avatar à la place de
/// l'icône de l'app. Met aussi à jour le widget de la discussion.
final class NotificationService: UNNotificationServiceExtension {
    private var contentHandler: ((UNNotificationContent) -> Void)?
    private var bestAttempt: UNMutableNotificationContent?

    override func didReceive(_ request: UNNotificationRequest, withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void) {
        self.contentHandler = contentHandler
        guard let content = request.content.mutableCopy() as? UNMutableNotificationContent else {
            contentHandler(request.content)
            return
        }
        bestAttempt = content
        let info = content.userInfo["kiko"] as? [String: Any] ?? [:]
        let type = info["type"] as? String ?? ""
        updateWidget(info: info, type: type)

        let fromPerson: Set<String> = ["message", "correction", "request", "call", "call_accepted"]
        guard fromPerson.contains(type), let name = info["sender_name"] as? String else {
            contentHandler(content)
            return
        }
        let senderId = info["sender_id"] as? String ?? name
        let image = INImage(imageData: Self.avatar(name))
        let sender = INPerson(
            personHandle: INPersonHandle(value: senderId, type: .unknown),
            nameComponents: nil,
            displayName: name,
            image: image,
            contactIdentifier: nil,
            customIdentifier: senderId
        )
        let intent = INSendMessageIntent(
            recipients: nil,
            outgoingMessageType: .outgoingMessageText,
            content: content.body,
            speakableGroupName: nil,
            conversationIdentifier: info["conversation_id"] as? String,
            serviceName: nil,
            sender: sender,
            attachments: nil
        )
        intent.setImage(image, forParameterNamed: \.sender)
        let interaction = INInteraction(intent: intent, response: nil)
        interaction.direction = .incoming
        interaction.donate(completion: nil)
        do {
            let updated = try content.updating(from: intent)
            contentHandler(updated)
        } catch {
            contentHandler(content)
        }
    }

    override func serviceExtensionTimeWillExpire() {
        if let contentHandler, let bestAttempt {
            contentHandler(bestAttempt)
        }
    }

    /// Le dernier message reçu s'affiche dans le widget, même app fermée.
    private func updateWidget(info: [String: Any], type: String) {
        guard ["message", "correction", "call", "call_accepted"].contains(type),
              let conv = info["conversation_id"] as? String,
              var snapshot = KikoSnapshot.load() else { return }
        if let i = snapshot.partners.firstIndex(where: { $0.conversationId == conv }) {
            var partner = snapshot.partners.remove(at: i)
            partner.unread += 1
            partner.lastMessage = info["preview"] as? String ?? partner.lastMessage
            partner.lastMessageMine = false
            snapshot.partners.insert(partner, at: 0)
        }
        if snapshot.streak?.conversationId == conv {
            snapshot.streak?.waitingForMe = true
        }
        snapshot.save()
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// L'avatar sans photo de l'app : l'initiale sur la teinte du prénom.
    static func avatar(_ name: String) -> Data {
        let size = CGSize(width: 120, height: 120)
        let tints: [UIColor] = [
            UIColor(red: 0.961, green: 0.608, blue: 0.294, alpha: 1), UIColor(red: 0.055, green: 0.584, blue: 0.580, alpha: 1),
            UIColor(red: 0.867, green: 0.247, blue: 0.102, alpha: 1), UIColor(red: 0.788, green: 0.541, blue: 0.071, alpha: 1),
            UIColor(red: 0.549, green: 0.353, blue: 0.620, alpha: 1), UIColor(red: 0.247, green: 0.486, blue: 0.675, alpha: 1),
        ]
        var h: Int32 = 0
        for unit in name.utf16 { h = h &* 31 &+ Int32(unit) }
        let tint = tints[Int(h == Int32.min ? 0 : abs(h)) % tints.count]
        let image = UIGraphicsImageRenderer(size: size).image { _ in
            tint.setFill()
            UIBezierPath(ovalIn: CGRect(origin: .zero, size: size)).fill()
            let letter = String(name.prefix(1)).uppercased() as NSString
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 52, weight: .heavy),
                .foregroundColor: UIColor.white,
            ]
            let textSize = letter.size(withAttributes: attributes)
            letter.draw(at: CGPoint(x: (size.width - textSize.width) / 2, y: (size.height - textSize.height) / 2), withAttributes: attributes)
        }
        return image.pngData() ?? Data()
    }
}
