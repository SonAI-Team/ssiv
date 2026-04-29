package com.sonai.ssiv

/**
 * An event listener, allowing subclasses and activities to be notified of significant events.
 */
interface OnImageEventListener {
    /**
     * Called when the dimensions of the image and view are known, and either a preview image,
     * the full size image, or base layer tiles are loaded.
     */
    fun onReady() {}

    /**
     * Called when the full size image is ready.
     */
    fun onImageLoaded() {}

    /**
     * Called when a preview image could not be loaded.
     */
    fun onPreviewLoadError(e: Throwable) {}

    /**
     * Indicates an error initializing the decoder or loading the full size bitmap.
     */
    fun onImageLoadError(e: Throwable) {}

    /**
     * Called when an image tile could not be loaded.
     */
    fun onTileLoadError(e: Throwable) {}

    /**
     * Called when a bitmap set using ImageSource.cachedBitmap is no longer being used.
     */
    fun onPreviewReleased() {}
}

/**
 * A default implementation of [OnImageEventListener] where all methods are empty.
 */
open class DefaultOnImageEventListener : OnImageEventListener
