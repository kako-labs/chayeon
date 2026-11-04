package dev.chayeon

import dev.chayeon.core.model.*
import dev.chayeon.data.social.EmptyConnectionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HomeProtectedConnectionTest {
    @Test fun suppressionBlocksRequestsAndAcceptanceBeforeAnyPresenceAckButPermitsDeclineAndOtherCommands() = runTest {
        val home = TestHomeZone()
        val calls = mutableListOf<String>()
        val raw = object : ConnectionRepository by EmptyConnectionRepository() {
            override suspend fun requestConnection(request: RequestConnectionRequest): AppResult<ConnectionRequest> {
                calls += "request"; return AppResult.Failure(AppError.Unknown)
            }
            override suspend fun respond(request: RespondConnectionRequest): AppResult<ConnectionRequest> {
                calls += request.decision.name; return AppResult.Failure(AppError.Unknown)
            }
            override suspend fun cancel(request: CancelConnectionRequest): AppResult<ConnectionRequest> {
                calls += "cancel"; return AppResult.Failure(AppError.Unknown)
            }
            override suspend fun leave(request: LeaveConnectionRequest): AppResult<Connection> {
                calls += "leave"; return AppResult.Failure(AppError.Unknown)
            }
            override suspend fun sendGreeting(request: SendGreetingRequest): AppResult<ConnectionGreeting> {
                calls += "greeting"; return AppResult.Failure(AppError.Unknown)
            }
        }
        val guarded = HomeProtectedConnectionRepository(raw, home)
        for (status in listOf(HomeZoneStatus.INSIDE, HomeZoneStatus.LOCATION_UNKNOWN, HomeZoneStatus.PERMISSION_OFF)) {
            home.state.value = HomeZoneState(status, HomeZoneRadius.MEDIUM, LocationSource.DEVICE)
            assertEquals(AppResult.Failure(AppError.PeerUnavailable), guarded.requestConnection(RequestConnectionRequest(CommandMeta("request"), "peer")))
            assertEquals(AppResult.Failure(AppError.PeerUnavailable), guarded.respond(RespondConnectionRequest(CommandMeta("accept"), "request", RequestDecision.ACCEPT)))
        }
        assertTrue(calls.isEmpty())
        guarded.respond(RespondConnectionRequest(CommandMeta("decline"), "request", RequestDecision.DECLINE))
        guarded.cancel(CancelConnectionRequest(CommandMeta("cancel"), "request"))
        guarded.leave(LeaveConnectionRequest(CommandMeta("leave"), "connection"))
        guarded.sendGreeting(SendGreetingRequest(CommandMeta("greeting"), "connection", "message", PresetCode.HELLO))
        assertEquals(listOf("DECLINE", "cancel", "leave", "greeting"), calls)
        home.state.value = home.state.value.copy(status = HomeZoneStatus.OUTSIDE)
        guarded.requestConnection(RequestConnectionRequest(CommandMeta("outside"), "peer"))
        guarded.respond(RespondConnectionRequest(CommandMeta("outside-accept"), "request", RequestDecision.ACCEPT))
        assertEquals(listOf("request", "ACCEPT"), calls.takeLast(2))
    }
}
