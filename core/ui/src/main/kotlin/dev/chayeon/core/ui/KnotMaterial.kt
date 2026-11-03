package dev.chayeon.core.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import kotlin.math.*

/** A bounded, cached tube raster: continuous depth replaces clipped crossing patches. */
internal object KnotMaterial {
    const val Radius = .034f
    const val RevealWidth = .085f
    const val MaximumRasterSide = 960
    val Crimson = listOf(ChayeonColors.KnotBase, ChayeonColors.KnotShadow, ChayeonColors.Red, ChayeonColors.KnotLight)
    val Pearl = listOf(ChayeonColors.WhiteInset, ChayeonColors.WhiteOrbBottom, ChayeonColors.WhiteBottom, ChayeonColors.White)
}

internal fun renderKnot(ropes: List<List<KnotPoint>>, width: Int, height: Int): Bitmap {
    // Supersampling smooths small cards; the upper bound also caps head-unit memory and setup work.
    val sampling = minOf(if (maxOf(width, height) <= 180) 2f else 1.5f, KnotMaterial.MaximumRasterSide.toFloat() / maxOf(width, height).coerceAtLeast(1))
    val w = (width * sampling).roundToInt().coerceAtLeast(1)
    val h = (height * sampling).roundToInt().coerceAtLeast(1)
    val scale = minOf(w, h).toFloat()
    val count = w * h
    val depths = FloatArray(count) { Float.NEGATIVE_INFINITY }
    val nx = FloatArray(count)
    val ny = FloatArray(count)
    val nz = FloatArray(count)
    val strands = ByteArray(count)
    val pixels = IntArray(count)
    val radius = scale * KnotMaterial.Radius
    ropes.forEachIndexed { strand, points ->
        val ringSize = 40
        val vertices = Array(points.lastIndex * ringSize) { FloatArray(6) }
        for (index in 0 until points.lastIndex) {
            val point = points[index]
            val previous = points[Math.floorMod(index - 1, points.lastIndex)]
            val next = points[(index + 1) % points.lastIndex]
            val tx = (next.x - previous.x) * scale
            val ty = (next.y - previous.y) * scale
            val tz = (next.depth - previous.depth) * scale * .14f
            val planar = sqrt(tx * tx + ty * ty).coerceAtLeast(.0001f)
            val length = sqrt(tx * tx + ty * ty + tz * tz)
            val sideX = -ty / planar
            val sideY = tx / planar
            val facingX = -tz * tx / (planar * length)
            val facingY = -tz * ty / (planar * length)
            val facingZ = planar / length
            val r = radius * (1f + point.depth * .05f)
            for (around in 0 until ringSize) {
                val theta = around.toFloat() / ringSize * PI.toFloat() * 2
                val c = cos(theta); val sn = sin(theta)
                val vertex = vertices[index * ringSize + around]
                vertex[3] = sideX * c + facingX * sn
                vertex[4] = sideY * c + facingY * sn
                vertex[5] = facingZ * sn
                vertex[0] = w / 2f + point.x * scale + vertex[3] * r
                vertex[1] = h / 2f + point.y * scale + vertex[4] * r
                vertex[2] = point.depth * scale * .14f + vertex[5] * r
            }
        }
        fun triangle(a: FloatArray, b: FloatArray, c: FloatArray) {
            if (a[5] < 0f && b[5] < 0f && c[5] < 0f) return
            val denominator = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if (abs(denominator) < .00001f) return
            val inverse = 1f / denominator
            val x0 = floor(minOf(a[0], b[0], c[0])).toInt().coerceAtLeast(0)
            val x1 = ceil(maxOf(a[0], b[0], c[0])).toInt().coerceAtMost(w - 1)
            val y0 = floor(minOf(a[1], b[1], c[1])).toInt().coerceAtLeast(0)
            val y1 = ceil(maxOf(a[1], b[1], c[1])).toInt().coerceAtMost(h - 1)
            for (y in y0..y1) for (x in x0..x1) {
                val u = ((b[1] - c[1]) * (x + .5f - c[0]) + (c[0] - b[0]) * (y + .5f - c[1])) * inverse
                val v = ((c[1] - a[1]) * (x + .5f - c[0]) + (a[0] - c[0]) * (y + .5f - c[1])) * inverse
                val q = 1f - u - v
                if (u < -.00001f || v < -.00001f || q < -.00001f) continue
                val depth = a[2] * u + b[2] * v + c[2] * q
                val pixel = y * w + x
                if (depth <= depths[pixel]) continue
                depths[pixel] = depth
                nx[pixel] = a[3] * u + b[3] * v + c[3] * q
                ny[pixel] = a[4] * u + b[4] * v + c[4] * q
                nz[pixel] = a[5] * u + b[5] * v + c[5] * q
                strands[pixel] = strand.toByte()
            }
        }
        for (index in 0 until points.lastIndex) for (around in 0 until ringSize) {
            val next = (index + 1) % points.lastIndex
            val across = (around + 1) % ringSize
            val a = vertices[index * ringSize + around]
            val b = vertices[next * ringSize + around]
            val c = vertices[next * ringSize + across]
            val d = vertices[index * ringSize + across]
            triangle(a, b, c)
            triangle(a, c, d)
        }
    }
    val palettes = listOf(KnotMaterial.Crimson, KnotMaterial.Pearl).map { palette -> palette.map { it.toArgb() }.toIntArray() }
    for (y in 0 until h) for (x in 0 until w) {
        val i = y * w + x
        if (!depths[i].isFinite()) continue
        val pearl = strands[i].toInt() == 1
        val normalX = nx[i]; val normalY = ny[i]; val normalZ = nz[i]
        val diffuse = (-.38f * normalX - .60f * normalY + .70f * normalZ).coerceIn(0f, 1f)
        // Two broad studio lights create changing reflections on the turning surface.
        val key = (-.25f * normalX - .36f * normalY + .899f * normalZ).coerceIn(0f, 1f)
        val key2 = key * key
        val key4 = key2 * key2
        val key8 = key4 * key4
        val key16 = key8 * key8
        val reflection = (key16 * key16 * key8 * key2) * (.50f + .50f * (1f - x.toFloat() / w))
        val softbox = key4 * key2 * key * .18f
        val rim = (.65f * normalX + .15f * normalY + .74f * normalZ).coerceIn(0f, 1f).pow(18) * .13f
        var occlusion = 0f
        for (step in 1..24) {
            val reach = radius * step * .07f
            val sx = (x - reach * .60f).roundToInt()
            val sy = (y - reach).roundToInt()
            if (sx in 0 until w && sy in 0 until h) {
                val higher = depths[sy * w + sx] - depths[i]
                occlusion += ((higher - radius * .9f) / (radius * 1.2f)).coerceIn(0f, 1f) * .022f
            }
        }
        val palette = palettes[if (pearl) 1 else 0]
        val tone = (.12f + diffuse * .88f) * 3f
        val band = tone.toInt().coerceIn(0, 2)
        val blend = (tone - band).coerceIn(0f, 1f)
        val gloss = (reflection * (if (pearl) .65f else .84f) + softbox + rim).coerceAtMost(.92f) * (1f - occlusion)
        fun channel(shift: Int): Int {
            val a = (palette[band] ushr shift) and 255
            val b = (palette[band + 1] ushr shift) and 255
            val body = (a + (b - a) * blend) * (1f - occlusion)
            return (body + (255f - body) * gloss).roundToInt().coerceIn(0, 255)
        }
        pixels[i] = (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}
