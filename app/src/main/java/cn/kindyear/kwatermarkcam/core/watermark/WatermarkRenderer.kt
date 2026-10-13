package cn.kindyear.kwatermarkcam.core.watermark

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import cn.kindyear.kwatermarkcam.domain.model.WatermarkSnapshot
import cn.kindyear.kwatermarkcam.domain.model.WatermarkStyle
import javax.inject.Inject
import kotlin.math.min

/** One measured Canvas layout is shared by live previews, template samples and full-resolution JPEGs. */
class WatermarkRenderer @Inject constructor() {
    data class Block(val layout: StaticLayout, val x: Float, val y: Float)
    data class Measured(val bounds: RectF, val blocks: List<Block>, val scale: Float)

    /** Output-pixel layout with wrapping, bounded line counts and image-relative dimensions. */
    fun measure(width: Int, height: Int, snapshot: WatermarkSnapshot): Measured {
        val scale = min(width, height) / 1080f
        val spec = snapshot.layout
        val margin = spec.marginOn1080 * scale
        val pad = spec.paddingOn1080 * scale
        val cardWidth = min(width * spec.widthFraction, spec.maxWidthOn1080 * scale)
        val sidebar = spec.style == WatermarkStyle.CONSTRUCTION || spec.style == WatermarkStyle.WORK_LOG
        val inset = if (sidebar) 10f * scale else 0f
        val textWidth = (cardWidth - 2 * pad - inset).toInt().coerceAtLeast(1)
        var font = spec.fontOn1080 * scale
        var blocks: List<Block>
        var contentHeight: Float
        do {
            var y = pad
            val all = mutableListOf<Block>()
            fun add(text: String, size: Float, bold: Boolean, lines: Int) {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (spec.style in setOf(WatermarkStyle.INSPECTION, WatermarkStyle.PROPERTY, WatermarkStyle.DELIVERY)) Color.rgb(28, 37, 43) else Color.WHITE
                    textSize = size
                    typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
                    if (spec.style == WatermarkStyle.TRAVEL) setShadowLayer(4f * scale, 0f, 2f * scale, Color.argb(210, 0, 0, 0))
                }
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, textWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                    .setLineSpacing(2f * scale, 1f).setMaxLines(lines)
                    .setEllipsize(TextUtils.TruncateAt.END).setEllipsizedWidth(textWidth).build()
                all += Block(layout, pad + inset, y)
                y += layout.height + (if (spec.style == WatermarkStyle.INSPECTION) 16f else 9f) * scale
            }
            if (snapshot.title.isNotBlank()) add(snapshot.title, font * 1.1f, true, 1)
            snapshot.rows.forEachIndexed { index, row ->
                val hero = index == 0 && spec.style != WatermarkStyle.CONSTRUCTION
                val text = if (hero) row.value else "${row.label}  ${row.value}"
                val display = if (hero && spec.style == WatermarkStyle.ATTENDANCE) text.replaceFirst(" ", "\n") else text
                add(display, font * if (hero) 1.35f else 1f, hero, if (hero) 2 else if (font <= 12f * scale) 1 else spec.maxLines)
            }
            contentHeight = y + pad - (if (spec.style == WatermarkStyle.INSPECTION) 16f else 9f) * scale
            blocks = all
            if (contentHeight <= height * spec.maxHeightFraction || font <= 9f * scale) break
            font *= .9f
        } while (true)
        return Measured(RectF(margin, height - margin - contentHeight, margin + cardWidth, height - margin), blocks, scale)
    }

    /** Draws on the existing mutable image, without allocating a second full-size bitmap. */
    fun draw(canvas: Canvas, width: Int, height: Int, snapshot: WatermarkSnapshot) {
        if (!snapshot.visible || snapshot.rows.isEmpty()) return
        val measured = measure(width, height, snapshot)
        val style = snapshot.layout.style
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val b = measured.bounds
        val s = measured.scale
        paint.color = when (style) {
            WatermarkStyle.CONSTRUCTION -> Color.argb(178, 12, 23, 28)
            WatermarkStyle.ATTENDANCE -> Color.argb(220, 15, 42, 70)
            WatermarkStyle.INSPECTION -> Color.argb(240, 248, 249, 250)
            WatermarkStyle.WORK_LOG -> Color.argb(210, 18, 40, 35)
            WatermarkStyle.TRAVEL -> Color.TRANSPARENT
            WatermarkStyle.PROPERTY -> Color.argb(240, 232, 243, 250)
            WatermarkStyle.SAFETY -> Color.argb(220, 75, 28, 36)
            WatermarkStyle.DELIVERY -> Color.argb(242, 255, 241, 207)
            WatermarkStyle.MINIMAL -> Color.argb(175, 38, 35, 48)
        }
        val radius = when (style) { WatermarkStyle.ATTENDANCE -> 28f; WatermarkStyle.WORK_LOG -> 6f; else -> 16f }
        if (style != WatermarkStyle.TRAVEL) canvas.drawRoundRect(b, radius * s, radius * s, paint)
        paint.color = when (style) {
            WatermarkStyle.CONSTRUCTION -> Color.rgb(245, 189, 79)
            WatermarkStyle.ATTENDANCE -> Color.rgb(94, 195, 255)
            WatermarkStyle.INSPECTION -> Color.rgb(218, 106, 29)
            WatermarkStyle.WORK_LOG -> Color.rgb(100, 220, 167)
            WatermarkStyle.TRAVEL -> Color.WHITE
            WatermarkStyle.PROPERTY -> Color.rgb(51, 114, 167)
            WatermarkStyle.SAFETY -> Color.rgb(255, 131, 125)
            WatermarkStyle.DELIVERY -> Color.rgb(143, 88, 21)
            WatermarkStyle.MINIMAL -> Color.rgb(216, 192, 249)
        }
        when (style) {
            WatermarkStyle.CONSTRUCTION, WatermarkStyle.WORK_LOG -> canvas.drawRoundRect(b.left + 13 * s, b.top + 24 * s, b.left + 19 * s, b.bottom - 24 * s, 3 * s, 3 * s, paint)
            WatermarkStyle.ATTENDANCE -> canvas.drawRoundRect(b.left + 30 * s, b.top + 12 * s, b.left + 112 * s, b.top + 18 * s, 3 * s, 3 * s, paint)
            WatermarkStyle.INSPECTION -> {
                canvas.drawRect(b.left + 28 * s, b.top + 12 * s, b.right - 28 * s, b.top + 17 * s, paint)
                paint.color = Color.argb(40, 28, 37, 43)
                measured.blocks.dropLast(1).forEach { block ->
                    val y = b.top + block.y + block.layout.height + 8 * s
                    canvas.drawLine(b.left + block.x, y, b.right - 28 * s, y, paint)
                }
            }
            WatermarkStyle.PROPERTY, WatermarkStyle.DELIVERY -> {
                canvas.drawRoundRect(b.left + 24 * s, b.top + 10 * s, b.right - 24 * s, b.top + 16 * s, 3 * s, 3 * s, paint)
            }
            WatermarkStyle.SAFETY -> {
                canvas.drawCircle(b.left + 18 * s, b.top + 18 * s, 5 * s, paint)
                canvas.drawRect(b.left + 28 * s, b.top + 14 * s, b.right - 28 * s, b.top + 20 * s, paint)
            }
            WatermarkStyle.MINIMAL -> canvas.drawRoundRect(b.left + 28 * s, b.bottom - 14 * s, b.left + 110 * s, b.bottom - 10 * s, 2 * s, 2 * s, paint)
            WatermarkStyle.TRAVEL -> {
                paint.setShadowLayer(3 * s, 0f, 1f * s, Color.BLACK)
                canvas.drawRect(b.left + 24 * s, b.bottom - 10 * s, b.left + 130 * s, b.bottom - 7 * s, paint)
            }
        }
        measured.blocks.forEach { block ->
            canvas.save(); canvas.translate(b.left + block.x, b.top + block.y)
            block.layout.draw(canvas); canvas.restore()
        }
    }
}
