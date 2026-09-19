package com.example.snapgps.data.media

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import kotlin.math.min

/**
 * Draws overlay lines straight onto a photo's canvas using android.graphics only, so it is
 * independent of Compose and never allocates a separate overlay bitmap.
 */
class OverlayBitmapRenderer {

    fun draw(canvas: Canvas, imageWidth: Int, imageHeight: Int, lines: List<OverlayLine>, config: OverlayConfig) {
        if (lines.isEmpty()) return
        val base = min(imageWidth, imageHeight).toFloat()
        val textSize = base * OverlayLayout.TEXT_SIZE_RATIO
        val padding = textSize * OverlayLayout.PADDING_TO_TEXT
        val margin = base * OverlayLayout.MARGIN_RATIO

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            setShadowLayer(textSize * 0.08f, 0f, textSize * 0.04f, Color.argb(160, 0, 0, 0))
        }
        val text = buildText(lines)
        val maxTextWidth = (imageWidth * OverlayLayout.MAX_WIDTH_RATIO - 2 * padding).toInt().coerceAtLeast(1)
        val wrapped = layout(text, paint, maxTextWidth)
        val contentWidth = (0 until wrapped.lineCount).maxOf { wrapped.getLineWidth(it) }
        // Re-layout at the tightest width so the background hugs the text.
        val tight = layout(text, paint, ceil(contentWidth).toInt().coerceAtLeast(1))

        val boxWidth = tight.width + 2 * padding
        val boxHeight = tight.height + 2 * padding
        val left = if (config.position.isStart) margin else imageWidth - margin - boxWidth
        val top = if (config.position.isTop) margin else imageHeight - margin - boxHeight
        val box = RectF(left, top, left + boxWidth, top + boxHeight)

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = (config.opacity.coerceIn(0f, 1f) * 255).toInt()
        }
        val radius = textSize * OverlayLayout.CORNER_TO_TEXT
        canvas.drawRoundRect(box, radius, radius, background)

        canvas.save()
        canvas.translate(box.left + padding, box.top + padding)
        tight.draw(canvas)
        canvas.restore()
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
