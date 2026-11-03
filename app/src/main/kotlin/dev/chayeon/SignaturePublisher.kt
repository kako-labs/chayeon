package dev.chayeon

import dev.chayeon.core.model.*
import dev.chayeon.core.signature.DefaultSignatureEngine
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A publication and session termination share a lock; retries retain the original command body. */
class SignaturePublisher(
    private val graph: AppGraph,
    private val ending: StateFlow<Boolean>,
    private val lock: Mutex,
    private val scope: CoroutineScope,
    private val localAccount: Flow<String?> = graph.sessionRepository.session.map { (it as? SessionState.Active)?.session?.accountId },
) {
    private val engine = DefaultSignatureEngine()
    val failure = MutableStateFlow(false)
    private data class Target(val sessionId: String, val signature: PublicSignature?)
    private var started = false
    private var retryTarget: Target? = null
    private var retryRequest: PublishSignatureRequest? = null

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(graph.sessionRepository.session, graph.signatureRepository.signature,
                graph.profileRepository.profile, ending, localAccount) { session, local, profile, stopping, loadedAccount ->
                val active = (session as? SessionState.Active)?.session
                val signature = (local as? LoadState.Ready)?.value
                val remote = (profile as? LoadState.Ready)?.value
                if (active == null || active.accountId != loadedAccount || signature == null || remote == null || stopping) null
                else {
                    val projection = engine.publicProjection(signature).normalized()
                    if (projection == remote.publicProfile.signature.normalized()) null else Target(active.id, projection)
                }
            }.distinctUntilChanged().collectLatest { target ->
                failure.value = false
                if (target == null) return@collectLatest
                if (retryTarget != target) {
                    retryTarget = target
                    retryRequest = PublishSignatureRequest(CommandMeta(UUID.randomUUID().toString()), target.signature)
                }
                val request = checkNotNull(retryRequest)
                while (!ending.value) {
                    val result = lock.withLock {
                        val current = (graph.sessionRepository.session.first() as? SessionState.Active)?.session
                        if (ending.value || current?.id != target.sessionId) return@withLock null
                        if (graph.currentPolicy() != DrivingPolicy.ALLOWED) AppResult.Failure(AppError.Restricted)
                        else graph.profileRepository.publishSignature(request)
                    } ?: break
                    if (result is AppResult.Success) {
                        retryTarget = null
                        retryRequest = null
                        break
                    }
                    failure.value = (result as AppResult.Failure).error != AppError.Restricted
                    delay(2_000)
                }
            }
        }
    }
}

private fun PublicSignature?.normalized() = this?.copy(id = "public-signature", version = 0)
