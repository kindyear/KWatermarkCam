package cn.kindyear.kwatermarkcam.core.watermark

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import cn.kindyear.kwatermarkcam.domain.model.WatermarkSnapshot
import javax.inject.Inject
import kotlin.math.min

/** Independent Canvas renderer. Preview and JPEG output use the exact same measured layout. */
class WatermarkRenderer @Inject constructor() {
    data class Block(val layout: StaticLayout, val x: Float, val y: Float)
    data class Measured(val bounds: RectF, val blocks: List<Block>, val scale: Float)

    /** Dimensions are output pixels, never screen dp. Text wraps and ellipsizes within bounded rows. */
    fun measure(width: Int, height: Int, snapshot: WatermarkSnapshot): Measured {
        val scale = min(width, height) / 1080f
        val spec = snapshot.layout
        val margin = spec.marginOn1080 * scale
        val pad = spec.paddingOn1080 * scale
        val cardWidth = min(width * spec.widthFraction, spec.maxWidthOn1080 * scale)
        val textWidth = (cardWidth - 2 * pad - 10f * scale).toInt().coerceAtLeast(1)
        val available = height * spec.maxHeightFraction
        var font = spec.fontOn1080 * scale
        var blocks: List<Block>
        var contentHeight: Float
        do {
            var y = pad
            val all = mutableListOf<Block>()
            fun add(text: String, size: Float, bold: Boolean, lines: Int) {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE; textSize = size
                    typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
                }
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, textWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                    .setLineSpacing(2f * scale, 1f).setMaxLines(lines)
                    .setEllipsize(TextUtils.TruncateAt.END).setEllipsizedWidth(textWidth).build()
                all += Block(layout, pad + 10f * scale, y)
                y += layout.height + 9f * scale
            }
            add(snapshot.title, font * 1.1f, true, 1)
            snapshot.rows.forEach { add("${it.label}  ${it.value}", font, false, spec.maxLines) }
            contentHeight = y + pad - 9f * scale
            blocks = all
            if (contentHeight <= available || font <= 18f * scale) break
            font *= .9f
        } while (true)
        val bounds = RectF(margin, height - margin - contentHeight, margin + cardWidth, height - margin)
        return Measured(bounds, blocks, scale)
    }

    /** Draws into an existing mutable bitmap/preview canvas; no full-frame bitmap allocation. */
    fun draw(canvas: Canvas, width: Int, height: Int, snapshot: WatermarkSnapshot) {
        if (!snapshot.visible || snapshot.rows.isEmpty()) return
        val measured = measure(width, height, snapshot)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(178, 12, 23, 28) }
        val b = measured.bounds
        canvas.drawRoundRect(b, 16 * measured.scale, 16 * measured.scale, paint)
        paint.color = Color.rgb(245, 189, 79)
        canvas.drawRoundRect(b.left + 13 * measured.scale, b.top + 24 * measured.scale,
            b.left + 19 * measured.scale, b.bottom - 24 * measured.scale, 3 * measured.scale, 3 * measured.scale, paint)
        measured.blocks.forEach { block ->
            canvas.save(); canvas.translate(b.left + block.x, b.top + block.y)
            block.layout.draw(canvas); canvas.restore()
        }
    }
}
