package dev.chayeon.core.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.*
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.KnotSeed
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.system.measureNanoTime
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1682dp-h1210dp-land-mdpi")
class KnotMaterialTest {
    @get:Rule val compose = createComposeRule()

    @Test fun referenceAndTubeMaterialAtLargeSize() {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                ChayeonBackground(Modifier.requiredSize(1400.dp, 700.dp).testTag("comparison")) {
                    Row(Modifier.fillMaxSize().padding(40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            ChayeonText("기준 에셋", style = ChayeonType.Label)
                            Image(painterResource(R.drawable.thread_knot), null, Modifier.size(620.dp, 300.dp), contentScale = ContentScale.Crop)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            ChayeonText("드라이브 시그니처 · 합성 주행", style = ChayeonType.Label)
                            SignatureKnot(KnotSeed(2357933L), Modifier.size(540.dp), reveal = false)
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("comparison").captureRoboImage("src/test/screenshots/material_reference_comparison.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun policyOrAnimatorDisablingMotionImmediatelyShowsTheCompletedKnot() {
        val policy = mutableStateOf(DrivingPolicy.ALLOWED)
        val scale = mutableStateOf(1f)
        val reveal = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ChayeonTheme(policy.value, scale.value) {
                ChayeonBackground(Modifier.requiredSize(560.dp, 360.dp).testTag("knot")) {
                    SignatureKnot(KnotSeed(2357933L), interlockedWith = KnotSeed(1842720L), reveal = reveal.value)
                }
            }
        }
        fun pixels(): IntArray {
            val file = File("build/test-render/knot-motion.png").apply { parentFile?.mkdirs() }
            compose.onNodeWithTag("knot").captureRoboImage(file.path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
            val bitmap = BitmapFactory.decodeFile(file.path)
            return IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        }
        compose.mainClock.advanceTimeBy(600)
        val partial = pixels()
        compose.runOnIdle { reveal.value = false }
        compose.mainClock.advanceTimeByFrame()
        val complete = pixels()
        assertFalse(partial.contentEquals(complete))
        for (restricted in listOf(DrivingPolicy.UNKNOWN, DrivingPolicy.RESTRICTED)) {
            compose.runOnIdle { policy.value = restricted; reveal.value = true }
            compose.mainClock.advanceTimeByFrame()
            assertContentEquals(complete, pixels())
        }
        compose.runOnIdle { policy.value = DrivingPolicy.ALLOWED; scale.value = 0f }
        compose.mainClock.advanceTimeByFrame()
        assertContentEquals(complete, pixels())
        compose.onNodeWithContentDescription("나의 붉은 실과 상대의 진주빛 흰 실로 엮은 두 사람의 시그니처 매듭").assertExists()
        compose.onNodeWithTag("knot").captureRoboImage("src/test/screenshots/material_motion_disabled.png")
    }

    @Test fun rasterIsDeterministicAndBounded() {
        val ropes = listOf(knotGeometry(KnotSeed(2357933L)).map { it.copy(x = it.x * .38f, y = it.y * .38f) })
        val a = renderKnot(ropes, 320, 320)
        val elapsed = measureNanoTime { assertTrue(a.sameAs(renderKnot(ropes, 320, 320))) }
        println("KNOT cached-material setup repeat on test host: ${elapsed / 1_000_000} ms; animation reuses the raster")
        val large = renderKnot(ropes, 2000, 1200)
        assertTrue(large.width <= KnotMaterial.MaximumRasterSide && large.height <= KnotMaterial.MaximumRasterSide)
    }
}
