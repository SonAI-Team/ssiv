package com.sonai.ssiv.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions

/**
 * Implementation of [TileEnhancer] using MediaPipe.
 *
 * NOTE: As of MediaPipe 0.10.33, the ImageUpscaler task is not part of the standard vision tasks.
 * This class provides a structured implementation using MediaPipe's BaseOptions and MPImage.
 * When an official ImageUpscaler or custom SuperResolution task is available, it can be
 * integrated here.
 *
 * @property context The application context.
 * @property modelPath The path to the TFLite model.
 */
class MediaPipeTileEnhancer(
    private val context: Context,
    private val modelPath: String
) : TileEnhancer, AutoCloseable {

    private val cache = TileCache()

    // BaseOptions for hardware acceleration and model loading
    @Suppress("unused")
    private val baseOptions: BaseOptions by lazy {
        val builder = BaseOptions.builder()
        if (modelPath.startsWith("assets/")) {
            builder.setModelAssetPath(modelPath.substringAfter("assets/"))
        } else {
            builder.setModelAssetPath(modelPath)
        }
        builder.build()
    }

    override fun enhance(bitmap: Bitmap, sRect: Rect, sampleSize: Int): Bitmap {
        val cached = cache.get(sRect, sampleSize)
        if (cached != null) return cached

        return try {
            // Convert Bitmap to MediaPipe's MPImage
            val mpImage = BitmapImageBuilder(bitmap).build()

            // TODO: Integrate the specific MediaPipe task once available or use a custom graph.
            // ... (rest of the logic)
            val enhanced = BitmapExtractor.extract(mpImage)
            cache.put(sRect, sampleSize, enhanced)
            enhanced
        } catch (e: Exception) {
            bitmap
        }
    }

    override fun close() {
        cache.clear()
        // Close task here when implemented
    }
}
