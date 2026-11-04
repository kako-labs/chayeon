package dev.chayeon.data.vehicle

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import dev.chayeon.core.model.*
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.random.asKotlinRandom
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalHomeZoneRepository internal constructor(
    context: Context,
    private val locations: HomeLocationProvider,
    private val policy: () -> DrivingPolicy,
    scope: CoroutineScope,
    private val demoEnabled: Boolean,
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    databaseName: String = "drive-signature.db",
) : HomeZoneRepository, AutoCloseable {
    private val database = VehicleDatabase(context.applicationContext, databaseName)
    private val mutex = Mutex()
    private val random = SecureRandom().asKotlinRandom()
    private var account: String? = null
    private var accountGeneration = 0L
    private var consentAccount: String? = null
    private var consent: Boolean? = null
    private val mutableLocationConsent = MutableStateFlow<Boolean?>(null)
    internal val locationConsent: StateFlow<Boolean?> = mutableLocationConsent.asStateFlow()
    private var record: HomeRecord? = null
    private var storageFailed = false
    private var syntheticInside: Boolean? = null
    private val evaluator = HomeZoneEvaluator(exitDelayMillis = {
        if (demoEnabled && record?.source == LocationSource.SYNTHETIC) 2_000 else random.nextLong(30_000, 90_001)
    })
    private val mutableState = MutableStateFlow(HomeZoneState())
    override val state: StateFlow<HomeZoneState> = mutableState.asStateFlow()
    private val observer = scope.launch {
        locations.location.collect { mutex.withLock { evaluate() } }
    }
    private val ticker = scope.launch {
        while (isActive) { delay(1_000); mutex.withLock { evaluate() } }
    }

    suspend fun bindAccount(accountId: String?) = withContext(dispatcher) {
        mutex.withLock {
            val key = accountKey(accountId)
            if (account == key && !storageFailed) return@withLock
            mutableState.value = HomeZoneState(HomeZoneStatus.LOCATION_UNKNOWN)
            accountGeneration++
            account = key
            mutableLocationConsent.value = consent.takeIf { key != null && key == consentAccount }
            syntheticInside = null
            record = null
            storageFailed = false
            try {
                if (key != null) database.readableDatabase.query("home_zone", null, "account_key=?", arrayOf(key), null, null, null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val point = PrivatePoint(cursor.getDouble(cursor.getColumnIndexOrThrow("center_lat")), cursor.getDouble(cursor.getColumnIndexOrThrow("center_lon")))
                        check(point.valid) { "Invalid stored position" }
                        record = HomeRecord(point, HomeZoneRadius.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("radius"))),
                            LocationSource.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("source"))))
                    }
                }
            } catch (_: Exception) { storageFailed = true }
            evaluator.reset(record)
            // A process/account start never reuses a previous outside decision.
            mutableState.value = if (record != null || storageFailed) HomeZoneState(HomeZoneStatus.LOCATION_UNKNOWN, record?.radius, record?.source) else HomeZoneState()
        }
    }

    private fun accountKey(id: String?) = id?.let {
        MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
    }

    internal suspend fun updateLocationConsent(accountId: String?, granted: Boolean?) = mutex.withLock {
        consentAccount = accountKey(accountId)
        consent = granted
        mutableLocationConsent.value = granted.takeIf { account != null && account == consentAccount }
        if (mutableLocationConsent.value != true) evaluator.reset(record)
        evaluate()
    }

    override suspend fun setFromCurrentPosition(radius: HomeZoneRadius, source: LocationSource): AppResult<Unit> {
        if (policy() != DrivingPolicy.ALLOWED) return AppResult.Failure(AppError.Restricted)
        if (source == LocationSource.DEVICE && locationConsent.value != true) return AppResult.Failure(AppError.Restricted)
        val expectedAccount = mutex.withLock { account?.let { it to accountGeneration } } ?: return AppResult.Failure(AppError.NotAuthorized)
        val fix = if (source == LocationSource.DEVICE) withTimeoutOrNull(8_000) {
            combine(locations.location, locationConsent) { location, granted -> location to granted }.first { (location, granted) ->
                granted != true || (location is ZoneLocation.Fix && location.point.valid &&
                    location.accuracyMeters in 0.0..75.0 && now() - location.observedAtMillis in 0..30_000)
            }.first as? ZoneLocation.Fix
        } else null
        return mutate {
            if (account != expectedAccount.first || accountGeneration != expectedAccount.second) return@mutate AppResult.Failure(AppError.NotAuthorized)
            if (source == LocationSource.DEVICE && locationConsent.value != true) return@mutate AppResult.Failure(AppError.Restricted)
            if (source == LocationSource.SYNTHETIC && (!demoEnabled || freshDeviceFix())) return@mutate AppResult.Failure(AppError.Restricted)
            if (source == LocationSource.DEVICE && (fix == null || !locations.hasPermission() || now() - fix.observedAtMillis !in 0..30_000)) return@mutate AppResult.Failure(AppError.Unknown)
            val center = fix?.point ?: PrivatePoint(0.0, 0.0)
            val next = HomeRecord.jitter(center, radius, source, random)
            save(next)
            record = next
            syntheticInside = if (source == LocationSource.SYNTHETIC) true else null
            evaluator.reset(record)
            evaluate()
            AppResult.Success(Unit)
        }
    }

    override suspend fun changeRadius(radius: HomeZoneRadius) = mutate {
        val current = record ?: return@mutate AppResult.Failure(AppError.Unknown)
        val next = current.withRadius(radius)
        save(next)
        record = next
        evaluator.reset(next)
        evaluate()
        AppResult.Success(Unit)
    }

    override suspend fun clear() = mutate {
        database.writableDatabase.delete("home_zone", "account_key=?", arrayOf(account!!))
        record = null
        syntheticInside = null
        storageFailed = false
        evaluator.reset(null)
        evaluate()
        AppResult.Success(Unit)
    }

    /** Called by a parked demo command, or by the debug-only external driving proof. */
    suspend fun setSyntheticPosition(inside: Boolean, externalProof: Boolean = false): AppResult<Unit> = mutex.withLock {
        if (!demoEnabled || record?.source != LocationSource.SYNTHETIC || account == null) return@withLock AppResult.Failure(AppError.Restricted)
        if (!externalProof && policy() != DrivingPolicy.ALLOWED) return@withLock AppResult.Failure(AppError.Restricted)
        syntheticInside = inside
        evaluate()
        AppResult.Success(Unit)
    }

    suspend fun clearLocalData(): AppResult<Unit> = withContext(dispatcher) {
        mutex.withLock {
            accountGeneration++
            try {
                database.writableDatabase.delete("home_zone", null, null)
                account = null
                consentAccount = null
                consent = null
                mutableLocationConsent.value = null
                record = null
                syntheticInside = null
                storageFailed = false
                evaluator.reset(null)
                mutableState.value = HomeZoneState()
                AppResult.Success(Unit)
            } catch (_: Exception) {
                storageFailed = true
                mutableState.value = HomeZoneState(HomeZoneStatus.LOCATION_UNKNOWN)
                AppResult.Failure(AppError.Unknown)
            }
        }
    }

    fun hasLocationPermission() = locations.hasPermission()

    private fun freshDeviceFix(): Boolean {
        if (locationConsent.value != true) return false
        val fix = locations.location.value as? ZoneLocation.Fix ?: return false
        return locations.hasPermission() && fix.point.valid && fix.accuracyMeters in 0.0..75.0 && now() - fix.observedAtMillis in 0..30_000
    }

    private fun evaluate() {
        if (storageFailed) { mutableState.value = HomeZoneState(HomeZoneStatus.LOCATION_UNKNOWN, record?.radius, record?.source); return }
        val current = record
        if (current != null && locationConsent.value != true) {
            mutableState.value = HomeZoneState(if (locationConsent.value == false) HomeZoneStatus.PERMISSION_OFF else HomeZoneStatus.LOCATION_UNKNOWN,
                current.radius, current.source)
            return
        }
        val location = if (current?.source == LocationSource.SYNTHETIC) {
            syntheticInside?.let { inside -> ZoneLocation.Fix(if (inside) current.center else current.center.offset(current.radius.meters + 500.0, 0.0), now()) } ?: ZoneLocation.Unknown
        } else if (!locations.hasPermission()) ZoneLocation.PermissionOff else locations.location.value
        mutableState.value = evaluator.evaluate(location, now())
    }

    private suspend fun mutate(block: () -> AppResult<Unit>): AppResult<Unit> {
        val expectedAccount = mutex.withLock { account?.let { it to accountGeneration } } ?: return AppResult.Failure(AppError.NotAuthorized)
        return withContext(dispatcher) {
            mutex.withLock {
                if (policy() != DrivingPolicy.ALLOWED) return@withLock AppResult.Failure(AppError.Restricted)
                if (account != expectedAccount.first || accountGeneration != expectedAccount.second) return@withLock AppResult.Failure(AppError.NotAuthorized)
                try { block() } catch (_: Exception) {
                    storageFailed = true
                    evaluate()
                    AppResult.Failure(AppError.Unknown)
                }
            }
        }
    }

    private fun save(value: HomeRecord) {
        val values = ContentValues().apply {
            put("account_key", account)
            put("center_lat", value.center.latitude)
            put("center_lon", value.center.longitude)
            put("radius", value.radius.name)
            put("source", value.source.name)
        }
        database.writableDatabase.insertWithOnConflict("home_zone", null, values, SQLiteDatabase.CONFLICT_REPLACE).also {
            check(it != -1L) { "Private storage unavailable" }
        }
        storageFailed = false
    }

    override fun close() { observer.cancel(); ticker.cancel(); database.close() }
    override fun toString() = "[redacted]"
}
