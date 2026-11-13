package dev.chayeon.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ChayeonBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(ChayeonColors.Background).drawBehind {
        data class Bloom(val center: Offset, val horizontal: Float, val vertical: Float, val color: Color)
        val blooms = listOf(
            Bloom(Offset(size.width * .82f, size.height * .04f), size.width * .6875f, size.height * .484375f, ChayeonColors.BloomTop),
            Bloom(Offset(size.width * .04f, size.height * .58f), size.width * .5625f, size.height * .484375f, ChayeonColors.BloomLeft),
            Bloom(Offset(size.width * .60f, size.height * 1.04f), size.width * .625f, size.height * .375f, ChayeonColors.BloomBottom),
        )
        blooms.forEach { bloom ->
            withTransform({
                translate(bloom.center.x, bloom.center.y)
                scale(bloom.horizontal, bloom.vertical, pivot = Offset.Zero)
            }) {
                drawCircle(Brush.radialGradient(0f to bloom.color, .62f to ChayeonColors.Transparent, center = Offset.Zero, radius = 1f), radius = 1f, center = Offset.Zero)
            }
        }
    }, content = content)
}

internal fun Modifier.glassSurface(hero: Boolean = false, radius: Dp = ChayeonRadii.Card): Modifier {
    val shape = RoundedCornerShape(radius)
    val colors = if (hero) listOf(ChayeonColors.Wine, ChayeonColors.Ink, ChayeonColors.HeroEnd)
        else listOf(ChayeonColors.White.copy(alpha = .12f), ChayeonColors.GlassMiddle, ChayeonColors.GlassBottom)
    return shadow(if (hero) ChayeonElevation.Hero else ChayeonElevation.Card, shape, clip = false)
        .clip(shape)
        .background(Brush.linearGradient(colors))
        .border(ChayeonElevation.Border, ChayeonColors.Border.copy(alpha = if (hero) .30f else .24f), shape)
        .drawBehind {
            val r = radius.toPx()
            val highlight = Path().apply {
                moveTo(0f, r)
                quadraticTo(0f, 0f, r, 0f)
                lineTo(size.width - r, 0f)
                quadraticTo(size.width, 0f, size.width, r)
            }
            drawPath(highlight, Brush.verticalGradient(listOf(ChayeonColors.White.copy(alpha = .20f), ChayeonColors.Transparent), endY = r), style = Stroke(ChayeonElevation.Highlight.toPx()))
        }
}

internal fun Modifier.gelSurface(
    variant: JellyVariant,
    radius: Dp = ChayeonRadii.Button,
): Modifier {
    val shape = RoundedCornerShape(radius)
    val colors = when (variant) {
        JellyVariant.PRIMARY -> listOf(ChayeonColors.BrightRed, ChayeonColors.Red, ChayeonColors.DeepRed)
        JellyVariant.SECONDARY -> listOf(ChayeonColors.White.copy(alpha = .18f), ChayeonColors.GlassButtonBottom)
        JellyVariant.WHITE -> listOf(ChayeonColors.White, ChayeonColors.WhiteBottom)
        JellyVariant.DANGER -> listOf(ChayeonColors.Black.copy(alpha = .35f), ChayeonColors.Black.copy(alpha = .35f))
    }
    val border = when (variant) {
        JellyVariant.PRIMARY -> ChayeonColors.ButtonBorder
        JellyVariant.SECONDARY -> ChayeonColors.GlassBorder
        JellyVariant.WHITE -> ChayeonColors.White.copy(alpha = .8f)
        JellyVariant.DANGER -> ChayeonColors.DangerBorder
    }
    return shadow(ChayeonElevation.Button, shape, clip = false,
        ambientColor = if (variant == JellyVariant.PRIMARY) ChayeonColors.Glow else ChayeonColors.Black,
        spotColor = if (variant == JellyVariant.PRIMARY) ChayeonColors.Glow else ChayeonColors.Black)
        .clip(shape)
        .background(Brush.verticalGradient(colors))
        .drawBehind {
            if (variant != JellyVariant.DANGER) {
                val r = radius.toPx()
                val corner = CornerRadius(r)
                val bottomColor = when (variant) {
                    JellyVariant.WHITE -> ChayeonColors.WhiteInset.copy(alpha = .24f)
                    JellyVariant.SECONDARY -> ChayeonColors.Black.copy(alpha = .25f)
                    else -> ChayeonColors.GelShadow
                }
                drawRoundRect(Brush.verticalGradient(
                    0f to ChayeonColors.Transparent, .66f to ChayeonColors.Transparent, 1f to bottomColor,
                ), cornerRadius = corner)
                val top = Path().apply {
                    moveTo(1f, r)
                    quadraticTo(1f, 1.5f, r, 1.5f)
                    lineTo(size.width - r, 1.5f)
                    quadraticTo(size.width - 1f, 1.5f, size.width - 1f, r)
                }
                drawPath(top, Brush.verticalGradient(listOf(ChayeonColors.White.copy(alpha = if (variant == JellyVariant.PRIMARY) .45f else .28f), ChayeonColors.Transparent), endY = r), style = Stroke(ChayeonElevation.GelHighlight.toPx()))
                drawRoundRect(Brush.verticalGradient(listOf(ChayeonColors.White.copy(alpha = .07f), ChayeonColors.Transparent), endY = size.height * .42f), cornerRadius = corner)
            }
        }
        .border(ChayeonElevation.Border, border, shape)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = ChayeonSpacing.CardHorizontal, vertical = ChayeonSpacing.Large),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.glassSurface().padding(contentPadding), content = content)
}

@Composable
fun HeroPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(ChayeonSpacing.Hero),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.glassSurface(hero = true).padding(contentPadding), content = content)
}
