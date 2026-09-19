package com.example.snapgps.data.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import com.example.snapgps.domain.format.OverlayLine
import com.example.snapgps.domain.format.OverlayLayout
import com.example.snapgps.domain.format.OverlayLineKind
import com.example.snapgps.domain.model.OverlayConfig
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Draws overlay lines (and optional map thumbnail) straight onto a photo's canvas using
 * android.graphics only, so it is independent of Compose and never allocates a separate overlay bitmap.
 */
class OverlayBitmapRenderer {

    /** [map], when given, is drawn as a square thumbnail to the left of the text. */
    fun draw(
        canvas: Canvas,
        imageWidth: Int,
        imageHeight: Int,
        lines: List<OverlayLine>,
        config: OverlayConfig,
        map: Bitmap? = null
    ) {
        if (lines.isEmpty() && map == null) return
        val base = min(imageWidth, imageHeight).toFloat()
        val textSize = base * OverlayLayout.TEXT_SIZE_RATIO
        val padding = textSize * OverlayLayout.PADDING_TO_TEXT
        val margin = base * OverlayLayout.MARGIN_RATIO
        val mapSide = if (map != null) base * OverlayLayout.MAP_SIZE_RATIO else 0f
        val gap = if (map != null && lines.isNotEmpty()) padding else 0f

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            setShadowLayer(textSize * 0.08f, 0f, textSize * 0.04f, Color.argb(160, 0, 0, 0))
        }
        val textLayout = if (lines.isEmpty()) null else {
            val text = buildText(lines)
            val maxTextWidth = (imageWidth * OverlayLayout.MAX_WIDTH_RATIO - 2 * padding - mapSide - gap)
                .toInt().coerceAtLeast(1)
            val wrapped = layout(text, paint, maxTextWidth)
            val contentWidth = (0 until wrapped.lineCount).maxOf { wrapped.getLineWidth(it) }
            // Re-layout at the tightest width so the background hugs the text.
            layout(text, paint, ceil(contentWidth).toInt().coerceAtLeast(1))
        }
        val textWidth = textLayout?.width?.toFloat() ?: 0f
        val textHeight = textLayout?.height?.toFloat() ?: 0f
        val contentHeight = max(mapSide, textHeight)

        val boxWidth = mapSide + gap + textWidth + 2 * padding
        val boxHeight = contentHeight + 2 * padding
        val left = if (config.position.isStart) margin else imageWidth - margin - boxWidth
        val top = if (config.position.isTop) margin else imageHeight - margin - boxHeight
        val box = RectF(left, top, left + boxWidth, top + boxHeight)

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = (config.opacity.coerceIn(0f, 1f) * 255).toInt()
        }
        val radius = textSize * OverlayLayout.CORNER_TO_TEXT
        canvas.drawRoundRect(box, radius, radius, background)

        if (map != null) {
            val mapTop = box.top + padding + (contentHeight - mapSide) / 2
            val mapRect = RectF(box.left + padding, mapTop, box.left + padding + mapSide, mapTop + mapSide)
            val mapRadius = textSize * OverlayLayout.MAP_CORNER_TO_TEXT
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(mapRect, mapRadius, mapRadius, Path.Direction.CW) })
            canvas.drawBitmap(map, null, mapRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        }

        if (textLayout != null) {
            canvas.save()
            canvas.translate(box.left + padding + mapSide + gap, box.top + padding + (contentHeight - textHeight) / 2)
            textLayout.draw(canvas)
            canvas.restore()
        }
    }

    private fun buildText(lines: List<OverlayLine>): CharSequence {
        val builder = SpannableStringBuilder()
        lines.forEachIndexed { index, line ->
            if (index > 0) builder.append('\n')
            val start = builder.length
            builder.append(line.text)
            if (line.kind == OverlayLineKind.COORDINATES || line.kind == OverlayLineKind.NO_LOCATION) {
                builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return builder
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, OverlayLayout.LINE_SPACING)
            .setIncludePad(false)
            .build()
}
