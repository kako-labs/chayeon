package dev.chayeon

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.chayeon.core.model.*
import dev.chayeon.feature.history.HistoryDestination
import dev.chayeon.feature.meet.MeetDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

const val REQUEST_DESTINATION = "dev.chayeon.request.destination"

interface RequestNotifier {
    fun notifyRequest(id: String, route: String = HistoryDestination.RELATIONSHIPS)
    fun cancelRequest(id: String)
}

class AppNotifications(private val context: Context) : RequestNotifier {
    override fun cancelRequest(id: String) {
        context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
    }

    override fun notifyRequest(id: String, route: String) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("chayeon-requests", "연결과 애프터 요청", NotificationManager.IMPORTANCE_HIGH))
        val intent = PendingIntent.getActivity(context, id.hashCode(), Intent(context, MainActivity::class.java)
            .putExtra(REQUEST_DESTINATION, route), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(id.hashCode(), Notification.Builder(context, "chayeon-requests")
            .setSmallIcon(android.R.drawable.ic_dialog_email).setCategory(Notification.CATEGORY_MESSAGE)
            .setContentTitle("차연에 새 소식이 있어요").setContentText("주차하면 확인할 수 있어요")
            .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(intent).setAutoCancel(true).setShowWhen(false).build())
    }
}

data class AppEvent(val id: String, val text: String, val route: String, val requestId: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class AppEvents(private val graph: AppGraph, private val notifier: RequestNotifier, private val scope: CoroutineScope, private val signals: Flow<List<String>> = flowOf(emptyList()),
    private val signalReadiness: Flow<Boolean> = flowOf(true)) {
    private val pending = MutableStateFlow<List<AppEvent>>(emptyList())
    val banner = pending.map { it.firstOrNull() }.stateIn(scope, SharingStarted.WhileSubscribed(5_000, 0), null)
    private val observed = mutableSetOf<String>()
    private val notified = mutableSetOf<String>()
    private val receivedEvents = mutableMapOf<String, AppEvent>()
    private var started = false
    private val viewing = mutableMapOf<Any, String?>()

    fun viewing(requestId: String?): () -> Unit {
        val token = Any()
        viewing[token] = requestId
        cancelViewedNotifications()
        pending.update { events -> events.filterNot(::isViewed) }
        return { viewing.remove(token); Unit }
    }

    private fun isViewed(event: AppEvent): Boolean = viewing.values.any { request ->
        request != null && request == event.requestId
    }

    private fun cancelViewedNotifications() {
        receivedEvents.values.filter(::isViewed).forEach(::cancelNotification)
    }

    private fun cancelNotification(event: AppEvent) {
        notifier.cancelRequest(event.id)
        notified.remove(event.id)
    }

    fun acknowledge(id: String) { pending.update { list -> list.filterNot { it.id == id } } }

    private suspend fun receive(event: AppEvent) {
        if (receivedEvents.put(event.id, event) == null && isViewed(event)) cancelNotification(event)
        if (!observed.add(event.id)) return
        val settings = (graph.profileRepository.settings.first() as? LoadState.Ready)?.value
        val enabled = settings?.notifyRequests != false
        if (!enabled || isViewed(event)) return
        pending.update { it + event }
        if (graph.currentPolicy() != DrivingPolicy.ALLOWED && notified.add(event.id)) {
            notifier.notifyRequest(event.id, event.route)
        }
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(pending.map { it.firstOrNull() }.distinctUntilChanged(), graph.drivingStateProvider.drivingState) { event, driving ->
                event.takeIf { driving.policy == DrivingPolicy.ALLOWED }
            }.distinctUntilChanged().collectLatest { event ->
                if (event != null) { delay(5_000); acknowledge(event.id) }
            }
        }
        scope.launch {
            graph.sessionRepository.session.map { (it as? SessionState.Active)?.session?.id }.distinctUntilChanged().collectLatest { id ->
                pending.value = emptyList()
                observed.clear()
                notified.forEach(notifier::cancelRequest)
                notified.clear()
                receivedEvents.clear()
                if (id == null) return@collectLatest
                kotlinx.coroutines.coroutineScope {
                    launch {
                        var baseline = true
                        val signalIds = mutableSetOf<String>()
                        signalReadiness.distinctUntilChanged().flatMapLatest { ready ->
                            if (ready) signals else emptyFlow()
                        }.collect { ids ->
                            val arrivals = ids.filter { signalIds.add(it) }
                            if (!baseline && graph.currentPolicy() != DrivingPolicy.ALLOWED) arrivals.forEach {
                                if (it !in observed && notified.add(it)) notifier.notifyRequest(it)
                            }
                            // The first session snapshot is history, including when it is non-empty.
                            if (baseline) observed.addAll(ids)
                            baseline = false
                        }
                    }
                    launch {
                        graph.connectionRepository.requests.collect { state ->
                            if (state is LoadState.Ready) {
                                val ids = state.value.filter { it.status == ConnectionRequestStatus.PENDING }.map { it.id }.toSet()
                                pending.update { events -> events.filterNot { it.route == MeetDestination.REQUESTS && it.requestId !in ids } }
                            }
                            (state as? LoadState.Ready)?.value?.forEach {
                                if (it.direction == RequestDirection.INCOMING && it.status == ConnectionRequestStatus.PENDING)
                                    receive(AppEvent(it.id, "새 연결 요청이 왔어요 · 확인하기", MeetDestination.REQUESTS, it.id))
                                else acknowledge(it.id)
                            }
                        }
                    }
                    launch {
                        graph.afterRepository.requests.collect { state ->
                            if (state is LoadState.Ready) {
                                val ids = state.value.filter { it.status == AfterRequestStatus.PENDING }.map { it.id }.toSet()
                                pending.update { events -> events.filterNot { it.requestId != null && it.route != MeetDestination.REQUESTS && it.requestId !in ids } }
                            }
                            (state as? LoadState.Ready)?.value?.forEach {
                                if (it.direction == RequestDirection.INCOMING && it.status == AfterRequestStatus.PENDING)
                                    receive(AppEvent(it.id, "애프터 신청이 왔어요 · 확인하기", HistoryDestination.person(it.peer.id), it.id))
                                else acknowledge(it.id)
                            }
                        }
                    }

                }
            }
        }
    }
}
