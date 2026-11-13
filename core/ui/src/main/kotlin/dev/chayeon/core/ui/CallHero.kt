package dev.chayeon.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class MascotPose(@DrawableRes internal val drawable: Int, val description: String) {
    WAVE(R.drawable.mochi_wave, "손을 흔드는 모찌"),
    THREAD(R.drawable.mochi_thread, "붉은 실을 건네는 모찌"),
    CALL(R.drawable.mochi_call, "통화하는 모찌"),
    SLEEP(R.drawable.mochi_sleep, "실을 베고 잠든 모찌"),
    BYE(R.drawable.mochi_bye, "손을 흔들며 인사하는 모찌"),
    KNOT(R.drawable.mochi_knot, "매듭을 선물하는 모찌"),
}

private fun Modifier.characterShadow(sleeping: Boolean = false): Modifier = drawBehind {
    val color = if (sleeping) ChayeonColors.Black else ChayeonColors.Red
    val center = Offset(size.width * .5f, size.height * .76f)
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .45f), ChayeonColors.Transparent), center,
        size.minDimension * .55f), radius = size.minDimension * .55f, center = center)
}

@Composable
fun Mascot(pose: MascotPose, modifier: Modifier = Modifier) {
    Image(painterResource(pose.drawable), pose.description,
        modifier.size(ChayeonDimensions.SceneMascot).characterShadow(pose == MascotPose.SLEEP), contentScale = ContentScale.Fit)
}

@Composable
fun CallAvatar(avatar: MochiAvatar, mirrored: Boolean, modifier: Modifier = Modifier) {
    val drawable = when (avatar) {
        MochiAvatar.DAWN -> R.drawable.av_dawn_call
        MochiAvatar.SLOW -> R.drawable.av_slow_call
        MochiAvatar.JAZZ -> R.drawable.av_jazz_call
        MochiAvatar.SEA -> R.drawable.av_sea_call
        MochiAvatar.FOREST -> R.drawable.av_forest_call
        MochiAvatar.STAR -> R.drawable.av_star_call
    }
    Image(painterResource(drawable), "통화하는 ${avatar.description}",
        modifier.size(ChayeonDimensions.SceneCallAvatar).characterShadow().graphicsLayer { scaleX = if (mirrored) -1f else 1f },
        contentScale = ContentScale.Fit)
}

@Immutable
data class CallParticipant(val avatar: MochiAvatar, val name: String, val title: String? = null)

@Composable
fun CallHero(me: CallParticipant, partner: CallParticipant, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val avatarSize = minOf(ChayeonDimensions.SceneCallAvatar, maxWidth * (360f / 1570f))
        val imageTop = 12.dp
        val left = maxWidth * (200f / 1570f)
        val right = maxWidth * (1010f / 1570f)
        Column {
            Box(Modifier.fillMaxWidth().height(avatarSize + imageTop)) {
                Canvas(Modifier.matchParentSize()) {
                    val start = Offset(left.toPx() + avatarSize.toPx() * .85f, imageTop.toPx() + avatarSize.toPx() * .70f)
                    val end = Offset(right.toPx() + avatarSize.toPx() * .15f, start.y)
                    val sag = avatarSize.toPx() * (96f / 360f)
                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        cubicTo(start.x + (end.x - start.x) * .276f, start.y + sag,
                            end.x - (end.x - start.x) * .276f, end.y + sag, end.x, end.y)
                    }
                    drawPath(path, ChayeonColors.DeepRed, style = Stroke(ChayeonDimensions.ThreadDarkStroke.toPx(), cap = StrokeCap.Round))
                    drawPath(path, ChayeonColors.BrightRed, style = Stroke(ChayeonDimensions.ThreadBrightStroke.toPx(), cap = StrokeCap.Round))
                    drawPath(path, ChayeonColors.ThreadHighlight, style = Stroke(ChayeonDimensions.ThreadHighlightStroke.toPx(), cap = StrokeCap.Round))
                }
                CallAvatar(me.avatar, mirrored = false, modifier = Modifier.offset(x = left, y = imageTop).size(avatarSize))
                CallAvatar(partner.avatar, mirrored = true, modifier = Modifier.offset(x = right, y = imageTop).size(avatarSize))
            }
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(200f))
                CallName(me.name, "나", Modifier.weight(360f))
                Spacer(Modifier.weight(450f))
                CallName(partner.name, partner.title, Modifier.weight(360f))
                Spacer(Modifier.weight(200f))
            }
        }
    }
}

@Composable
private fun CallName(name: String, title: String?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ChayeonText(name, style = ChayeonType.SceneName.copy(textAlign = TextAlign.Center))
        title?.let { ChayeonText(it, style = ChayeonType.Minimum.copy(textAlign = TextAlign.Center), color = ChayeonColors.InfoText) }
    }
}
