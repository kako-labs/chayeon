package dev.chayeon.data.social

import dev.chayeon.core.model.ApiRoutes
import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.EnterSessionRequest
import dev.chayeon.core.model.EnterSessionResponse
import dev.chayeon.core.model.Session
import dev.chayeon.core.model.SessionMode
import dev.chayeon.core.model.SyncResponse
import dev.chayeon.core.model.SyncSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class RemoteBackend(
    serverUrl: String,
    private val sessionStorage: SessionStorage,
    private val client: HttpClient = createSocialHttpClient(),
    private val retryDelayMillis: Long = 250,
    private val secretStorage: ClientSecretStorage,
) : SocialBackend {
    private val baseUrl = serverUrl.trimEnd('/')
    override val mode = SessionMode.SERVER_DEMO

    init {
        val url = URLBuilder(baseUrl).build()
        require(url.protocol.name in setOf("https", "http") && url.user == null && url.password == null)
        require(url.parameters.isEmpty() && url.fragment.isEmpty())
    }

    override suspend fun restoreSession(): AppResult<Session?> = localResult {
        sessionStorage.read()?.also { check(it.mode == mode && !it.accessToken.isNullOrBlank()) }
    }

    override suspend fun enter(request: EnterSessionRequest): AppResult<Session> {
        if (request.mode != mode) return AppResult.Failure(AppError.Unknown)
        return try {
            val room = request.roomCode ?: "default"
            val secret = secretStorage.getOrCreate(baseUrl, room)
            val body = socialJson.encodeToString(EnterSessionRequest.serializer(), request.copy(clientSecret = secret))
            val response = retryNetwork { post(ApiRoutes.ENTER_SESSION, body, null) }
            val result = socialJson.decodeFromString(EnterSessionResponse.serializer(), response).result
            if (result is AppResult.Success) {
                if (result.value.mode != mode || result.value.accessToken.isNullOrBlank()) {
                    return AppResult.Failure(AppError.Unknown)
                }
                val saved = localResult { sessionStorage.write(result.value) }
                if (saved is AppResult.Failure) return saved
            }
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            AppResult.Failure(AppError.Network)
        } catch (_: Exception) {
            AppResult.Failure(AppError.Unknown)
        }
    }

    /** Retries reuse the exact serialized body, retaining commandId and clientMessageId. */
    override suspend fun execute(session: Session, route: String, body: String, canExecute: suspend () -> Boolean): String {
        require(route in commandRoutes) { "Unsupported command" }
        return retryNetwork(canExecute) { post(route, body, requireToken(session)) }
    }

    override suspend fun sync(session: Session, since: Long): AppResult<SyncSnapshot> = try {
        val response = client.get("$baseUrl${ApiRoutes.SYNC}") {
            bearerAuth(requireToken(session))
            url { parameters.append(ApiRoutes.SINCE_QUERY, since.coerceAtLeast(0).toString()) }
        }
        if (response.status.value == 401 || response.status.value == 403) {
            AppResult.Failure(AppError.NotAuthorized)
        } else if (response.status.value >= 500) {
            AppResult.Failure(AppError.Network)
        } else {
            socialJson.decodeFromString(SyncResponse.serializer(), response.bodyAsText()).result
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        AppResult.Failure(AppError.Network)
    } catch (_: Exception) {
        AppResult.Failure(AppError.Unknown)
    }

    override fun snapshots(session: Session, since: Long, onRequest: () -> Unit): Flow<AppResult<SyncSnapshot>> = flow {
        var cursor = since
        var backoff = retryDelayMillis.coerceAtLeast(1)
        while (true) {
            onRequest()
            val response = sync(session, cursor)
            val result = if (response is AppResult.Success && response.value.id != session.id) {
                AppResult.Failure(AppError.NotAuthorized)
            } else response
            emit(result)
            when (result) {
                is AppResult.Success -> {
                    cursor = maxOf(cursor, result.value.version)
                    backoff = retryDelayMillis.coerceAtLeast(1)
                    // A misconfigured proxy must not turn an immediate heartbeat into a hot loop.
                    delay(25)
                }
                is AppResult.Failure -> {
                    if (result.error == AppError.NotAuthorized) return@flow
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(8_000)
                }
            }
        }
    }

    override suspend fun clearLocalData(): AppResult<Unit> = localResult { sessionStorage.clear() }

    private suspend fun post(route: String, body: String, token: String?): String {
        val response = client.post("$baseUrl$route") {
            contentType(ContentType.Application.Json)
            if (token != null) bearerAuth(token)
            setBody(body)
        }
        if (response.status.value == 401 || response.status.value == 403) {
            return socialJson.encodeToString(dev.chayeon.core.model.SignOutResponse.serializer(),
                dev.chayeon.core.model.SignOutResponse(AppResult.Failure(AppError.NotAuthorized)))
        }
        if (response.status.value >= 500) throw IOException("Social service is unavailable")
        return response.bodyAsText()
    }

    private fun requireToken(session: Session): String {
        require(session.mode == mode)
        return requireNotNull(session.accessToken).also { require(it.isNotBlank()) }
    }

    private suspend fun <T> retryNetwork(canExecute: suspend () -> Boolean = { true }, action: suspend () -> T): T {
        repeat(2) { attempt ->
            try {
                if (!canExecute()) throw SocialRestrictedException()
                return action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                if (attempt == 1) throw failure
                delay(retryDelayMillis.coerceAtLeast(0))
            }
        }
        error("Unreachable retry state")
    }

    override fun close() { client.close() }

    private companion object {
        val commandRoutes = setOf(
            ApiRoutes.SIGN_OUT, ApiRoutes.DELETE_ACCOUNT, ApiRoutes.SAVE_PROFILE,
            ApiRoutes.RECORD_CONSENT, ApiRoutes.UPDATE_SETTINGS, ApiRoutes.PUBLISH_SIGNATURE,
            ApiRoutes.SET_DISCOVERY, ApiRoutes.REFRESH_DISCOVERY, ApiRoutes.UPDATE_LOCATION,
            ApiRoutes.SET_SYNTHETIC_PRESENCE, ApiRoutes.REQUEST_CONNECTION, ApiRoutes.RESPOND_CONNECTION,
            ApiRoutes.CANCEL_CONNECTION, ApiRoutes.LEAVE_CONNECTION,
            ApiRoutes.DELETE_ENCOUNTER, ApiRoutes.DELETE_ALL_ENCOUNTERS, ApiRoutes.REQUEST_AFTER,
            ApiRoutes.RESPOND_AFTER, ApiRoutes.WITHDRAW_AFTER,
            ApiRoutes.END_AFTER_CONNECTION, ApiRoutes.BLOCK_PEER, ApiRoutes.UNBLOCK_PEER, ApiRoutes.REPORT_PEER,
        )
    }
}

fun createSocialHttpClient(): HttpClient = HttpClient(OkHttp) {
    followRedirects = false
    install(HttpTimeout) {
        requestTimeoutMillis = 35_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 35_000
    }
}
