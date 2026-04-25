package com.sonai.ssiv

import android.graphics.PointF

/**
 * An event listener, allowing activities to be notified of pan and zoom events.
 */
interface OnStateChangedListener {
    /**
     * The scale has changed.
     */
    fun onScaleChanged(newScale: Float, origin: Int) {}

    /**
     * The source center has been changed.
     */
    fun onCenterChanged(newCenter: PointF?, origin: Int) {}
}
