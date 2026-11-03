package dev.chayeon.data.vehicle

import android.content.Context
import android.content.pm.ApplicationInfo
import dev.chayeon.core.model.*
import dev.chayeon.core.signature.TripAccumulator
import dev.chayeon.core.signature.VehicleSample
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultVehicleConsole(
    private val context: Context,
    private val gateway: VehicleGateway,
    private val driving: VehicleDrivingStateProvider,
    private val repository: LocalSignatureRepository,
    private val gate: RecordingGate,
    private val scope: CoroutineScope,
    private val enabled: Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
    private val epochMillis: () -> Long = System::currentTimeMillis,
) : VehicleConsole, AutoCloseable {
    private val mutableState = MutableStateFlow(ConsoleState(available = enabled))
    override val consoleState = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var replayJob: Job? = null
    private val commands = LinkedHashMap<String, DriveScenario>()

    override suspend fun setGear(request: SetGearRequest): AppResult<DrivingState> = mutex.withLock {
        if (!enabled || gateway !is SimulatedVehicleGateway) return@withLock AppResult.Failure(AppError.Restricted)
        gateway.setGear(request.gear)
        AppResult.Success(driving.current())
    }

    override suspend fun replay(request: ReplayDriveRequest): AppResult<ConsoleState> = mutex.withLock {
        if (!enabled || driving.current().policy != DrivingPolicy.ALLOWED) return@withLock AppResult.Failure(AppError.Restricted)
        val permission = gate.permission.value
        if (!permission.canRecord) return@withLock AppResult.Failure(AppError.NotAuthorized)
        if (request.meta.commandId.isBlank()) return@withLock AppResult.Failure(AppError.Unknown)
        commands[request.meta.commandId]?.let {
            return@withLock if (it == request.scenario) AppResult.Success(mutableState.value) else AppResult.Failure(AppError.Unknown)
        }
        if (mutableState.value.status == ReplayStatus.PLAYING) return@withLock AppResult.Failure(AppError.AlreadyClosed)
        if (request.meta.expectedVersion != null && request.meta.expectedVersion != mutableState.value.version) return@withLock AppResult.Failure(AppError.AlreadyClosed)
        val asset = try { DriveScenarioAsset.load(context, request.scenario) } catch (_: Exception) { return@withLock AppResult.Failure(AppError.Unknown) }
        commands[request.meta.commandId] = request.scenario
        if (commands.size > 64) commands.remove(commands.keys.first())
        mutableState.value = mutableState.value.copy(version = mutableState.value.version + 1, status = ReplayStatus.PLAYING, scenario = request.scenario)
        replayJob = scope.launch {
            try {
                val end = epochMillis()
                val accumulator = TripAccumulator(UUID.randomUUID().toString(), end - asset.durationMillis - 1, DataSource.SYNTHETIC)
                if ("belt" in asset.signals) accumulator.accept(VehicleSample.SeatBelt(0, asset.frames.first().belt))
                accumulator.accept(VehicleSample.Gear(1, Gear.DRIVE))
                var time = 1L
                while (time <= asset.durationMillis) {
                    if (gate.permission.value != permission || driving.current().policy != DrivingPolicy.ALLOWED) throw ReplayInterrupted()
                    repeat(5) {
                        if (time <= asset.durationMillis) {
                            feed(accumulator, asset, time)
                            time += 200
                        }
                    }
                    delay((asset.replayWallMillis * 1_000 / asset.durationMillis).coerceAtLeast(1))
                }
                accumulator.accept(VehicleSample.Gear(asset.durationMillis + 1, Gear.PARK))
                val metrics = accumulator.finish(end)
                if (gate.permission.value != permission || driving.current().policy != DrivingPolicy.ALLOWED) throw ReplayInterrupted()
                val result = repository.recordTripFor(permission, RecordTripRequest(CommandMeta("replay-${metrics.id}"), metrics))
                mutableState.value = mutableState.value.copy(version = mutableState.value.version + 1,
                    status = if (result is AppResult.Success) ReplayStatus.FINISHED else ReplayStatus.FAILED)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = mutableState.value.copy(version = mutableState.value.version + 1, status = ReplayStatus.FAILED) }
        }
        AppResult.Success(mutableState.value)
    }

    override suspend fun stopReplay(request: StopReplayRequest): AppResult<ConsoleState> = mutex.withLock {
        replayJob?.cancel()
        replayJob = null
        mutableState.value = mutableState.value.copy(version = mutableState.value.version + 1, status = ReplayStatus.IDLE)
        AppResult.Success(mutableState.value)
    }

    private fun feed(accumulator: TripAccumulator, scenario: DriveScenarioAsset, time: Long) {
        val frame = scenario.frameAt(time)
        val s = scenario.signals
        if ("speed" in s) accumulator.accept(VehicleSample.Speed(time, frame.speedKph / 3.6))
        if ("brake" in s) accumulator.accept(VehicleSample.Brake(time, frame.brake))
        if ("steering" in s) accumulator.accept(VehicleSample.Steering(time, frame.steering))
        if ("indicator" in s) accumulator.accept(VehicleSample.TurnIndicator(time, frame.indicator))
        if ("cruise" in s) accumulator.accept(VehicleSample.Cruise(time, frame.cruise))
        if ("clock" in s) accumulator.accept(VehicleSample.Clock(time, frame.localHour))
        if (time % 1_000 == 1L) {
            if ("tire" in s) {
                accumulator.accept(VehicleSample.TirePressure(time, frame.tireKpa, frame.tireLowKpa))
                accumulator.accept(VehicleSample.TireWarning(time, frame.tireKpa <= frame.tireLowKpa))
            }
            if ("energy" in s) accumulator.accept(VehicleSample.BatteryLevel(time, frame.energyPercent))
            if ("chargeLimit" in s) accumulator.accept(VehicleSample.ChargeLimit(time, frame.chargeLimitPercent))
            if ("fluid" in s) {
                accumulator.accept(VehicleSample.OilWarning(time, frame.fluidWarning))
                accumulator.accept(VehicleSample.BrakeFluidWarning(time, frame.fluidWarning))
                accumulator.accept(VehicleSample.WasherFluidWarning(time, frame.fluidWarning))
            }
            if ("airCleaning" in s) accumulator.accept(VehicleSample.AirCleaning(time, frame.airCleaning))
        }
    }

    override fun close() { replayJob?.cancel(); replayJob = null }
    private class ReplayInterrupted : Exception()
}
