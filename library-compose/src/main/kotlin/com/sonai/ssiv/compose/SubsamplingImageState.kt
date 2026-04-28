package com.sonai.ssiv.compose

import android.graphics.PointF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sonai.ssiv.AnimationBuilder
import com.sonai.ssiv.SubsamplingScaleImageView

@Stable
class SubsamplingImageState(
    initialScale: Float? = null,
    initialCenter: PointF? = null
) {
    var scale by mutableStateOf(initialScale)
        internal set
    var center by mutableStateOf(initialCenter)
        internal set

    internal var view: SubsamplingScaleImageView? = null

    @Suppress("unused")
    fun setScaleAndCenter(scale: Float, center: PointF) {
        this.scale = scale
        this.center = center
        view?.setScaleAndCenter(scale, center)
    }

    @Suppress("unused")
    fun animateScaleAndCenter(scale: Float, center: PointF): AnimationBuilder? {
        return view?.animateScaleAndCenter(scale, center)
    }
}

@Composable
fun rememberSubsamplingImageState(
    initialScale: Float? = null,
    initialCenter: PointF? = null
): SubsamplingImageState {
    return remember {
        SubsamplingImageState(initialScale, initialCenter)
    }
}
