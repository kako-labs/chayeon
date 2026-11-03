package dev.chayeon.core.engine

import dev.chayeon.core.model.*
import kotlin.math.*

internal class Rejected(val error: AppError) : RuntimeException(null, null, false, false)
internal fun reject(error: AppError = AppError.PeerUnavailable): Nothing = throw Rejected(error)
internal fun ensure(condition: Boolean, error: AppError = AppError.Restricted) { if (!condition) reject(error) }

/** A transaction owns this working copy. Location values never enter the serializable state. */
internal class Room(
    var state: EngineState,
    val locations: MutableMap<String, LocationInput>,
    val rules: SignatureRules,
    val policy: EnginePolicy,
    val ids: EngineIdGenerator,
    val roomId: String,
    val demoEnabled: Boolean,
    var now: Long,
) {
    var expectedRevision: Long? = null
    val v get() = state.revision
    fun stamp(): Long {
        state = state.copy(revision = state.revision + 1)
        return v
    }
    fun account(actor: String): EngineAccount = state.accounts[actor]?.takeIf { it.active && !it.deleted }
        ?: reject(AppError.NotAuthorized)
    fun put(account: EngineAccount) { state = state.copy(accounts = state.accounts + (account.id to account)) }
    fun checkVersion(meta: CommandMeta, version: Long) {
        ensure(meta.expectedVersion == null || meta.expectedVersion == (expectedRevision ?: version), AppError.AlreadyClosed)
    }
    fun blocked(a: String, b: String) = state.blocks.values.any {
        (it.owner == a && it.peer == b) || (it.owner == b && it.peer == a)
    }
    fun consent(account: EngineAccount, scope: ConsentScope) = account.settings.consents.any {
        it.scope == scope && it.status == ConsentStatus.GRANTED && it.documentVersion == policy.consentDocumentVersion
    }
    fun ready(account: EngineAccount) = account.active && !account.deleted &&
        account.profile.registration == RegistrationState.READY && ConsentScope.entries.all { consent(account, it) }
    fun available(actor: String) = state.accounts[actor]?.let { ready(it) && it.discovery == DiscoveryMode.ON } == true
    fun activeConnection(actor: String) = state.connections.values.firstOrNull {
        actor in listOf(it.first, it.second) && it.status != ConnectionStatus.ENDED
    }
    fun peer(first: String, second: String, actor: String): String = when (actor) {
        first -> second
        second -> first
        else -> reject()
    }
    fun visiblePeer(actor: String, profileId: String): String {
        val target = state.accounts.values.firstOrNull { it.profile.id == profileId && it.id != actor } ?: reject()
        val known = discovery(actor).candidates.any { it.profile.id == profileId } ||
            state.requests.values.any { setOf(it.sender, it.recipient) == setOf(actor, target.id) } ||
            state.connections.values.any { setOf(it.first, it.second) == setOf(actor, target.id) }
        ensure(known, AppError.PeerUnavailable)
        return target.id
    }
    fun publicProfile(owner: String, viewer: String): PublicProfile {
        val a = state.accounts.getValue(owner)
        val public = a.profile.publicProfile
        if (a.deleted || (owner != viewer && (!consent(a, ConsentScope.PUBLIC_PROFILE) ||
                state.blocks.values.any { it.owner == owner && it.peer == viewer }))) {
            return public.copy(version = v, nickname = "가상 프로필", avatar = AvatarId.DAWN,
                interests = emptySet(), signature = null)
        }
        return public.copy(version = v, signature = public.signature.takeIf {
            consent(a, ConsentScope.VEHICLE_DATA) && (owner == viewer || !blocked(owner, viewer))
        })
    }
    fun chemistry(a: String, b: String): Chemistry {
        val first = publicProfile(a, b).signature ?: return Chemistry()
        val second = publicProfile(b, a).signature ?: return Chemistry()
        return rules.chemistry(first, second)
    }
    fun presets(actor: String, other: String): Set<PresetCode> = BASE_PRESETS +
        publicProfile(other, actor).signature?.traits.orEmpty().map { trait ->
            PresetCode.valueOf(if (trait.trait in setOf(TraitId.LONG_ROAD, TraitId.NIGHT_DRIVE))
                "SHARE_${trait.trait}" else "ADMIRE_${trait.trait}")
        }

    fun freshness(actor: String): LocationAvailability {
        val a = state.accounts.getValue(actor)
        if (!a.active || a.deleted) return LocationAvailability.UNAVAILABLE
        if (a.locationSource == LocationSource.SYNTHETIC) return when (a.syntheticPresence) {
            SyntheticPresence.NEARBY, SyntheticPresence.AWAY -> LocationAvailability.FRESH
            SyntheticPresence.STALE -> LocationAvailability.STALE
            SyntheticPresence.OFFLINE -> LocationAvailability.UNAVAILABLE
        }
        val location = locations[actor] ?: return LocationAvailability.STALE
        return if (location.observedAtEpochMillis <= now && now - location.observedAtEpochMillis <= policy.locationFreshnessMillis)
            LocationAvailability.FRESH else LocationAvailability.STALE
    }

    fun near(a: String, b: String, exiting: Boolean = false): Boolean {
        if (freshness(a) != LocationAvailability.FRESH || freshness(b) != LocationAvailability.FRESH) return false
        val first = state.accounts.getValue(a)
        val second = state.accounts.getValue(b)
        if (first.locationSource == LocationSource.SYNTHETIC || second.locationSource == LocationSource.SYNTHETIC) {
            return (first.locationSource != LocationSource.SYNTHETIC || first.syntheticPresence == SyntheticPresence.NEARBY) &&
                (second.locationSource != LocationSource.SYNTHETIC || second.syntheticPresence == SyntheticPresence.NEARBY)
        }
        val x = locations.getValue(a)
        val y = locations.getValue(b)
        val lat = Math.toRadians(y.latitude - x.latitude)
        val lon = Math.toRadians(y.longitude - x.longitude)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(x.latitude)) * cos(Math.toRadians(y.latitude)) * sin(lon / 2).pow(2)
        val separation = 6_371_000 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
        return separation <= if (exiting) policy.exitRadiusMeters else policy.enterRadiusMeters
    }

    fun discovery(actor: String): DiscoveryState {
        val a = state.accounts.getValue(actor)
        val fresh = freshness(actor)
        val candidates = if (available(actor) && fresh == LocationAvailability.FRESH && activeConnection(actor) == null) {
            state.accounts.values.filter { it.id != actor && available(it.id) && !blocked(actor, it.id) &&
                activeConnection(it.id) == null && near(actor, it.id) }.map { other ->
                val latest = state.requests.values.filter { setOf(it.sender, it.recipient) == setOf(actor, other.id) }.maxByOrNull { it.createdAt }
                Candidate(other.profile.id, v, publicProfile(other.id, actor), chemistry(actor, other.id),
                    a.profile.publicProfile.interests intersect other.profile.publicProfile.interests,
                    latest?.retryAt?.takeIf { it > now })
            }
        } else emptyList()
        val status = when {
            a.discovery == DiscoveryMode.OFF -> DiscoveryStatus.OFF
            activeConnection(actor) != null -> DiscoveryStatus.ALREADY_CONNECTED
            fresh == LocationAvailability.PERMISSION_DENIED -> DiscoveryStatus.LOCATION_PERMISSION_OFF
            fresh != LocationAvailability.FRESH -> DiscoveryStatus.LOCATION_STALE
            candidates.isEmpty() -> DiscoveryStatus.EMPTY
            else -> DiscoveryStatus.AVAILABLE
        }
        return DiscoveryState("discovery-$actor", v, a.discovery, status, a.locationSource, fresh, candidates, a.discoveryRefreshedAt)
    }

    fun request(r: RequestRecord, actor: String): ConnectionRequest {
        val other = peer(r.sender, r.recipient, actor)
        return ConnectionRequest(r.id, v, publicProfile(other, actor), if (actor == r.sender) RequestDirection.OUTGOING else RequestDirection.INCOMING,
            r.status, r.createdAt, r.expiresAt, chemistry(actor, other), r.connectionId, r.retryAt)
    }
    fun connection(c: ConnectionRecord, actor: String): Connection {
        val other = peer(c.first, c.second, actor)
        return Connection(c.id, v, c.requestId, publicProfile(other, actor), c.status, c.startedAt, chemistry(actor, other),
            c.endedAt, if (actor == c.first) c.firstReason else c.secondReason,
            (if (actor == c.first) c.firstEncounter else c.secondEncounter)?.takeIf { it in state.encounters },
            if (c.status == ConnectionStatus.CONNECTED && !blocked(actor, other)) presets(actor, other) else emptySet())
    }
    fun afterEligibility(actor: String, c: ConnectionRecord): Pair<AfterEligibility, Long?> {
        val other = peer(c.first, c.second, actor)
        if (blocked(actor, other) || !ready(state.accounts.getValue(actor)) || !ready(state.accounts.getValue(other)))
            return AfterEligibility.UNAVAILABLE to null
        val pairConnections = state.afterConnections.values.filter { setOf(it.first, it.second) == setOf(actor, other) }
        if (pairConnections.any { it.status == AfterConnectionStatus.CONNECTED }) return AfterEligibility.CONNECTED to null
        if (pairConnections.any { it.connectionId == c.id }) return AfterEligibility.UNAVAILABLE to null
        val requests = state.afterRequests.values.filter { setOf(it.sender, it.recipient) == setOf(actor, other) }
        if (requests.any { it.status == AfterRequestStatus.PENDING }) return AfterEligibility.PENDING to null
        val retry = requests.mapNotNull { it.retryAt }.maxOrNull()
        if (retry != null && retry > now) return AfterEligibility.COOLDOWN to retry
        return AfterEligibility.AVAILABLE to null
    }
    fun encounter(e: EncounterRecord, actor: String): Encounter {
        ensure(e.owner == actor, AppError.PeerUnavailable)
        val c = state.connections.getValue(e.connectionId)
        val other = peer(c.first, c.second, actor)
        val after = state.afterRequests.values.filter { it.connectionId == c.id }.maxByOrNull { it.createdAt }
        val eligibility = afterEligibility(actor, c)
        return Encounter(e.id, v, c.id, publicProfile(other, actor), c.startedAt, c.endedAt!!,
            (if (actor == c.first) c.firstReason else c.secondReason)!!, chemistry(actor, other),
            publicProfile(actor, other).interests intersect publicProfile(other, actor).interests,
            eligibility.first, eligibility.second, after?.id, after?.afterConnectionId)
    }
    fun encounterId(c: ConnectionRecord, actor: String) = if (actor == c.first) c.firstEncounter!! else c.secondEncounter!!
    fun afterRequest(r: AfterRequestRecord, actor: String): AfterRequest {
        val other = peer(r.sender, r.recipient, actor)
        return AfterRequest(r.id, v, encounterId(state.connections.getValue(r.connectionId), actor), publicProfile(other, actor),
            if (actor == r.sender) RequestDirection.OUTGOING else RequestDirection.INCOMING, r.status, r.createdAt, r.expiresAt,
            chemistry(actor, other), r.afterConnectionId, r.retryAt)
    }
    fun afterConnection(c: AfterConnectionRecord, actor: String): AfterConnection {
        val other = peer(c.first, c.second, actor)
        return AfterConnection(c.id, v, c.requestId, encounterId(state.connections.getValue(c.connectionId), actor), publicProfile(other, actor),
            c.status, c.connectedAt, chemistry(actor, other), c.closedAt, if (actor == c.first) c.firstReason else c.secondReason,
            if (c.status == AfterConnectionStatus.CONNECTED && !blocked(actor, other)) presets(actor, other) else emptySet())
    }
    fun greeting(g: GreetingRecord, actor: String) = g.greeting.copy(author = if (g.author == actor) MessageAuthor.SELF else MessageAuthor.PEER)
    fun message(m: MessageRecord, actor: String) = m.message.copy(author = if (m.author == actor) MessageAuthor.SELF else MessageAuthor.PEER)
    fun block(b: BlockRecord, actor: String) = Block(b.id, v, publicProfile(b.peer, actor), b.createdAt)

    fun snapshot(actor: String): SyncSnapshot {
        val a = account(actor)
        val cs = state.connections.values.filter { actor == it.first || actor == it.second }
        val acs = state.afterConnections.values.filter { actor == it.first || actor == it.second }
        return SyncSnapshot(a.session.id, v, now, a.profile.copy(version = v, publicProfile = publicProfile(actor, actor)),
            a.settings.copy(version = v), discovery(actor),
            state.requests.values.filter { actor == it.sender || actor == it.recipient }.map { request(it, actor) },
            cs.map { connection(it, actor) },
            state.greetings.values.filter { g -> cs.any { it.id == g.greeting.connectionId } }.map { greeting(it, actor) },
            state.encounters.values.filter { it.owner == actor }.map { encounter(it, actor) },
            state.afterRequests.values.filter { actor == it.sender || actor == it.recipient }.map { afterRequest(it, actor) },
            acs.map { afterConnection(it, actor) },
            state.messages.values.filter { m -> acs.any { it.id == m.message.afterConnectionId } }.map { message(it, actor) },
            state.blocks.values.filter { it.owner == actor }.map { block(it, actor) })
    }

    companion object {
        val BASE_PRESETS = setOf(PresetCode.HELLO, PresetCode.NICE_TO_MEET_YOU, PresetCode.GOOD_EVENING,
            PresetCode.SHARED_INTERESTS, PresetCode.TALK_LATER, PresetCode.TAKE_CARE, PresetCode.NICE_TO_MEET_AGAIN)
    }
}
