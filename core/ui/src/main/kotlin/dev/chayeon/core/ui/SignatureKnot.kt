package dev.chayeon.core.ui

import androidx.core.graphics.withSave
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.chayeon.core.model.KnotSeed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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

@Composable
fun SignatureKnot(
    seed: KnotSeed,
    modifier: Modifier = Modifier,
    reveal: Boolean = true,
    interlockedWith: KnotSeed? = null,
    contentDescription: String = if (interlockedWith == null) "나의 붉은 시그니처 매듭" else "나의 붉은 실과 상대의 진주빛 흰 실로 엮은 두 사람의 시그니처 매듭",
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
    Box(modifier.size(if (interlockedWith == null) ChayeonDimensions.Knot else ChayeonDimensions.ChemistryKnot, ChayeonDimensions.Knot)
        .semantics { this.contentDescription = contentDescription }
        .drawWithCache {
            // Geometry, lighting and path measures change only with the seed or measured size.
            val center = Offset(size.width / 2f, size.height / 2f)
            val scale = size.minDimension
            val raster = renderKnot(ropes, size.width.roundToInt(), size.height.roundToInt())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val destination = android.graphics.RectF(0f, 0f, size.width, size.height)
            val measures = ropes.map { points ->
                val path = AndroidPath()
                points.forEachIndexed { index, point ->
                    val x = center.x + point.x * scale
                    val y = center.y + point.y * scale
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                PathMeasure(path, false)
            }
            val revealPath = AndroidPath()
            val outline = AndroidPath()
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = scale * KnotMaterial.RevealWidth
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val shadowSize = Size(scale * (if (ropes.size == 1) .76f else 1.25f), scale * .16f)
            val shadowCenter = Offset(center.x, center.y + scale * .39f)
            val shadow = Brush.radialGradient(listOf(ChayeonColors.Black.copy(alpha = .38f), ChayeonColors.Transparent), Offset.Zero, 1f)
            onDrawBehind {
                // Disabling motion takes effect in this draw, without waiting for an effect frame.
                val fraction = if (!motion || !reveal) 1f else progress.value
                withTransform({
                    translate(shadowCenter.x, shadowCenter.y)
                    scale(shadowSize.width / 2, shadowSize.height / 2, Offset.Zero)
                }) { drawCircle(shadow, 1f, Offset.Zero, alpha = fraction) }
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.withSave {
                        if (fraction < 1f) {
                            revealPath.rewind()
                            measures.forEach { it.getSegment(0f, it.length * fraction, revealPath, true) }
                            maskPaint.getFillPath(revealPath, outline)
                            native.clipPath(outline)
                        }
                        native.drawBitmap(raster, null, destination, paint)
                    }
                }
            }
        })
}
