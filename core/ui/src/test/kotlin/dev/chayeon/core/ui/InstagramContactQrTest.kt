package dev.chayeon.core.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import android.graphics.BitmapFactory
import java.io.File
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import dev.chayeon.core.model.DrivingPolicy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class InstagramContactQrTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun renderedQrDecodesExactInstagramUrlAndDisappearsUnderBothLocks() {
        val policy = mutableStateOf(DrivingPolicy.ALLOWED)
        compose.setContent {
            ChayeonTheme(policy.value, 0f) {
                InstagramContactQr("byeol-night.drive", Modifier.testTag("contact-qr"))
            }
        }
        val node = compose.onNodeWithTag("contact-qr")
        val file = File("build/test-render/contact-qr.png").apply { parentFile?.mkdirs() }
        node.captureRoboImage(file.path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        val bitmap = BitmapFactory.decodeFile(file.path)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))))
        assertEquals("https://instagram.com/byeol-night.drive", decoded.text)
        node.captureRoboImage("src/test/screenshots/instagram_contact_qr.png")
        for (locked in listOf(DrivingPolicy.RESTRICTED, DrivingPolicy.UNKNOWN)) {
            compose.runOnIdle { policy.value = locked }
            compose.onNodeWithTag("contact-qr").assertDoesNotExist()
            compose.onNodeWithContentDescription("인스타그램 QR 코드").assertDoesNotExist()
        }
    }
}
