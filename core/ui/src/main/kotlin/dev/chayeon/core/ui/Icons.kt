package dev.chayeon.core.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

enum class ChayeonIcon(private val svg: String) {
    HOME("M4 11l8-6.5 8 6.5V20a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1z"),
    HEART("M12 20s-7.5-4.6-7.5-10A4.2 4.2 0 0 1 12 7.4 4.2 4.2 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10z"),
    PHONE("M5 4h3.5l1.8 4.5-2.2 1.4a11 11 0 0 0 6 6l1.4-2.2L20 15.5V19a1.5 1.5 0 0 1-1.6 1.5A16.5 16.5 0 0 1 3.5 5.6 1.5 1.5 0 0 1 5 4z"),
    GENTLE("M3 13c3-8 5 8 9 0s6 0 9-2M3 19h18"),
    BRAKE("M5 6v12M19 6v12M8 12h8M12 8l-4 4 4 4"),
    SIGNAL("M10 5l-7 7 7 7M3 12h18M17 8l4 4-4 4"),
    PACE("M3 8h18M3 16h18M8 5v6M16 13v6"),
    BELT("M6 3l12 18M18 3L6 21M9 10h6v5H9z"),
    ROAD("M7 3L3 21M17 3l4 18M12 3v4M12 10v4M12 17v4"),
    MOON("M19 16A9 9 0 0 1 8 4a9 9 0 1 0 11 12z"),
    TIRE("M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM12 3v5M12 16v5M3 12h5M16 12h5"),
    ENERGY("M13 2L5 13h6l-1 9 9-13h-6z"),
    BATTERY("M3 6h16v12H3zM22 10v4M7 12h8M11 9v6"),
    DROP("M12 3s-7 8-7 12a7 7 0 0 0 14 0c0-4-7-12-7-12zM8 15a4 4 0 0 0 4 4"),
    AIR("M3 8h12a3 3 0 1 0-3-3M3 12h16a3 3 0 1 1-3 3M3 17h6a2 2 0 1 1-2 2"),
    FORWARD("M9 5l7 7-7 7"),
    THREAD("M6.5 17C6.5 9 17.5 15 17.5 7M6.5 19.4a2.4 2.4 0 1 0 0-4.8 2.4 2.4 0 0 0 0 4.8zM17.5 9.4a2.4 2.4 0 1 0 0-4.8 2.4 2.4 0 0 0 0 4.8z"),
    HISTORY("M12 21a9 9 0 1 0-8.2-5.3M3 21v-5h5M12 7v5l3.5 2"),
    KNOT("M8 8a4 4 0 1 0 0 8c2.5 0 5.5-8 8-8a4 4 0 1 1 0 8c-2.5 0-5.5-8-8-8z"),
    PROFILE("M12 12a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9zM3.5 21a8.5 8.5 0 0 1 17 0"),
    BACK("M15 5l-7 7 7 7"),
    CHECK("M4 12l6 6L20 6"),
    BELL("M12 3a6 6 0 0 0-6 6c0 5-2 6-2 8h16c0-2-2-3-2-8a6 6 0 0 0-6-6zM10 20a2 2 0 0 0 4 0"),
    REFRESH("M4 12a8 8 0 0 1 13.7-5.6L20 9M20 4v5h-5M20 12a8 8 0 0 1-13.7 5.6L4 15M4 20v-5h5"),
    PIN("M12 21s7-6.2 7-11.5a7 7 0 1 0-14 0C5 14.8 12 21 12 21zM12 12a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z"),
    CLOCK("M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3.5 2"),
    PEOPLE("M9 11a3.4 3.4 0 1 0 0-6.8A3.4 3.4 0 0 0 9 11zM3 20a6 6 0 0 1 12 0M16 11.5a2.5 2.5 0 1 0 0-5M16.5 15.2a4.6 4.6 0 0 1 4.5 4.8"),
    OFFLINE("M2 8.5a15 15 0 0 1 6-3.4M22 8.5a15 15 0 0 0-11-4M5.5 12.5a10 10 0 0 1 3.6-2.2M18.5 12.5a10 10 0 0 0-4-2.5M9 16a5 5 0 0 1 6 0M12 20h.01M3 3l18 18"),
    CAR("M3 15l2.4-6.2C6 7.2 7 6 9 6h6c2 0 3 1.2 3.6 2.8L21 15v4H3zM7.5 15.5h.01M16.5 15.5h.01"),
    WARNING("M12 4l9.5 16.5h-19zM12 10v4.5M12 17.5h.01"),
    EDIT("M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4"),
    CLOSE("M5 5l14 14M19 5L5 19"),
    CHAT("M4 5h16v11H9l-5 4z"),
    CUT("M3 12c2-3 4-3 6.5 0M21 12c-2 3-4 3-6.5 0M10 6l1.5 3M14 18l-1.5-3"),
    EYE("M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"),
    LOCK("M6 11h12v10H6zM8.5 11V7.5a3.5 3.5 0 0 1 7 0V11");

    val vector: ImageVector by lazy {
        ImageVector.Builder(name, ChayeonDimensions.SmallIcon, ChayeonDimensions.SmallIcon, 24f, 24f)
            .addPath(PathParser().parsePathString(svg).toNodes(), stroke = SolidColor(ChayeonColors.White),
                strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }
}

@Composable
fun ChayeonIcon(icon: ChayeonIcon, modifier: Modifier = Modifier, color: Color = ChayeonColors.White, contentDescription: String? = null) {
    Image(icon.vector, contentDescription, modifier, colorFilter = ColorFilter.tint(color))
}
