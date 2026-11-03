package dev.chayeon

import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.CommandMeta
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.SessionMode
import dev.chayeon.core.model.SetDiscoveryRequest
import dev.chayeon.core.model.DiscoveryMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AppGraphTest {
    @Test
    fun blankServerConfigurationOnlyExposesSoloMode() = runTest {
        val graph = AppGraph.foundation()
        assertFalse(graph.serverModeAvailable)
        assertEquals(setOf(SessionMode.SOLO), graph.sessionRepository.availableModes.first())
        assertEquals(DrivingPolicy.UNKNOWN, graph.drivingStateProvider.drivingState.first().policy)
        assertNull(graph.serverTimeProvider.serverTime.first())
        assertFalse(graph.vehicleConsole.consoleState.first().available)
    }

    @Test
    fun configuredServerModeDoesNotFabricateAuthorization() = runTest {
        val graph = AppGraph.foundation(serverUrl = "https://example.invalid")
        assertEquals(
            setOf(SessionMode.SOLO, SessionMode.SERVER_DEMO),
            graph.historyDeps.sessionRepository.availableModes.first(),
        )
        assertEquals(
            AppResult.Failure(AppError.NotAuthorized),
            graph.discoveryRepository.setDiscovery(SetDiscoveryRequest(CommandMeta("test-command"), DiscoveryMode.ON)),
        )
    }
}
