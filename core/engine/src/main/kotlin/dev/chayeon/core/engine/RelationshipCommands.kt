package dev.chayeon.core.engine

import dev.chayeon.core.model.*

internal fun Room.requestConnection(actor: String, r: RequestConnectionRequest): ConnectionRequest {
    val other = visiblePeer(actor, r.candidateId)
    if (r.automatic) {
        val owner = account(actor)
        ensure(owner.settings.drivingConnectMode == DrivingConnectMode.AUTO)
        ensure(owner.settings.autoConnectConditions.matches(publicProfile(other, actor), owner.profile.publicProfile.interests))
        ensure(state.requests.values.none { it.sender == actor && it.status == ConnectionRequestStatus.PENDING }, AppError.AlreadyClosed)
    }
    ensure(available(actor) && available(other) && !blocked(actor, other), AppError.PeerUnavailable)
    ensure(activeConnection(actor) == null && activeConnection(other) == null, AppError.AlreadyClosed)
    ensure(near(actor, other), AppError.PeerUnavailable)
    val prior = state.requests.values.filter { setOf(it.sender, it.recipient) == setOf(actor, other) }
    ensure(prior.none { it.status == ConnectionRequestStatus.PENDING || (it.retryAt ?: 0) > now }, AppError.AlreadyClosed)
    checkVersion(r.meta, v)
    stamp()
    val record = RequestRecord(ids.next(), v, actor, other, ConnectionRequestStatus.PENDING, now, now + policy.requestTtlMillis, automatic = r.automatic)
    state = state.copy(requests = state.requests + (record.id to record))
    return request(record, actor)
}

internal fun Room.pendingRequest(actor: String, id: String, recipient: Boolean): RequestRecord {
    val r = state.requests[id]?.takeIf { if (recipient) it.recipient == actor else it.sender == actor } ?: reject()
    when (r.status) {
        ConnectionRequestStatus.PENDING -> Unit
        ConnectionRequestStatus.EXPIRED -> reject(AppError.Expired)
        ConnectionRequestStatus.UNAVAILABLE -> reject()
        else -> reject(AppError.AlreadyClosed)
    }
    return r
}

internal fun Room.respondConnection(actor: String, r: RespondConnectionRequest): ConnectionRequest {
    val record = pendingRequest(actor, r.requestId, true)
    if (r.automatic) {
        val owner = account(actor)
        ensure(r.decision == RequestDecision.ACCEPT && owner.settings.drivingConnectMode == DrivingConnectMode.AUTO)
        ensure(owner.settings.autoConnectConditions.matches(publicProfile(record.sender, actor), owner.profile.publicProfile.interests))
    }
    checkVersion(r.meta, v)
    if (r.decision == RequestDecision.ACCEPT) {
        ensure(available(actor) && available(record.sender) && !blocked(actor, record.sender), AppError.PeerUnavailable)
        ensure(activeConnection(actor) == null && activeConnection(record.sender) == null, AppError.AlreadyClosed)
        ensure(near(actor, record.sender), AppError.PeerUnavailable)
    }
    stamp()
    val connection = if (r.decision == RequestDecision.ACCEPT) ConnectionRecord(ids.next(), v, record.id, record.sender, actor, now) else null
    val updated = record.copy(version = v, status = if (connection != null) ConnectionRequestStatus.ACCEPTED else ConnectionRequestStatus.DECLINED,
        connectionId = connection?.id, retryAt = if (connection == null) now + policy.requestCooldownMillis else null)
    state = state.copy(requests = state.requests + (record.id to updated))
    if (connection != null) {
        state = state.copy(connections = state.connections + (connection.id to connection))
        state = state.copy(requests = state.requests.mapValues { (_, x) ->
            if (x.status == ConnectionRequestStatus.PENDING && (x.sender in setOf(actor, record.sender) || x.recipient in setOf(actor, record.sender)))
                x.copy(version = v, status = ConnectionRequestStatus.UNAVAILABLE) else x
        })
    }
    return request(updated, actor)
}

internal fun Room.cancelConnection(actor: String, r: CancelConnectionRequest): ConnectionRequest {
    val record = pendingRequest(actor, r.requestId, false)
    checkVersion(r.meta, v)
    stamp()
    val updated = record.copy(version = v, status = ConnectionRequestStatus.CANCELLED, retryAt = now + policy.requestCooldownMillis)
    state = state.copy(requests = state.requests + (record.id to updated))
    return request(updated, actor)
}

internal fun Room.leaveConnection(actor: String, r: LeaveConnectionRequest): Connection {
    val c = state.connections[r.connectionId] ?: reject()
    peer(c.first, c.second, actor)
    ensure(c.status != ConnectionStatus.ENDED, AppError.AlreadyClosed)
    checkVersion(r.meta, v)
    end(c, actor, ConnectionEndReason.SELF_LEFT)
    return connection(state.connections.getValue(c.id), actor)
}

internal fun Room.deleteEncounter(actor: String, r: DeleteEncounterRequest): CommandReceipt {
    val e = state.encounters[r.encounterId]?.takeIf { it.owner == actor } ?: reject()
    checkVersion(r.meta, v)
    stamp()
    state = state.copy(encounters = state.encounters - e.id)
    return CommandReceipt(r.meta.commandId, v)
}

internal fun Room.deleteAllEncounters(actor: String, r: DeleteAllEncountersRequest): CommandReceipt {
    account(actor)
    checkVersion(r.meta, v)
    stamp()
    state = state.copy(encounters = state.encounters.filterValues { it.owner != actor })
    return CommandReceipt(r.meta.commandId, v)
}

internal fun Room.validIdentifier(value: String) = value.isNotBlank() && value.length <= policy.maximumCommandIdLength && value.none(Char::isISOControl)
