package dev.chayeon.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class ContractTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `public signature and full snapshot survive round trip without private evidence`() {
        val signature = PublicSignature(
            id = "signature-a", version = 2, title = "미리 멈추는 드라이버",
            traits = listOf(PublicTrait(TraitId.EARLY_BRAKE, TraitLevel.STEADY)),
            knotSeed = KnotSeed(42), source = DataSource.SYNTHETIC,
            evidenceBucket = EvidenceBucket.ESTABLISHED,
        )
        val profile = PublicProfile(
            id = "profile-a", version = 3, nickname = "가상 프로필", avatar = AvatarId.DAWN,
            interests = setOf(InterestId.JAZZ), kind = ProfileKind.VIRTUAL_PROFILE, signature = signature,
        )
        val snapshot = SyncSnapshot(
            id = "session-a", version = 9, serverTimeEpochMillis = 100,
            profile = MyProfile("profile-a", 3, profile, RegistrationState.READY),
            discovery = DiscoveryState(version = 8),
            tombstones = listOf(Tombstone("old-encounter", 9, EntityKind.ENCOUNTER)),
        )
        val encoded = json.encodeToString(SyncSnapshot.serializer(), snapshot)
        assertEquals(snapshot, json.decodeFromString(SyncSnapshot.serializer(), encoded))
        assertTrue(encoded.contains("SYNTHETIC"))
        assertFalse(encoded.contains("sampleCount"))
        assertFalse(encoded.contains("metrics"))
        assertFalse(encoded.contains("validTripCount"))
    }

    @Test
    fun `response graph has no location input raw signals or private metric types`() {
        responseSerializers().forEach { serializer ->
            val visited = mutableSetOf<String>()
            visit(serializer.descriptor, visited) { descriptor ->
                val name = descriptor.serialName.substringAfterLast('.')
                assertFalse("Private type in response: $name", name in privateTypeNames)
                repeat(descriptor.elementsCount) { index ->
                    val field = descriptor.getElementName(index)
                    assertFalse("Private field in response: $field", field in privateFieldNames)
                }
            }
        }
    }

    @Test
    fun `all command requests omit actor identity and asserted consent flags`() {
        requestSerializers().forEach { serializer ->
            val visited = mutableSetOf<String>()
            visit(serializer.descriptor, visited) { descriptor ->
                repeat(descriptor.elementsCount) { index ->
                    val field = descriptor.getElementName(index)
                    assertFalse("Untrusted actor or consent field: $field", field in setOf(
                        "actorId", "senderId", "accountId", "userId", "ownerId", "consent", "consented",
                        "accepted", "isAdult", "hasConsent", "drivingPolicy", "policy",
                    ))
                }
            }
        }
    }

    @Test
    fun `recording traits cannot enter a public signature`() {
        val serialized = """{"trait":"GENTLE_RIDE","level":"RECORDING"}"""
        assertTrue(runCatching { json.decodeFromString(PublicTrait.serializer(), serialized) }.isFailure)
    }

    @Test
    fun `unsupported traits cannot be modeled as numeric zero`() {
        val metric = PrivateMetric(MetricId.SIGNAL_USAGE_RATIO, 0.0, MetricUnit.RATIO)
        assertTrue(runCatching {
            DerivedTraitMetrics(TraitId.SIGNAL_MANNERS, TraitAvailability.UNSUPPORTED, listOf(metric), 0, 0)
        }.isFailure)
        assertEquals(emptyList<PrivateMetric>(), DerivedTraitMetrics(
            TraitId.SIGNAL_MANNERS, TraitAvailability.UNSUPPORTED, emptyList(), 0, 0,
        ).metrics)
    }

    @Test
    fun `night trait is private by default and no startup driving state is allowed`() {
        assertFalse(TraitId.NIGHT_DRIVE in VisibilitySettings().visibleTraits)
        assertFalse(VisibilitySettings().publishSignature)
        assertEquals(DrivingPolicy.UNKNOWN, DrivingState().policy)
        assertEquals(Gear.UNKNOWN, DrivingState().gear)
    }

    @Test
    fun `temporary greetings cannot carry free text but after messages can`() {
        assertEquals(listOf("meta", "connectionId", "clientMessageId", "code"),
            elementNames(SendGreetingRequest.serializer().descriptor))
        val message = SendAfterMessageRequest(CommandMeta("command-a"), "after-a", "message-a",
            AfterMessageContent.Text("반가워요"))
        assertEquals(message, json.decodeFromString(SendAfterMessageRequest.serializer(),
            json.encodeToString(SendAfterMessageRequest.serializer(), message)))
    }

    @Test
    fun `request payload diagnostics never expose private location`() {
        val location = LocationInput(37.123456, 127.123456, 10, LocationSource.DEVICE)
        val request = UpdateLocationRequest(CommandMeta("update-location"), location)
        assertEquals("LocationInput(redacted)", location.toString())
        assertEquals("UpdateLocationRequest(redacted)", request.toString())
        assertTrue(json.encodeToString(UpdateLocationRequest.serializer(), request).contains("latitude"))
    }

    @Test
    fun `all seven failures are stable serializable and do not reveal blocking`() {
        val errors = listOf(AppError.NotAuthorized, AppError.AlreadyClosed, AppError.Expired,
            AppError.PeerUnavailable, AppError.Restricted, AppError.Network, AppError.Unknown)
        assertEquals(7, errors.toSet().size)
        errors.forEach { error ->
            val response = LeaveConnectionResponse(AppResult.Failure(error))
            assertEquals(response, json.decodeFromString(LeaveConnectionResponse.serializer(),
                json.encodeToString(LeaveConnectionResponse.serializer(), response)))
        }
        assertFalse(AppError::class.java.declaredClasses.any { it.simpleName.contains("Block") })
    }

    @Test
    fun `eighteen approved card states retain their stable names`() {
        assertEquals(setOf(
            "DISCOVERY_LOADING", "LOCATION_PERMISSION_OFF", "LOCATION_STALE", "NO_CANDIDATES",
            "NETWORK_FAILURE", "DRIVING_RESTRICTED", "DRIVING_UNKNOWN", "ALREADY_CONNECTED",
            "PROFILE_SAVE_FAILED", "REQUEST_DECLINED", "REQUEST_EXPIRED", "REQUEST_CANCELLED",
            "PEER_UNAVAILABLE", "MESSAGE_SEND_FAILED", "CONNECTION_CHECKING", "AFTER_DECLINED",
            "AFTER_EXPIRED", "AFTER_WITHDRAWN",
        ), ExperienceState.entries.map { it.name }.toSet())
    }

    @Test
    fun `mutable wire entities always include id and version`() {
        val serializers = listOf(
            Persona.serializer(), Session.serializer(), ParticipationPolicy.serializer(),
            PublicProfile.serializer(), MyProfile.serializer(), ConsentRecord.serializer(),
            ParticipationSettings.serializer(), Candidate.serializer(), DiscoveryState.serializer(),
            PublicSignature.serializer(), VisibilitySettings.serializer(), ConnectionRequest.serializer(),
            Connection.serializer(), ConnectionGreeting.serializer(), Encounter.serializer(),
            AfterRequest.serializer(), AfterConnection.serializer(), AfterMessage.serializer(),
            Block.serializer(), Report.serializer(), Tombstone.serializer(), SyncSnapshot.serializer(),
        )
        serializers.forEach { serializer ->
            val fields = elementNames(serializer.descriptor)
            assertTrue("No id: ${serializer.descriptor.serialName}", "id" in fields)
            assertTrue("No version: ${serializer.descriptor.serialName}", "version" in fields)
        }
    }

    @Test
    fun `empty full snapshot can clear all social state`() {
        val snapshot = SyncSnapshot("session-a", 12, 10)
        assertEquals(null, snapshot.profile)
        assertTrue(snapshot.connections.isEmpty())
        assertTrue(snapshot.afterConnections.isEmpty())
        assertTrue(snapshot.discovery.candidates.isEmpty())
        assertTrue(snapshot.encounters.isEmpty())
        assertTrue(snapshot.greetings.isEmpty())
        assertTrue(snapshot.afterMessages.isEmpty())
    }

    private fun elementNames(descriptor: SerialDescriptor): List<String> =
        (0 until descriptor.elementsCount).map(descriptor::getElementName)

    private fun visit(descriptor: SerialDescriptor, visited: MutableSet<String>, block: (SerialDescriptor) -> Unit) {
        if (!visited.add(descriptor.serialName)) return
        block(descriptor)
        repeat(descriptor.elementsCount) { visit(descriptor.getElementDescriptor(it), visited, block) }
    }

    private fun requestSerializers(): List<KSerializer<*>> = listOf(
        EnterSessionRequest.serializer(), SignOutRequest.serializer(), DeleteAccountRequest.serializer(),
        SaveProfileRequest.serializer(), RecordConsentRequest.serializer(), UpdateSettingsRequest.serializer(),
        PublishSignatureRequest.serializer(), SetDiscoveryRequest.serializer(), RefreshDiscoveryRequest.serializer(),
        UpdateLocationRequest.serializer(), SetSyntheticPresenceRequest.serializer(), RequestConnectionRequest.serializer(),
        RespondConnectionRequest.serializer(), CancelConnectionRequest.serializer(), SendGreetingRequest.serializer(),
        LeaveConnectionRequest.serializer(), DeleteEncounterRequest.serializer(), DeleteAllEncountersRequest.serializer(),
        RequestAfterRequest.serializer(), RespondAfterRequest.serializer(), WithdrawAfterRequest.serializer(),
        SendAfterMessageRequest.serializer(), EndAfterConnectionRequest.serializer(), BlockPeerRequest.serializer(),
        UnblockPeerRequest.serializer(), ReportPeerRequest.serializer(),
    )

    private fun responseSerializers(): List<KSerializer<*>> = listOf(
        EnterSessionResponse.serializer(), SignOutResponse.serializer(), DeleteAccountResponse.serializer(),
        SaveProfileResponse.serializer(), RecordConsentResponse.serializer(), UpdateSettingsResponse.serializer(),
        PublishSignatureResponse.serializer(), SetDiscoveryResponse.serializer(), RefreshDiscoveryResponse.serializer(),
        UpdateLocationResponse.serializer(), SetSyntheticPresenceResponse.serializer(), RequestConnectionResponse.serializer(),
        RespondConnectionResponse.serializer(), CancelConnectionResponse.serializer(), SendGreetingResponse.serializer(),
        LeaveConnectionResponse.serializer(), DeleteEncounterResponse.serializer(), DeleteAllEncountersResponse.serializer(),
        RequestAfterResponse.serializer(), RespondAfterResponse.serializer(), WithdrawAfterResponse.serializer(),
        SendAfterMessageResponse.serializer(), EndAfterConnectionResponse.serializer(), BlockPeerResponse.serializer(),
        UnblockPeerResponse.serializer(), ReportPeerResponse.serializer(), SyncResponse.serializer(),
    )

    @Test
    fun `home zone types never enter any request or response graph`() {
        (requestSerializers() + responseSerializers()).forEach { serializer ->
            visit(serializer.descriptor, mutableSetOf()) { descriptor ->
                assertFalse(descriptor.serialName, descriptor.serialName.substringAfterLast('.') in setOf(
                    "HomeZoneRadius", "HomeZoneStatus", "HomeZoneState", "HomeZoneRepository"))
                repeat(descriptor.elementsCount) { index ->
                    assertFalse(descriptor.getElementName(index), descriptor.getElementName(index).lowercase().let {
                        "home" in it || "zone" in it || "radius" in it
                    })
                }
            }
        }
        listOf(HomeZoneRadius::class.java, HomeZoneStatus::class.java, HomeZoneState::class.java).forEach {
            assertFalse(it.declaredMethods.any { method -> method.name == "serializer" })
            assertFalse(it.declaredFields.any { field -> field.name in setOf("latitude", "longitude", "center") })
        }
    }

    private val privateTypeNames = setOf("LocationInput", "UpdateLocationRequest", "MySignature", "TraitEvidence",
        "PrivateMetric", "DerivedTripMetrics", "DerivedTraitMetrics", "TripRecap", "SignalKind")
    private val privateFieldNames = setOf("latitude", "longitude", "coordinates", "distance", "trajectory",
        "vin", "crn", "plate", "vehicleModel", "speed", "steering", "sampleCount", "metrics", "validTripCount")
}
