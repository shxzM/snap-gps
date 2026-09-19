package com.example.snapgps.domain.format

/**
 * Overlay proportions relative to the image's short edge. Shared by the Compose preview and the
 * bitmap renderer so the preview is a faithful scale model of the stamped photo.
 */
object OverlayLayout {
    const val TEXT_SIZE_RATIO = 0.032f
    const val MARGIN_RATIO = 0.03f
    const val MAX_WIDTH_RATIO = 0.85f
    const val PADDING_TO_TEXT = 0.6f
    const val CORNER_TO_TEXT = 0.5f
    const val LINE_SPACING = 1.1f
}
