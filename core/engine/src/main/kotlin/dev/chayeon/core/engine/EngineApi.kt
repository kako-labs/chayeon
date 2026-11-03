package dev.chayeon.core.engine

import dev.chayeon.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Both hosts use the same strict DTO dispatcher. No request body is retained or logged. */
class EngineApi(private val engine: ChayeonEngine) {
    private val json = EngineJson

    fun execute(actor: String, route: String, body: String): String = try {
        when (route) {
            ApiRoutes.ENTER_SESSION -> json.encodeToString(EnterSessionResponse(engine.enterSession(actor, json.decodeFromString<EnterSessionRequest>(body))))
            ApiRoutes.SIGN_OUT -> json.encodeToString(SignOutResponse(engine.signOut(actor, json.decodeFromString<SignOutRequest>(body))))
            ApiRoutes.DELETE_ACCOUNT -> json.encodeToString(DeleteAccountResponse(engine.deleteAccount(actor, json.decodeFromString<DeleteAccountRequest>(body))))
            ApiRoutes.SAVE_PROFILE -> json.encodeToString(SaveProfileResponse(engine.saveProfile(actor, json.decodeFromString<SaveProfileRequest>(body))))
            ApiRoutes.RECORD_CONSENT -> json.encodeToString(RecordConsentResponse(engine.recordConsent(actor, json.decodeFromString<RecordConsentRequest>(body))))
            ApiRoutes.UPDATE_SETTINGS -> json.encodeToString(UpdateSettingsResponse(engine.updateSettings(actor, json.decodeFromString<UpdateSettingsRequest>(body))))
            ApiRoutes.PUBLISH_SIGNATURE -> json.encodeToString(PublishSignatureResponse(engine.publishSignature(actor, json.decodeFromString<PublishSignatureRequest>(body))))
            ApiRoutes.SET_DISCOVERY -> json.encodeToString(SetDiscoveryResponse(engine.setDiscovery(actor, json.decodeFromString<SetDiscoveryRequest>(body))))
            ApiRoutes.REFRESH_DISCOVERY -> json.encodeToString(RefreshDiscoveryResponse(engine.refreshDiscovery(actor, json.decodeFromString<RefreshDiscoveryRequest>(body))))
            ApiRoutes.UPDATE_LOCATION -> json.encodeToString(UpdateLocationResponse(engine.updateLocation(actor, json.decodeFromString<UpdateLocationRequest>(body))))
            ApiRoutes.SET_SYNTHETIC_PRESENCE -> json.encodeToString(SetSyntheticPresenceResponse(engine.setSyntheticPresence(actor, json.decodeFromString<SetSyntheticPresenceRequest>(body))))
            ApiRoutes.REQUEST_CONNECTION -> json.encodeToString(RequestConnectionResponse(engine.requestConnection(actor, json.decodeFromString<RequestConnectionRequest>(body))))
            ApiRoutes.RESPOND_CONNECTION -> json.encodeToString(RespondConnectionResponse(engine.respondConnection(actor, json.decodeFromString<RespondConnectionRequest>(body))))
            ApiRoutes.CANCEL_CONNECTION -> json.encodeToString(CancelConnectionResponse(engine.cancelConnection(actor, json.decodeFromString<CancelConnectionRequest>(body))))
            ApiRoutes.SEND_GREETING -> json.encodeToString(SendGreetingResponse(engine.sendGreeting(actor, json.decodeFromString<SendGreetingRequest>(body))))
            ApiRoutes.LEAVE_CONNECTION -> json.encodeToString(LeaveConnectionResponse(engine.leaveConnection(actor, json.decodeFromString<LeaveConnectionRequest>(body))))
            ApiRoutes.DELETE_ENCOUNTER -> json.encodeToString(DeleteEncounterResponse(engine.deleteEncounter(actor, json.decodeFromString<DeleteEncounterRequest>(body))))
            ApiRoutes.DELETE_ALL_ENCOUNTERS -> json.encodeToString(DeleteAllEncountersResponse(engine.deleteAllEncounters(actor, json.decodeFromString<DeleteAllEncountersRequest>(body))))
            ApiRoutes.REQUEST_AFTER -> json.encodeToString(RequestAfterResponse(engine.requestAfter(actor, json.decodeFromString<RequestAfterRequest>(body))))
            ApiRoutes.RESPOND_AFTER -> json.encodeToString(RespondAfterResponse(engine.respondAfter(actor, json.decodeFromString<RespondAfterRequest>(body))))
            ApiRoutes.WITHDRAW_AFTER -> json.encodeToString(WithdrawAfterResponse(engine.withdrawAfter(actor, json.decodeFromString<WithdrawAfterRequest>(body))))
            ApiRoutes.SEND_AFTER_MESSAGE -> json.encodeToString(SendAfterMessageResponse(engine.sendAfterMessage(actor, json.decodeFromString<SendAfterMessageRequest>(body))))
            ApiRoutes.END_AFTER_CONNECTION -> json.encodeToString(EndAfterConnectionResponse(engine.endAfterConnection(actor, json.decodeFromString<EndAfterConnectionRequest>(body))))
            ApiRoutes.BLOCK_PEER -> json.encodeToString(BlockPeerResponse(engine.blockPeer(actor, json.decodeFromString<BlockPeerRequest>(body))))
            ApiRoutes.UNBLOCK_PEER -> json.encodeToString(UnblockPeerResponse(engine.unblockPeer(actor, json.decodeFromString<UnblockPeerRequest>(body))))
            ApiRoutes.REPORT_PEER -> json.encodeToString(ReportPeerResponse(engine.reportPeer(actor, json.decodeFromString<ReportPeerRequest>(body))))
            else -> json.encodeToString(SyncResponse(AppResult.Failure(AppError.NotAuthorized)))
        }
    } catch (_: Exception) {
        json.encodeToString(SyncResponse(AppResult.Failure(AppError.Restricted)))
    }
}
