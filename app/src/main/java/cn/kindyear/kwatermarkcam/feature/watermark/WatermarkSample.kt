package cn.kindyear.kwatermarkcam.feature.watermark

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import cn.kindyear.kwatermarkcam.core.watermark.WatermarkRenderer
import cn.kindyear.kwatermarkcam.domain.model.WatermarkSnapshot
import kotlin.math.min

/** Zoomed crop of the exact portrait-photo watermark layout, without a separate Compose rendering scheme. */
@Composable
fun WatermarkSample(renderer: WatermarkRenderer, snapshot: WatermarkSnapshot, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Color(0xFF52676E))
        val measured = renderer.measure(1080, 1440, snapshot)
        val padding = 16f
        val scale = min((size.width - padding * 2) / measured.bounds.width(), (size.height - padding * 2) / measured.bounds.height())
        drawIntoCanvas { wrapped ->
            val canvas = wrapped.nativeCanvas
            canvas.save()
            canvas.translate((size.width - measured.bounds.width() * scale) / 2, (size.height - measured.bounds.height() * scale) / 2)
            canvas.scale(scale, scale)
            canvas.translate(-measured.bounds.left, -measured.bounds.top)
            renderer.draw(canvas, 1080, 1440, snapshot)
            canvas.restore()
        }
    }
}
