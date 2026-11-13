package dev.chayeon.core.engine

import dev.chayeon.core.model.*

internal fun Room.requestAfter(actor: String, r: RequestAfterRequest): AfterRequest {
    ensure(r.contacts.isValid(requireAtLeastOne = true))
    val contacts = r.contacts.normalized()
    val e = state.encounters[r.encounterId]?.takeIf { it.owner == actor } ?: reject()
    val c = state.connections.getValue(e.connectionId)
    val other = peer(c.first, c.second, actor)
    ensure(afterEligibility(actor, c).first == AfterEligibility.AVAILABLE, AppError.PeerUnavailable)
    checkVersion(r.meta, v)
    stamp()
    val record = AfterRequestRecord(ids.next(), v, actor, other, c.id, AfterRequestStatus.PENDING, now, now + policy.afterTtlMillis, submittedContacts = contacts)
    state = state.copy(afterRequests = state.afterRequests + (record.id to record))
    if (r.saveToProfile) saveContacts(actor, contacts)
    return afterRequest(record, actor)
}

internal fun Room.pendingAfter(actor: String, id: String, recipient: Boolean): AfterRequestRecord {
    val r = state.afterRequests[id]?.takeIf { if (recipient) it.recipient == actor else it.sender == actor } ?: reject()
    when (r.status) {
        AfterRequestStatus.PENDING -> Unit
        AfterRequestStatus.EXPIRED -> reject(AppError.Expired)
        AfterRequestStatus.UNAVAILABLE -> reject()
        else -> reject(AppError.AlreadyClosed)
    }
    return r
}

internal fun Room.respondAfter(actor: String, r: RespondAfterRequest, trustedDemo: Boolean = false): AfterRequest {
    val record = pendingAfter(actor, r.requestId, true)
    checkVersion(r.meta, v)
    val contacts = r.contacts.normalized()
    if (r.decision == RequestDecision.ACCEPT) {
        ensure(r.contacts.isValid(requireAtLeastOne = true) ||
            (trustedDemo && demoEnabled && DemoCast.peers.any { it.id == actor && it.contacts == contacts }))
        ensure(record.submittedContacts != null)
        ensure(ready(account(actor)) && state.accounts[record.sender]?.let(::ready) == true && !blocked(actor, record.sender), AppError.PeerUnavailable)
        ensure(state.afterConnections.values.none { setOf(it.first, it.second) == setOf(actor, record.sender) && it.status == AfterConnectionStatus.CONNECTED }, AppError.AlreadyClosed)
    }
    stamp()
    val c = if (r.decision == RequestDecision.ACCEPT) AfterConnectionRecord(ids.next(), v, record.id, record.connectionId, record.sender, actor, now, firstContacts = record.submittedContacts, secondContacts = contacts) else null
    val updated = record.copy(version = v, submittedContacts = null, status = if (c != null) AfterRequestStatus.ACCEPTED else AfterRequestStatus.DECLINED,
        afterConnectionId = c?.id, retryAt = if (c == null) now + policy.afterCooldownMillis else null)
    state = state.copy(afterRequests = state.afterRequests + (record.id to updated))
    if (c != null) {
        state = state.copy(afterConnections = state.afterConnections + (c.id to c))
        if (r.saveToProfile) saveContacts(actor, contacts)
    }
    return afterRequest(updated, actor)
}

internal fun Room.withdrawAfter(actor: String, r: WithdrawAfterRequest): AfterRequest {
    val record = pendingAfter(actor, r.requestId, false)
    checkVersion(r.meta, v)
    stamp()
    val updated = record.copy(version = v, submittedContacts = null, status = AfterRequestStatus.WITHDRAWN, retryAt = now + policy.afterCooldownMillis)
    state = state.copy(afterRequests = state.afterRequests + (record.id to updated))
    return afterRequest(updated, actor)
}

internal fun Room.endAfterConnection(actor: String, r: EndAfterConnectionRequest): AfterConnection {
    val c = state.afterConnections[r.afterConnectionId] ?: reject()
    peer(c.first, c.second, actor)
    ensure(c.status != AfterConnectionStatus.CLOSED, AppError.AlreadyClosed)
    checkVersion(r.meta, v)
    closeAfter(c, actor, AfterCloseReason.SELF_LEFT)
    return afterConnection(state.afterConnections.getValue(c.id), actor)
}

internal fun Room.blockPeer(actor: String, r: BlockPeerRequest): Block {
    val other = visiblePeer(actor, r.peerProfileId)
    val existing = state.blocks.values.firstOrNull { it.owner == actor && it.peer == other }
    if (existing != null) return block(existing, actor)
    checkVersion(r.meta, v)
    stamp()
    val b = BlockRecord(ids.next(), v, actor, other, now)
    state = state.copy(blocks = state.blocks + (b.id to b))
    revoke(actor, other, ConnectionEndReason.BLOCKED)
    return block(b, actor)
}

internal fun Room.unblockPeer(actor: String, r: UnblockPeerRequest): CommandReceipt {
    val b = state.blocks[r.blockId]?.takeIf { it.owner == actor } ?: reject()
    checkVersion(r.meta, v)
    stamp()
    state = state.copy(blocks = state.blocks - b.id)
    return CommandReceipt(r.meta.commandId, v)
}

internal fun Room.reportPeer(actor: String, r: ReportPeerRequest): Report {
    val other = visiblePeer(actor, r.peerProfileId)
    r.context?.let { context ->
        val involved = when (context.kind) {
            ReportContextKind.CONNECTION -> state.connections[context.entityId]?.let { setOf(it.first, it.second) }
            ReportContextKind.ENCOUNTER -> state.encounters[context.entityId]?.takeIf { it.owner == actor }?.let { state.connections[it.connectionId] }?.let { setOf(it.first, it.second) }
            ReportContextKind.AFTER_CONNECTION -> state.afterConnections[context.entityId]?.let { setOf(it.first, it.second) }
        }
        ensure(involved == setOf(actor, other), AppError.PeerUnavailable)
    }
    stamp()
    val report = Report(ids.next(), v, r.reason, ReportStatus.RECEIVED, now)
    state = state.copy(reports = state.reports + (report.id to ReportRecord(actor, other, report, r.context)))
    return report
}

internal fun Room.saveContacts(actor: String, contacts: ContactIds) {
    val account = account(actor)
    put(account.copy(profile = account.profile.copy(version = v, contacts = contacts)))
}
