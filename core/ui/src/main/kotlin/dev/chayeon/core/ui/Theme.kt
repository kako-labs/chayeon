package dev.chayeon.core.ui

import android.icu.text.BreakIterator
import java.util.Locale
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chayeon.core.model.DrivingPolicy

object ChayeonColors {
    val SceneGlass = Color(0xFF1A0408).copy(alpha = .62f)
    val SceneGlassTop = Color(0xFF1A0408).copy(alpha = .66f)
    val SceneBorder = Color(0xFFFFC8D0).copy(alpha = .24f)
    val OutlineBorder = Color(0xFFFFC8D0).copy(alpha = .45f)
    val MutedText = Color(0xFFF3D4D9).copy(alpha = .80f)
    val InfoText = Color(0xFFF3D4D9).copy(alpha = .70f)
    val NavigationText = Color(0xFFFFF5F6).copy(alpha = .75f)
    val SelectedTop = Color(0xFFFF4A5E).copy(alpha = .35f)
    val SelectedBottom = Color(0xFFA50A23).copy(alpha = .35f)
    val SelectedBorder = Color(0xFFFF9AA6).copy(alpha = .55f)
    val ScoreSurface = Color(0xFFFFF5F6).copy(alpha = .08f)
    val ThreadHighlight = Color(0xFFFFD2D8).copy(alpha = .55f)

    val Background = Color(0xFF070304)
    val Wine = Color(0xFF3A0710)
    val DeepRed = Color(0xFFA50A23)
    val Red = Color(0xFFD5102F)
    val BrightRed = Color(0xFFFF4A5E)
    val Accent = Color(0xFFFF9AA6)
    val Secondary = Color(0xFFF3D4D9)
    val Black = Color.Black
    val Transparent = Color.Transparent
    val White = Color.White
    val Text = Color(0xFFFFF5F6)
    val WhiteBottom = Color(0xFFF0E4E6)
    val Ink = Color(0xFF1A0408)
    val Pink = Color(0xFFFFC2CB)
    val HeroEnd = Color(0xFF0C0305)
    val BloomTop = Color(0xFF5A0714)
    val BloomLeft = Color(0xFF33050D)
    val BloomBottom = Color(0xFF45060F)
    val GlassMiddle = Color(0xFF962837).copy(alpha = .10f)
    val GlassBottom = Color(0xFF140408).copy(alpha = .40f)
    val Border = Color(0xFFFFC8D0).copy(alpha = .24f)
    val ButtonBorder = Color(0xFFFFBEC6).copy(alpha = .70f)
    val GlassBorder = Color(0xFFFFD2D8).copy(alpha = .36f)
    val GlassButtonBottom = Color(0xFF781E2D).copy(alpha = .16f)
    val GelShadow = Color(0xFF5A000F).copy(alpha = .55f)
    val Glow = Color(0xFFE61E3C)
    val DishTop = Color(0xFF7A1424)
    val DishMiddle = Color(0xFF3A0A14)
    val DishBottom = Color(0xFF150408)
    val OrbTop = Color(0xFFFF8A96)
    val OrbMiddle = Color(0xFFE3173A)
    val OrbBottom = Color(0xFF8E0A20)
    val ToggleBottom = Color(0xFFC20D29)
    val KnobBottom = Color(0xFFEADCDF)
    val KnobOff = Color(0xFF9A8A8E)
    val DangerBorder = Color(0xFFFF6B7D).copy(alpha = .70f)
    val WhiteInset = Color(0xFF783C46)
    val GlassOrbMiddle = Color(0xFF5A2832)
    val GlassOrbBottom = Color(0xFF14060A)
    val WhiteOrbBottom = Color(0xFFC9B5BA)
    val KnotBase = Color(0xFF4B0311)
    val KnotShadow = Color(0xFF81051B)
    val KnotBody = Color(0xFFC81032)
    val KnotLight = Color(0xFFEF2447)
    val KnotSpecular = Color(0xFFFFA2AE)
}

object ChayeonSpacing {
    val ControlInset = 5.dp
    val TagVertical = 9.dp
    val ChipHorizontal = 18.dp
    val StepHorizontal = 22.dp
    val BubbleHorizontal = 26.dp
    val Tiny = 4.dp
    val Small = 8.dp
    val TagGap = 10.dp
    val Compact = 12.dp
    val Gap = 14.dp
    val Medium = 16.dp
    val AvatarGap = 20.dp
    val Large = 24.dp
    val CardHorizontal = 30.dp
    val Screen = 32.dp
    val Hero = 44.dp
    val Touch = 76.dp
    val Button = 84.dp
    val NarrowBreakpoint = 1100.dp
}

object ChayeonRadii {
    val Small = 12.dp
    val Check = 18.dp
    val Chip = 22.dp
    val Tag = 24.dp
    val Row = 28.dp
    val Card = 32.dp
    val Button = 36.dp
    val Banner = 40.dp
}

object ChayeonElevation {
    val Card = 18.dp
    val Hero = 26.dp
    val Button = 10.dp
    val Avatar = 24.dp
    val Highlight = 1.5.dp
    val GelHighlight = 2.5.dp
    val GelInset = 14.dp
    val Border = 1.5.dp
    val StrongBorder = 2.dp
}

object ChayeonDimensions {
    val SceneScreenHorizontal = 56.dp
    val SceneScreenTop = 44.dp
    val SceneScreenBottom = 40.dp
    val SceneBottomScrim = 640.dp
    val ScenePanelRadius = 40.dp
    val ScenePanelPadding = 34.dp
    val SceneTileRadius = 28.dp
    val SceneNavigation = 112.dp
    val SceneNavItem = 86.dp
    val SceneNavRadius = 56.dp
    val SceneStatus = 64.dp
    val SceneBrandOrb = 40.dp
    val SceneBadge = 30.dp
    val ScenePrimary = 118.dp
    val DrivingControl = 180.dp
    val DrivingTouch = 96.dp
    val SceneButtonRadius = 59.dp
    val SceneOutlineRadius = 42.dp
    val SceneShadow = 48.dp
    val SceneScore = 54.dp
    val SceneScoreLarge = 64.dp
    val SceneListAvatar = 92.dp
    val SceneCallAvatar = 360.dp
    val SceneMascot = 560.dp
    val ThreadDarkStroke = 9.dp
    val ThreadBrightStroke = 5.dp
    val ThreadHighlightStroke = 1.6.dp
    val Hairline = 1.dp

    val ContactQr = 256.dp
    val Logo = 88.dp
    val ProfileAvatar = 60.dp
    val PeerSmall = 104.dp
    val PeerLarge = 240.dp
    val MiniKnot = 96.dp
    val HeroKnot = 300.dp
    val PickerAvatar = 100.dp
    val ConfirmWidth = 720.dp
    val ScrollFade = 40.dp
    val CountdownTrack = 6.dp
    val ProgressDot = 12.dp
    val ProgressActive = 36.dp
    val SmallIcon = 24.dp
    val BackIcon = 30.dp
    val UtilityIcon = 32.dp
    val NavigationIcon = 34.dp
    val OrbIcon = 36.dp
    val GearBadge = 38.dp
    val StepDot = 40.dp
    val Radio = 44.dp
    val RadioDot = 26.dp
    val GearStrip = 46.dp
    val ToggleKnob = 50.dp
    val Checkbox = 56.dp
    val ToggleHeight = 60.dp
    val StateOrb = 68.dp
    val Banner = 80.dp
    val Navigation = 110.dp
    val ToggleWidth = 112.dp
    val CenterOrb = 122.dp
    val Avatar = 168.dp
    val Knot = 320.dp
    val StateCard = 330.dp
    val ChemistryKnot = 528.dp
}

object ChayeonMotion {
    const val Press = 120
    const val Connect = 700
    const val End = 450
    const val After = 900
    const val Reveal = 1200
    const val Flow = 3600
}

@OptIn(ExperimentalTextApi::class)
object ChayeonType {
    val Font = FontFamily(
        Font(R.font.asta_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.asta_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.asta_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.asta_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
        Font(R.font.asta_sans, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    )
    val Headline = TextStyle(fontFamily = Font, fontSize = 84.sp, lineHeight = 94.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp)
    val SceneTitle = TextStyle(fontFamily = Font, fontSize = 72.sp, lineHeight = 83.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp)
    val CallTitle = TextStyle(fontFamily = Font, fontSize = 64.sp, lineHeight = 76.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp)
    val CardTitle = TextStyle(fontFamily = Font, fontSize = 54.sp, lineHeight = 64.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
    val TileValue = TextStyle(fontFamily = Font, fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.ExtraBold)
    val SceneButton = TextStyle(fontFamily = Font, fontSize = 40.sp, lineHeight = 50.sp, fontWeight = FontWeight.ExtraBold)
    val DrivingButton = TextStyle(fontFamily = Font, fontSize = 44.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold)
    val SceneBrand = TextStyle(fontFamily = Font, fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold)
    val SceneName = TextStyle(fontFamily = Font, fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold)
    val SceneBody = TextStyle(fontFamily = Font, fontSize = 28.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
    val SceneNavigation = TextStyle(fontFamily = Font, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    val SceneStatus = TextStyle(fontFamily = Font, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
    val Score = TextStyle(fontFamily = Font, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
    val DrivingScore = TextStyle(fontFamily = Font, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold)
    val Brand = TextStyle(fontFamily = Font, fontSize = 50.sp, lineHeight = 55.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
    val Hero = TextStyle(fontFamily = Font, fontSize = 66.sp, lineHeight = 76.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.65).sp)
    val Title = TextStyle(fontFamily = Font, fontSize = 46.sp, lineHeight = 55.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.92).sp)
    val Section = TextStyle(fontFamily = Font, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.64).sp)
    val Body = TextStyle(fontFamily = Font, fontSize = 27.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium)
    val Button = TextStyle(fontFamily = Font, fontSize = 28.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold)
    val Secondary = TextStyle(fontFamily = Font, fontSize = 23.sp, lineHeight = 33.sp, fontWeight = FontWeight.Normal)
    val Label = TextStyle(fontFamily = Font, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val Minimum = TextStyle(fontFamily = Font, fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal)
}

val LocalDrivingPolicy = staticCompositionLocalOf { DrivingPolicy.UNKNOWN }
val LocalSourceLabels = staticCompositionLocalOf { true }
val LocalDecorativeMotion = staticCompositionLocalOf { false }

@Composable
fun ChayeonTheme(
    policy: DrivingPolicy = DrivingPolicy.UNKNOWN,
    animatorScaleOverride: Float? = null,
    showSourceLabels: Boolean = true,
    content: @Composable () -> Unit,
) {
    val resolver = LocalContext.current.contentResolver
    var scale by remember(resolver) {
        mutableFloatStateOf(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    CompositionLocalProvider(
        LocalDrivingPolicy provides policy,
        LocalSourceLabels provides showSourceLabels,
        LocalDecorativeMotion provides (policy == DrivingPolicy.ALLOWED && (animatorScaleOverride ?: scale) > 0f),
        content = content,
    )
}

@Composable
fun ChayeonText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = ChayeonType.Body,
    color: Color = ChayeonColors.Text,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val rendered = remember(text) { keepHangulWords(text) }
    BasicText(rendered, modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        onTextLayout = onTextLayout)
}

private val HangulWordPattern = Regex("\\S+")

internal fun keepHangulWords(text: String): String = HangulWordPattern.replace(text) { word ->
    if (word.value.none { it in '\uAC00'..'\uD7A3' || it in '\u1100'..'\u11FF' || it in '\u3130'..'\u318F' }) word.value
    else buildString {
        val characters = BreakIterator.getCharacterInstance(Locale.KOREAN).apply { setText(word.value) }
        var start = characters.first()
        var end = characters.next()
        while (end != BreakIterator.DONE) {
            if (start > 0) append('\u2060')
            append(word.value, start, end)
            start = end
            end = characters.next()
        }
    }
}
