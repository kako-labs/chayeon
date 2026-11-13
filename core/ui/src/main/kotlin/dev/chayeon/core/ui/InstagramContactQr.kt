package dev.chayeon.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.chayeon.core.model.DrivingPolicy
import kotlin.math.floor

internal fun instagramQrMatrix(instagramId: String): BitMatrix = QRCodeWriter().encode(
    "https://instagram.com/$instagramId", BarcodeFormat.QR_CODE, 0, 0,
    mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
)

@Composable
fun InstagramContactQr(instagramId: String, modifier: Modifier = Modifier) {
    if (LocalDrivingPolicy.current != DrivingPolicy.ALLOWED) return
    val matrix = remember(instagramId) { instagramQrMatrix(instagramId) }
    Canvas(modifier.size(ChayeonDimensions.ContactQr).semantics { contentDescription = "인스타그램 QR 코드" }) {
        val module = floor(size.minDimension / matrix.width).coerceAtLeast(1f)
        val origin = Offset((size.width - module * matrix.width) / 2, (size.height - module * matrix.height) / 2)
        drawRect(ChayeonColors.White)
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(ChayeonColors.Ink, origin + Offset(x * module, y * module), Size(module, module))
        }
    }
}
