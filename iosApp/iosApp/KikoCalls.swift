import AVFoundation
import CallKit
import Foundation
import PushKit
import ComposeApp
import LiveKit

/// Les appels audio côté iPhone :
/// - PushKit reçoit le push VoIP « call_ring », même app fermée, et réveille l'app ;
/// - CallKit fait sonner le téléphone comme un vrai appel (écran verrouillé, casque, voiture) ;
/// - LiveKit transporte le son.
///
/// Le déroulé de l'appel (sonnerie, décroché, raccroché) est dans le code Kotlin partagé
/// (composeApp/.../calls/Calls.kt) : ce fichier n'en est que les mains.
///
/// Apple impose que chaque push VoIP montre un appel CallKit, tout de suite, dans le rappel de
/// PushKit. C'est donc ici, et pas dans Kotlin, qu'on signale l'appel entrant.
final class KikoCalls: NSObject, CallSystem {
    static let shared = KikoCalls()

    private let provider: CXProvider
    private let controller = CXCallController()
    private var registry: PKPushRegistry?
    private let media = KikoCallMedia()
    /// Appels que CallKit connaît déjà : les signaler deux fois ferait sonner deux fois.
    private var known = Set<UUID>()

    override init() {
        let configuration = CXProviderConfiguration()
        configuration.supportsVideo = false
        configuration.maximumCallGroups = 1
        configuration.maximumCallsPerCallGroup = 1
        configuration.supportedHandleTypes = [.generic]
        // Rien dans l'historique « Récents » du téléphone (ni dans sa copie iCloud).
        configuration.includesCallsInRecents = false
        provider = CXProvider(configuration: configuration)
        super.init()
        provider.setDelegate(self, queue: nil)
    }

    func start() {
        // CallKit gère la session audio : LiveKit attend qu'elle soit active pour ouvrir le micro.
        AudioManager.shared.audioSession.isAutomaticConfigurationEnabled = false
        try? AudioManager.shared.setEngineAvailability(.none)
        Calls.shared.install(media: media, system: self)

        let registry = PKPushRegistry(queue: .main)
        registry.delegate = self
        registry.desiredPushTypes = [.voIP]
        self.registry = registry
    }

    // MARK: - CallSystem (appelé par Kotlin)

    func reportIncoming(callId: String, conversationId: String, callerName: String) {
        guard let uuid = UUID(uuidString: callId), !known.contains(uuid) else { return }
        reportNewIncoming(uuid, callerName: callerName) { _ in }
    }

    func reportOutgoing(callId: String, conversationId: String, partnerName: String) {
        guard let uuid = UUID(uuidString: callId), !known.contains(uuid) else { return }
        known.insert(uuid)
        let action = CXStartCallAction(call: uuid, handle: CXHandle(type: .generic, value: partnerName))
        action.isVideo = false
        controller.request(CXTransaction(action: action)) { error in
            if let error { print("Kiko : CallKit refuse l'appel sortant (\(error.localizedDescription))") }
        }
    }

    func reportConnected(callId: String) {
        guard let uuid = UUID(uuidString: callId), known.contains(uuid) else { return }
        provider.reportOutgoingCall(with: uuid, connectedAt: Date())
    }

    func reportEnded(callId: String, reason: CallEndReason) {
        guard let uuid = UUID(uuidString: callId), known.contains(uuid) else { return }
        known.remove(uuid)
        if reason == CallEndReason.local {
            // Raccroché dans l'app : on le demande à CallKit, qui ferme son écran et la session audio.
            controller.request(CXTransaction(action: CXEndCallAction(call: uuid))) { _ in }
            return
        }
        let ended: CXCallEndedReason
        switch reason {
        case CallEndReason.declined: ended = .declinedElsewhere
        case CallEndReason.missed: ended = .unanswered
        case CallEndReason.failed: ended = .failed
        default: ended = .remoteEnded
        }
        provider.reportCall(with: uuid, endedAt: Date(), reason: ended)
    }

    func reportMuted(callId: String, muted: Bool) {
        guard let uuid = UUID(uuidString: callId), known.contains(uuid) else { return }
        controller.request(CXTransaction(action: CXSetMutedCallAction(call: uuid, muted: muted))) { _ in }
    }

    // MARK: - Outils

    private func reportNewIncoming(_ uuid: UUID, callerName: String, completion: @escaping (Bool) -> Void) {
        known.insert(uuid)
        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .generic, value: callerName)
        update.localizedCallerName = callerName
        update.hasVideo = false
        update.supportsHolding = false
        update.supportsGrouping = false
        update.supportsUngrouping = false
        update.supportsDTMF = false
        provider.reportNewIncomingCall(with: uuid, update: update) { [weak self] error in
            if error != nil {
                // Ne pas déranger activé, ou appel déjà en cours : on n'insiste pas, l'appel sera manqué.
                self?.known.remove(uuid)
            }
            completion(error == nil)
        }
    }

    static func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playAndRecord, mode: .voiceChat, options: [.allowBluetooth, .allowBluetoothA2DP])
    }
}

// MARK: - PushKit

extension KikoCalls: PKPushRegistryDelegate {
    func pushRegistry(_ registry: PKPushRegistry, didUpdate pushCredentials: PKPushCredentials, for type: PKPushType) {
        let token = pushCredentials.token.map { String(format: "%02x", $0) }.joined()
        PushRegistry.shared.onToken(token: token, platform: "ios", kind: "voip", activity: nil, sandbox: KikoBridge.isSandbox)
    }

    func pushRegistry(_ registry: PKPushRegistry, didInvalidatePushTokenFor type: PKPushType) {}

    func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload,
                      for type: PKPushType, completion: @escaping () -> Void) {
        let info = payload.dictionaryPayload["kiko"] as? [String: Any] ?? [:]
        let kind = info["type"] as? String
        let callId = (info["call_id"] as? String ?? "").lowercased()
        let conversation = info["conversation_id"] as? String ?? ""
        let name = info["caller_name"] as? String ?? "Kiko"
        // Apple exige un appel CallKit pour chaque push VoIP, même illisible.
        let uuid = UUID(uuidString: callId) ?? UUID()

        if kind == "call_cancel" {
            if known.contains(uuid) {
                Calls.shared.onCancelled(callId: callId)
            } else {
                // Rien ne sonne (déjà fini) : on montre l'appel et on le ferme aussitôt.
                reportNewIncoming(uuid, callerName: name) { [weak self] _ in
                    self?.known.remove(uuid)
                    self?.provider.reportCall(with: uuid, endedAt: Date(), reason: .remoteEnded)
                    completion()
                }
                return
            }
            completion()
            return
        }

        guard kind == "call_ring", UUID(uuidString: callId) != nil else {
            reportNewIncoming(uuid, callerName: name) { [weak self] _ in
                self?.known.remove(uuid)
                self?.provider.reportCall(with: uuid, endedAt: Date(), reason: .failed)
                completion()
            }
            return
        }
        // Le même appel poussé deux fois (réseau capricieux) : il sonne déjà.
        guard !known.contains(uuid) else {
            completion()
            return
        }
        reportNewIncoming(uuid, callerName: name) { ok in
            if ok { Calls.shared.onIncoming(callId: callId, conversationId: conversation, callerName: name) }
            completion()
        }
    }
}

// MARK: - CallKit

extension KikoCalls: CXProviderDelegate {
    func providerDidReset(_ provider: CXProvider) {
        known.removeAll()
        Calls.shared.hangUp()
    }

    func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        Self.configureAudioSession()
        provider.reportOutgoingCall(with: action.callUUID, startedConnectingAt: Date())
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        Self.configureAudioSession()
        Calls.shared.answer(callId: action.callUUID.uuidString.lowercased())
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        known.remove(action.callUUID)
        Calls.shared.endFromSystem(callId: action.callUUID.uuidString.lowercased())
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        Calls.shared.setMuted(muted: action.isMuted)
        action.fulfill()
    }

    func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        try? AudioManager.shared.setEngineAvailability(.default)
    }

    func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
        try? AudioManager.shared.setEngineAvailability(.none)
    }
}

// MARK: - LiveKit

/// Le son : une salle LiveKit par appel, micro seulement.
final class KikoCallMedia: NSObject, CallMedia, RoomDelegate, @unchecked Sendable {
    private var room: Room?
    private var listener: CallMediaListener?

    func connect(url: String, token: String, listener: CallMediaListener) {
        disconnect()
        let room = Room(delegate: self)
        self.room = room
        self.listener = listener
        Task {
            do {
                try await room.connect(url: url, token: token)
                try await room.localParticipant.setMicrophone(enabled: true)
                let here = !room.remoteParticipants.isEmpty
                await MainActor.run { listener.onConnected(partnerHere: here) }
            } catch {
                await MainActor.run { listener.onFailed(message: error.localizedDescription) }
            }
        }
    }

    func setMuted(muted: Bool) {
        guard let room else { return }
        Task { _ = try? await room.localParticipant.setMicrophone(enabled: !muted) }
    }

    func setSpeaker(on: Bool) {
        try? AVAudioSession.sharedInstance().overrideOutputAudioPort(on ? .speaker : .none)
    }

    func disconnect() {
        let old = room
        room = nil
        listener = nil
        guard let old else { return }
        Task { await old.disconnect() }
    }

    private func tell(_ room: Room, _ block: @escaping (CallMediaListener) -> Void) {
        DispatchQueue.main.async { [weak self] in
            guard let self, self.room === room, let listener = self.listener else { return }
            block(listener)
        }
    }

    func room(_ room: Room, participantDidConnect participant: RemoteParticipant) {
        tell(room) { $0.onPartnerJoined() }
    }

    func room(_ room: Room, participantDidDisconnect participant: RemoteParticipant) {
        tell(room) { $0.onPartnerLeft() }
    }

    func room(_ room: Room, didUpdateConnectionState connectionState: ConnectionState, from oldConnectionState: ConnectionState) {
        if connectionState == .reconnecting {
            tell(room) { $0.onReconnecting() }
        } else if connectionState == .connected && oldConnectionState == .reconnecting {
            tell(room) { $0.onReconnected() }
        }
    }

    func room(_ room: Room, didDisconnectWithError error: LiveKitError?) {
        guard let error else { return }
        tell(room) { $0.onFailed(message: error.localizedDescription) }
    }
}
