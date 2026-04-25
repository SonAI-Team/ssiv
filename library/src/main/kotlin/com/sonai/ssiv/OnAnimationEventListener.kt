package com.sonai.ssiv

/**
 * An event listener for animations.
 */
interface OnAnimationEventListener {
    /**
     * The animation has completed.
     */
    fun onComplete() {}

    /**
     * The animation has been aborted by a touch event.
     */
    fun onInterruptedByUser() {}

    /**
     * The animation has been aborted because a new animation has been started.
     */
    fun onInterruptedByNewAnim() {}
}

/**
 * Empty implementation for backward compatibility.
 */
@Deprecated("Use OnAnimationEventListener directly as it now has default implementations.", 
    ReplaceWith("OnAnimationEventListener"))
open class DefaultOnAnimationEventListener : OnAnimationEventListener
