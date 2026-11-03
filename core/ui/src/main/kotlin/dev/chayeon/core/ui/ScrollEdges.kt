package dev.chayeon.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/** Place before verticalScroll so the fade belongs to the viewport, not the moving content. */
fun Modifier.scrollEdgeFade(state: ScrollState, length: Dp = ChayeonDimensions.ScrollFade): Modifier =
    scrollEdgeFade({ state.canScrollBackward }, { state.canScrollForward }, length)

/** Attach directly to the LazyColumn viewport. */
fun Modifier.scrollEdgeFade(state: LazyListState, length: Dp = ChayeonDimensions.ScrollFade): Modifier =
    scrollEdgeFade({ state.canScrollBackward }, { state.canScrollForward }, length)

private fun Modifier.scrollEdgeFade(
    backward: () -> Boolean,
    forward: () -> Boolean,
    length: Dp,
): Modifier = graphicsLayer {
    compositingStrategy = if (backward() || forward()) CompositingStrategy.Offscreen else CompositingStrategy.Auto
}.drawWithCache {
    val height = length.toPx().coerceIn(0f, size.height / 2f)
    val top = Brush.verticalGradient(listOf(ChayeonColors.Transparent, ChayeonColors.Black), endY = height)
    val bottom = Brush.verticalGradient(listOf(ChayeonColors.Black, ChayeonColors.Transparent), startY = size.height - height, endY = size.height)
    onDrawWithContent {
        drawContent()
        if (height > 0f) {
            if (backward()) drawRect(top, size = Size(size.width, height), blendMode = BlendMode.DstIn)
            if (forward()) drawRect(bottom, topLeft = Offset(0f, size.height - height), size = Size(size.width, height), blendMode = BlendMode.DstIn)
        }
    }
}
