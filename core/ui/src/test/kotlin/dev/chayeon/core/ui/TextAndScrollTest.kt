package dev.chayeon.core.ui

import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import java.io.File
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.model.DrivingPolicy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1682dp-h1210dp-land-mdpi")
class TextAndScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test fun joiningPreservesCombiningHangulAndEmojiGraphemes() {
        val decomposed = "한글"
        assertEquals("한\u2060글", keepHangulWords(decomposed))
        assertEquals("한\u2060👩‍👩‍👧‍👦", keepHangulWords("한👩‍👩‍👧‍👦"))
    }

    @Test fun hangulWordsStayWholeAndSemanticsKeepOriginalText() {
        val original = "이번 주행의 매력을 담았어요. 함께 타는 공기까지 신경 써요"
        var layout: TextLayoutResult? = null
        compose.setContent { TextFrame("text_keep_all") {
            ChayeonText(original, Modifier.width(250.dp), onTextLayout = { layout = it })
        } }
        compose.onNodeWithText(original).assertExists()
        assertEquals(original, compose.onNodeWithText(original).fetchSemanticsNode().config[SemanticsProperties.Text].single().text)
        val result = assertNotNull(layout)
        assertTrue(result.lineCount > 1)
        val rendered = result.layoutInput.text.text
        for (line in 0 until result.lineCount - 1) {
            val end = result.getLineEnd(line)
            assertTrue(rendered[end - 1].isWhitespace() || rendered.getOrNull(end)?.isWhitespace() == true, "Broken word at $end: $rendered")
        }
        compose.onNodeWithTag("text_keep_all").captureRoboImage("src/test/screenshots/text_keep_all_api34.png")
    }

    @Test fun longUnbrokenHangulAndLatinHaveAnEmergencyWrapWithoutOverflow() {
        val values = listOf("가나다라마바사아자차카타파하".repeat(5), "longunbrokenstring".repeat(7))
        val layouts = mutableMapOf<String, TextLayoutResult>()
        compose.setContent { TextFrame("text_long_words") { values.forEach { value ->
            ChayeonText(value, Modifier.width(190.dp), onTextLayout = { layouts[value] = it })
        } } }
        compose.waitForIdle()
        values.forEach { value ->
            compose.onNodeWithText(value).assertExists()
            val result = assertNotNull(layouts[value])
            assertTrue(result.lineCount > 1)
            assertFalse(result.didOverflowWidth)
        }
        compose.onNodeWithTag("text_long_words").captureRoboImage("src/test/screenshots/text_long_words_api34.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun scrollEdgesFadeOnlyWhereMoreContentExists() {
        compose.setContent {
            Row {
                ScrollSample("still", 80, 0)
                ScrollSample("start", 600, 0)
                ScrollSample("middle", 600, 150)
                ScrollSample("end", 600, 400)
            }
        }
        val actual = File("build/test-render/scroll-edges.png").apply { parentFile?.mkdirs() }
        compose.onRoot().captureRoboImage(actual.path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        val bitmap = BitmapFactory.decodeFile(actual.path)
        fun red(column: Int, y: Int) = AndroidColor.red(bitmap.getPixel(column * 140 + 50, y)) / 255f
        assertTrue(red(0, 2) > .95f && red(0, 75) > .95f)
        assertTrue(red(1, 2) > .95f && red(1, 197) < .15f)
        assertTrue(red(2, 2) < .15f && red(2, 197) < .15f)
        assertTrue(red(3, 2) < .15f && red(3, 197) > .95f)
        assertTrue(red(2, 20) in .35f.. .65f)
        compose.onRoot().captureRoboImage("src/test/screenshots/scroll_edges_api34.png")
    }

    @Composable private fun ScrollSample(tag: String, contentHeight: Int, offset: Int) {
        val state = rememberScrollState(offset)
        Box(Modifier.size(140.dp, 200.dp).background(Color.Black).testTag(tag)) {
            Column(Modifier.fillMaxSize().scrollEdgeFade(state).verticalScroll(state)) {
                Box(Modifier.fillMaxWidth().height(contentHeight.dp).background(Color.White))
            }
        }
    }

    @Composable private fun TextFrame(tag: String, content: @Composable ColumnScope.() -> Unit) {
        ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            ChayeonBackground(Modifier.requiredSize(600.dp, 900.dp).testTag(tag)) {
                Column(Modifier.padding(40.dp), verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
            }
        }
    }
}
