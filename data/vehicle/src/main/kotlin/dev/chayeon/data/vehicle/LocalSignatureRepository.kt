package dev.chayeon.data.vehicle

import android.content.ContentValues
import android.content.Context
import androidx.core.database.sqlite.transaction
import android.database.sqlite.SQLiteDatabase
import dev.chayeon.core.model.*
import dev.chayeon.core.signature.DefaultSignatureEngine
import dev.chayeon.core.signature.SignatureEngine
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A single transaction commits evidence, signature, recap and retry receipt together. */
class LocalSignatureRepository(
    context: Context,
    private val gate: RecordingGate,
    private val currentDrivingPolicy: () -> DrivingPolicy,
    scope: CoroutineScope,
    private val engine: SignatureEngine = DefaultSignatureEngine(),
    private val epochMillis: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    databaseName: String = "drive-signature.db",
) : SignatureRepository, AutoCloseable {
    private val database = VehicleDatabase(context.applicationContext, databaseName)
    private val mutex = Mutex()
    private var invalidatedGeneration = -1L
    private data class Snapshot(val account: String?, val signature: LoadState<MySignature>, val recaps: LoadState<List<TripRecap>>)
    private val snapshots = MutableStateFlow(Snapshot(null, LoadState.Loading(), LoadState.Loading()))
    override val signature = combine(gate.permission, snapshots) { permission, snapshot ->
        if (permission.accountId == null) LoadState.Ready(MySignature(), 0)
        else if (permission.accountId != snapshot.account) LoadState.Loading()
        else snapshot.signature
    }
    override val recaps = combine(gate.permission, snapshots) { permission, snapshot ->
        if (permission.accountId == null) LoadState.Ready(emptyList(), 0)
        else if (permission.accountId != snapshot.account) LoadState.Loading()
        else snapshot.recaps
    }
    private val gateJob = scope.launch {
        gate.permission.collect { refresh() }
    }
    private val retentionJob = scope.launch {
        while (true) {
            delay(60_000)
            refresh()
        }
    }

    override fun observeEvidence(trait: TraitId) = signature.map { state ->
        when (state) {
            is LoadState.Ready -> LoadState.Ready(state.value.evidence.firstOrNull { it.trait == trait }, state.version)
            is LoadState.Failed -> state
            is LoadState.Loading -> state
        }
    }

    override fun observeRecap(recapId: String) = recaps.map { state ->
        when (state) {
            is LoadState.Ready -> LoadState.Ready(state.value.firstOrNull { it.id == recapId }, state.version)
            is LoadState.Failed -> state
            is LoadState.Loading -> state
        }
    }

    override suspend fun recordTrip(request: RecordTripRequest): AppResult<MySignature> = recordTripFor(gate.permission.value, request)

    internal suspend fun recordTripFor(expectedPermission: RecordingPermission, request: RecordTripRequest): AppResult<MySignature> = mutate(
        request.meta, "record:${VehicleStorageCodec.trip(request.metrics)}", editing = false, expectedPermission = expectedPermission,
        encode = { VehicleStorageCodec.signature(it) }, decode = { VehicleStorageCodec.signature(it) },
    ) { db, account, previous, revision ->
        if (!gate.permission.value.canRecord) return@mutate AppResult.Failure(AppError.NotAuthorized)
        val metrics = request.metrics
        if (metrics.startedAtEpochMillis < 0 || metrics.endedAtEpochMillis < metrics.startedAtEpochMillis ||
            metrics.endedAtEpochMillis > epochMillis() + 5_000 || metrics.id.isBlank() ||
            metrics.traits.any { t -> t.supportedSampleCount < 0 || t.passingSampleCount !in 0..t.supportedSampleCount || t.metrics.any { !it.value.isFinite() } }) {
            return@mutate AppResult.Failure(AppError.Unknown)
        }
        if (trips(db, account).any { it.id == metrics.id }) return@mutate AppResult.Failure(AppError.AlreadyClosed)
        insert(db, "trips", account, metrics.id, metrics.endedAtEpochMillis, "derived_metrics", VehicleStorageCodec.trip(metrics))
        val current = calculate(previous, trips(db, account), revision)
        val recap = engine.recap(previous, current, metrics)
        insert(db, "recaps", account, recap.id, recap.completedAtEpochMillis, "recap", VehicleStorageCodec.recap(recap))
        saveSignature(db, account, current)
        AppResult.Success(current)
    }

    override suspend fun updateVisibility(request: UpdateVisibilityRequest): AppResult<VisibilitySettings> = mutate(
        request.meta, "visibility:${request.publishSignature}:${request.visibleTraits.sortedBy { it.ordinal }.joinToString()}",
        encode = { VehicleStorageCodec.visibility(it) }, decode = { VehicleStorageCodec.visibility(it) },
    ) { db, account, previous, revision ->
        val visibility = previous.visibility.copy(version = revision, visibleTraits = request.visibleTraits.toSet(), publishSignature = request.publishSignature)
        val current = calculate(previous.copy(visibility = visibility), trips(db, account), revision)
        saveSignature(db, account, current)
        AppResult.Success(visibility)
    }

    override suspend fun excludeTrip(request: ExcludeTripRequest): AppResult<MySignature> = mutate(
        request.meta, "exclude:${request.tripId}", encode = { VehicleStorageCodec.signature(it) }, decode = { VehicleStorageCodec.signature(it) },
    ) { db, account, previous, revision ->
        val trip = trips(db, account).firstOrNull { it.id == request.tripId } ?: return@mutate AppResult.Failure(AppError.AlreadyClosed)
        val excluded = trip.copy(excluded = true, version = revision)
        db.update("trips", values("derived_metrics" to VehicleStorageCodec.trip(excluded)), "account_key=? AND id=?", arrayOf(account, trip.id))
        val current = calculate(previous, trips(db, account), revision)
        recaps(db, account).filter { it.tripId == trip.id }.forEach {
            val recap = it.copy(version = revision, included = false, currentKnot = current.knotSeed, changes = emptyList())
            db.update("recaps", values("recap" to VehicleStorageCodec.recap(recap)), "account_key=? AND id=?", arrayOf(account, recap.id))
        }
        saveSignature(db, account, current)
        AppResult.Success(current)
    }

    override suspend fun markRecapSeen(request: MarkRecapSeenRequest): AppResult<TripRecap> = mutate(
        request.meta, "seen:${request.recapId}", encode = { VehicleStorageCodec.recap(it) }, decode = { VehicleStorageCodec.recap(it) },
    ) { db, account, previous, revision ->
        val recap = recaps(db, account).firstOrNull { it.id == request.recapId } ?: return@mutate AppResult.Failure(AppError.AlreadyClosed)
        val seen = recap.copy(version = revision, seen = true)
        db.update("recaps", values("recap" to VehicleStorageCodec.recap(seen)), "account_key=? AND id=?", arrayOf(account, recap.id))
        saveSignature(db, account, previous.copy(version = revision))
        AppResult.Success(seen)
    }

    override suspend fun clearLocalData(): AppResult<Unit> = withContext(dispatcher) {
        mutex.withLock {
            try {
                val db = database.writableDatabase
                invalidatedGeneration = gate.permission.value.generation
                db.transaction { db.delete("accounts", null, null) }
                snapshots.value = Snapshot(null, LoadState.Ready(MySignature(), 0), LoadState.Ready(emptyList(), 0))
                AppResult.Success(Unit)
            } catch (_: Exception) { AppResult.Failure(AppError.Unknown) }
        }
    }

    private suspend fun refresh() = withContext(dispatcher) {
        mutex.withLock {
            val permission = gate.permission.value
            try {
                val db = database.writableDatabase
                db.transaction {
                    purge(db)
                    permission.accountId?.let { id ->
                        val account = key(id)
                        val previous = loadSignature(db, account)
                        val current = engine.calculate(previous, trips(db, account), previous.visibility, epochMillis())
                        saveSignature(db, account, current)
                    }
                }
                publish(db, permission.accountId)
            } catch (_: Exception) {
                snapshots.value = Snapshot(permission.accountId, LoadState.Failed(AppError.Unknown), LoadState.Failed(AppError.Unknown))
            }
        }
    }

    private suspend fun <T> mutate(
        meta: CommandMeta, fingerprint: String, editing: Boolean = true, expectedPermission: RecordingPermission = gate.permission.value, encode: (T) -> String, decode: (String) -> T,
        block: (SQLiteDatabase, String, MySignature, Long) -> AppResult<T>,
    ): AppResult<T> = withContext(dispatcher) {
        mutex.withLock {
            val permission = gate.permission.value
            if (permission != expectedPermission) return@withLock AppResult.Failure(AppError.NotAuthorized)
            val accountId = permission.accountId ?: return@withLock AppResult.Failure(AppError.NotAuthorized)
            if (editing && currentDrivingPolicy() != DrivingPolicy.ALLOWED) return@withLock AppResult.Failure(AppError.Restricted)
            if (!editing && (!permission.canRecord || permission.generation <= invalidatedGeneration)) return@withLock AppResult.Failure(AppError.NotAuthorized)
            if (meta.commandId.isBlank()) return@withLock AppResult.Failure(AppError.Unknown)
            val account = key(accountId)
            try {
                val db = database.writableDatabase
                var result: AppResult<T>
                db.beginTransaction()
                try {
                    purge(db)
                    val previous = loadSignature(db, account)
                    val receipt = db.rawQuery("SELECT fingerprint,result FROM commands WHERE account_key=? AND id=?", arrayOf(account, meta.commandId)).use {
                        if (it.moveToFirst()) it.getString(0) to it.getString(1) else null
                    }
                    result = when {
                        receipt != null -> if (receipt.first == key(fingerprint)) AppResult.Success(decode(receipt.second)) else AppResult.Failure(AppError.Unknown)
                        meta.expectedVersion != null && meta.expectedVersion != previous.version -> AppResult.Failure(AppError.AlreadyClosed)
                        else -> block(db, account, previous, previous.version + 1)
                    }
                    if (gate.permission.value != permission) return@withLock AppResult.Failure(AppError.NotAuthorized)
                    if (editing && currentDrivingPolicy() != DrivingPolicy.ALLOWED) return@withLock AppResult.Failure(AppError.Restricted)
                    if (result is AppResult.Success) {
                        if (receipt == null) db.insertOrThrow("commands", null, values("account_key" to account, "id" to meta.commandId,
                            "created_at" to epochMillis(), "fingerprint" to key(fingerprint), "result" to encode(result.value)))
                        db.setTransactionSuccessful()
                    }
                } finally { db.endTransaction() }
                publish(db, accountId)
                result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { AppResult.Failure(AppError.Unknown) }
        }
    }

    private fun calculate(previous: MySignature, trips: List<DerivedTripMetrics>, revision: Long): MySignature =
        engine.calculate(previous, trips, previous.visibility, epochMillis()).let { value ->
            value.copy(version = revision, publicSignature = value.publicSignature?.copy(version = revision), evidence = value.evidence.map { it.copy(version = revision) })
        }

    private fun loadSignature(db: SQLiteDatabase, account: String): MySignature = db.rawQuery("SELECT signature FROM accounts WHERE account_key=?", arrayOf(account)).use {
        if (it.moveToFirst()) VehicleStorageCodec.signature(it.getString(0)) else MySignature(id = "signature-$account")
    }

    private fun saveSignature(db: SQLiteDatabase, account: String, signature: MySignature) {
        val row = values("revision" to signature.version, "signature" to VehicleStorageCodec.signature(signature))
        if (db.update("accounts", row, "account_key=?", arrayOf(account)) == 0) {
            row.put("account_key", account)
            db.insertOrThrow("accounts", null, row)
        }
    }

    private fun trips(db: SQLiteDatabase, account: String): List<DerivedTripMetrics> = db.rawQuery("SELECT derived_metrics FROM trips WHERE account_key=? ORDER BY ended_at,id", arrayOf(account)).use {
        buildList { while (it.moveToNext()) add(VehicleStorageCodec.trip(it.getString(0))) }
    }
    private fun recaps(db: SQLiteDatabase, account: String): List<TripRecap> = db.rawQuery("SELECT recap FROM recaps WHERE account_key=? ORDER BY ended_at DESC,id", arrayOf(account)).use {
        buildList { while (it.moveToNext()) add(VehicleStorageCodec.recap(it.getString(0))) }
    }
    private fun purge(db: SQLiteDatabase) {
        val cutoff = epochMillis() - 30L * 24 * 60 * 60 * 1_000
        val accounts = db.rawQuery("SELECT DISTINCT account_key FROM trips WHERE ended_at<?", arrayOf(cutoff.toString())).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        for (table in listOf("trips", "recaps")) db.delete(table, "ended_at<?", arrayOf(cutoff.toString()))
        db.delete("commands", "created_at<?", arrayOf(cutoff.toString()))
        // Retry snapshots and cached signatures must not extend the retention of expired evidence.
        accounts.forEach { account ->
            db.delete("commands", "account_key=?", arrayOf(account))
            val previous = loadSignature(db, account)
            saveSignature(db, account, engine.calculate(previous.copy(evidence = emptyList()), trips(db, account), previous.visibility, epochMillis()))
        }
    }
    private fun publish(db: SQLiteDatabase, accountId: String?) {
        if (accountId == null || gate.permission.value.accountId != accountId) {
            snapshots.value = Snapshot(null, LoadState.Ready(MySignature(), 0), LoadState.Ready(emptyList(), 0))
        } else {
            val account = key(accountId)
            val signature = loadSignature(db, account)
            snapshots.value = Snapshot(accountId, LoadState.Ready(signature, signature.version), LoadState.Ready(recaps(db, account), signature.version))
        }
    }
    private fun insert(db: SQLiteDatabase, table: String, account: String, id: String, end: Long, column: String, payload: String) {
        if (db.rawQuery("SELECT 1 FROM accounts WHERE account_key=?", arrayOf(account)).use { !it.moveToFirst() }) saveSignature(db, account, MySignature(id = "signature-$account"))
        db.insertOrThrow(table, null, values("account_key" to account, "id" to id, "ended_at" to end, column to payload))
    }
    private fun values(vararg pairs: Pair<String, Any>) = ContentValues().apply {
        pairs.forEach { (name, value) -> when (value) { is Long -> put(name, value); else -> put(name, value.toString()) } }
    }
    private fun key(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    override fun close() { gateJob.cancel(); retentionJob.cancel(); database.close() }
}
