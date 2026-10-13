package cn.kindyear.kwatermarkcam.core.storage

import android.graphics.*
import kotlin.math.min

/** Non-destructive edit recipe; center crops use the rotated image's coordinates. */
data class PhotoEdits(val quarterTurns: Int = 0, val mirror: Boolean = false, val cropRatio: Float? = null,
    val brightness: Float = 0f, val contrast: Float = 1f, val monochrome: Boolean = false)

/** Shared geometry and color processing for the small preview and full-resolution copy. */
object PhotoEditEngine {
    fun rotatedSize(width: Int, height: Int, turns: Int): Pair<Int, Int> = if (Math.floorMod(turns, 2) == 0) width to height else height to width
    fun crop(width: Int, height: Int, ratio: Float?): Rect {
        require(width > 0 && height > 0)
        require(ratio == null || ratio.isFinite() && ratio > 0)
        if (ratio == null) return Rect(0, 0, width, height)
        val w = min(width, (height * ratio).toInt().coerceAtLeast(1))
        val h = min(height, (width / ratio).toInt().coerceAtLeast(1))
        val left = (width - w) / 2; val top = (height - h) / 2
        return Rect(left, top, left + w, top + h)
    }
    fun render(source: Bitmap, edits: PhotoEdits): Bitmap {
        require(edits.brightness.isFinite() && edits.brightness in -.5f.. .5f)
        require(edits.contrast.isFinite() && edits.contrast in .5f..1.5f)
        val turns = Math.floorMod(edits.quarterTurns, 4)
        val (w, h) = rotatedSize(source.width, source.height, turns)
        val crop = crop(w, h, edits.cropRatio)
        val result = Bitmap.createBitmap(crop.width(), crop.height(), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(result)
            canvas.translate(-crop.left.toFloat(), -crop.top.toFloat())
            if (edits.mirror) { canvas.translate(w.toFloat(), 0f); canvas.scale(-1f, 1f) }
            when (turns) {
                1 -> { canvas.translate(source.height.toFloat(), 0f); canvas.rotate(90f) }
                2 -> { canvas.translate(source.width.toFloat(), source.height.toFloat()); canvas.rotate(180f) }
                3 -> { canvas.translate(0f, source.width.toFloat()); canvas.rotate(270f) }
            }
            val colors = ColorMatrix().apply { setSaturation(if (edits.monochrome) 0f else 1f) }
            val c = edits.contrast
            val shift = edits.brightness * 255f + (1f - c) * 128f
            colors.postConcat(ColorMatrix(floatArrayOf(c,0f,0f,0f,shift, 0f,c,0f,0f,shift, 0f,0f,c,0f,shift, 0f,0f,0f,1f,0f)))
            canvas.drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(colors) })
            return result
        } catch (error: Throwable) { result.recycle(); throw error }
    }
}
