package dev.chayeon.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun ThreadView(stage: ThreadStage, modifier: Modifier = Modifier, animateTransition: Boolean = true, decorative: Boolean = false) {
    val motion = LocalDecorativeMotion.current
    val progress = remember { Animatable(1f) }
    LaunchedEffect(stage, motion, animateTransition) {
        if (motion && animateTransition) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(when (stage) {
                ThreadStage.ENDED -> ChayeonMotion.End
                ThreadStage.AFTER_CONNECTED -> ChayeonMotion.After
                else -> ChayeonMotion.Connect
            }, easing = LinearEasing))
        } else progress.snapTo(1f)
    }
    val flow = if (motion && stage == ThreadStage.CONNECTED) {
        val transition = rememberInfiniteTransition(label = "thread flow")
        val value by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(ChayeonMotion.Flow, easing = LinearEasing)), label = "thread light")
        value
    } else null
    Box(modifier.aspectRatio(5f).semantics {
        if (!decorative) contentDescription = stage.description
    }) {
        if (stage == ThreadStage.AFTER_CONNECTED && progress.value < 1f) {
            Image(painterResource(ThreadStage.AFTER_PENDING.drawable), null,
                Modifier.fillMaxSize().graphicsLayer {
                    val gather = (progress.value / .64f).coerceIn(0f, 1f)
                    scaleX = 1f - .82f * gather
                    scaleY = 1f - .36f * gather
                    alpha = (1f - ((progress.value - .45f) / .35f)).coerceIn(0f, 1f)
                }, contentScale = ContentScale.FillBounds)
        }
        Image(painterResource(stage.drawable), null,
            Modifier.fillMaxSize().graphicsLayer {
                if (stage == ThreadStage.AFTER_CONNECTED) {
                    val tying = ((progress.value - .45f) / .55f).coerceIn(0f, 1f)
                    scaleX = .58f + .42f * tying
                    scaleY = .58f + .42f * tying
                    alpha = tying
                }
            }.drawWithContent {
                when (stage) {
                    ThreadStage.CONNECTED, ThreadStage.REACH -> clipRect(right = size.width * progress.value) { this@drawWithContent.drawContent() }
                    ThreadStage.ENDED -> {
                        val retreat = size.width * .045f * (1f - progress.value)
                        clipRect(right = size.width * .5f) {
                            translate(left = retreat) { this@drawWithContent.drawContent() }
                        }
                        clipRect(left = size.width * .5f) {
                            translate(left = -retreat) { this@drawWithContent.drawContent() }
                        }
                    }
                    else -> drawContent()
                }
            }, contentScale = ContentScale.FillBounds)
        if (stage == ThreadStage.ENDED && progress.value < .36f) {
            Image(painterResource(ThreadStage.CONNECTED.drawable), null,
                Modifier.fillMaxSize().graphicsLayer { alpha = (1f - progress.value / .36f).coerceIn(0f, 1f) },
                contentScale = ContentScale.FillBounds)
        }
        if (flow != null) Canvas(Modifier.fillMaxSize()) {
            val phase = if (progress.value < 1f) progress.value else flow
            val x = size.width * (.015f + .97f * phase)
            val y = size.height * (.5f - .20f * sin(phase * PI * 4).toFloat())
            drawCircle(Brush.radialGradient(listOf(ChayeonColors.White.copy(alpha = .9f), ChayeonColors.Accent.copy(alpha = .4f), ChayeonColors.Transparent), Offset(x, y), size.height * .10f), size.height * .10f, Offset(x, y))
        }
    }
}
