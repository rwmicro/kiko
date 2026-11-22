@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package app.kiko

import app.kiko.calls.CallAccess
import app.kiko.calls.CallController
import app.kiko.calls.CallEndReason
import app.kiko.calls.CallMedia
import app.kiko.calls.CallMediaListener
import app.kiko.calls.CallPhase
import app.kiko.calls.CallService
import app.kiko.calls.CallStatus
import app.kiko.calls.CallSystem
import app.kiko.calls.StartedCall
import app.kiko.calls.elapsed
import app.kiko.calls.explain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private class FakeService : CallService {
    var startStatus = CallStatus.Ringing
    var polled = CallStatus.Ringing
    var answerStatus = CallStatus.Active
    val ended = mutableListOf<String>()
    var accessed = 0

    override suspend fun start(conversationId: String, partnerName: String) = StartedCall("call-1", startStatus)
    override suspend fun answer(callId: String) = answerStatus
    override suspend fun end(callId: String): CallStatus {
        ended += callId
        return CallStatus.Ended
    }
    override suspend fun status(callId: String) = polled
    override suspend fun access(callId: String): CallAccess {
        accessed++
        return CallAccess("wss://kiko.livekit.cloud", "jeton")
    }
}

private class FakeMedia : CallMedia {
    var listener: CallMediaListener? = null
    var disconnected = 0
    var micMuted = false
    override fun connect(url: String, token: String, listener: CallMediaListener) {
        this.listener = listener
    }
    override fun setMuted(muted: Boolean) {
        micMuted = muted
    }
    override fun setSpeaker(on: Boolean) = Unit
    override fun disconnect() {
        disconnected++
    }
}

private class FakeSystem : CallSystem {
    val events = mutableListOf<String>()
    override fun reportIncoming(callId: String, conversationId: String, callerName: String) {
        events += "incoming:$callId:$callerName"
    }
    override fun reportOutgoing(callId: String, conversationId: String, partnerName: String) {
        events += "outgoing:$callId"
    }
    override fun reportConnected(callId: String) {
        events += "connected:$callId"
    }
    override fun reportEnded(callId: String, reason: CallEndReason) {
        events += "ended:$callId:$reason"
    }
    override fun reportMuted(callId: String, muted: Boolean) {
        events += "muted:$muted"
    }
}

private class TestClock(private val scope: TestScope) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_795_000_000_000 + scope.testScheduler.currentTime)
}

class CallsTest {
    private fun TestScope.controller(service: FakeService, media: FakeMedia, system: FakeSystem) =
        CallController({ service }, backgroundScope, TestClock(this)).also {
            it.media = media
            it.system = system
        }

    @Test
    fun appelSortantDecrocheEtRaccroche() = runTest {
        val service = FakeService()
        val media = FakeMedia()
        val system = FakeSystem()
        val calls = controller(service, media, system)

        calls.call("conv-ayu", "Ayu")
        runCurrent()
        assertEquals(CallPhase.Calling, calls.state.value.phase)
        assertEquals("call-1", calls.state.value.callId)
        // L'appelant entre dans la salle tout de suite, mais reste « ça sonne » tant qu'Ayu n'y est pas.
        media.listener!!.onConnected(partnerHere = false)
        assertEquals(CallPhase.Calling, calls.state.value.phase)

        media.listener!!.onPartnerJoined()
        assertEquals(CallPhase.Active, calls.state.value.phase)
        advanceTimeBy(65.seconds)
        calls.toggleMute()
        assertTrue(media.micMuted)

        calls.hangUp()
        runCurrent()
        assertEquals(CallPhase.Ended, calls.state.value.phase)
        assertEquals("Appel terminé · 1:05", calls.state.value.message)
        assertEquals(listOf("call-1"), service.ended)
        assertEquals(1, media.disconnected)
        assertEquals(listOf("outgoing:call-1", "connected:call-1", "muted:true", "ended:call-1:Local"), system.events)
        advanceTimeBy(4.seconds)
        assertEquals(CallPhase.Idle, calls.state.value.phase)
    }

    @Test
    fun appelRefuse() = runTest {
        val service = FakeService()
        val media = FakeMedia()
        val system = FakeSystem()
        val calls = controller(service, media, system)

        calls.call("conv-ayu", "Ayu")
        runCurrent()
        service.polled = CallStatus.Declined
        advanceTimeBy(CallController.PollEvery + 1.seconds)
        assertEquals(CallPhase.Ended, calls.state.value.phase)
        assertEquals("Ayu ne peut pas répondre pour l'instant.", calls.state.value.message)
        assertEquals("ended:call-1:Declined", system.events.last())
        assertEquals(1, media.disconnected)
    }

    @Test
    fun appelSansReponseManqueApres45Secondes() = runTest {
        val service = FakeService()
        val calls = controller(service, FakeMedia(), FakeSystem())
        calls.call("conv-ayu", "Ayu")
        runCurrent()
        advanceTimeBy(CallController.RingTimeout + 1.seconds)
        assertEquals(CallPhase.Ended, calls.state.value.phase)
        assertEquals("Ayu n'a pas répondu.", calls.state.value.message)
        assertEquals(listOf("call-1"), service.ended)
    }

    @Test
    fun appelEntrantDecroche() = runTest {
        val service = FakeService()
        val media = FakeMedia()
        val system = FakeSystem()
        val calls = controller(service, media, system)

        calls.onIncoming("call-9", "conv-ayu", "Ayu")
        assertEquals(CallPhase.Incoming, calls.state.value.phase)
        assertEquals("Ayu", calls.state.value.partnerName)
        calls.answer("call-9")
        runCurrent()
        assertEquals(CallPhase.Connecting, calls.state.value.phase)
        assertEquals(1, service.accessed)
        media.listener!!.onConnected(partnerHere = true)
        assertEquals(CallPhase.Active, calls.state.value.phase)
        media.listener!!.onReconnecting()
        assertEquals(CallPhase.Reconnecting, calls.state.value.phase)
        media.listener!!.onReconnected()
        media.listener!!.onPartnerLeft()
        runCurrent()
        assertEquals(CallPhase.Ended, calls.state.value.phase)
        assertEquals(listOf("incoming:call-9:Ayu", "connected:call-9", "ended:call-9:Remote"), system.events)
    }

    @Test
    fun appelEntrantAnnuleOuDejaFini() = runTest {
        val service = FakeService()
        val system = FakeSystem()
        val calls = controller(service, FakeMedia(), system)

        calls.onIncoming("call-9", "conv-ayu", "Ayu")
        calls.onCancelled("call-9")
        assertEquals("Appel manqué de Ayu", calls.state.value.message)
        assertEquals("ended:call-9:Missed", system.events.last())

        advanceTimeBy(4.seconds)
        service.answerStatus = CallStatus.Cancelled
        calls.onIncoming("call-10", "conv-ayu", "Ayu")
        calls.answer("call-10")
        runCurrent()
        assertEquals(CallPhase.Ended, calls.state.value.phase)
        assertEquals(0, service.accessed)
    }

    @Test
    fun deuxiemeAppelPendantUnAppelEstRefuse() = runTest {
        val service = FakeService()
        val system = FakeSystem()
        val calls = controller(service, FakeMedia(), system)
        calls.onIncoming("call-9", "conv-ayu", "Ayu")
        calls.onIncoming("call-10", "conv-budi", "Budi")
        runCurrent()
        assertEquals("call-9", calls.state.value.callId)
        assertEquals(listOf("call-10"), service.ended)
        assertEquals("ended:call-10:Declined", system.events.last())
    }

    @Test
    fun raccrocheParLeSystemeSansLeLuiRedire() = runTest {
        val service = FakeService()
        val media = FakeMedia()
        val system = FakeSystem()
        val calls = controller(service, media, system)
        calls.onIncoming("call-9", "conv-ayu", "Ayu")
        calls.answer("call-9")
        runCurrent()
        media.listener!!.onConnected(partnerHere = true)
        calls.endFromSystem("call-9")
        runCurrent()
        assertEquals(CallPhase.Ended, calls.state.value.phase)
        assertTrue(system.events.none { it.startsWith("ended:") })
        assertEquals(listOf("call-9"), service.ended)
    }

    @Test
    fun sansMicroNiLiveKitOnNAppellePas() = runTest {
        val calls = CallController({ FakeService() }, backgroundScope, TestClock(this))
        calls.call("conv-ayu", "Ayu")
        assertEquals(CallPhase.Ended, calls.state.value.phase)
        assertNull(calls.state.value.callId)
    }

    @Test
    fun messagesEtDurees() {
        assertTrue(explain("P0001: partner_asleep", "Ayu").startsWith("Il fait nuit chez Ayu"))
        assertTrue(explain("too_many_calls", "Ayu").contains("souvent"))
        assertEquals("L'appel n'a pas pu se faire. Vérifie ta connexion.", explain("timeout", "Ayu"))
        assertEquals("0:07", elapsed(7))
        assertEquals("12:00", elapsed(720))
        assertEquals("1:02:03", elapsed(3723))
    }
}
