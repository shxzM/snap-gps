package com.example.snapgps.domain.model

data class OverlayConfig(
    val showCoordinates: Boolean = true,
    val showAddress: Boolean = true,
    val showDate: Boolean = true,
    val showTime: Boolean = true,
    val showAltitude: Boolean = true,
    val showAccuracy: Boolean = true,
    val showSpeed: Boolean = false,
    val showDirection: Boolean = false,
    val position: OverlayPosition = OverlayPosition.BOTTOM_LEFT,
    val opacity: Float = 0.6f
)

enum class OverlayPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    val isTop: Boolean get() = this == TOP_LEFT || this == TOP_RIGHT
    val isStart: Boolean get() = this == TOP_LEFT || this == BOTTOM_LEFT
}
