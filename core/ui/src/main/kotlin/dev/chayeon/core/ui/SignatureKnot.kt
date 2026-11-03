package dev.chayeon.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.chayeon.core.model.KnotSeed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class KnotPoint(val x: Float, val y: Float, val depth: Float, val progress: Float)

internal fun knotGeometry(seed: KnotSeed, samples: Int = 480): List<KnotPoint> {
    val mixedA = (seed.value xor (seed.value ushr 30)) * -4658895280553007687L
    val mixedB = (mixedA xor (mixedA ushr 27)) * -7723592293110705685L
    val hash = mixedB xor (mixedB ushr 31)
    val rotation = ((hash and 255) / 255.0 * .8 - .4)
    val tilt = .38 + ((hash ushr 8) and 255) / 255.0 * .55
    val width = .88 + ((hash ushr 16) and 255) / 255.0 * .24
    val lobe = .70 + ((hash ushr 24) and 255) / 255.0 * .20
    val phase = (hash and 63) / 63.0 * PI * 2
    val points = (0..samples).map { index ->
        val t = index.toDouble() / samples * PI * 2
        val radius = 1.65 + lobe * cos(3 * t + phase)
        val x = radius * cos(2 * t)
        val y = radius * sin(2 * t)
        val z = .85 * sin(3 * t + phase)
        val tiltedY = y * cos(tilt) - z * sin(tilt)
        val depth = y * sin(tilt) + z * cos(tilt)
        KnotPoint(((x * cos(rotation) - tiltedY * sin(rotation)) * width).toFloat(),
            (x * sin(rotation) + tiltedY * cos(rotation)).toFloat(), depth.toFloat(), index.toFloat() / samples)
    }
    val maxX = points.maxOf { kotlin.math.abs(it.x) }
    val maxY = points.maxOf { kotlin.math.abs(it.y) }
    val extent = maxOf(maxX, maxY)
    return points.map { it.copy(x = it.x / extent, y = it.y / extent) }
}

private data class KnotCrossing(val rope: Int, val index: Int, val depth: Float, val x: Float, val y: Float)

private fun findCrossings(ropes: List<List<KnotPoint>>): List<KnotCrossing> {
    val found = mutableListOf<KnotCrossing>()
    ropes.forEachIndexed { ropeA, a ->
        ropes.forEachIndexed { ropeB, b ->
            if (ropeB < ropeA) return@forEachIndexed
            for (i in 0 until a.lastIndex) {
                for (j in 0 until b.lastIndex) {
                    if (ropeA == ropeB && (j <= i + 12 || j - i > a.lastIndex - 12)) continue
                    val ax = a[i + 1].x - a[i].x
                    val ay = a[i + 1].y - a[i].y
                    val bx = b[j + 1].x - b[j].x
                    val by = b[j + 1].y - b[j].y
                    val determinant = ax * by - ay * bx
                    if (abs(determinant) < .0000001f) continue
                    val dx = b[j].x - a[i].x
                    val dy = b[j].y - a[i].y
                    val t = (dx * by - dy * bx) / determinant
                    val u = (dx * ay - dy * ax) / determinant
                    if (t !in 0f..1f || u !in 0f..1f) continue
                    val depthA = a[i].depth + t * (a[i + 1].depth - a[i].depth)
                    val depthB = b[j].depth + u * (b[j + 1].depth - b[j].depth)
                    val x = a[i].x + t * ax
                    val y = a[i].y + t * ay
                    val crossing = if (depthA > depthB) KnotCrossing(ropeA, i, depthA, x, y) else KnotCrossing(ropeB, j, depthB, x, y)
                    if (found.none { it.rope == crossing.rope && abs(it.index - crossing.index) < 4 }) found += crossing
                }
            }
        }
    }
    return found.sortedBy { it.depth }
}

@Composable
fun SignatureKnot(
    seed: KnotSeed,
    modifier: Modifier = Modifier,
    reveal: Boolean = true,
    interlockedWith: KnotSeed? = null,
    contentDescription: String = if (interlockedWith == null) "나의 붉은 시그니처 매듭" else "서로 엮인 두 시그니처 매듭",
) {
    val motion = LocalDecorativeMotion.current
    val progress = remember { Animatable(1f) }
    LaunchedEffect(seed, interlockedWith, reveal, motion) {
        if (reveal && motion) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(ChayeonMotion.Reveal, easing = LinearEasing))
        } else progress.snapTo(1f)
    }
    val ropes = remember(seed, interlockedWith) {
        if (interlockedWith == null) listOf(knotGeometry(seed).map { it.copy(x = it.x * .38f, y = it.y * .38f) })
        else listOf(
            knotGeometry(seed).map { it.copy(x = it.x * .38f - .231f, y = it.y * .38f) },
            knotGeometry(interlockedWith).map { it.copy(x = it.x * .38f + .231f, y = it.y * .38f, depth = -it.depth) },
        )
    }
    val crossings = remember(ropes) { findCrossings(ropes) }
    Canvas(modifier.size(if (interlockedWith == null) ChayeonDimensions.Knot else ChayeonDimensions.ChemistryKnot, ChayeonDimensions.Knot).semantics { this.contentDescription = contentDescription }) {
        val scale = size.minDimension
        val thickness = scale * .056f
        fun path(points: List<KnotPoint>): Path = Path().apply {
            points.forEachIndexed { index, point ->
                val x = center.x + point.x * scale
                val y = center.y + point.y * scale
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawOval(Brush.radialGradient(listOf(ChayeonColors.Glow.copy(alpha = .18f), ChayeonColors.Transparent), center, scale * .51f))
        ropes.forEach { points ->
            val line = path(points.filter { it.progress <= progress.value })
            translate(top = thickness * .48f) {
                drawPath(line, ChayeonColors.Black.copy(alpha = .16f), style = Stroke(thickness * 1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(line, ChayeonColors.Black.copy(alpha = .20f), style = Stroke(thickness * 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        ropes.forEach { points -> drawRope(path(points.filter { it.progress <= progress.value }), thickness) }
        // Only the short overpass is repainted, so long curves retain uninterrupted reflections.
        crossings.forEach { crossing ->
            val points = ropes[crossing.rope]
            val span = 42
            val bridge = ((crossing.index - span)..(crossing.index + span)).map {
                points[Math.floorMod(it, points.lastIndex)]
            }.filter { it.progress <= progress.value }
            if (bridge.size > 1) {
                val centerX = center.x + crossing.x * scale
                val centerY = center.y + crossing.y * scale
                val radius = thickness * 1.65f
                val area = Path().apply { addOval(Rect(centerX - radius, centerY - radius, centerX + radius, centerY + radius)) }
                clipPath(area) { drawRope(path(bridge), thickness) }
            }
        }
    }
}

private fun DrawScope.drawRope(path: Path, thickness: Float) {
    fun stroke(color: Color, width: Float, offset: Float = 0f) {
        translate(left = -thickness * .12f * offset, top = -thickness * .20f * offset) {
            drawPath(path, color, style = Stroke(thickness * width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
    stroke(ChayeonColors.KnotBase, 1.08f)
    stroke(ChayeonColors.KnotShadow, 1f)
    for (layer in 0..16) {
        val fraction = layer / 16f
        stroke(lerp(ChayeonColors.KnotBody, ChayeonColors.BrightRed, fraction), .94f - fraction * .65f, fraction * 1.1f)
    }
    stroke(ChayeonColors.KnotSpecular.copy(alpha = .78f), .10f, 1.6f)
}
