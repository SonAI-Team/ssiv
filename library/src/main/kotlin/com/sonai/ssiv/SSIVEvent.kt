package com.sonai.ssiv

import android.graphics.PointF

sealed class SSIVEvent {
    data object OnReady : SSIVEvent()
    data object OnImageLoaded : SSIVEvent()
    data class OnPreviewLoadError(val e: Exception) : SSIVEvent()
    data class OnImageLoadError(val e: Exception) : SSIVEvent()
    data class OnTileLoadError(val e: Exception) : SSIVEvent()
    data object OnPreviewReleased : SSIVEvent()
    data class OnScaleChanged(val scale: Float, val origin: Int) : SSIVEvent()
    data class OnCenterChanged(val center: PointF?, val origin: Int) : SSIVEvent()
}
