package dev.chayeon.data.vehicle

import android.content.Context
import dev.chayeon.core.model.DriveScenario
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class DriveScenarioAsset(
    val schemaVersion: Int = 1,
    val label: String,
    val source: String,
    val durationMillis: Long,
    val replayWallMillis: Long = 10_000,
    val signals: Set<String>,
    val frames: List<DriveFrame>,
) {
    fun frameAt(time: Long): DriveFrame {
        val before = frames.lastOrNull { it.atMillis <= time } ?: frames.first()
        val after = frames.firstOrNull { it.atMillis > time } ?: before
        val fraction = if (before == after) 0.0 else (time - before.atMillis).toDouble() / (after.atMillis - before.atMillis)
        return before.copy(atMillis = time, speedKph = before.speedKph + fraction * (after.speedKph - before.speedKph))
    }
    companion object {
        fun load(context: Context, scenario: DriveScenario): DriveScenarioAsset {
            val name = scenario.name.lowercase().replace('_', '-')
            val asset = context.assets.open("drive/$name.json").bufferedReader().use { Json.decodeFromString<DriveScenarioAsset>(it.readText()) }
            require(asset.schemaVersion == 1 && asset.source == "SYNTHETIC" && asset.label.contains("합성 주행"))
            require(asset.durationMillis in 180_000..3_600_000 && asset.replayWallMillis in 1_000..30_000)
            require(asset.frames.isNotEmpty() && asset.frames.first().atMillis == 0L && asset.frames.last().atMillis == asset.durationMillis)
            require(asset.frames.zipWithNext().all { (a, b) -> b.atMillis > a.atMillis })
            return asset
        }
    }
}

@Serializable
internal data class DriveFrame(
    val atMillis: Long,
    val speedKph: Double = 0.0,
    val brake: Boolean = false,
    val steering: Double = 0.0,
    val indicator: Boolean = false,
    val cruise: Boolean = false,
    val belt: Boolean = true,
    val tireKpa: Double = 240.0,
    val tireLowKpa: Double = 200.0,
    val energyPercent: Double = 65.0,
    val chargeLimitPercent: Double = 80.0,
    val fluidWarning: Boolean = false,
    val airCleaning: Boolean = true,
    val localHour: Int = 14,
)
