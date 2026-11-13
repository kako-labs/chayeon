package dev.chayeon.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.chayeon.core.model.DrivingPolicy

enum class Scene(@DrawableRes internal val drawable: Int?) {
    PARKING(R.drawable.bg_parking), ROAD(R.drawable.bg_road), HOME(R.drawable.bg_home),
    DAWN(R.drawable.bg_dawn), PLAIN(null),
}

enum class SceneScrim { DEFAULT, RIGHT, CALL }

val LocalScene = staticCompositionLocalOf { Scene.PARKING }
val LocalSceneScrim = staticCompositionLocalOf { SceneScrim.DEFAULT }
val LocalBrandInChrome = staticCompositionLocalOf { false }

@Composable
fun SceneBackground(
    scene: Scene,
    modifier: Modifier = Modifier,
    dimmed: Boolean = LocalDrivingPolicy.current != DrivingPolicy.ALLOWED,
    scrim: SceneScrim = SceneScrim.DEFAULT,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.background(ChayeonColors.Background)) {
        scene.drawable?.let {
            Image(painterResource(it), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop,
                alpha = when { dimmed -> .6f; scrim == SceneScrim.CALL -> .55f; else -> 1f })
        }
        Box(Modifier.matchParentSize().drawWithCache {
            val base = ChayeonColors.Background
            val overlay = when {
                dimmed -> Brush.verticalGradient(0f to base.copy(alpha = .55f), .4f to base.copy(alpha = .35f), .78f to base.copy(alpha = .9f), 1f to base)
                scrim == SceneScrim.CALL -> Brush.radialGradient(0f to ChayeonColors.BloomTop.copy(alpha = .35f), .7f to base.copy(alpha = .82f), 1f to base,
                    center = Offset(size.width * .5f, size.height * .38f), radius = size.width * .7f)
                scrim == SceneScrim.RIGHT -> Brush.horizontalGradient(0f to base.copy(alpha = .35f), .38f to base.copy(alpha = .6f), .6f to base.copy(alpha = .9f), 1f to base.copy(alpha = .94f))
                scene == Scene.HOME -> Brush.horizontalGradient(0f to base.copy(alpha = .93f), .45f to base.copy(alpha = .72f), .75f to base.copy(alpha = .2f), 1f to base.copy(alpha = .4f))
                scene == Scene.DAWN -> Brush.horizontalGradient(0f to base.copy(alpha = .55f), .35f to base.copy(alpha = .45f), .52f to base.copy(alpha = .88f), 1f to base.copy(alpha = .94f))
                else -> Brush.horizontalGradient(0f to base.copy(alpha = .92f), .42f to base.copy(alpha = .70f), .72f to base.copy(alpha = .15f), 1f to base.copy(alpha = .35f))
            }
            val bottomHeight = if (scene == Scene.HOME) 420.dp.toPx() else ChayeonDimensions.SceneBottomScrim.toPx()
            val bottom = Brush.verticalGradient(0f to ChayeonColors.Transparent, .6f to base.copy(alpha = if (scene == Scene.HOME) .9f else .85f), 1f to base,
                startY = (size.height - bottomHeight).coerceAtLeast(0f), endY = size.height)
            onDrawBehind {
                if (scene != Scene.PLAIN) {
                    drawRect(overlay)
                    if (!dimmed && scrim == SceneScrim.DEFAULT && scene != Scene.DAWN) drawRect(bottom)
                }
            }
        })
        content()
    }
}
