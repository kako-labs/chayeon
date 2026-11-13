package dev.chayeon.data.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The composition root supplies authenticated session and consent state; never a UI toggle alone. */
data class RecordingPermission(val accountId: String? = null, val vehicleDataConsent: Boolean = false, val generation: Long = 0) {
    val canRecord: Boolean get() = !accountId.isNullOrBlank() && vehicleDataConsent
}

interface RecordingGate {
    val permission: StateFlow<RecordingPermission>
}

class MutableRecordingGate : RecordingGate {
    private val mutable = MutableStateFlow(RecordingPermission())
    override val permission = mutable.asStateFlow()

    fun update(accountId: String?, vehicleDataConsent: Boolean) {
        mutable.update { previous ->
            if (previous.accountId == accountId && previous.vehicleDataConsent == vehicleDataConsent) previous
            else RecordingPermission(accountId, vehicleDataConsent, previous.generation + 1)
        }
    }
}
