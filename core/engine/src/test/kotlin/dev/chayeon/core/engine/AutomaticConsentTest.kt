package dev.chayeon.core.engine

import dev.chayeon.core.model.*
import org.junit.Test
import kotlin.test.*

class AutomaticConsentTest {
    @Test fun automaticCommandsRequireStoredSettingsAndConditionsOnBothActors() {
        val f = Fixture(); f.join("a"); f.join("b"); f.signature("a", TraitId.SIGNAL_MANNERS); f.signature("b", TraitId.SIGNAL_MANNERS)
        fun request() = f.engine.requestConnection("a", RequestConnectionRequest(f.meta(), f.snap("b").profile!!.id, automatic = true))
        assertEquals(AppError.Restricted, request().error())
        f.engine.updateSettings("a", UpdateSettingsRequest(f.meta(), true, drivingConnectMode = DrivingConnectMode.AUTO,
            autoConnectConditions = AutoConnectConditions(mapOf(TraitId.SIGNAL_MANNERS to 95)))).ok()
        assertEquals(AppError.Restricted, request().error())
        f.engine.updateSettings("a", UpdateSettingsRequest(f.meta(), true, drivingConnectMode = DrivingConnectMode.AUTO,
            autoConnectConditions = AutoConnectConditions(mapOf(TraitId.SIGNAL_MANNERS to 90)))).ok()
        val pending = request().ok()
        assertTrue(pending.automatic)
        fun accept() = f.engine.respondConnection("b", RespondConnectionRequest(f.meta(), pending.id, RequestDecision.ACCEPT, automatic = true))
        assertEquals(AppError.Restricted, accept().error())
        f.engine.updateSettings("b", UpdateSettingsRequest(f.meta(), true, drivingConnectMode = DrivingConnectMode.RING)).ok()
        assertEquals(AppError.Restricted, accept().error())
        f.engine.updateSettings("b", UpdateSettingsRequest(f.meta(), true, drivingConnectMode = DrivingConnectMode.AUTO,
            autoConnectConditions = AutoConnectConditions(mapOf(TraitId.SIGNAL_MANNERS to 90), true))).ok()
        accept().ok()
        assertEquals(ConnectionStatus.CONNECTED, f.snap("a").connections.single().status)
        assertEquals(ConnectionStatus.CONNECTED, f.snap("b").connections.single().status)
    }

    @Test fun onePendingAutomaticRequestAndInvalidSettingsAreRejected() {
        val f = Fixture(); listOf("a", "b", "c").forEach(f::join)
        assertEquals(AppError.Restricted, f.engine.updateSettings("a", UpdateSettingsRequest(f.meta(), true,
            autoConnectConditions = AutoConnectConditions(mapOf(TraitId.LONG_ROAD to 60)))).error())
        f.engine.updateSettings("a", UpdateSettingsRequest(f.meta(), true, drivingConnectMode = DrivingConnectMode.AUTO)).ok()
        f.engine.requestConnection("a", RequestConnectionRequest(f.meta(), f.snap("b").profile!!.id, true)).ok()
        assertEquals(AppError.AlreadyClosed, f.engine.requestConnection("a", RequestConnectionRequest(f.meta(), f.snap("c").profile!!.id, true)).error())
    }
}
