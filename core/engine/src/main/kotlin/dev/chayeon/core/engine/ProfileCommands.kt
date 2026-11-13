package dev.chayeon.core.engine

import dev.chayeon.core.model.*
import kotlinx.serialization.json.encodeToJsonElement

internal fun Room.enterSession(actor: String, r: EnterSessionRequest): Session {
    ensure(actor.isNotBlank() && actor.length <= 128)
    ensure(state.accounts[actor]?.deleted != true, AppError.NotAuthorized)
    val old = state.accounts[actor]?.takeUnless { it.deleted }
    stamp()
    val session = Session(ids.next(), v, actor, r.mode, r.persona, r.roomCode, null, null, policy.participation(v))
    val profile = old?.profile ?: run {
        val profileId = ids.next()
        MyProfile(profileId, v, PublicProfile(profileId, v,
            if (r.persona == PersonaCode.ACCOUNT_A) "새벽드라이브" else "노을산책",
            if (r.persona == PersonaCode.ACCOUNT_A) AvatarId.DAWN else AvatarId.SLOW,
            emptySet(), ProfileKind.VIRTUAL_PROFILE, null), RegistrationState.PROFILE_REQUIRED)
    }
    put(old?.copy(session = session, active = true,
        presenceLeaseDeadline = policy.presenceLeaseMillis?.takeIf {
            old.syntheticPresence == SyntheticPresence.NEARBY && old.profile.publicProfile.kind != ProfileKind.DEMO_PEER
        }?.let { now + it }) ?: EngineAccount(actor, session, profile,
        ParticipationSettings(ids.next(), v), presenceLeaseDeadline = policy.presenceLeaseMillis?.let { now + it }))
    return session
}

internal fun Room.saveProfile(actor: String, r: SaveProfileRequest): MyProfile {
    val a = account(actor)
    checkVersion(r.meta, v)
    val nick = r.draft.nickname
    ensure(nick == nick.trim() && nick.codePointCount(0, nick.length) in policy.nicknameMinLength..policy.nicknameMaxLength &&
        nick.none { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() })
    ensure(r.draft.interests.size <= policy.maximumInterests)
    ensure(r.draft.contacts.isValid())
    stamp()
    val profile = a.profile.copy(version = v, publicProfile = a.profile.publicProfile.copy(version = v,
        nickname = nick, avatar = r.draft.avatar, interests = r.draft.interests),
        contacts = r.draft.contacts.normalized(),
        registration = if (ConsentScope.entries.all { consent(a, it) }) RegistrationState.READY else RegistrationState.CONSENT_REQUIRED)
    put(a.copy(profile = profile))
    return profile
}

internal fun Room.recordConsent(actor: String, r: RecordConsentRequest): ParticipationSettings {
    val a = account(actor)
    ensure(r.documentVersion == policy.consentDocumentVersion)
    checkVersion(r.meta, v)
    stamp()
    val record = ConsentRecord("consent-$actor-${r.scope}", v, r.scope,
        if (r.action == ConsentAction.GRANT) ConsentStatus.GRANTED else ConsentStatus.REVOKED, r.documentVersion, now)
    val settings = a.settings.copy(version = v, consents = a.settings.consents.filterNot { it.scope == r.scope } + record)
    val changed = a.copy(settings = settings)
    val registration = if (a.profile.registration == RegistrationState.PROFILE_REQUIRED) RegistrationState.PROFILE_REQUIRED
        else if (ConsentScope.entries.all { consent(changed, it) }) RegistrationState.READY else RegistrationState.CONSENT_REQUIRED
    put(changed.copy(profile = a.profile.copy(version = v, registration = registration,
        publicProfile = a.profile.publicProfile.copy(version = v, signature = a.profile.publicProfile.signature.takeUnless {
            r.action == ConsentAction.REVOKE && r.scope in setOf(ConsentScope.VEHICLE_DATA, ConsentScope.PUBLIC_PROFILE)
        }))))
    if (r.action == ConsentAction.REVOKE) {
        put(state.accounts.getValue(actor).copy(discovery = DiscoveryMode.OFF, discoverySince = null))
        revoke(actor, null, if (r.scope == ConsentScope.PUBLIC_PROFILE) ConnectionEndReason.SELF_LEFT else ConnectionEndReason.DISCOVERY_OFF)
    }
    return settings
}

internal fun Room.updateSettings(actor: String, r: UpdateSettingsRequest): ParticipationSettings {
    val a = account(actor)
    ensure(r.autoConnectConditions.isValid())
    checkVersion(r.meta, v)
    stamp()
    val settings = a.settings.copy(version = v, notifyRequests = r.notifyRequests,
        receiveRequestsWhileDriving = r.drivingConnectMode != DrivingConnectMode.OFF,
        drivingConnectMode = r.drivingConnectMode, autoConnectConditions = r.autoConnectConditions)
    put(a.copy(settings = settings))
    return settings
}

internal fun Room.publishSignature(actor: String, r: PublishSignatureRequest): MyProfile {
    val a = account(actor)
    checkVersion(r.meta, v)
    val input = r.signature
    if (input != null) {
        ensure(ready(a))
        ensure(input.traits.size <= TraitId.entries.size && input.traits.map { it.trait }.distinct().size == input.traits.size)
        ensure(input.traits.none { it.level == TraitLevel.RECORDING })
        ensure(input.traits.all { trait ->
            if (!trait.trait.isScored) trait.score == null else {
                val score = trait.score
                score != null && score in 60..100 && trait.level == when {
                    score >= 90 -> TraitLevel.SIGNATURE
                    score >= 75 -> TraitLevel.STEADY
                    else -> TraitLevel.VISIBLE
                }
            }
        })
    }
    stamp()
    val signature = input?.copy(id = "signature-${a.profile.id}", version = v, title = rules.title(input.traits))
    val profile = a.profile.copy(version = v, publicProfile = a.profile.publicProfile.copy(version = v, signature = signature))
    put(a.copy(profile = profile))
    return profile
}

internal fun Room.setDiscovery(actor: String, r: SetDiscoveryRequest): DiscoveryState {
    val a = account(actor)
    checkVersion(r.meta, v)
    if (r.mode == DiscoveryMode.ON) ensure(ready(a))
    stamp()
    put(a.copy(discovery = r.mode, discoverySince = if (r.mode == DiscoveryMode.ON) a.discoverySince ?: now else null,
        discoveryRefreshedAt = now))
    if (r.mode == DiscoveryMode.OFF) revoke(actor, null, ConnectionEndReason.DISCOVERY_OFF, closeAfter = false)
    return discovery(actor)
}

internal fun Room.refreshDiscovery(actor: String, r: RefreshDiscoveryRequest): DiscoveryState {
    val a = account(actor)
    checkVersion(r.meta, v)
    stamp()
    put(a.copy(discoveryRefreshedAt = now))
    return discovery(actor)
}

internal fun Room.updateLocation(actor: String, r: UpdateLocationRequest): CommandReceipt {
    val a = account(actor)
    val p = r.location
    ensure(consent(a, ConsentScope.LOCATION_USE))
    ensure(p.latitude.isFinite() && p.longitude.isFinite() && p.latitude in -90.0..90.0 && p.longitude in -180.0..180.0)
    ensure(p.observedAtEpochMillis in 0..now && p.observedAtEpochMillis >= (a.locationObservedAt ?: 0))
    ensure(p.source == LocationSource.DEVICE)
    stamp()
    locations[actor] = p
    put(a.copy(locationSource = p.source, locationObservedAt = p.observedAtEpochMillis,
        presenceLeaseDeadline = null,
        locationAvailability = if (now - p.observedAtEpochMillis <= policy.locationFreshnessMillis) LocationAvailability.FRESH else LocationAvailability.STALE))
    evaluateConnections()
    return CommandReceipt(r.meta.commandId, v)
}

internal fun Room.setSyntheticPresence(actor: String, r: SetSyntheticPresenceRequest): CommandReceipt {
    val a = account(actor)
    ensure(consent(a, ConsentScope.LOCATION_USE))
    applySyntheticPresence(a, r.presence)
    return CommandReceipt(r.meta.commandId, v)
}

/** Explicit departures and lease expiry share the same mutation and connection evaluation. */
internal fun Room.applySyntheticPresence(account: EngineAccount, presence: SyntheticPresence) {
    val renewal = policy.presenceLeaseMillis != null && presence == SyntheticPresence.NEARBY &&
        account.locationSource == LocationSource.SYNTHETIC && account.syntheticPresence == presence
    if (!renewal) stamp()
    locations.remove(account.id)
    put(account.copy(locationSource = LocationSource.SYNTHETIC, syntheticPresence = presence, locationObservedAt = null,
        presenceLeaseDeadline = policy.presenceLeaseMillis?.takeIf {
            presence == SyntheticPresence.NEARBY && account.profile.publicProfile.kind != ProfileKind.DEMO_PEER
        }?.let { now + it }))
    evaluateConnections()
}

internal fun Room.signOut(actor: String, r: SignOutRequest): CommandReceipt {
    val a = account(actor)
    stamp()
    put(a.copy(active = false, discovery = DiscoveryMode.OFF, discoverySince = null, presenceLeaseDeadline = null))
    locations.remove(actor)
    revoke(actor, null, ConnectionEndReason.SELF_LEFT, closeAfter = false)
    return CommandReceipt(r.meta.commandId, v)
}

internal fun Room.deleteAccount(actor: String, r: DeleteAccountRequest): CommandReceipt {
    val a = account(actor)
    stamp()
    revoke(actor, null, ConnectionEndReason.SELF_LEFT, accountDeleted = true)
    put(a.copy(active = false, deleted = true, discovery = DiscoveryMode.OFF, discoverySince = null, presenceLeaseDeadline = null,
        settings = a.settings.copy(version = v, consents = emptyList()),
        profile = a.profile.copy(version = v, registration = RegistrationState.PROFILE_REQUIRED, contacts = ContactIds(),
            publicProfile = a.profile.publicProfile.copy(version = v, nickname = "가상 프로필", avatar = AvatarId.DAWN,
                interests = emptySet(), signature = null))))
    locations.remove(actor)
    state = state.copy(afterRequests = state.afterRequests.mapValues { (_, request) ->
        if (actor in setOf(request.sender, request.recipient)) request.copy(submittedContacts = null) else request
    }, encounters = state.encounters.filterValues { it.owner != actor },
        afterConnections = state.afterConnections.mapValues { (_, connection) ->
            if (actor in setOf(connection.first, connection.second)) connection.copy(version = v, firstContacts = null, secondContacts = null,
                firstReason = AfterCloseReason.ACCOUNT_DELETED, secondReason = AfterCloseReason.ACCOUNT_DELETED)
            else connection
        },
        // Only the deleted actor's cached results are dropped. Other people's retries must keep their own results;
        // anything that projected this account (contacts, profile) is rebuilt from current state on replay.
        blocks = state.blocks.filterValues { it.owner != actor }, commands = state.commands.filterNot { it.actor == actor })
    return CommandReceipt(r.meta.commandId, v)
}
