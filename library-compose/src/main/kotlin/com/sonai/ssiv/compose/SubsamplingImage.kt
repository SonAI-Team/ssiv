package com.sonai.ssiv.compose

import android.graphics.PointF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.sonai.ssiv.ImageSource
import com.sonai.ssiv.OnImageEventListener
import com.sonai.ssiv.OnStateChangedListener
import com.sonai.ssiv.SubsamplingScaleImageView
import kotlin.math.abs

@Composable
fun SubsamplingImage(
    imageSource: ImageSource,
    modifier: Modifier = Modifier,
    state: SubsamplingImageState = rememberSubsamplingImageState(),
    minScale: Float = 1f,
    maxScale: Float = 2f,
    panEnabled: Boolean = true,
    zoomEnabled: Boolean = true,
    quickScaleEnabled: Boolean = true,
    onImageLoaded: (() -> Unit)? = null,
    onImageError: ((Exception) -> Unit)? = null,
) {
    AndroidView(
        factory = { context ->
            SubsamplingScaleImageView(context).apply {
                this.setMinScale(minScale)
                this.setMaxScale(maxScale)
                this.setPanEnabled(panEnabled)
                this.setZoomEnabled(zoomEnabled)
                this.setQuickScaleEnabled(quickScaleEnabled)
                
                addOnStateChangedListener(object : OnStateChangedListener {
                    override fun onScaleChanged(newScale: Float, origin: Int) {
                        state.scale = newScale
                    }

                    override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                        state.center = newCenter
                    }
                })
                
                state.view = this
            }
        },
        modifier = modifier,
        update = { view ->
            view.setOnImageEventListener(object : OnImageEventListener {
                override fun onImageLoaded() {
                    onImageLoaded?.invoke()
                }

                override fun onImageLoadError(e: Exception) {
                    onImageError?.invoke(e)
                }

                override fun onPreviewLoadError(e: Exception) {
                    onImageError?.invoke(e)
                }

                override fun onTileLoadError(e: Exception) {
                    onImageError?.invoke(e)
                }
            })

            // Update ImageSource only if changed
            if (view.tag != imageSource) {
                view.setImage(imageSource)
                view.tag = imageSource
            }

            view.setMinScale(minScale)
            view.setMaxScale(maxScale)
            view.setPanEnabled(panEnabled)
            view.setZoomEnabled(zoomEnabled)
            view.setQuickScaleEnabled(quickScaleEnabled)
            
            // Sync scale and center only if they differ significantly from view's current values
            // to avoid cancelling animations or gestures prematurely.
            val currentScale = view.getScale()
            val targetScale = state.scale
            if (targetScale != null && abs(currentScale - targetScale) > 0.001f) {
                view.setScaleAndCenter(targetScale, state.center)
            } else {
                val currentCenter = view.center
                val targetCenter = state.center
                if (targetCenter != null && (currentCenter == null || 
                    abs(currentCenter.x - targetCenter.x) > 1f || 
                    abs(currentCenter.y - targetCenter.y) > 1f)) {
                    view.setScaleAndCenter(currentScale, targetCenter)
                }
            }
        }
    )

    DisposableEffect(state) {
        onDispose {
            state.view = null
        }
    }
}
