package dev.chayeon.core.engine

import dev.chayeon.core.model.*

internal fun Room.end(c: ConnectionRecord, initiator: String?, reason: ConnectionEndReason) {
    if (c.status == ConnectionStatus.ENDED) return
    stamp()
    val firstEncounter = EncounterRecord(ids.next(), v, c.first, c.id)
    val secondEncounter = EncounterRecord(ids.next(), v, c.second, c.id)
    fun forActor(actor: String) = when (reason) {
        ConnectionEndReason.SELF_LEFT -> if (initiator == actor) reason else ConnectionEndReason.PEER_LEFT
        ConnectionEndReason.BLOCKED -> if (initiator == actor) reason else ConnectionEndReason.PEER_LEFT
        else -> reason
    }
    val ended = c.copy(version = v, status = ConnectionStatus.ENDED, endedAt = now,
        checkingSince = null, checkingReason = null, firstReason = forActor(c.first), secondReason = forActor(c.second),
        firstEncounter = firstEncounter.id, secondEncounter = secondEncounter.id)
    state = state.copy(connections = state.connections + (c.id to ended),
        encounters = state.encounters + (firstEncounter.id to firstEncounter) + (secondEncounter.id to secondEncounter))
}

internal fun Room.closeAfter(c: AfterConnectionRecord, initiator: String, reason: AfterCloseReason) {
    if (c.status == AfterConnectionStatus.CLOSED) return
    stamp()
    fun forActor(actor: String) = if (reason == AfterCloseReason.ACCOUNT_DELETED) reason
        else if (actor == initiator) reason else AfterCloseReason.PEER_LEFT
    state = state.copy(afterConnections = state.afterConnections + (c.id to c.copy(version = v,
        status = AfterConnectionStatus.CLOSED, closedAt = now, firstReason = forActor(c.first), secondReason = forActor(c.second))))
}

internal fun Room.revoke(actor: String, other: String?, reason: ConnectionEndReason, closeAfter: Boolean = true, accountDeleted: Boolean = false) {
    fun involves(a: String, b: String) = actor in setOf(a, b) && (other == null || other in setOf(a, b))
    state.requests.values.filter { it.status == ConnectionRequestStatus.PENDING && involves(it.sender, it.recipient) }.forEach {
        stamp()
        state = state.copy(requests = state.requests + (it.id to it.copy(version = v, status = ConnectionRequestStatus.UNAVAILABLE)))
    }
    state.connections.values.filter { it.status != ConnectionStatus.ENDED && involves(it.first, it.second) }.forEach { end(it, actor, reason) }
    if (closeAfter) {
        state.afterRequests.values.filter { it.status == AfterRequestStatus.PENDING && involves(it.sender, it.recipient) }.forEach {
            stamp()
            state = state.copy(afterRequests = state.afterRequests + (it.id to it.copy(version = v, status = AfterRequestStatus.UNAVAILABLE)))
        }
        state.afterConnections.values.filter { it.status != AfterConnectionStatus.CLOSED && involves(it.first, it.second) }.forEach {
            closeAfter(it, actor, if (accountDeleted) AfterCloseReason.ACCOUNT_DELETED else if (reason == ConnectionEndReason.BLOCKED) AfterCloseReason.BLOCKED else AfterCloseReason.SELF_LEFT)
        }
    }
}

internal fun Room.evaluateConnections() {
    state.connections.values.filter { it.status != ConnectionStatus.ENDED }.forEach { c ->
        val reason = when {
            freshness(c.first) != LocationAvailability.FRESH || freshness(c.second) != LocationAvailability.FRESH -> ConnectionEndReason.STATUS_UNKNOWN
            !near(c.first, c.second, exiting = true) -> ConnectionEndReason.DISTANCE
            else -> null
        }
        if (reason == null) {
            if (c.status == ConnectionStatus.CHECKING) {
                stamp()
                state = state.copy(connections = state.connections + (c.id to c.copy(version = v,
                    status = ConnectionStatus.CONNECTED, checkingSince = null, checkingReason = null)))
            }
        } else if (c.checkingReason != reason || c.checkingSince == null) {
            stamp()
            state = state.copy(connections = state.connections + (c.id to c.copy(version = v,
                status = ConnectionStatus.CHECKING, checkingSince = now, checkingReason = reason)))
        } else if (now - c.checkingSince >= if (reason == ConnectionEndReason.DISTANCE) policy.exitGraceMillis else policy.statusUnknownGraceMillis) {
            end(c, null, reason)
        }
    }
}

internal fun Room.seedDemo() {
    if (!demoEnabled) return
    DemoCast.peers.forEachIndexed { index, demo ->
        val existing = state.accounts[demo.id]
        if (existing != null) {
            val profile = existing.profile.publicProfile
            val signature = profile.signature
            if (profile.kind == ProfileKind.DEMO_PEER && !existing.deleted && signature != null &&
                (signature.traits != demo.traits || signature.title != rules.title(demo.traits))) {
                stamp()
                put(existing.copy(profile = existing.profile.copy(version = v, publicProfile = profile.copy(
                    version = v, signature = signature.copy(version = v, traits = demo.traits, title = rules.title(demo.traits)),
                ))))
            }
            return@forEachIndexed
        }
        stamp()
        val profileId = "profile-${demo.id}"
        val signature = PublicSignature("signature-${demo.id}", v, rules.title(demo.traits), demo.traits,
            KnotSeed((index + 1L) * 171), DataSource.SYNTHETIC, EvidenceBucket.ESTABLISHED)
        val profile = MyProfile(profileId, v, PublicProfile(profileId, v, demo.nickname, demo.avatar, demo.interests,
            ProfileKind.DEMO_PEER, signature), RegistrationState.READY)
        val settings = ParticipationSettings("settings-${demo.id}", v, ConsentScope.entries.map {
            ConsentRecord("consent-${demo.id}-$it", v, it, ConsentStatus.GRANTED, policy.consentDocumentVersion, now)
        })
        put(EngineAccount(demo.id, Session("session-${demo.id}", v, demo.id, SessionMode.SOLO, PersonaCode.ACCOUNT_A,
            null, null, null, policy.participation(v)), profile, settings, discovery = DiscoveryMode.ON))
    }
}

private data class Scheduled(val at: Long, val key: String, val run: Room.() -> Unit)

/** Rebuild the small event queue from authoritative state; sparse ticks have the same result as frequent ticks. */
private fun Room.scheduled(): List<Scheduled> = buildList {
    state.requests.values.forEach { r ->
        if (r.status == ConnectionRequestStatus.PENDING) add(Scheduled(r.expiresAt, "expire-request-${r.id}") {
            stamp(); state = state.copy(requests = state.requests + (r.id to r.copy(version = v, status = ConnectionRequestStatus.EXPIRED, retryAt = now + policy.requestCooldownMillis)))
        })
        r.retryAt?.let { at -> add(Scheduled(at, "retry-request-${r.id}") { stamp() }) }
    }
    state.afterRequests.values.forEach { r ->
        if (r.status == AfterRequestStatus.PENDING) add(Scheduled(r.expiresAt, "expire-after-${r.id}") {
            stamp(); state = state.copy(afterRequests = state.afterRequests + (r.id to r.copy(version = v, status = AfterRequestStatus.EXPIRED, retryAt = now + policy.afterCooldownMillis)))
        })
        r.retryAt?.let { at -> add(Scheduled(at, "retry-after-${r.id}") { stamp() }) }
    }
    state.connections.values.filter { it.status == ConnectionStatus.CHECKING }.forEach { c ->
        val delay = if (c.checkingReason == ConnectionEndReason.DISTANCE) policy.exitGraceMillis else policy.statusUnknownGraceMillis
        add(Scheduled(c.checkingSince!! + delay, "checking-${c.id}-${c.checkingSince}-${c.checkingReason}") { evaluateConnections() })
    }
    state.accounts.values.filter { it.locationSource == LocationSource.DEVICE && it.locationAvailability == LocationAvailability.FRESH }.forEach { a ->
        val due = (locations[a.id]?.observedAtEpochMillis ?: (now - policy.locationFreshnessMillis - 1)) + policy.locationFreshnessMillis + 1
        add(Scheduled(due, "stale-${a.id}-${a.locationObservedAt}") {
            stamp(); put(state.accounts.getValue(a.id).copy(locationAvailability = LocationAvailability.STALE)); evaluateConnections()
        })
    }
    if (demoEnabled) DemoCast.peers.forEach { demo ->
        state.requests.values.filter { it.recipient == demo.id && it.status == ConnectionRequestStatus.PENDING && demo.response != DemoResponse.IGNORE }.forEach { r ->
            add(Scheduled(r.createdAt + demo.responseDelayMillis, "demo-respond-${r.id}") {
                respondConnection(demo.id, RespondConnectionRequest(CommandMeta("demo-respond-${r.id}"), r.id,
                    if (demo.response == DemoResponse.ACCEPT) RequestDecision.ACCEPT else RequestDecision.DECLINE))
            })
        }
        if (demo.incomingDelayMillis != null) state.accounts.values.filter { it.profile.publicProfile.kind == ProfileKind.VIRTUAL_PROFILE && it.discoverySince != null }.forEach { a ->
            add(Scheduled(a.discoverySince!! + demo.incomingDelayMillis, "demo-incoming-${a.id}-${a.discoverySince}") {
                requestConnection(demo.id, RequestConnectionRequest(CommandMeta("demo-incoming-${a.id}-${a.discoverySince}"), a.profile.id))
            })
        }
        state.connections.values.lastOrNull { demo.id in setOf(it.first, it.second) }?.let { c ->
            add(Scheduled(c.startedAt + demo.greetingDelayMillis, "demo-greeting-${c.id}-${demo.id}") {
                val other = peer(c.first, c.second, demo.id)
                if (state.greetings.values.any { it.greeting.connectionId == c.id && it.author == other }) return@Scheduled
                val code = presets(demo.id, other).firstOrNull { it !in Room.BASE_PRESETS } ?: PresetCode.NICE_TO_MEET_YOU
                sendGreeting(demo.id, SendGreetingRequest(CommandMeta("demo-greeting-${c.id}"), c.id, "demo-greeting-${c.id}", code))
            })
            demo.presenceScript.forEach { step ->
                add(Scheduled(c.startedAt + step.afterConnectionMillis, "demo-presence-${c.id}-${demo.id}-${step.afterConnectionMillis}") {
                    setSyntheticPresence(demo.id, SetSyntheticPresenceRequest(CommandMeta("demo-presence-${c.id}-${step.afterConnectionMillis}"), step.presence))
                })
            }
        }
        state.connections.values.filter { it.status == ConnectionStatus.CONNECTED && demo.id in setOf(it.first, it.second) }.forEach { c ->
            val messages = state.greetings.values.filter { it.greeting.connectionId == c.id }.sortedBy { it.greeting.version }
            val pending = messages.drop(messages.indexOfLast { it.author == demo.id } + 1).firstOrNull { it.author != demo.id }
            if (pending != null && demo.greetingReplies.isNotEmpty()) {
                val key = "demo-reply-${c.id}-${pending.greeting.id}"
                val turn = messages.count { it.author == demo.id && it.greeting.clientMessageId.startsWith("demo-reply-") }
                add(Scheduled(pending.greeting.sentAtEpochMillis + demo.replyDelayMillis, key) {
                    sendGreeting(demo.id, SendGreetingRequest(CommandMeta(key), c.id, key,
                        demo.greetingReplies[turn % demo.greetingReplies.size]))
                })
            }
        }
        state.afterConnections.values.filter { it.status == AfterConnectionStatus.CONNECTED && demo.id in setOf(it.first, it.second) }.forEach { c ->
            val messages = state.messages.values.filter { it.message.afterConnectionId == c.id }.sortedBy { it.message.version }
            val pending = messages.drop(messages.indexOfLast { it.author == demo.id } + 1).firstOrNull { it.author != demo.id }
            if (pending != null && demo.afterReplies.isNotEmpty()) {
                val key = "demo-after-reply-${c.id}-${pending.message.id}"
                val turn = messages.count { it.author == demo.id }
                add(Scheduled(pending.message.sentAtEpochMillis + demo.replyDelayMillis, key) {
                    sendAfterMessage(demo.id, SendAfterMessageRequest(CommandMeta(key), c.id, key,
                        AfterMessageContent.Text(demo.afterReplies[turn % demo.afterReplies.size])))
                })
            }
        }
        if (demo.acceptsAfter) state.afterRequests.values.filter { it.recipient == demo.id && it.status == AfterRequestStatus.PENDING }.forEach { r ->
            add(Scheduled(r.createdAt + demo.responseDelayMillis, "demo-after-${r.id}") {
                respondAfter(demo.id, RespondAfterRequest(CommandMeta("demo-after-${r.id}"), r.id, RequestDecision.ACCEPT))
            })
        }
    }
}.filter { it.key !in state.demoEvents }

internal fun Room.advance(until: Long) {
    val target = maxOf(until, state.lastTick)
    now = state.lastTick
    evaluateConnections()
    updateViews()
    while (true) {
        val event = scheduled().filter { it.at <= target }.minWithOrNull(compareBy<Scheduled> { it.at }.thenBy { it.key }) ?: break
        now = maxOf(now, event.at)
        val before = state
        val beforeLocations = locations.toMap()
        try { event.run(this) } catch (_: Rejected) {
            state = before
            locations.clear(); locations.putAll(beforeLocations)
        }
        state = state.copy(demoEvents = state.demoEvents + event.key)
        evaluateConnections()
        updateViews()
    }
    now = target
    evaluateConnections()
    state = state.copy(lastTick = target)
}
