package dev.chayeon.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.chayeon.core.model.AvatarId

enum class MochiAvatar(val id: AvatarId, @DrawableRes val drawable: Int, val description: String) {
    DAWN(AvatarId.DAWN, R.drawable.av_dawn, AvatarId.DAWN.label),
    SLOW(AvatarId.SLOW, R.drawable.av_slow, AvatarId.SLOW.label),
    JAZZ(AvatarId.JAZZ, R.drawable.av_jazz, AvatarId.JAZZ.label),
    SEA(AvatarId.SEA, R.drawable.av_sea, AvatarId.SEA.label),
    FOREST(AvatarId.FOREST, R.drawable.av_forest, AvatarId.FOREST.label),
    STAR(AvatarId.STAR, R.drawable.av_star, AvatarId.STAR.label);

    companion object {
        fun from(id: AvatarId): MochiAvatar = when (id) {
            AvatarId.DAWN -> DAWN
            AvatarId.SLOW -> SLOW
            AvatarId.JAZZ -> JAZZ
            AvatarId.SEA -> SEA
            AvatarId.FOREST -> FOREST
            AvatarId.STAR -> STAR
        }
    }
}

enum class ThreadStage(@DrawableRes val drawable: Int, val description: String) {
    REACH(R.drawable.thread_reach, "실이 뻗어요 · 요청 대기"),
    CONNECTED(R.drawable.thread_taut, "실이 이어졌어요 · 일시 연결"),
    ENDED(R.drawable.thread_cut, "실이 끊어졌어요 · 연결 종료"),
    AFTER_PENDING(R.drawable.thread_wait, "구슬이 기다려요 · 애프터 대기"),
    AFTER_CONNECTED(R.drawable.thread_knot, "매듭이 묶였어요 · 이어진 인연"),
}

object ChayeonAssets {
    @DrawableRes val KeyVisual = R.drawable.kv
    @DrawableRes val Logo = R.drawable.logo
    @DrawableRes val Ribbon = R.drawable.thread_ribbon
}

@Composable
fun MochiAvatar(avatar: MochiAvatar, modifier: Modifier = Modifier, floating: Boolean = true, decorative: Boolean = false) {
    val offset = if (floating && LocalDecorativeMotion.current) {
        val transition = rememberInfiniteTransition(label = "mochi float")
        val value by transition.animateFloat(-3f, 3f, infiniteRepeatable(tween(ChayeonMotion.Flow, easing = LinearEasing), RepeatMode.Reverse), label = "mochi lift")
        value
    } else 0f
    Image(painterResource(avatar.drawable), if (decorative) null else avatar.description,
        modifier.aspectRatio(1f).graphicsLayer { translationY = offset.dp.toPx() }, contentScale = ContentScale.Fit)
}

@Composable
fun AvatarDish(avatar: MochiAvatar, modifier: Modifier = Modifier, decorative: Boolean = false) {
    Box(modifier.size(ChayeonDimensions.Avatar).drawBehind {
        drawCircle(Brush.radialGradient(listOf(ChayeonColors.Glow.copy(alpha = .25f), ChayeonColors.Transparent), radius = size.width * .68f), radius = size.width * .68f)
    }.shadow(ChayeonElevation.Avatar, CircleShape, clip = false)
        .drawBehind {
            drawCircle(Brush.radialGradient(listOf(ChayeonColors.DishTop, ChayeonColors.DishMiddle, ChayeonColors.DishBottom),
                center = Offset(size.width * .5f, size.height * .38f), radius = size.width * .7f))
        }
        .border(ChayeonElevation.StrongBorder, ChayeonColors.ButtonBorder.copy(alpha = .55f), CircleShape)) {
        MochiAvatar(avatar, Modifier.matchParentSize(), floating = false, decorative = decorative)
    }
}
