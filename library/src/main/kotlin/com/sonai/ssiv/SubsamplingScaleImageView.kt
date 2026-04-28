package com.sonai.ssiv

import android.content.ContentResolver
import android.os.Build
import java.nio.ByteBuffer
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Paint.Style
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.annotation.AnyThread
import androidx.core.content.withStyledAttributes
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sonai.ssiv.R.styleable
import com.sonai.ssiv.SubsamplingScaleImageView.Companion.ORIENTATION_USE_EXIF
import com.sonai.ssiv.SubsamplingScaleImageView.Companion.PAN_LIMIT_INSIDE
import com.sonai.ssiv.SubsamplingScaleImageView.Companion.SCALE_TYPE_CENTER_INSIDE
import com.sonai.ssiv.SubsamplingScaleImageView.Companion.TILE_SIZE_AUTO
import com.sonai.ssiv.decoder.BitmapFactorySSIVImageDecoder
import com.sonai.ssiv.decoder.DecoderFactory
import com.sonai.ssiv.decoder.ImageRegionDecoder
import com.sonai.ssiv.decoder.SSIVImageDecoder
import com.sonai.ssiv.decoder.SkiaImageRegionDecoder
import com.sonai.ssiv.decoder.SkiaSSIVImageDecoder
import com.sonai.ssiv.internal.Anim
import com.sonai.ssiv.internal.ExifUtils
import com.sonai.ssiv.internal.MathUtils
import com.sonai.ssiv.internal.Tile
import com.sonai.ssiv.internal.TileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Displays an image subsampled as necessary to avoid loading too much image data into memory. After zooming in,
 * a set of image tiles subsampled at higher resolution are loaded and displayed over the base layer. During pan and
 * zoom, tiles off-screen or higher/lower resolution than required are discarded from memory.
 *
 * Tiles are no larger than the max supported bitmap size, so with large images tiling may be used even when zoomed out.
 *
 * v prefixes - coordinates, translations and distances measured in screen (view) pixels
 * s prefixes - coordinates, translations and distances measured in rotated and cropped source image pixels (scaled)
 * f prefixes - coordinates, translations and distances measured in original unrotated, uncropped source file pixels
 *
 * [View project on GitHub](https://github.com/SonAI-Team/ssiv)
 */
@Suppress(
    "unused",
    "MemberVisibilityCanBePrivate",
    "LargeClass",
    "TooManyFunctions",
    "ComplexCondition",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "NestedBlockDepth",
    "CyclomaticComplexMethod",
    "ReturnCount",
    "MaxLineLength"
)
open class SubsamplingScaleImageView @JvmOverloads constructor(
    context: Context,
    attr: AttributeSet? = null
) : View(context, attr) {

    // Bitmap (preview or full image)
    private var bitmap: Bitmap? = null

    // Whether the bitmap is a preview image
    private var bitmapIsPreview = false

    // Specifies if a cache handler is also referencing the bitmap. Do not recycle if so.
    private var bitmapIsCached = false

    // ByteBuffer image source
    private var buffer: ByteBuffer? = null

    // Uri of full size image
    private var uri: Uri? = null

    // Sample size used to display the whole image when fully zoomed out
    private var fullImageSampleSize = 0

    // Tile manager
    private val tileManager = TileManager()

    // Overlay tile boundaries and other info
    private var debug = false

    // Image orientation setting
    private var orientation = ORIENTATION_0

    // Max scale allowed (prevent infinite zoom)
    private var maxScale = 2f

    // Min scale allowed (prevent infinite zoom)
    private var minScale = minScale()

    // Density to reach before loading higher resolution tiles
    private var minimumTileDpi = -1

    // Pan limiting style
    private var panLimit = PAN_LIMIT_INSIDE

    // Minimum scale type
    private var minimumScaleType = SCALE_TYPE_CENTER_INSIDE

    // overrides for the dimensions of the generated tiles
    private var maxTileWidth = TILE_SIZE_AUTO
    private var maxTileHeight = TILE_SIZE_AUTO

    // Whether tiles should be loaded while gestures and animations are still in progress
    private var eagerLoadingEnabled = true

    // Gesture detection settings
    private var panEnabled = true
    private var zoomEnabled = true
    private var quickScaleEnabled = true

    // Double tap zoom behaviour
    private var doubleTapZoomScale = 1f
    private var doubleTapZoomStyle = ZOOM_FOCUS_FIXED
    private var doubleTapZoomDuration = DEFAULT_ANIM_DURATION

    // Current scale and scale at start of zoom
    internal var scale = 0f
    private var scaleStart = 0f

    // Screen coordinate of top-left corner of source image
    private var vTranslate: PointF? = null
    private var vTranslateStart: PointF? = null
    private var vTranslateBefore: PointF? = null

    // Source coordinate to center on, used when new position is set externally before view is ready
    private var pendingScale: Float? = null
    private var sPendingCenter: PointF? = null
    private var sRequestedCenter: PointF? = null

    // Source image dimensions and orientation - dimensions relate to the unrotated image
    private var sWidth = 0
    private var sHeight = 0
    private var sOrientation = 0
    private var sRegion: Rect? = null
    private var pRegion: Rect? = null

    // Is two-finger zooming in progress
    private var isZooming = false

    // Is one-finger panning in progress
    private var isPanning = false

    // Is quick-scale gesture in progress
    private var isQuickScaling = false

    // Max touches used in current gesture
    private var maxTouchCount = 0

    // Fling detector
    private var detector: GestureDetector? = null
    private var singleDetector: GestureDetector? = null

    // Tile and image decoding
    private var decoder: ImageRegionDecoder? = null
    private val decoderLock: ReadWriteLock = ReentrantReadWriteLock(true)
    private var bitmapDecoderFactory: DecoderFactory<out SSIVImageDecoder> =
        DecoderFactory {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SkiaSSIVImageDecoder()
            } else {
                BitmapFactorySSIVImageDecoder()
            }
        }
    private var regionDecoderFactory: DecoderFactory<out ImageRegionDecoder> =
        DecoderFactory { SkiaImageRegionDecoder() }

    // Debug values
    private var vCenterStart: PointF? = null
    private var vDistStart = 0f

    // Current quickscale state
    private val quickScaleThreshold: Float
    private var quickScaleLastDistance = 0f
    private var quickScaleMoved = false
    private var quickScaleVLastPoint: PointF? = null
    private var quickScaleSCenter: PointF? = null
    private var quickScaleVStart: PointF? = null

    // Scale and center animation tracking
    internal var anim: Anim? = null

    // Whether a ready notification has been sent to subclasses
    private var readySent = false

    // Whether a base layer loaded notification has been sent to subclasses
    private var imageLoadedSent = false

    // Event listener
    private var onImageEventListener: OnImageEventListener? = null
    private val onImageEventListeners = CopyOnWriteArrayList<OnImageEventListener>()

    // Scale and center listener
    private var onStateChangedListener: OnStateChangedListener? = null
    private val onStateChangedListeners = CopyOnWriteArrayList<OnStateChangedListener>()

    private var onEdgeReachedListener: OnEdgeReachedListener? = null

    private val _events = MutableSharedFlow<SSIVEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private var restoreStrategy = RESTORE_STRATEGY_DEFERRED

    // Long click listener
    private var onLongClickListener: OnLongClickListener? = null
    private var longClickJob: Job? = null

    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastRefreshTime = 0L

    // Paint objects created once and reused for efficiency
    private var bitmapPaint: Paint? = null
    private var debugTextPaint: Paint? = null
    private var debugLinePaint: Paint? = null
    private var tileBgPaint: Paint? = null

    // Volatile fields used to reduce object creation
    private var satTemp: ScaleAndTranslate? = null
    private var matrix: Matrix? = null
    private var sRect: RectF? = null
    private val srcArray = FloatArray(MATRIX_ARRAY_SIZE)
    private val dstArray = FloatArray(MATRIX_ARRAY_SIZE)

    // Objects for use in onDraw to avoid allocation
    private val vCenterStartDebug = PointF()
    private val vCenterEndRequestedDebug = PointF()
    private val vCenterEndDebug = PointF()
    private val centerDebug = PointF()

    //The logical density of the display
    private val density: Float = resources.displayMetrics.density

    init {
        setMinimumDpi(DEFAULT_MIN_DPI)
        setDoubleTapZoomDpi(DEFAULT_DOUBLE_TAP_ZOOM_DPI)
        setMinimumTileDpi(DEFAULT_MIN_TILE_DPI)
        setGestureDetector(context)
        // Handle XML attributes
        if (attr != null) {
            getContext().withStyledAttributes(attr, styleable.SubsamplingScaleImageView) {
                if (hasValue(styleable.SubsamplingScaleImageView_assetName)) {
                    val assetName = getString(styleable.SubsamplingScaleImageView_assetName)
                    if (!assetName.isNullOrEmpty()) {
                        setImage(ImageSource.asset(assetName).tilingEnabled())
                    }
                }
                if (hasValue(styleable.SubsamplingScaleImageView_src)) {
                    val resId = getResourceId(styleable.SubsamplingScaleImageView_src, 0)
                    if (resId > 0) {
                        setImage(ImageSource.resource(resId).tilingEnabled())
                    }
                }
                if (hasValue(styleable.SubsamplingScaleImageView_panEnabled)) {
                    setPanEnabled(getBoolean(styleable.SubsamplingScaleImageView_panEnabled, true))
                }
                if (hasValue(styleable.SubsamplingScaleImageView_zoomEnabled)) {
                    setZoomEnabled(
                        getBoolean(
                            styleable.SubsamplingScaleImageView_zoomEnabled,
                            true
                        )
                    )
                }
                if (hasValue(styleable.SubsamplingScaleImageView_quickScaleEnabled)) {
                    setQuickScaleEnabled(
                        getBoolean(
                            styleable.SubsamplingScaleImageView_quickScaleEnabled,
                            true
                        )
                    )
                }
                if (hasValue(styleable.SubsamplingScaleImageView_tileBackgroundColor)) {
                    setTileBackgroundColor(
                        getColor(
                            styleable.SubsamplingScaleImageView_tileBackgroundColor,
                            Color.argb(0, 0, 0, 0)
                        )
                    )
                }
                if (hasValue(styleable.SubsamplingScaleImageView_doubleTapZoomStyle)) {
                    setDoubleTapZoomStyle(
                        getInt(
                            styleable.SubsamplingScaleImageView_doubleTapZoomStyle,
                            ZOOM_FOCUS_FIXED
                        )
                    )
                }
                if (hasValue(styleable.SubsamplingScaleImageView_restoreStrategy)) {
                    restoreStrategy = getInt(
                        styleable.SubsamplingScaleImageView_restoreStrategy,
                        RESTORE_STRATEGY_DEFERRED
                    )
                }
            }
        }

        quickScaleThreshold = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            DEFAULT_QUICK_SCALE_THRESHOLD_DP,
            context.resources.displayMetrics
        )
    }

    /**
     * Sets the image orientation. It's best to call this before setting the image file or asset, because it may waste
     * loading of tiles. However, this can be freely called at any time.
     * @param orientation orientation to be set. See ORIENTATION_ static fields for valid values.
     */
    fun setOrientation(orientation: Int) {
        require(VALID_ORIENTATIONS.contains(orientation)) { "Invalid orientation: $orientation" }
        this.orientation = orientation
        reset(false)
        invalidate()
    }

    // Set a tile enhancer to use AI for upscaling tiles.

    /**
     * Set the image source from a bitmap, resource, asset, file or other URI.
     * @param imageSource Image source.
     */
    fun setImage(imageSource: ImageSource) {
        setImage(imageSource, null, null)
    }

    /**
     * Set the image source from a bitmap, resource, asset, file or other URI, starting with a given orientation
     * setting, scale and center. This is the best method to use when you want scale and center to be restored
     * after screen orientation change; it avoids any redundant loading of tiles in the wrong orientation.
     * @param imageSource Image source.
     * @param state State to be restored. Nullable.
     */
    fun setImage(imageSource: ImageSource, state: ImageViewState?) {
        setImage(imageSource, null, state)
    }

    /**
     * Set the image source from a bitmap, resource, asset, file or other URI, providing a preview image to be
     * displayed until the full size image is loaded.
     *
     * You must declare the dimensions of the full size image by calling [ImageSource.dimensions]
     * on the imageSource object. The preview source will be ignored if you don't provide dimensions,
     * and if you provide a bitmap for the full size image.
     * @param imageSource Image source. Dimensions must be declared.
     * @param previewSource Optional source for a preview image to be displayed and allow interaction while the full size image loads.
     */
    fun setImage(imageSource: ImageSource, previewSource: ImageSource?) {
        setImage(imageSource, previewSource, null)
    }

    /**
     * Set the image source from a bitmap, resource, asset, file or other URI, providing a preview image to be
     * displayed until the full size image is loaded, starting with a given orientation setting, scale and center.
     * This is the best method to use when you want scale and center to be restored after screen orientation change;
     * it avoids any redundant loading of tiles in the wrong orientation.
     *
     * You must declare the dimensions of the full size image by calling [ImageSource.dimensions]
     * on the imageSource object. The preview source will be ignored if you don't provide dimensions,
     * and if you provide a bitmap for the full size image.
     * @param imageSource Image source. Dimensions must be declared.
     * @param previewSource Optional source for a preview image to be displayed and allow interaction while the full size image loads.
     * @param state State to be restored. Nullable.
     */
    fun setImage(imageSource: ImageSource, previewSource: ImageSource?, state: ImageViewState?) {
        reset(true)
        state?.let { restoreState(it) }

        if (previewSource != null) {
            require(imageSource.bitmap == null) { "Preview image cannot be used when a bitmap is provided for the main image" }
            require(!(imageSource.sWidth <= 0 || imageSource.sHeight <= 0)) {
                "Preview image cannot be used unless dimensions are provided for the main image"
            }
            this.sWidth = imageSource.sWidth
            this.sHeight = imageSource.sHeight
            this.pRegion = previewSource.sRegion
            if (previewSource.bitmap != null) {
                this.bitmapIsCached = previewSource.isCached
                onPreviewLoaded(previewSource.bitmap)
            } else if (previewSource.buffer != null) {
                executeBitmapLoadTask(context, bitmapDecoderFactory, previewSource.buffer, true)
            } else {
                var uri = previewSource.uri
                if (uri == null && previewSource.resource != null) {
                    uri =
                        "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${previewSource.resource}".toUri()
                }
                executeBitmapLoadTask(context, bitmapDecoderFactory, uri!!, true)
            }
        }

        if (imageSource.bitmap != null && imageSource.sRegion != null) {
            onImageLoaded(
                Bitmap.createBitmap(
                    imageSource.bitmap,
                    imageSource.sRegion!!.left,
                    imageSource.sRegion!!.top,
                    imageSource.sRegion!!.width(),
                    imageSource.sRegion!!.height()
                ), ORIENTATION_0, false
            )
        } else if (imageSource.bitmap != null) {
            onImageLoaded(imageSource.bitmap, ORIENTATION_0, imageSource.isCached)
        } else if (imageSource.buffer != null) {
            sRegion = imageSource.sRegion
            buffer = imageSource.buffer
            if (imageSource.tile || sRegion != null) {
                executeTilesInitTask(context, regionDecoderFactory, buffer!!)
            } else {
                executeBitmapLoadTask(context, bitmapDecoderFactory, buffer!!, false)
            }
        } else {
            sRegion = imageSource.sRegion
            uri = imageSource.uri
            if (uri == null && imageSource.resource != null) {
                uri =
                    "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${imageSource.resource}".toUri()
            }
            if (imageSource.tile || sRegion != null) {
                // Load the bitmap using tile decoding.
                executeTilesInitTask(context, regionDecoderFactory, uri!!)
            } else {
                // Load the bitmap as a single image.
                executeBitmapLoadTask(context, bitmapDecoderFactory, uri!!, false)
            }
        }
    }

    /**
     * Reset all state before setting/changing image or setting new rotation.
     */
    private fun reset(newImage: Boolean) {
        debug("reset newImage=$newImage")
        scale = 0f
        scaleStart = 0f
        vTranslate = null
        vTranslateStart = null
        vTranslateBefore = null
        pendingScale = 0f
        sPendingCenter = null
        sRequestedCenter = null
        isZooming = false
        isPanning = false
        isQuickScaling = false
        maxTouchCount = 0
        fullImageSampleSize = 0
        vCenterStart = null
        vDistStart = 0f
        quickScaleLastDistance = 0f
        quickScaleMoved = false
        quickScaleSCenter = null
        quickScaleVLastPoint = null
        quickScaleVStart = null
        anim = null
        satTemp = null
        matrix = null
        sRect = null
        if (newImage) {
            uri = null
            buffer = null
            decoderLock.writeLock().lock()
            try {
                decoder?.recycle()
                decoder = null
            } finally {
                decoderLock.writeLock().unlock()
            }
            if (bitmap != null && !bitmapIsCached) {
                bitmap?.recycle()
            }
            if (bitmap != null && bitmapIsCached) {
                onImageEventListener?.onPreviewReleased()
                onImageEventListeners.forEach { it.onPreviewReleased() }
            }
            sWidth = 0
            sHeight = 0
            sOrientation = 0
            sRegion = null
            pRegion = null
            readySent = false
            imageLoadedSent = false
            bitmap = null
            bitmapIsPreview = false
            bitmapIsCached = false
        }
        tileManager.clear()
        setGestureDetector(context)
    }

    private fun setGestureDetector(context: Context) {
        this.detector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (panEnabled && readySent && vTranslate != null && e1 != null && (abs(e1.x - e2.x) > FLING_MIN_DISTANCE || abs(
                            e1.y - e2.y
                        ) > FLING_MIN_DISTANCE) && (abs(velocityX) > FLING_MIN_VELOCITY || abs(
                            velocityY
                        ) > FLING_MIN_VELOCITY) && !isZooming
                    ) {
                        val vTranslateEnd = PointF(
                            vTranslate!!.x + velocityX * FLING_VELOCITY_MULTIPLIER,
                            vTranslate!!.y + velocityY * FLING_VELOCITY_MULTIPLIER
                        )
                        val sCenterXEnd = (width / 2 - vTranslateEnd.x) / scale
                        val sCenterYEnd = (height / 2 - vTranslateEnd.y) / scale
                        AnimationBuilder(
                            this@SubsamplingScaleImageView,
                            PointF(sCenterXEnd, sCenterYEnd)
                        ).withEasing(EASE_OUT_QUAD)
                            .withPanLimited(false).withOrigin(ORIGIN_FLING).start()
                        return true
                    }
                    return super.onFling(e1, e2, velocityX, velocityY)
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    performClick()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (zoomEnabled && readySent && vTranslate != null) {
                        // Hacky solution for #15 - after a double tap the GestureDetector gets in a state
                        // where the next fling is ignored, so here we replace it with a new one.
                        setGestureDetector(context)
                        return if (quickScaleEnabled) {
                            // Store quick scale params. This will become either a double tap zoom or a
                            // quick scale depending on whether the user swipes.
                            vCenterStart = PointF(e.x, e.y)
                            vTranslateStart = PointF(vTranslate!!.x, vTranslate!!.y)
                            scaleStart = scale
                            isQuickScaling = true
                            isZooming = true
                            quickScaleLastDistance = -1f
                            quickScaleSCenter = viewToSourceCoord(vCenterStart!!)
                            quickScaleVStart = PointF(e.x, e.y)
                            quickScaleVLastPoint =
                                PointF(quickScaleSCenter!!.x, quickScaleSCenter!!.y)
                            quickScaleMoved = false
                            // We need to get events in onTouchEvent after this.
                            false
                        } else {
                            // Start double tap zoom animation.
                            doubleTapZoom(viewToSourceCoord(PointF(e.x, e.y))!!, PointF(e.x, e.y))
                            true
                        }
                    }
                    return super.onDoubleTapEvent(e)
                }
            })

        singleDetector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    performClick()
                    return true
                }
            })
    }

    /**
     * On resize, preserve center and scale. Various behaviors are possible, override this method to use another.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        debug("onSizeChanged %dx%d -> %dx%d", oldw, oldh, w, h)
        val sCenter = center
        if (readySent && sCenter != null) {
            this.anim = null
            this.pendingScale = scale
            this.sPendingCenter = sCenter
        }
    }

    /**
     * Measures the width and height of the view, preserving the aspect ratio of the image displayed if wrap_content is
     * used. The image will scale within this box, not resizing the view as it is zoomed.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSpecMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightSpecMode = MeasureSpec.getMode(heightMeasureSpec)
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)
        val resizeWidth = widthSpecMode != MeasureSpec.EXACTLY
        val resizeHeight = heightSpecMode != MeasureSpec.EXACTLY
        var width = parentWidth
        var height = parentHeight
        if (sWidth > 0 && sHeight > 0) {
            if (resizeWidth && resizeHeight) {
                width = sWidth()
                height = sHeight()
            } else if (resizeHeight) {
                height = (sHeight().toDouble() / sWidth().toDouble() * width).toInt()
            } else if (resizeWidth) {
                width = (sWidth().toDouble() / sHeight().toDouble() * height).toInt()
            }
        }
        width = max(width, suggestedMinimumWidth)
        height = max(height, suggestedMinimumHeight)
        setMeasuredDimension(width, height)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    private fun executeTilesInitTask(
        context: Context,
        decoderFactory: DecoderFactory<out ImageRegionDecoder>,
        source: Uri
    ) {
        Log.d(TAG, "executeTilesInitTask source=$source")
        scope.launch {
            var decoder: ImageRegionDecoder? = null
            var exception: Exception? = null
            val result = withContext(Dispatchers.IO) {
                try {
                    val sourceUri = source.toString()
                    debug("TilesInitTask.doInBackground")
                    val makeDecoder = decoderFactory.make()
                    decoder = makeDecoder
                    val dimensions = makeDecoder.init(context, source)
                    processTilesInitResult(dimensions, ExifUtils.getExifOrientation(context, sourceUri))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialise bitmap decoder", e)
                    exception = e
                    null
                }
            }
            handleTilesInitResult(decoder, result, exception, context, source, null)
        }
    }

    private fun executeTilesInitTask(
        context: Context,
        decoderFactory: DecoderFactory<out ImageRegionDecoder>,
        buffer: ByteBuffer
    ) {
        debug("executeTilesInitTask from ByteBuffer")
        scope.launch {
            var decoder: ImageRegionDecoder? = null
            var exception: Exception? = null
            val result = withContext(Dispatchers.IO) {
                try {
                    debug("TilesInitTask.doInBackground (ByteBuffer)")
                    val makeDecoder = decoderFactory.make()
                    decoder = makeDecoder
                    val dimensions = makeDecoder.init(context, buffer)
                    processTilesInitResult(dimensions, ORIENTATION_0)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialise bitmap decoder from ByteBuffer", e)
                    exception = e
                    null
                }
            }
            handleTilesInitResult(decoder, result, exception, context, null, buffer)
        }
    }

    private fun processTilesInitResult(dimensions: Point, exifOrientation: Int): IntArray {
        var sWidth = dimensions.x
        var sHeight = dimensions.y
        sRegion?.let { region ->
            region.left = max(0, region.left)
            region.top = max(0, region.top)
            region.right = min(sWidth, region.right)
            region.bottom = min(sHeight, region.bottom)
            sWidth = region.width()
            sHeight = region.height()
        }
        return intArrayOf(sWidth, sHeight, exifOrientation)
    }

    private fun handleTilesInitResult(
        decoder: ImageRegionDecoder?,
        result: IntArray?,
        exception: Exception?,
        context: Context,
        source: Uri?,
        buffer: ByteBuffer?
    ) {
        if (decoder != null && result != null && result.size == INIT_RESULT_COUNT) {
            onTilesInited(decoder, result[0], result[1], result[2])
        } else if (exception != null) {
            // Fallback to single bitmap loading if tiling fails (common for RAW/DNG formats)
            debug("Tiling init failed, falling back to single bitmap load: ${exception.message}")
            if (buffer != null) {
                executeBitmapLoadTask(context, bitmapDecoderFactory, buffer, false)
            } else if (source != null) {
                executeBitmapLoadTask(context, bitmapDecoderFactory, source, false)
            } else {
                onImageEventListener?.onImageLoadError(exception)
                onImageEventListeners.forEach { it.onImageLoadError(exception) }
            }
        }
    }

    private fun executeTileLoadTask(decoder: ImageRegionDecoder, tile: Tile) {
        tile.loading = true
        scope.launch {
            var exception: Exception? = null
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    if (decoder.isReady() && tile.visible) {
                        debug(
                            "TileLoadTask.doInBackground, tile.sRect=%s, tile.sampleSize=%d",
                            tile.sRect!!,
                            tile.sampleSize
                        )
                        decoderLock.readLock().lock()
                        try {
                            if (decoder.isReady()) {
                                // Update tile's file sRect according to rotation
                                fileSRect(tile.sRect!!, tile.fileSRect!!)
                                sRegion?.let {
                                    tile.fileSRect!!.offset(it.left, it.top)
                                }
                                val decoded =
                                    decoder.decodeRegion(tile.fileSRect!!, tile.sampleSize)
                                decoded?.prepareToDraw()
                                decoded
                                    ?: error("Decoder returned null bitmap")
                            } else {
                                tile.loading = false
                                null
                            }
                        } finally {
                            decoderLock.readLock().unlock()
                        }
                    } else {
                        tile.loading = false
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode tile", e)
                    exception = e
                    null
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "Failed to decode tile - OutOfMemoryError", e)
                    exception = RuntimeException(e)
                    null
                }
            }

            if (bitmap != null) {
                tile.bitmap = bitmap
                tile.loading = false
                onTileLoaded()
            } else if (exception != null) {
                onImageEventListener?.onTileLoadError(exception)
                onImageEventListeners.forEach { it.onTileLoadError(exception) }
                _events.tryEmit(SSIVEvent.OnTileLoadError(exception))
            }
        }
    }

    private fun executeBitmapLoadTask(
        context: Context,
        decoderFactory: DecoderFactory<out SSIVImageDecoder>,
        source: Uri,
        preview: Boolean
    ) {
        scope.launch {
            var bitmap: Bitmap? = null
            var exception: Exception? = null
            val orientation = withContext(Dispatchers.IO) {
                try {
                    val sourceUri = source.toString()
                    debug("BitmapLoadTask.doInBackground")
                    val decoded = decoderFactory.make().decode(context, source)
                    decoded.prepareToDraw()
                    bitmap = decoded
                    ExifUtils.getExifOrientation(context, sourceUri)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load bitmap", e)
                    exception = e
                    null
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "Failed to load bitmap - OutOfMemoryError", e)
                    exception = RuntimeException(e)
                    null
                }
            }

            if (bitmap != null && orientation != null) {
                if (preview) {
                    onPreviewLoaded(bitmap)
                } else {
                    onImageLoaded(bitmap, orientation, false)
                }
            } else if (exception != null) {
                if (preview) {
                    onImageEventListener?.onPreviewLoadError(exception)
                    onImageEventListeners.forEach { it.onPreviewLoadError(exception) }
                    _events.tryEmit(SSIVEvent.OnPreviewLoadError(exception))
                } else {
                    onImageEventListener?.onImageLoadError(exception)
                    onImageEventListeners.forEach { it.onImageLoadError(exception) }
                    _events.tryEmit(SSIVEvent.OnImageLoadError(exception))
                }
            }
        }
    }

    private fun executeBitmapLoadTask(
        context: Context,
        decoderFactory: DecoderFactory<out SSIVImageDecoder>,
        buffer: ByteBuffer,
        preview: Boolean
    ) {
        scope.launch {
            var bitmap: Bitmap? = null
            var exception: Exception? = null
            withContext(Dispatchers.IO) {
                try {
                    debug("BitmapLoadTask.doInBackground (ByteBuffer)")
                    val decoded = decoderFactory.make().decode(context, buffer)
                    decoded.prepareToDraw()
                    bitmap = decoded
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load bitmap from ByteBuffer", e)
                    exception = e
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "Failed to load bitmap from ByteBuffer - OutOfMemoryError", e)
                    exception = RuntimeException(e)
                }
            }

            if (bitmap != null) {
                if (preview) {
                    onPreviewLoaded(bitmap)
                } else {
                    onImageLoaded(bitmap, ORIENTATION_0, false)
                }
            } else if (exception != null) {
                if (preview) {
                    onImageEventListener?.onPreviewLoadError(exception)
                    onImageEventListeners.forEach { it.onPreviewLoadError(exception) }
                    _events.tryEmit(SSIVEvent.OnPreviewLoadError(exception))
                } else {
                    onImageEventListener?.onImageLoadError(exception)
                    onImageEventListeners.forEach { it.onImageLoadError(exception) }
                    _events.tryEmit(SSIVEvent.OnImageLoadError(exception))
                }
            }
        }
    }

    // Handle touch events. One finger pans, and two finger pinch and zoom plus panning.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // During non-interruptible anims, ignore all touch events
        if (anim != null && !anim!!.interruptible) {
            requestDisallowInterceptTouchEvent(true)
            return true
        } else {
            anim?.listener?.let {
                try {
                    it.onInterruptedByUser()
                } catch (e: Exception) {
                    Log.w(TAG, "Error thrown by animation listener", e)
                }
            }
            anim = null
        }

        // Abort if not ready
        if (vTranslate == null) {
            singleDetector?.onTouchEvent(event)
            return true
        }
        // Detect flings, taps and double taps
        if (!isQuickScaling && (detector == null || detector!!.onTouchEvent(event))) {
            isZooming = false
            isPanning = false
            maxTouchCount = 0
            return true
        }

        if (vTranslateStart == null) {
            vTranslateStart = PointF(0f, 0f)
        }
        if (vTranslateBefore == null) {
            vTranslateBefore = PointF(0f, 0f)
        }
        if (vCenterStart == null) {
            vCenterStart = PointF(0f, 0f)
        }

        // Store current values so we can send an event if they change
        val scaleBefore = scale
        vTranslateBefore!!.set(vTranslate!!)

        val handled = onTouchEventInternal(event)
        sendStateChanged(scaleBefore, vTranslateBefore!!, ORIGIN_TOUCH)
        return handled || super.onTouchEvent(event)
    }

    private fun onTouchEventInternal(event: MotionEvent): Boolean {
        val touchCount = event.pointerCount
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> onTouchActionDown(
                event,
                touchCount
            )

            MotionEvent.ACTION_MOVE -> onTouchActionMove(event, touchCount)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> onTouchActionUp(
                event,
                touchCount
            )

            else -> false
        }
    }

    private fun onTouchActionDown(event: MotionEvent, touchCount: Int): Boolean {
        anim = null
        requestDisallowInterceptTouchEvent(true)
        maxTouchCount = max(maxTouchCount, touchCount)
        if (touchCount >= 2) {
            if (zoomEnabled) {
                val distance =
                    MathUtils.distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1))
                scaleStart = scale
                vDistStart = distance
                vTranslateStart!!.set(vTranslate!!.x, vTranslate!!.y)
                vCenterStart!!.set(
                    (event.getX(0) + event.getX(1)) / 2,
                    (event.getY(0) + event.getY(1)) / 2
                )
            } else {
                maxTouchCount = 0
            }
            longClickJob?.cancel()
        } else if (!isQuickScaling) {
            vTranslateStart!!.set(vTranslate!!.x, vTranslate!!.y)
            vCenterStart!!.set(event.x, event.y)
            startLongClickTimer()
        }
        return true
    }

    private fun startLongClickTimer() {
        longClickJob = scope.launch {
            delay(LONG_CLICK_DELAY)
            if (onLongClickListener != null) {
                maxTouchCount = 0
                super@SubsamplingScaleImageView.setOnLongClickListener(onLongClickListener)
                performLongClick()
                super@SubsamplingScaleImageView.setOnLongClickListener(null)
            }
        }
    }

    private fun onTouchActionMove(event: MotionEvent, touchCount: Int): Boolean {
        var consumed = false
        if (maxTouchCount > 0) {
            if (touchCount >= 2) {
                consumed = handlePinch(event)
            } else if (isQuickScaling) {
                consumed = handleQuickScale(event)
            } else if (!isZooming) {
                consumed = handlePan(event)
            }
        }
        if (consumed) {
            longClickJob?.cancel()
            invalidate()
            return true
        }
        return false
    }

    private fun handlePinch(event: MotionEvent): Boolean {
        val vDistEnd =
            MathUtils.distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1))
        val vCenterEndX = (event.getX(0) + event.getX(1)) / 2
        val vCenterEndY = (event.getY(0) + event.getY(1)) / 2

        if (zoomEnabled && (MathUtils.distance(
                vCenterStart!!.x,
                vCenterEndX,
                vCenterStart!!.y,
                vCenterEndY
            ) > TOUCH_SLOP_PX
                    || abs(vDistEnd - vDistStart) > TOUCH_SLOP_PX || isPanning)
        ) {
            isZooming = true
            isPanning = true
            val previousScale = scale.toDouble()
            scale = min(maxScale, vDistEnd / vDistStart * scaleStart)

            if (scale <= minScale()) {
                vDistStart = vDistEnd
                scaleStart = minScale()
                vCenterStart!!.set(vCenterEndX, vCenterEndY)
                vTranslateStart!!.set(vTranslate!!)
            } else if (panEnabled) {
                val vLeftStart = vCenterStart!!.x - vTranslateStart!!.x
                val vTopStart = vCenterStart!!.y - vTranslateStart!!.y
                val vLeftNow = vLeftStart * (scale / scaleStart)
                val vTopNow = vTopStart * (scale / scaleStart)
                vTranslate!!.x = vCenterEndX - vLeftNow
                vTranslate!!.y = vCenterEndY - vTopNow
                if (previousScale * sHeight() < height && scale * sHeight() >= height || previousScale * sWidth() < width && scale * sWidth() >= width) {
                    fitToBounds(true)
                    vCenterStart!!.set(vCenterEndX, vCenterEndY)
                    vTranslateStart!!.set(vTranslate!!)
                    scaleStart = scale
                    vDistStart = vDistEnd
                }
            } else if (sRequestedCenter != null) {
                vTranslate!!.x = width / 2 - scale * sRequestedCenter!!.x
                vTranslate!!.y = height / 2 - scale * sRequestedCenter!!.y
            } else {
                vTranslate!!.x = width / 2 - scale * (sWidth() / 2)
                vTranslate!!.y = height / 2 - scale * (sHeight() / 2)
            }

            fitToBounds(true)
            refreshRequiredTiles(eagerLoadingEnabled)
            return true
        }
        return false
    }

    private fun handleQuickScale(event: MotionEvent): Boolean {
        var dist = abs(quickScaleVStart!!.y - event.y) * 2 + quickScaleThreshold

        if (quickScaleLastDistance == -1f) {
            quickScaleLastDistance = dist
        }
        val isUpwards = event.y > quickScaleVLastPoint!!.y
        quickScaleVLastPoint!!.set(0f, event.y)

        val spanDiff = abs(1 - dist / quickScaleLastDistance) * QUICK_SCALE_MULTIPLIER_BASE

        if (spanDiff > QUICK_SCALE_SPAN_DIFF_THRESHOLD || quickScaleMoved) {
            quickScaleMoved = true

            var multiplier = 1f
            if (quickScaleLastDistance > 0) {
                multiplier = if (isUpwards) 1 + spanDiff else 1 - spanDiff
            }

            val previousScale = scale.toDouble()
            scale = max(minScale(), min(maxScale, scale * multiplier))

            if (panEnabled) {
                val vLeftStart = vCenterStart!!.x - vTranslateStart!!.x
                val vTopStart = vCenterStart!!.y - vTranslateStart!!.y
                val vLeftNow = vLeftStart * (scale / scaleStart)
                val vTopNow = vTopStart * (scale / scaleStart)
                vTranslate!!.x = vCenterStart!!.x - vLeftNow
                vTranslate!!.y = vCenterStart!!.y - vTopNow
                if (previousScale * sHeight() < height && scale * sHeight() >= height || previousScale * sWidth() < width && scale * sWidth() >= width) {
                    fitToBounds(true)
                    vCenterStart!!.set(sourceToViewCoord(quickScaleSCenter!!)!!)
                    vTranslateStart!!.set(vTranslate!!)
                    scaleStart = scale
                    dist = 0f
                }
            } else if (sRequestedCenter != null) {
                vTranslate!!.x = width / 2 - scale * sRequestedCenter!!.x
                vTranslate!!.y = height / 2 - scale * sRequestedCenter!!.y
            } else {
                vTranslate!!.x = width / 2 - scale * (sWidth() / 2)
                vTranslate!!.y = height / 2 - scale * (sHeight() / 2)
            }
        }

        quickScaleLastDistance = dist
        fitToBounds(true)
        refreshRequiredTiles(eagerLoadingEnabled)
        return true
    }

    private fun handlePan(event: MotionEvent): Boolean {
        val dx = abs(event.x - vCenterStart!!.x)
        val dy = abs(event.y - vCenterStart!!.y)

        val offset = density * TOUCH_SLOP_PX
        if (dx > offset || dy > offset || isPanning) {
            vTranslate!!.x = vTranslateStart!!.x + (event.x - vCenterStart!!.x)
            vTranslate!!.y = vTranslateStart!!.y + (event.y - vCenterStart!!.y)

            val lastX = vTranslate!!.x
            val lastY = vTranslate!!.y
            fitToBounds(true)
            val atXEdge = lastX != vTranslate!!.x
            val atYEdge = lastY != vTranslate!!.y
            val edgeXSwipe = atXEdge && dx > dy && !isPanning
            val edgeYSwipe = atYEdge && dy > dx && !isPanning
            val yPan = lastY == vTranslate!!.y && dy > offset * PAN_EDGE_THRESHOLD_MULTIPLIER
            if (!edgeXSwipe && !edgeYSwipe && (!atXEdge || !atYEdge || yPan || isPanning)) {
                isPanning = true
            } else if (dx > offset || dy > offset) {
                maxTouchCount = 0
                longClickJob?.cancel()
                requestDisallowInterceptTouchEvent(false)
            }
            if (!panEnabled) {
                vTranslate!!.x = vTranslateStart!!.x
                vTranslate!!.y = vTranslateStart!!.y
                requestDisallowInterceptTouchEvent(false)
            }

            refreshRequiredTiles(eagerLoadingEnabled)
            return true
        }
        return false
    }

    private fun onTouchActionUp(event: MotionEvent, touchCount: Int): Boolean {
        longClickJob?.cancel()
        var consumed = false
        if (isQuickScaling) {
            isQuickScaling = false
            if (!quickScaleMoved) {
                doubleTapZoom(quickScaleSCenter!!, vCenterStart!!)
            }
            consumed = true
        }
        if (maxTouchCount > 0 && (isZooming || isPanning)) {
            if (isZooming && touchCount == 2) {
                isPanning = true
                vTranslateStart!!.set(vTranslate!!.x, vTranslate!!.y)
                if (event.actionIndex == 1) {
                    vCenterStart!!.set(event.getX(0), event.getY(0))
                } else {
                    vCenterStart!!.set(event.getX(1), event.getY(1))
                }
            }
            if (touchCount < MIN_PINCH_TOUCH_COUNT + 1) {
                isZooming = false
            }
            if (touchCount < MIN_PINCH_TOUCH_COUNT) {
                isPanning = false
                maxTouchCount = 0
            }
            refreshRequiredTiles(true)
            consumed = true
        }
        if (touchCount == 1) {
            if (isZooming || isPanning || maxTouchCount > 0) {
                consumed = true
            }
            isZooming = false
            isPanning = false
            maxTouchCount = 0
        }
        return consumed
    }

    private fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        val parent = parent
        parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    /**
     * Double tap zoom handler triggered from gesture detector or on touch, depending on whether
     * quick scale is enabled.
     */
    private fun doubleTapZoom(sCenter: PointF, vFocus: PointF) {
        if (!panEnabled) {
            if (sRequestedCenter != null) {
                // With a center specified from code, zoom around that point.
                sCenter.x = sRequestedCenter!!.x
                sCenter.y = sRequestedCenter!!.y
            } else {
                // With no requested center, scale around the image center.
                sCenter.x = (sWidth() / 2).toFloat()
                sCenter.y = (sHeight() / 2).toFloat()
            }
        }
        val targetDoubleTapZoomScale = min(maxScale, doubleTapZoomScale)
        val zoomIn =
            scale <= targetDoubleTapZoomScale * DOUBLE_TAP_ZOOM_THRESHOLD || scale == minScale
        val targetScale = if (zoomIn) targetDoubleTapZoomScale else minScale()
        if (doubleTapZoomStyle == ZOOM_FOCUS_CENTER_IMMEDIATE) {
            setScaleAndCenter(targetScale, sCenter)
        } else if (doubleTapZoomStyle == ZOOM_FOCUS_CENTER || !zoomIn || !panEnabled) {
            AnimationBuilder(this, targetScale, sCenter).withInterruptible(false)
                .withDuration(doubleTapZoomDuration.toLong()).withOrigin(ORIGIN_DOUBLE_TAP_ZOOM)
                .start()
        } else if (doubleTapZoomStyle == ZOOM_FOCUS_FIXED) {
            AnimationBuilder(this, targetScale, sCenter, vFocus).withInterruptible(false)
                .withDuration(doubleTapZoomDuration.toLong()).withOrigin(ORIGIN_DOUBLE_TAP_ZOOM)
                .start()
        }
        invalidate()
    }

    /**
     * Draw method should not be called until the view has dimensions so the first calls are used as triggers to calculate
     * the scaling and tiling required. Once the view is set up, tiles are displayed as they are loaded.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        createPaints()

        // If image or view dimensions are not known yet, abort.
        if (sWidth == 0 || sHeight == 0 || width == 0 || height == 0) {
            return
        }

        // When using tiles, on first render with no tile map ready, initialize it and kick off async base image loading.
        if (tileManager.tileMap == null && decoder != null) {
            initialiseBaseLayer(getMaxBitmapDimensions(canvas))
        }

        // If image has been loaded or supplied as a bitmap, onDraw may be the first time the view has
        // dimensions and therefore the first opportunity to set scale and translate. If this call returns
        // false there is nothing to be drawn so return immediately.
        if (!checkReady()) {
            return
        }

        // Set scale and translate before draw.
        preDraw()

        drawAnimation(canvas)
        drawTiles(canvas)
        drawFullBitmap(canvas)
        drawDebug(canvas)
    }

    private fun drawAnimation(canvas: Canvas) {
        val a = anim ?: return
        if (a.vFocusStart == null) return

        // Store current values so we can send an event if they change
        val scaleBefore = scale
        if (vTranslateBefore == null) {
            vTranslateBefore = PointF(0f, 0f)
        }
        vTranslateBefore!!.set(vTranslate!!)

        var scaleElapsed = System.currentTimeMillis() - a.time
        val finished = scaleElapsed > a.duration
        scaleElapsed = min(scaleElapsed, a.duration)
        scale = ease(a.easing, scaleElapsed, a.scaleStart, a.scaleEnd - a.scaleStart, a.duration)

        // Apply required animation to the focal point
        val vFocusNowX = ease(
            a.easing,
            scaleElapsed,
            a.vFocusStart!!.x,
            a.vFocusEnd!!.x - a.vFocusStart!!.x,
            a.duration
        )
        val vFocusNowY = ease(
            a.easing,
            scaleElapsed,
            a.vFocusStart!!.y,
            a.vFocusEnd!!.y - a.vFocusStart!!.y,
            a.duration
        )
        // Find out where the focal point is at this scale and adjust its position to follow the animation path
        vTranslate!!.x -= sourceToViewX(a.sCenterEnd!!.x) - vFocusNowX
        vTranslate!!.y -= sourceToViewY(a.sCenterEnd!!.y) - vFocusNowY

        // For translate anims, showing the image non-centered is never allowed, for scaling anims it is during the animation.
        fitToBounds(finished || a.scaleStart == a.scaleEnd)
        sendStateChanged(scaleBefore, vTranslateBefore!!, a.origin)
        refreshRequiredTiles(finished)
        if (finished) {
            a.listener?.let {
                try {
                    it.onComplete()
                } catch (e: Exception) {
                    Log.w(TAG, "Error thrown by animation listener", e)
                }
            }
            anim = null
        }
        invalidate()
    }

    private fun drawTiles(canvas: Canvas) {
        val tileMap = tileManager.tileMap
        if (tileMap != null && isBaseLayerReady()) {

            // Optimum sample size for current scale
            val sampleSize = min(fullImageSampleSize, calculateInSampleSize(scale))

            // First check for missing tiles - if there are any we need the base layer underneath to avoid gaps
            var hasMissingTiles = false
            val currentLayer = tileMap[sampleSize]
            if (currentLayer != null) {
                for (tile in currentLayer) {
                    if (tile.visible && (tile.loading || tile.bitmap == null)) {
                        hasMissingTiles = true
                        break
                    }
                }
            }

            // Render all loaded tiles. LinkedHashMap used for bottom up rendering - lower res tiles underneath.
            for (tileMapEntry in tileMap.entries) {
                if (tileMapEntry.key == sampleSize || hasMissingTiles) {
                    for (tile in tileMapEntry.value) {
                        drawTile(canvas, tile)
                    }
                }
            }
        }
    }

    private fun drawTile(canvas: Canvas, tile: Tile) {
        sourceToViewRect(tile.sRect!!, tile.vRect!!)
        if (!tile.loading && tile.bitmap != null) {
            tileBgPaint?.let {
                canvas.drawRect(tile.vRect!!, it)
            }
            val rotation = getRequiredRotation()
            if (rotation == 0) {
                canvas.drawBitmap(tile.bitmap!!, null, tile.vRect!!, bitmapPaint)
            } else {
                if (matrix == null) {
                    matrix = Matrix()
                }
                matrix!!.reset()
                setMatrixArray(
                    srcArray,
                    0f,
                    0f,
                    tile.bitmap!!.width.toFloat(),
                    0f,
                    tile.bitmap!!.width.toFloat(),
                    tile.bitmap!!.height.toFloat(),
                    0f,
                    tile.bitmap!!.height.toFloat()
                )
                setupDstArray(tile.vRect!!, rotation)
                matrix!!.setPolyToPoly(srcArray, 0, dstArray, 0, POLY_TO_POLY_COUNT)
                canvas.drawBitmap(tile.bitmap!!, matrix!!, bitmapPaint)
            }
            if (debug) {
                canvas.drawRect(tile.vRect!!, debugLinePaint!!)
            }
        } else if (tile.loading && debug) {
            canvas.drawText(
                "LOADING",
                (tile.vRect!!.left + px(DEBUG_OFFSET_PX)).toFloat(),
                (tile.vRect!!.top + px(DEBUG_OFFSET_Y_LOADING_PX)).toFloat(),
                debugTextPaint!!
            )
        }
        if (tile.visible && debug) {
            canvas.drawText(
                "ISS ${tile.sampleSize} RECT ${tile.sRect!!.top},${tile.sRect!!.left},${tile.sRect!!.bottom},${tile.sRect!!.right}",
                (tile.vRect!!.left + px(DEBUG_OFFSET_PX)).toFloat(),
                (tile.vRect!!.top + px(DEBUG_LINE_SPACING_PX)).toFloat(),
                debugTextPaint!!
            )
        }
    }

    private fun setupDstArray(vRect: Rect, rotation: Int) {
        when (rotation) {
            ORIENTATION_0 -> setMatrixArray(
                dstArray,
                vRect.left.toFloat(),
                vRect.top.toFloat(),
                vRect.right.toFloat(),
                vRect.top.toFloat(),
                vRect.right.toFloat(),
                vRect.bottom.toFloat(),
                vRect.left.toFloat(),
                vRect.bottom.toFloat()
            )

            ORIENTATION_90 -> setMatrixArray(
                dstArray,
                vRect.right.toFloat(),
                vRect.top.toFloat(),
                vRect.right.toFloat(),
                vRect.bottom.toFloat(),
                vRect.left.toFloat(),
                vRect.bottom.toFloat(),
                vRect.left.toFloat(),
                vRect.top.toFloat()
            )

            ORIENTATION_180 -> setMatrixArray(
                dstArray,
                vRect.right.toFloat(),
                vRect.bottom.toFloat(),
                vRect.left.toFloat(),
                vRect.bottom.toFloat(),
                vRect.left.toFloat(),
                vRect.top.toFloat(),
                vRect.right.toFloat(),
                vRect.top.toFloat()
            )

            ORIENTATION_270 -> setMatrixArray(
                dstArray,
                vRect.left.toFloat(),
                vRect.bottom.toFloat(),
                vRect.left.toFloat(),
                vRect.top.toFloat(),
                vRect.right.toFloat(),
                vRect.top.toFloat(),
                vRect.right.toFloat(),
                vRect.bottom.toFloat()
            )
        }
    }

    private fun drawFullBitmap(canvas: Canvas) {
        val tileMap = tileManager.tileMap
        if ((tileMap == null || !isBaseLayerReady()) && bitmap != null && !bitmap!!.isRecycled) {

            var xScale = scale
            var yScale = scale
            if (bitmapIsPreview) {
                xScale = scale * (sWidth.toFloat() / bitmap!!.width)
                yScale = scale * (sHeight.toFloat() / bitmap!!.height)
            }

            if (matrix == null) {
                matrix = Matrix()
            }
            matrix!!.reset()
            matrix!!.postScale(xScale, yScale)
            matrix!!.postRotate(getRequiredRotation().toFloat())
            matrix!!.postTranslate(vTranslate!!.x, vTranslate!!.y)

            when (getRequiredRotation()) {
                ORIENTATION_180 -> matrix!!.postTranslate(scale * sWidth, scale * sHeight)
                ORIENTATION_90 -> matrix!!.postTranslate(scale * sHeight, 0f)
                ORIENTATION_270 -> matrix!!.postTranslate(0f, scale * sWidth)
            }

            tileBgPaint?.let {
                if (sRect == null) {
                    sRect = RectF()
                }
                sRect!!.set(
                    0f,
                    0f,
                    (if (bitmapIsPreview) bitmap!!.width else sWidth).toFloat(),
                    (if (bitmapIsPreview) bitmap!!.height else sHeight).toFloat()
                )
                matrix!!.mapRect(sRect)
                canvas.drawRect(sRect!!, it)
            }
            canvas.drawBitmap(bitmap!!, matrix!!, bitmapPaint)
        }
    }

    private fun drawDebug(canvas: Canvas) {
        if (debug) {
            drawDebugText(canvas)
            drawDebugCircles(canvas)
            debugLinePaint!!.color = Color.MAGENTA
        }
    }

    private fun drawDebugText(canvas: Canvas) {
        canvas.drawText(
            "Scale: ${
                String.format(
                    Locale.ENGLISH,
                    "%.2f",
                    scale
                )
            } (${
                String.format(
                    Locale.ENGLISH,
                    "%.2f",
                    minScale()
                )
            } - ${String.format(Locale.ENGLISH, "%.2f", maxScale)})",
            px(DEBUG_OFFSET_PX).toFloat(),
            px(DEBUG_LINE_SPACING_PX).toFloat(),
            debugTextPaint!!
        )
        canvas.drawText(
            "Translate: ${
                String.format(
                    Locale.ENGLISH,
                    "%.2f",
                    vTranslate!!.x
                )
            }:${String.format(Locale.ENGLISH, "%.2f", vTranslate!!.y)}",
            px(DEBUG_OFFSET_PX).toFloat(),
            px(DEBUG_OFFSET_Y_TRANS_PX).toFloat(),
            debugTextPaint!!
        )
        val currentCenter = center
        if (currentCenter != null) {
            centerDebug.set(currentCenter)
            canvas.drawText(
                "Source center: ${
                    String.format(
                        Locale.ENGLISH,
                        "%.2f",
                        centerDebug.x
                    )
                }:${String.format(Locale.ENGLISH, "%.2f", centerDebug.y)}",
                px(DEBUG_OFFSET_PX).toFloat(),
                px(DEBUG_OFFSET_Y_CENTER_PX).toFloat(),
                debugTextPaint!!
            )
        }
    }

    private fun drawDebugCircles(canvas: Canvas) {
        anim?.let {
            sourceToViewCoord(it.sCenterStart!!, vCenterStartDebug)
            sourceToViewCoord(it.sCenterEndRequested!!, vCenterEndRequestedDebug)
            sourceToViewCoord(it.sCenterEnd!!, vCenterEndDebug)
            canvas.drawCircle(
                vCenterStartDebug.x,
                vCenterStartDebug.y,
                px(DEBUG_CIRCLE_RADIUS_PX).toFloat(),
                debugLinePaint!!
            )
            debugLinePaint!!.color = Color.RED
            canvas.drawCircle(
                vCenterEndRequestedDebug.x,
                vCenterEndRequestedDebug.y,
                px(DEBUG_CIRCLE_20_PX).toFloat(),
                debugLinePaint!!
            )
            debugLinePaint!!.color = Color.BLUE
            canvas.drawCircle(
                vCenterEndDebug.x,
                vCenterEndDebug.y,
                px(DEBUG_CIRCLE_25_PX).toFloat(),
                debugLinePaint!!
            )
            debugLinePaint!!.color = Color.CYAN
            canvas.drawCircle(
                (width / 2).toFloat(),
                (height / 2).toFloat(),
                px(DEBUG_CIRCLE_30_PX).toFloat(),
                debugLinePaint!!
            )
        }
        vCenterStart?.let {
            debugLinePaint!!.color = Color.RED
            canvas.drawCircle(it.x, it.y, px(DEBUG_CIRCLE_20_PX).toFloat(), debugLinePaint!!)
        }
        quickScaleSCenter?.let {
            debugLinePaint!!.color = Color.BLUE
            canvas.drawCircle(
                sourceToViewX(it.x),
                sourceToViewY(it.y),
                px(DEBUG_CIRCLE_35_PX).toFloat(),
                debugLinePaint!!
            )
        }
        quickScaleVStart?.let {
            if (isQuickScaling) {
                debugLinePaint!!.color = Color.CYAN
                canvas.drawCircle(it.x, it.y, px(DEBUG_CIRCLE_30_PX).toFloat(), debugLinePaint!!)
            }
        }
    }

    /**
     * Helper method for setting the values of a tile matrix array.
     */
    private fun setMatrixArray(
        array: FloatArray,
        vararg values: Float
    ) {
        for (i in values.indices) {
            array[i] = values[i]
        }
    }

    /**
     * Checks whether the base layer of tiles or full size bitmap is ready.
     */
    private fun isBaseLayerReady(): Boolean {
        if (bitmap != null && !bitmapIsPreview) {
            return true
        }
        val tileMap = tileManager.tileMap
        val baseGrid = tileMap?.get(fullImageSampleSize) ?: return false
        return baseGrid.none { it.loading || it.bitmap == null }
    }

    /**
     * Check whether view and image dimensions are known and either a preview, full size image or
     * base layer tiles are loaded. First time, send ready event to listener. The next draw will
     * display an image.
     */
    private fun checkReady(): Boolean {
        val ready =
            width > 0 && height > 0 && sWidth > 0 && sHeight > 0 && (bitmap != null || isBaseLayerReady())
        if (!readySent && ready) {
            preDraw()
            readySent = true
            onReady()
            onImageEventListener?.onReady()
            onImageEventListeners.forEach { it.onReady() }
            _events.tryEmit(SSIVEvent.OnReady)
        }
        return ready
    }

    /**
     * Check whether either the full size bitmap or base layer tiles are loaded. First time, send image
     * loaded event to listener.
     */
    private fun checkImageLoaded(): Boolean {
        val imageLoaded = isBaseLayerReady()
        if (!imageLoadedSent && imageLoaded) {
            preDraw()
            imageLoadedSent = true
            onImageLoaded()
            onImageEventListener?.onImageLoaded()
            onImageEventListeners.forEach { it.onImageLoaded() }
            _events.tryEmit(SSIVEvent.OnImageLoaded)
        }
        return imageLoaded
    }

    /**
     * Creates Paint objects once when first needed.
     */
    private fun createPaints() {
        if (bitmapPaint == null) {
            bitmapPaint = Paint()
            bitmapPaint!!.isAntiAlias = true
            bitmapPaint!!.isFilterBitmap = true
            bitmapPaint!!.isDither = true
        }
        if ((debugTextPaint == null || debugLinePaint == null) && debug) {
            debugTextPaint = Paint()
            debugTextPaint!!.textSize = px(DEBUG_TEXT_SIZE_DP).toFloat()
            debugTextPaint!!.color = Color.MAGENTA
            debugTextPaint!!.style = Style.FILL
            debugLinePaint = Paint()
            debugLinePaint!!.color = Color.MAGENTA
            debugLinePaint!!.style = Style.STROKE
            debugLinePaint!!.strokeWidth = px(DEBUG_LINE_WIDTH_PX).toFloat()
        }
    }

    /**
     * Called on first draw when the view has dimensions. Calculates the initial sample size and starts async loading of
     * the base layer image - the whole source subsampled as necessary.
     */
    @Synchronized
    private fun initialiseBaseLayer(maxTileDimensions: Point) {
        debug(
            "initialiseBaseLayer maxTileDimensions=%dx%d",
            maxTileDimensions.x,
            maxTileDimensions.y
        )

        satTemp = ScaleAndTranslate(0f, PointF(0f, 0f))
        fitToBounds(true, satTemp!!)

        // Load double resolution - next level will be split into four tiles and at the center all four are required,
        // so don't bother with tiling until the next level 16 tiles are needed.
        fullImageSampleSize = calculateInSampleSize(satTemp!!.scale)
        if (fullImageSampleSize > 1) {
            fullImageSampleSize /= 2
        }

        if (fullImageSampleSize == FULL_IMAGE_SAMPLE_SIZE && sRegion == null && sWidth() < maxTileDimensions.x && sHeight() < maxTileDimensions.y) {

            // Whole image is required at native resolution, and is smaller than the canvas max bitmap size.
            // Use BitmapDecoder for better image support.
            decoderLock.writeLock().lock()
            try {
                decoder?.recycle()
                decoder = null
            } finally {
                decoderLock.writeLock().unlock()
            }
            if (buffer != null) {
                executeBitmapLoadTask(context, bitmapDecoderFactory, buffer!!, false)
            } else {
                executeBitmapLoadTask(context, bitmapDecoderFactory, uri!!, false)
            }

        } else {

            tileManager.initialiseTileMap(
                TileManager.TileMapInitParams(
                    maxTileDimensions,
                    fullImageSampleSize,
                    sWidth(),
                    sHeight(),
                    width,
                    height
                )
            )

            val baseGrid = tileManager.tileMap!![fullImageSampleSize]
            if (baseGrid != null) {
                for (baseTile in baseGrid) {
                    executeTileLoadTask(decoder!!, baseTile)
                }
            }
            refreshRequiredTiles(true)

        }

    }

    private fun refreshRequiredTiles(load: Boolean) {
        if (load) {
            val now = System.currentTimeMillis()
            if (now - lastRefreshTime < REFRESH_THROTTLE_MS) {
                return
            }
            lastRefreshTime = now
        }

        val marginX = width * 0.5f
        val marginY = height * 0.5f
        val sVisRect = RectF(
            viewToSourceX(-marginX),
            viewToSourceY(-marginY),
            viewToSourceX(width + marginX),
            viewToSourceY(height + marginY)
        )
        tileManager.refreshRequiredTiles(
            TileManager.RefreshParams(
                load,
                scale,
                fullImageSampleSize,
                decoder,
                sVisRect,
                { calculateInSampleSize(it) },
                { d, t -> executeTileLoadTask(d, t) }
            )
        )
    }

    fun setOnEdgeReachedListener(listener: OnEdgeReachedListener?) {
        this.onEdgeReachedListener = listener
    }

    interface OnEdgeReachedListener {
        fun onEdgeReached(left: Boolean, top: Boolean, right: Boolean, bottom: Boolean)
    }

    private fun notifyEdgeReached(left: Boolean, top: Boolean, right: Boolean, bottom: Boolean) {
        onEdgeReachedListener?.onEdgeReached(left, top, right, bottom)
    }

    /**
     * Sets scale and translate ready for the next draw.
     */
    private fun preDraw() {
        if (width == 0 || height == 0 || sWidth <= 0 || sHeight <= 0) {
            return
        }

        // If waiting to translate to new center position, set translate now
        if (sPendingCenter != null && pendingScale != null) {
            scale = pendingScale!!
            if (vTranslate == null) {
                vTranslate = PointF()
            }
            vTranslate!!.x = width / 2 - scale * sPendingCenter!!.x
            vTranslate!!.y = height / 2 - scale * sPendingCenter!!.y
            sPendingCenter = null
            pendingScale = null
            fitToBounds(true)
            refreshRequiredTiles(true)
        }

        // On first display of base image set up position, and in other cases make sure scale is correct.
        fitToBounds(false)
    }

    /**
     * Calculates sample size to fit the source image in given bounds.
     */
    private fun calculateInSampleSize(scale: Float): Int {
        var mutableScale = scale
        if (minimumTileDpi > 0) {
            val metrics = resources.displayMetrics
            val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
            mutableScale *= minimumTileDpi / averageDpi
        }

        val reqWidth = (sWidth() * mutableScale).toInt()
        val reqHeight = (sHeight() * mutableScale).toInt()

        // Raw height and width of image
        var inSampleSize = 1
        if (reqWidth == 0 || reqHeight == 0) {
            return 32
        }

        if (sHeight() > reqHeight || sWidth() > reqWidth) {

            // Calculate ratios of height and width to requested height and width
            val heightRatio = (sHeight().toFloat() / reqHeight.toFloat()).roundToInt()
            val widthRatio = (sWidth().toFloat() / reqWidth.toFloat()).roundToInt()

            // Choose the smallest ratio as inSampleSize value, this will guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
        }

        // We want the actual sample size that will be used, so round down to nearest power of 2.
        var power = 1
        while (power * 2 < inSampleSize) {
            power *= 2
        }

        return power
    }

    /**
     * Adjusts hypothetical future scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
     * is set so one dimension fills the view and the image is centered on the other dimension. Used to calculate what the target of an
     * animation should be.
     * @param center Whether the image should be centered in the dimension it's too small to fill. While animating this can be false to avoid changes in direction as bounds are reached.
     * @param sat The scale we want and the translation we're aiming for. The values are adjusted to be valid.
     */
    internal fun fitToBounds(center: Boolean, sat: ScaleAndTranslate) {
        var mutableCenter = center
        if (panLimit == PAN_LIMIT_OUTSIDE && isReady) {
            mutableCenter = false
        }

        val vTranslate = sat.vTranslate
        val scale = limitedScale(sat.scale)
        val scaleWidth = scale * sWidth()
        val scaleHeight = scale * sHeight()

        if (panLimit == PAN_LIMIT_CENTER && isReady) {
            vTranslate.x = max(vTranslate.x, width / 2 - scaleWidth)
            vTranslate.y = max(vTranslate.y, height / 2 - scaleHeight)
        } else if (mutableCenter) {
            vTranslate.x = max(vTranslate.x, width - scaleWidth)
            vTranslate.y = max(vTranslate.y, height - scaleHeight)
        } else {
            vTranslate.x = max(vTranslate.x, -scaleWidth)
            vTranslate.y = max(vTranslate.y, -scaleHeight)
        }

        // Asymmetric padding adjustments
        val xPaddingRatio =
            if (paddingLeft > 0 || paddingRight > 0) {
                paddingLeft.toFloat() / (paddingLeft + paddingRight)
            } else {
                CENTER_RATIO
            }
        val yPaddingRatio =
            if (paddingTop > 0 || paddingBottom > 0) {
                paddingTop.toFloat() / (paddingTop + paddingBottom)
            } else {
                CENTER_RATIO
            }

        val maxTx: Float
        val maxTy: Float
        if (panLimit == PAN_LIMIT_CENTER && isReady) {
            maxTx = max(0f, (width / 2).toFloat())
            maxTy = max(0f, (height / 2).toFloat())
        } else if (mutableCenter) {
            maxTx = max(0f, (width - scaleWidth) * xPaddingRatio)
            maxTy = max(0f, (height - scaleHeight) * yPaddingRatio)
        } else {
            maxTx = max(0f, width.toFloat())
            maxTy = max(0f, height.toFloat())
        }

        vTranslate.x = min(vTranslate.x, maxTx)
        vTranslate.y = min(vTranslate.y, maxTy)

        sat.scale = scale
    }

    /**
     * Adjusts current scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
     * is set so one dimension fills the view and the image is centered on the other dimension.
     * @param center Whether the image should be centered in the dimension it's too small to fill. While animating this can be false to avoid changes in direction as bounds are reached.
     */
    internal fun fitToBounds(center: Boolean) {
        var init = false
        if (vTranslate == null) {
            init = true
            vTranslate = PointF(0f, 0f)
        }
        if (satTemp == null) {
            satTemp = ScaleAndTranslate(0f, PointF(0f, 0f))
        }
        satTemp!!.scale = scale
        satTemp!!.vTranslate.set(vTranslate!!)
        fitToBounds(center, satTemp!!)
        scale = satTemp!!.scale
        vTranslate!!.set(satTemp!!.vTranslate)
        if (init) {
            if (minimumScaleType == SCALE_TYPE_START || minimumScaleType == SCALE_TYPE_FIT_WIDTH) {
                vTranslate!!.set(0f, 0f)
            } else {
                vTranslate!!.set(
                    vTranslateForSCenter(
                        (sWidth() / 2).toFloat(),
                        (sHeight() / 2).toFloat(),
                        scale
                    )
                )
            }
            fitToBounds(center)
        }
        checkEdges()
    }

    private fun checkEdges() {
        val translate = vTranslate ?: return
        if (onEdgeReachedListener == null) return

        val scaleWidth = scale * sWidth()
        val scaleHeight = scale * sHeight()

        val xPaddingRatio =
            if (paddingLeft > 0 || paddingRight > 0) {
                paddingLeft.toFloat() / (paddingLeft + paddingRight)
            } else {
                CENTER_RATIO
            }
        val yPaddingRatio =
            if (paddingTop > 0 || paddingBottom > 0) {
                paddingTop.toFloat() / (paddingTop + paddingBottom)
            } else {
                CENTER_RATIO
            }

        val maxTx: Float
        val minTx: Float
        if (panLimit == PAN_LIMIT_CENTER && isReady) {
            maxTx = max(0f, (width / 2).toFloat())
            minTx = width / 2 - scaleWidth
        } else {
            maxTx = max(0f, (width - scaleWidth) * xPaddingRatio)
            minTx = min(0f, width - scaleWidth)
        }

        val maxTy: Float
        val minTy: Float
        if (panLimit == PAN_LIMIT_CENTER && isReady) {
            maxTy = max(0f, (height / 2).toFloat())
            minTy = height / 2 - scaleHeight
        } else {
            maxTy = max(0f, (height - scaleHeight) * yPaddingRatio)
            minTy = min(0f, height - scaleHeight)
        }

        val atLeft = translate.x >= maxTx - 1f
        val atRight = translate.x <= minTx + 1f
        val atTop = translate.y >= maxTy - 1f
        val atBottom = translate.y <= minTy + 1f

        notifyEdgeReached(atLeft, atTop, atRight, atBottom)
    }

    /**
     * Once source image and view dimensions are known, creates a map of sample size to tile grid.
     * Async task used to get image details without blocking the UI thread.
     * Called by worker task when decoder is ready and image size and EXIF orientation is known.
     */
    @Synchronized
    private fun onTilesInited(
        decoder: ImageRegionDecoder,
        sWidth: Int,
        sHeight: Int,
        sOrientation: Int
    ) {
        debug("onTilesInited sWidth=%d, sHeight=%d, sOrientation=%d", sWidth, sHeight, orientation)
        // If actual dimensions don't match the declared size, reset everything.
        if (this.sWidth > 0 && this.sHeight > 0 && (this.sWidth != sWidth || this.sHeight != sHeight)) {
            reset(false)
            if (bitmap != null) {
                if (!bitmapIsCached) {
                    bitmap!!.recycle()
                }
                bitmap = null
                if (bitmapIsCached) {
                    onImageEventListener?.onPreviewReleased()
                    onImageEventListeners.forEach { it.onPreviewReleased() }
                    _events.tryEmit(SSIVEvent.OnPreviewReleased)
                }
                bitmapIsPreview = false
                bitmapIsCached = false
            }
        }
        this.decoder = decoder
        this.sWidth = sWidth
        this.sHeight = sHeight
        this.sOrientation = sOrientation
        checkReady()
        if (!checkImageLoaded() && maxTileWidth > 0 && maxTileWidth != TILE_SIZE_AUTO && maxTileHeight > 0 && maxTileHeight != TILE_SIZE_AUTO && width > 0 && height > 0) {
            initialiseBaseLayer(Point(maxTileWidth, maxTileHeight))
        }
        invalidate()
        requestLayout()
    }

    /**
     * Async task used to load images without blocking the UI thread.
     */
    /**
     * Called by worker task when a tile has loaded. Redraws the view.
     */
    private fun onTileLoaded() {
        debug("onTileLoaded")
        checkReady()
        checkImageLoaded()
        if (isBaseLayerReady() && bitmap != null) {
            if (!bitmapIsCached) {
                bitmap!!.recycle()
            }
            bitmap = null
            if (bitmapIsCached) {
                onImageEventListener?.onPreviewReleased()
                onImageEventListeners.forEach { it.onPreviewReleased() }
                _events.tryEmit(SSIVEvent.OnPreviewReleased)
            }
            bitmapIsPreview = false
            bitmapIsCached = false
        }
        invalidate()
    }

    /**
     * Async task used to load bitmap without blocking the UI thread.
     */
    /**
     * Called by worker task when preview image is loaded.
     */
    @Synchronized
    private fun onPreviewLoaded(previewBitmap: Bitmap) {
        debug("onPreviewLoaded")
        if (bitmap != null || imageLoadedSent) {
            previewBitmap.recycle()
            return
        }
        bitmap = if (pRegion != null) {
            Bitmap.createBitmap(
                previewBitmap,
                pRegion!!.left,
                pRegion!!.top,
                pRegion!!.width(),
                pRegion!!.height()
            )
        } else {
            previewBitmap
        }
        bitmapIsPreview = true
        if (checkReady()) {
            invalidate()
            requestLayout()
        }
    }

    /**
     * Called by worker task when full size image bitmap is ready (tiling is disabled).
     */
    @Synchronized
    private fun onImageLoaded(bitmap: Bitmap, sOrientation: Int, bitmapIsCached: Boolean) {
        debug("onImageLoaded")
        // Handle HDR and Wide Color Gamut (DCI-P3) support
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupColorMode(bitmap)
        }
        // If actual dimensions don't match the declared size, reset everything.
        if (this.sWidth > 0 && this.sHeight > 0 && (this.sWidth != bitmap.width || this.sHeight != bitmap.height)) {
            reset(false)
        }
        if (this.bitmap != null && !this.bitmapIsCached) {
            this.bitmap!!.recycle()
        }

        if (this.bitmap != null && this.bitmapIsCached) {
            onImageEventListener?.onPreviewReleased()
            _events.tryEmit(SSIVEvent.OnPreviewReleased)
        }

        this.bitmapIsPreview = false
        this.bitmapIsCached = bitmapIsCached
        this.bitmap = bitmap
        this.sWidth = bitmap.width
        this.sHeight = bitmap.height
        this.sOrientation = sOrientation
        val ready = checkReady()
        val imageLoaded = checkImageLoaded()
        if (ready || imageLoaded) {
            invalidate()
            requestLayout()
        }
    }


    /**
     * Set scale, center and orientation from saved state.
     */
    private fun restoreState(state: ImageViewState) {
        if (VALID_ORIENTATIONS.contains(state.orientation)) {
            this.orientation = state.orientation
            this.pendingScale = state.scale
            this.sPendingCenter = state.center
            invalidate()
        }
    }

    /**
     * By default, the View automatically calculates the optimal tile size. Set this to override this, and force an upper limit to the dimensions of the generated tiles. Passing [TILE_SIZE_AUTO] will re-enable the default behavior.
     *
     * @param maxPixels Maximum tile size X and Y in pixels.
     */
    fun setMaxTileSize(maxPixels: Int) {
        this.maxTileWidth = maxPixels
        this.maxTileHeight = maxPixels
    }

    /**
     * By default, the View automatically calculates the optimal tile size. Set this to override this, and force an upper limit to the dimensions of the generated tiles. Passing [TILE_SIZE_AUTO] will re-enable the default behavior.
     *
     * @param maxPixelsX Maximum tile width.
     * @param maxPixelsY Maximum tile height.
     */
    fun setMaxTileSize(maxPixelsX: Int, maxPixelsY: Int) {
        this.maxTileWidth = maxPixelsX
        this.maxTileHeight = maxPixelsY
    }

    /**
     * Use canvas max bitmap width and height instead of the default 2048, to avoid redundant tiling.
     */
    private fun getMaxBitmapDimensions(canvas: Canvas): Point {
        return Point(
            min(canvas.maximumBitmapWidth, maxTileWidth),
            min(canvas.maximumBitmapHeight, maxTileHeight)
        )
    }

    /**
     * Get source width taking rotation into account.
     */
    @Suppress("SuspiciousNameCombination")
    private fun sWidth(): Int {
        val rotation = getRequiredRotation()
        return if (rotation == ORIENTATION_90 || rotation == ORIENTATION_270) {
            sHeight
        } else {
            sWidth
        }
    }

    /**
     * Get source height taking rotation into account.
     */
    @Suppress("SuspiciousNameCombination")
    private fun sHeight(): Int {
        val rotation = getRequiredRotation()
        return if (rotation == ORIENTATION_90 || rotation == ORIENTATION_270) {
            sWidth
        } else {
            sHeight
        }
    }

    /**
     * Converts source rectangle from tile, which treats the image file as if it were in the correct orientation already,
     * to the rectangle of the image that needs to be loaded.
     */
    @Suppress("SuspiciousNameCombination")
    @AnyThread
    private fun fileSRect(sRect: Rect, target: Rect) {
        when (getRequiredRotation()) {
            ORIENTATION_0 -> target.set(sRect)
            ORIENTATION_90 -> target.set(
                sRect.top,
                sHeight - sRect.right,
                sRect.bottom,
                sHeight - sRect.left
            )

            ORIENTATION_180 -> target.set(
                sWidth - sRect.right,
                sHeight - sRect.bottom,
                sWidth - sRect.left,
                sHeight - sRect.top
            )

            else -> target.set(sWidth - sRect.bottom, sRect.left, sWidth - sRect.top, sRect.right)
        }
    }

    /**
     * Determines the rotation to be applied to tiles, based on EXIF orientation or chosen setting.
     */
    @AnyThread
    private fun getRequiredRotation(): Int {
        return if (orientation == ORIENTATION_USE_EXIF) {
            sOrientation
        } else {
            orientation
        }
    }


    /**
     * Releases all resources the view is using and resets the state, nulling any fields that use significant memory.
     * After you have called this method, the view can be re-used by setting a new image. Settings are remembered
     * but state (scale and center) is forgotten. You can restore these yourself if required.
     */
    fun recycle() {
        reset(true)
        bitmapPaint = null
        debugTextPaint = null
        debugLinePaint = null
        tileBgPaint = null
    }

    /**
     * Convert screen to source x coordinate.
     */
    private fun viewToSourceX(vx: Float): Float {
        return if (vTranslate == null) Float.NaN else (vx - vTranslate!!.x) / scale
    }

    /**
     * Convert screen to source y coordinate.
     */
    private fun viewToSourceY(vy: Float): Float {
        return if (vTranslate == null) Float.NaN else (vy - vTranslate!!.y) / scale
    }

    /**
     * Converts a rectangle within the view to the corresponding rectangle from the source file, taking
     * into account the current scale, translation, orientation and clipped region. This can be used
     * to decode a bitmap from the source file.
     *
     * This method will only work when the image has fully initialized, after [isReady] returns
     * true. It is not guaranteed to work with preloaded bitmaps.
     *
     * The result is written to the fRect argument. Re-use a single instance for efficiency.
     * @param vRect rectangle representing the view area to interpret.
     * @param fRect rectangle instance to which the result will be written. Re-use for efficiency.
     */
    fun viewToFileRect(vRect: Rect, fRect: Rect) {
        if (vTranslate == null || !readySent) {
            return
        }
        fRect.set(
            viewToSourceX(vRect.left.toFloat()).toInt(),
            viewToSourceY(vRect.top.toFloat()).toInt(),
            viewToSourceX(vRect.right.toFloat()).toInt(),
            viewToSourceY(vRect.bottom.toFloat()).toInt()
        )
        fileSRect(fRect, fRect)
        fRect.set(
            max(0, fRect.left),
            max(0, fRect.top),
            min(sWidth, fRect.right),
            min(sHeight, fRect.bottom)
        )
        sRegion?.let { fRect.offset(it.left, it.top) }
    }

    /**
     * Find the area of the source file that is currently visible on screen, taking into account the
     * current scale, translation, orientation and clipped region. This is a convenience method; see
     * [viewToFileRect].
     * @param fRect rectangle instance to which the result will be written. Re-use for efficiency.
     */
    fun visibleFileRect(fRect: Rect) {
        if (vTranslate == null || !readySent) {
            return
        }
        fRect.set(0, 0, width, height)
        viewToFileRect(fRect, fRect)
    }

    /**
     * Convert screen coordinate to source coordinate.
     * @param vxy view X/Y coordinate.
     * @return a coordinate representing the corresponding source coordinate.
     */
    fun viewToSourceCoord(vxy: PointF): PointF? {
        return viewToSourceCoord(vxy.x, vxy.y, PointF())
    }

    /**
     * Convert screen coordinate to source coordinate.
     * @param vx view X coordinate.
     * @param vy view Y coordinate.
     * @return a coordinate representing the corresponding source coordinate.
     */
    fun viewToSourceCoord(vx: Float, vy: Float): PointF? {
        return viewToSourceCoord(vx, vy, PointF())
    }

    /**
     * Convert screen coordinate to source coordinate.
     * @param vxy view coordinates to convert.
     * @param sTarget target object for result. The same instance is also returned.
     * @return source coordinates. This is the same instance passed to the sTarget param.
     */
    fun viewToSourceCoord(vxy: PointF, sTarget: PointF): PointF? {
        return viewToSourceCoord(vxy.x, vxy.y, sTarget)
    }

    /**
     * Convert screen coordinate to source coordinate.
     * @param vx view X coordinate.
     * @param vy view Y coordinate.
     * @param sTarget target object for result. The same instance is also returned.
     * @return source coordinates. This is the same instance passed to the sTarget param.
     */
    fun viewToSourceCoord(vx: Float, vy: Float, sTarget: PointF): PointF? {
        if (vTranslate == null) {
            return null
        }
        sTarget.set(viewToSourceX(vx), viewToSourceY(vy))
        return sTarget
    }

    /**
     * Convert source to view x coordinate.
     */
    private fun sourceToViewX(sx: Float): Float {
        return if (vTranslate == null) Float.NaN else sx * scale + vTranslate!!.x
    }

    /**
     * Convert source to view y coordinate.
     */
    private fun sourceToViewY(sy: Float): Float {
        return if (vTranslate == null) Float.NaN else sy * scale + vTranslate!!.y
    }

    /**
     * Convert source coordinate to view coordinate.
     * @param sxy source coordinates to convert.
     * @return view coordinates.
     */
    fun sourceToViewCoord(sxy: PointF): PointF? {
        return sourceToViewCoord(sxy.x, sxy.y, PointF())
    }

    /**
     * Convert source coordinate to view coordinate.
     * @param sx source X coordinate.
     * @param sy source Y coordinate.
     * @return view coordinates.
     */
    fun sourceToViewCoord(sx: Float, sy: Float): PointF? {
        return sourceToViewCoord(sx, sy, PointF())
    }

    /**
     * Convert source coordinate to view coordinate.
     * @param sxy source coordinates to convert.
     * @param vTarget target object for result. The same instance is also returned.
     * @return view coordinates. This is the same instance passed to the vTarget param.
     */
    fun sourceToViewCoord(sxy: PointF, vTarget: PointF): PointF? {
        return sourceToViewCoord(sxy.x, sxy.y, vTarget)
    }

    /**
     * Convert source coordinate to view coordinate.
     * @param sx source X coordinate.
     * @param sy source Y coordinate.
     * @param vTarget target object for result. The same instance is also returned.
     * @return view coordinates. This is the same instance passed to the vTarget param.
     */
    fun sourceToViewCoord(sx: Float, sy: Float, vTarget: PointF): PointF? {
        if (vTranslate == null) {
            return null
        }
        vTarget.set(sourceToViewX(sx), sourceToViewY(sy))
        return vTarget
    }

    /**
     * Convert source rect to screen rect, integer values.
     */
    private fun sourceToViewRect(sRect: Rect, vTarget: Rect) {
        vTarget.set(
            sourceToViewX(sRect.left.toFloat()).toInt(),
            sourceToViewY(sRect.top.toFloat()).toInt(),
            sourceToViewX(sRect.right.toFloat()).toInt(),
            sourceToViewY(sRect.bottom.toFloat()).toInt()
        )
    }

    /**
     * Get the translation required to place a given source coordinate at the center of the screen, with the center
     * adjusted for asymmetric padding. Accepts the desired scale as an argument, so this is independent of current
     * translate and scale. The result is fitted to bounds, putting the image point as near to the screen center as permitted.
     */
    private fun vTranslateForSCenter(sCenterX: Float, sCenterY: Float, scale: Float): PointF {
        val vxCenter = paddingLeft + (width - paddingRight - paddingLeft) / 2
        val vyCenter = paddingTop + (height - paddingBottom - paddingTop) / 2
        if (satTemp == null) {
            satTemp = ScaleAndTranslate(0f, PointF(0f, 0f))
        }
        satTemp!!.scale = scale
        satTemp!!.vTranslate.set(vxCenter - sCenterX * scale, vyCenter - sCenterY * scale)
        fitToBounds(true, satTemp!!)
        return satTemp!!.vTranslate
    }

    /**
     * Given a requested source center and scale, calculate what the actual center will have to be to keep the image in
     * pan limits, keeping the requested center as near to the middle of the screen as allowed.
     */
    internal fun limitedSCenter(
        sCenterX: Float,
        sCenterY: Float,
        scale: Float,
        sTarget: PointF
    ): PointF {
        val vTranslate = vTranslateForSCenter(sCenterX, sCenterY, scale)
        val vxCenter = paddingLeft + (width - paddingRight - paddingLeft) / 2
        val vyCenter = paddingTop + (height - paddingBottom - paddingTop) / 2
        val sx = (vxCenter - vTranslate.x) / scale
        val sy = (vyCenter - vTranslate.y) / scale
        sTarget.set(sx, sy)
        return sTarget
    }

    /**
     * Returns the minimum allowed scale.
     */
    private fun minScale(): Float {
        val vPadding = paddingBottom + paddingTop
        val hPadding = paddingLeft + paddingRight
        return when (minimumScaleType) {
            SCALE_TYPE_CENTER_CROP, SCALE_TYPE_START -> max(
                (width - hPadding) / sWidth().toFloat(),
                (height - vPadding) / sHeight().toFloat()
            )

            SCALE_TYPE_FIT_WIDTH -> (width - hPadding) / sWidth().toFloat()

            SCALE_TYPE_CUSTOM if minScale > 0 -> minScale
            else -> min(
                (width - hPadding) / sWidth().toFloat(),
                (height - vPadding) / sHeight().toFloat()
            )
        }
    }

    /**
     * Adjust a requested scale to be within the allowed limits.
     */
    internal fun limitedScale(targetScale: Float): Float {
        var mutableTargetScale = max(minScale(), targetScale)
        mutableTargetScale = min(maxScale, mutableTargetScale)
        return mutableTargetScale
    }

    /**
     * Apply a selected type of easing.
     * @param type Easing type, from static fields
     * @param time Elapsed time
     * @param from Start value
     * @param change Target value
     * @param duration Anm duration
     * @return Current value
     */
    private fun ease(type: Int, time: Long, from: Float, change: Float, duration: Long): Float {
        return when (type) {
            EASE_IN_OUT_QUAD -> MathUtils.easeInOutQuad(time, from, change, duration)
            EASE_OUT_QUAD -> MathUtils.easeOutQuad(time, from, change, duration)
            else -> error("Unexpected easing type: $type")
        }
    }


    /**
     * Debug logger
     */
    @AnyThread
    private fun debug(message: String, vararg args: Any) {
        if (debug) {
            Log.d(TAG, String.format(message, *args))
        }
    }

    /**
     * For debug overlays. Scale pixel value according to screen density.
     */
    private fun px(px: Int): Int {
        return (density * px).toInt()
    }

    /**
     * Swap the default region decoder implementation for one of your own. You must do this before setting the image file or
     * asset, and you cannot use a custom decoder when using layout XML to set an asset name.
     * @param regionDecoderFactory The [DecoderFactory] implementation that produces [ImageRegionDecoder]
     * instances.
     */
    fun setRegionDecoderFactory(regionDecoderFactory: DecoderFactory<out ImageRegionDecoder>) {
        this.regionDecoderFactory = regionDecoderFactory
    }

    /**
     * Swap the default bitmap decoder implementation for one of your own. You must do this before setting the image file or
     * asset, and you cannot use a custom decoder when using layout XML to set an asset name.
     * @param bitmapDecoderFactory The [DecoderFactory] implementation that produces [SSIVImageDecoder] instances.
     */
    fun setBitmapDecoderFactory(bitmapDecoderFactory: DecoderFactory<out SSIVImageDecoder>) {
        this.bitmapDecoderFactory = bitmapDecoderFactory
    }

    /**
     * Calculate how much further the image can be panned in each direction. The results are set on
     * the supplied [RectF] and expressed as screen pixels. For example, if the image cannot be
     * panned any further towards the left, the value of [RectF.left] will be set to 0.
     * @param vTarget target object for results. Re-use for efficiency.
     */
    fun getPanRemaining(vTarget: RectF) {
        if (!isReady) {
            return
        }

        val scaleWidth = scale * sWidth()
        val scaleHeight = scale * sHeight()

        when (panLimit) {
            PAN_LIMIT_CENTER -> {
                vTarget.top = max(0f, -(vTranslate!!.y - height / 2))
                vTarget.left = max(0f, -(vTranslate!!.x - width / 2))
                vTarget.bottom = max(0f, vTranslate!!.y - (height / 2 - scaleHeight))
                vTarget.right = max(0f, vTranslate!!.x - (width / 2 - scaleWidth))
            }

            PAN_LIMIT_OUTSIDE -> {
                vTarget.top = max(0f, -(vTranslate!!.y - height))
                vTarget.left = max(0f, -(vTranslate!!.x - width))
                vTarget.bottom = max(0f, vTranslate!!.y + scaleHeight)
                vTarget.right = max(0f, vTranslate!!.x + scaleWidth)
            }

            else -> {
                vTarget.top = max(0f, -vTranslate!!.y)
                vTarget.left = max(0f, -vTranslate!!.x)
                vTarget.bottom = max(0f, scaleHeight + vTranslate!!.y - height)
                vTarget.right = max(0f, scaleWidth + vTranslate!!.x - width)
            }
        }
    }

    /**
     * Set the pan limiting style. See static fields. Normally [PAN_LIMIT_INSIDE] is best, for image galleries.
     * @param panLimit a pan limit constant. See static fields.
     */
    fun setPanLimit(panLimit: Int) {
        require(VALID_PAN_LIMITS.contains(panLimit)) { "Invalid pan limit: $panLimit" }
        this.panLimit = panLimit
        if (isReady) {
            fitToBounds(true)
            invalidate()
        }
    }

    /**
     * Set the minimum scale type. See static fields. Normally [SCALE_TYPE_CENTER_INSIDE] is best, for image galleries.
     * @param scaleType a scale type constant. See static fields.
     */
    fun setMinimumScaleType(scaleType: Int) {
        require(VALID_SCALE_TYPES.contains(scaleType)) { "Invalid scale type: $scaleType" }
        this.minimumScaleType = scaleType
        if (isReady) {
            fitToBounds(true)
            invalidate()
        }
    }

    /**
     * Set the maximum scale allowed. A value of 1 means 1:1 pixels at maximum scale. You may wish to set this according
     * to screen density - on a retina screen, 1:1 may still be too small. Consider using [setMinimumDpi],
     * which is density aware.
     * @param maxScale maximum scale expressed as a source/view pixels ratio.
     */
    fun setMaxScale(maxScale: Float) {
        this.maxScale = maxScale
    }

    /**
     * Set the minimum scale allowed. A value of 1 means 1:1 pixels at minimum scale. You may wish to set this according
     * to screen density. Consider using [setMaximumDpi], which is density aware.
     * @param minScale minimum scale expressed as a source/view pixels ratio.
     */
    fun setMinScale(minScale: Float) {
        this.minScale = minScale
    }

    /**
     * This is a screen density aware alternative to [setMaxScale]; it allows you to express the maximum
     * allowed scale in terms of the minimum pixel density. This avoids the problem of 1:1 scale still being
     * too small on a high density screen. A sensible starting point is 160 - the default used by this view.
     * @param dpi Source image pixel density at maximum zoom.
     */
    fun setMinimumDpi(dpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        setMaxScale(averageDpi / dpi)
    }

    /**
     * This is a screen density aware alternative to [setMinScale]; it allows you to express the minimum
     * allowed scale in terms of the maximum pixel density.
     * @param dpi Source image pixel density at minimum zoom.
     */
    fun setMaximumDpi(dpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        setMinScale(averageDpi / dpi)
    }

    /**
     * Returns the maximum allowed scale.
     * @return the maximum scale as a source/view pixels ratio.
     */
    fun getMaxScale(): Float {
        return maxScale
    }

    /**
     * Returns the minimum allowed scale.
     * @return the minimum scale as a source/view pixels ratio.
     */
    fun getMinScale(): Float {
        return minScale()
    }

    /**
     * By default, image tiles are at least as high resolution as the screen. For a retina screen this may not be
     * necessary, and may increase the likelihood of an OutOfMemoryError. This method sets a DPI at which higher
     * resolution tiles should be loaded. Using a lower number will on average use less memory but result in a lower
     * quality image. 160-240dpi will usually be enough. This should be called before setting the image source,
     * because it affects which tiles get loaded. When using an untiled source image this method has no effect.
     * @param minimumTileDpi Tile loading threshold.
     */
    fun setMinimumTileDpi(minimumTileDpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        this.minimumTileDpi = min(averageDpi, minimumTileDpi.toFloat()).toInt()
        if (isReady) {
            reset(false)
            invalidate()
        }
    }

    /**
     * Returns the source point at the center of the view.
     * @return the source coordinates current at the center of the view.
     */
    val center: PointF?
        get() {
            val mX = width / 2
            val mY = height / 2
            return viewToSourceCoord(mX.toFloat(), mY.toFloat())
        }

    /**
     * Returns the current scale value.
     * @return the current scale as a source/view pixels ratio.
     */
    fun getScale(): Float {
        return scale
    }

    /**
     * Externally change the scale and translation of the source image. This may be used with getCenter() and getScale()
     * to restore the scale and zoom after a screen rotate.
     * @param scale New scale to set.
     * @param sCenter New source image coordinate to center on the screen, subject to boundaries.
     */
    fun setScaleAndCenter(scale: Float, sCenter: PointF?) {
        this.anim = null
        this.pendingScale = scale
        this.sPendingCenter = sCenter
        this.sRequestedCenter = sCenter
        invalidate()
    }

    /**
     * Fully zoom out and return the image to the middle of the screen. This might be useful if you have a view pager
     * and want images to be reset when the user has moved to another page.
     */
    fun resetScaleAndCenter() {
        this.anim = null
        this.pendingScale = limitedScale(0f)
        this.sPendingCenter = if (isReady) {
            PointF((sWidth() / 2).toFloat(), (sHeight() / 2).toFloat())
        } else {
            PointF(0f, 0f)
        }
        invalidate()
    }

    /**
     * Call to find whether the view is initialized, has dimensions, and will display an image on
     * the next draw. If a preview has been provided, it may be the preview that will be displayed
     * and the full size image may still be loading. If no preview was provided, this is called once
     * the base layer tiles of the full size image are loaded.
     * @return true if the view is ready to display an image and accept touch gestures.
     */
    val isReady: Boolean
        get() = readySent

    /**
     * Called once when the view is initialized, has dimensions, and will display an image on the
     * next draw. This is triggered at the same time as [OnImageEventListener.onReady] but
     * allows a subclass to receive this event without using a listener.
     */
    protected open fun onReady() {}

    /**
     * Call to find whether the main image (base layer tiles where relevant) have been loaded. Before
     * this event the view is blank unless a preview was provided.
     * @return true if the main image (not the preview) has been loaded and is ready to display.
     */
    val isImageLoaded: Boolean
        get() = imageLoadedSent

    /**
     * Called once when the full size image or its base layer tiles have been loaded.
     */
    protected open fun onImageLoaded() {}

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun setupColorMode(bitmap: Bitmap) {
        val activity = context as? android.app.Activity ?: return
        val window = activity.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (bitmap.hasGainmap()) {
                window.colorMode = ActivityInfo.COLOR_MODE_HDR
                return
            }
        }
        if (bitmap.colorSpace?.isWideGamut == true) {
            window.colorMode = ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
        }
    }

    /**
     * Get source width, ignoring orientation. If [getOrientation] returns 90 or 270, you can use [getSHeight]
     * for the apparent width.
     * @return the source image width in pixels.
     */
    fun getSWidth(): Int {
        return sWidth
    }

    /**
     * Get source height, ignoring orientation. If [getOrientation] returns 90 or 270, you can use [getSWidth]
     * for the apparent height.
     * @return the source image height in pixels.
     */
    fun getSHeight(): Int {
        return sHeight
    }

    /**
     * Returns the orientation setting. This can return [ORIENTATION_USE_EXIF], in which case it doesn't tell you
     * the applied orientation of the image. For that, use [getAppliedOrientation].
     * @return the orientation setting. See static fields.
     */
    fun getOrientation(): Int {
        return orientation
    }

    /**
     * Returns the actual orientation of the image relative to the source file. This will be based on the source file's
     * EXIF orientation if you're using ORIENTATION_USE_EXIF. Values are 0, 90, 180, 270.
     * @return the orientation applied after EXIF information has been extracted. See static fields.
     */
    fun getAppliedOrientation(): Int {
        return getRequiredRotation()
    }

    /**
     * Get the current state of the view (scale, center, orientation) for restoration after rotate. Will return null if
     * the view is not ready.
     * @return an [ImageViewState] instance representing the current position of the image. null if the view isn't ready.
     */
    val state: ImageViewState?
        get() {
            return if (vTranslate != null && sWidth > 0 && sHeight > 0) {
                ImageViewState(getScale(), center!!, getOrientation())
            } else null
        }

    /**
     * Returns true if zoom gesture detection is enabled.
     * @return true if zoom gesture detection is enabled.
     */
    fun zoomEnabled(): Boolean {
        return zoomEnabled
    }

    /**
     * Enable or disable zoom gesture detection. Disabling zoom locks the current scale.
     * @param zoomEnabled true to enable zoom gestures, false to disable.
     */
    fun setZoomEnabled(zoomEnabled: Boolean) {
        this.zoomEnabled = zoomEnabled
    }

    /**
     * Returns true if double tap & swipe to zoom is enabled.
     * @return true if double tap & swipe to zoom is enabled.
     */
    fun quickScaleEnabled(): Boolean {
        return quickScaleEnabled
    }

    /**
     * Enable or disable double tap & swipe to zoom.
     * @param quickScaleEnabled true to enable quick scale, false to disable.
     */
    fun setQuickScaleEnabled(quickScaleEnabled: Boolean) {
        this.quickScaleEnabled = quickScaleEnabled
    }

    /**
     * Returns true if pan gesture detection is enabled.
     * @return true if pan gesture detection is enabled.
     */
    fun panEnabled(): Boolean {
        return panEnabled
    }

    /**
     * Enable or disable pan gesture detection. Disabling pan causes the image to be centered. Pan
     * can still be changed from code.
     * @param panEnabled true to enable panning, false to disable.
     */
    fun setPanEnabled(panEnabled: Boolean) {
        this.panEnabled = panEnabled
        if (!panEnabled && vTranslate != null) {
            vTranslate!!.x = width / 2 - scale * (sWidth() / 2)
            vTranslate!!.y = height / 2 - scale * (sHeight() / 2)
            if (isReady) {
                refreshRequiredTiles(true)
                invalidate()
            }
        }
    }

    /**
     * Set a solid color to render behind tiles, useful for displaying transparent PNGs.
     * @param tileBgColor Background color for tiles.
     */
    fun setTileBackgroundColor(tileBgColor: Int) {
        if (Color.alpha(tileBgColor) == 0) {
            tileBgPaint = null
        } else {
            tileBgPaint = Paint()
            tileBgPaint!!.style = Style.FILL
            tileBgPaint!!.color = tileBgColor
        }
        invalidate()
    }

    /**
     * Set the scale the image will zoom in to when double tapped. This also the scale point where a double tap is interpreted
     * as a zoom out gesture - if the scale is greater than 90% of this value, a double tap zooms out. Avoid using values
     * greater than the max zoom.
     * @param doubleTapZoomScale New value for double tap gesture zoom scale.
     */
    fun setDoubleTapZoomScale(doubleTapZoomScale: Float) {
        this.doubleTapZoomScale = doubleTapZoomScale
    }

    /**
     * A density aware alternative to [setDoubleTapZoomScale]; this allows you to express the scale the
     * image will zoom in to when double tapped in terms of the image pixel density. Values lower than the max scale will
     * be ignored. A sensible starting point is 160 - the default used by this view.
     * @param dpi New value for double tap gesture zoom scale.
     */
    fun setDoubleTapZoomDpi(dpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        setDoubleTapZoomScale(averageDpi / dpi)
    }

    /**
     * Set the type of zoom animation to be used for double taps. See static fields.
     * @param doubleTapZoomStyle New value for zoom style.
     */
    fun setDoubleTapZoomStyle(doubleTapZoomStyle: Int) {
        require(VALID_ZOOM_STYLES.contains(doubleTapZoomStyle)) { "Invalid zoom style: $doubleTapZoomStyle" }
        this.doubleTapZoomStyle = doubleTapZoomStyle
    }

    /**
     * Set the duration of the double tap zoom animation.
     * @param durationMs Duration in milliseconds.
     */
    fun setDoubleTapZoomDuration(durationMs: Int) {
        this.doubleTapZoomDuration = max(0, durationMs)
    }

    /**
     * Enable or disable eager loading of tiles
    that appear on screen during gestures or animations,
     * while the gesture or animation is still in progress. By default, this is enabled to improve
     * responsiveness, but it can result in tiles being loaded and discarded more rapidly than
     * necessary and reduce the animation frame rate on old/cheap devices. Disable this on older
     * devices if you see poor performance. Tiles will then be loaded only when gestures and animations
     * are completed.
     * @param eagerLoadingEnabled true to enable loading during gestures, false to delay loading until gestures end
     */
    fun setEagerLoadingEnabled(eagerLoadingEnabled: Boolean) {
        this.eagerLoadingEnabled = eagerLoadingEnabled
    }

    /**
     * Enables visual debugging, showing tile boundaries and sizes.
     * @param debug true to enable debugging, false to disable.
     */
    fun setDebug(debug: Boolean) {
        this.debug = debug
    }

    /**
     * Check if an image has been set. The image may not have been loaded and displayed yet.
     * @return If an image is currently set.
     */
    fun hasImage(): Boolean {
        return uri != null || bitmap != null
    }

    /**
     * {@inheritDoc}
     */
    override fun setOnLongClickListener(onLongClickListener: OnLongClickListener?) {
        this.onLongClickListener = onLongClickListener
    }

    /**
     * Set a listener allowing notification of load and error events.
     * @param onImageEventListener an [OnImageEventListener] instance.
     */
    fun setOnImageEventListener(onImageEventListener: OnImageEventListener?) {
        this.onImageEventListener = onImageEventListener
    }

    /**
     * Add a listener allowing notification of load and error events.
     * @param onImageEventListener an [OnImageEventListener] instance.
     */
    fun addOnImageEventListener(onImageEventListener: OnImageEventListener) {
        this.onImageEventListeners.add(onImageEventListener)
    }

    /**
     * Remove a listener.
     * @param onImageEventListener an [OnImageEventListener] instance.
     */
    fun removeOnImageEventListener(onImageEventListener: OnImageEventListener) {
        this.onImageEventListeners.remove(onImageEventListener)
    }

    /**
     * Set a listener for pan and zoom events.
     * @param onStateChangedListener an [OnStateChangedListener] instance.
     */
    fun setOnStateChangedListener(onStateChangedListener: OnStateChangedListener?) {
        this.onStateChangedListener = onStateChangedListener
    }

    /**
     * Add a listener for pan and zoom events.
     * @param onStateChangedListener an [OnStateChangedListener] instance.
     */
    fun addOnStateChangedListener(onStateChangedListener: OnStateChangedListener) {
        this.onStateChangedListeners.add(onStateChangedListener)
    }

    /**
     * Remove a listener.
     * @param onStateChangedListener an [OnStateChangedListener] instance.
     */
    fun removeOnStateChangedListener(onStateChangedListener: OnStateChangedListener) {
        this.onStateChangedListeners.remove(onStateChangedListener)
    }

    /**
     * Set a lambda to be called when the scale has changed.
     */
    fun onScaleChanged(listener: (newScale: Float, origin: Int) -> Unit) {
        addOnStateChangedListener(object : OnStateChangedListener {
            override fun onScaleChanged(newScale: Float, origin: Int) {
                listener(newScale, origin)
            }
        })
    }

    /**
     * Set a lambda to be called when the source center has been changed.
     */
    fun onCenterChanged(listener: (newCenter: PointF?, origin: Int) -> Unit) {
        addOnStateChangedListener(object : OnStateChangedListener {
            override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                listener(newCenter, origin)
            }
        })
    }

    /**
     * Set a lambda to be called when the image is ready.
     */
    fun onImageReady(listener: () -> Unit) {
        addOnImageEventListener(object : OnImageEventListener {
            override fun onReady() {
                listener()
            }
        })
    }

    /**
     * Bind this view to a [LifecycleOwner] to automatically recycle resources when destroyed.
     */
    fun bindToLifecycle(owner: LifecycleOwner) {
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                recycle()
                owner.lifecycle.removeObserver(this)
            }
        })
    }

    private fun sendStateChanged(oldScale: Float, oldVTranslate: PointF, origin: Int) {
        val currentCenter = center
        if (scale != oldScale) {
            onStateChangedListener?.onScaleChanged(scale, origin)
            onStateChangedListeners.forEach { it.onScaleChanged(scale, origin) }
            _events.tryEmit(SSIVEvent.OnScaleChanged(scale, origin))
        }
        if (vTranslate != oldVTranslate) {
            onStateChangedListener?.onCenterChanged(currentCenter, origin)
            onStateChangedListeners.forEach { it.onCenterChanged(currentCenter, origin) }
            _events.tryEmit(SSIVEvent.OnCenterChanged(currentCenter, origin))
        }
    }

    /**
     * Creates a panning animation builder, that when started will animate the image to place the given coordinates of
     * the image in the center of the screen. If doing this would move the image beyond the edges of the screen, the
     * image is instead animated to move the center point as near to the center of the screen as is allowed - it's
     * guaranteed to be on screen.
     * @param sCenter Target center point
     * @return [AnimationBuilder] instance. Call [SubsamplingScaleImageView.//AnimationBuilder.start] to start the anim.
     */
    fun animateCenter(sCenter: PointF): AnimationBuilder? {
        return if (!isReady) null else AnimationBuilder(this, sCenter)
    }

    /**
     * Creates a scale animation builder, that when started will animate a zoom in or out. If this moved the image
     * beyond the panning limits, the image is automatically panned during the animation.
     * @param scale Target scale.
     * @return [AnimationBuilder] instance. Call [SubsamplingScaleImageView.//AnimationBuilder.start] to start the anim.
     */
    fun animateScale(scale: Float): AnimationBuilder? {
        val sCenter = center ?: return null
        return if (!isReady) null else AnimationBuilder(this, scale, sCenter)
    }

    /**
     * Creates a scale animation builder, that when started will animate a zoom in or out. If this moved the image
     * beyond the panning limits, the image is automatically panned during the animation.
     * @param scale Target scale.
     * @param sCenter Target source center.
     * @return [AnimationBuilder] instance. Call [SubsamplingScaleImageView.//AnimationBuilder.start] to start the anim.
     */
    fun animateScaleAndCenter(scale: Float, sCenter: PointF): AnimationBuilder? {
        return if (!isReady) null else AnimationBuilder(this, scale, sCenter)
    }

    companion object {
        private val TAG = SubsamplingScaleImageView::class.java.simpleName

        /** Attempt to use EXIF information on the image to rotate it. Works for external files only. */
        const val ORIENTATION_USE_EXIF = -1

        /** Display the image file in its native orientation. */
        const val ORIENTATION_0 = 0

        /** Rotate the image 90 degrees clockwise. */
        const val ORIENTATION_90 = 90

        /** Rotate the image 180 degrees. */
        const val ORIENTATION_180 = 180

        /** Rotate the image 270 degrees clockwise. */
        const val ORIENTATION_270 = 270

        const val RESTORE_STRATEGY_DEFERRED = 1
        const val RESTORE_STRATEGY_IMMEDIATE = 2

        private val VALID_ORIENTATIONS = listOf(
            ORIENTATION_0,
            ORIENTATION_90,
            ORIENTATION_180,
            ORIENTATION_270,
            ORIENTATION_USE_EXIF
        )

        /** During zoom animation, keep the point of the image that was tapped in the same place and scale the surrounding image. */
        const val ZOOM_FOCUS_FIXED = 1

        /** During zoom animation, move the point of the image that was tapped to the center of the screen. */
        const val ZOOM_FOCUS_CENTER = 2

        /** Zoom in to and center the tapped point immediately without animating. */
        const val ZOOM_FOCUS_CENTER_IMMEDIATE = 3

        private val VALID_ZOOM_STYLES =
            listOf(ZOOM_FOCUS_FIXED, ZOOM_FOCUS_CENTER, ZOOM_FOCUS_CENTER_IMMEDIATE)

        /** Quadratic ease out. Not recommended for scale animation, but good for panning. */
        const val EASE_OUT_QUAD = 1

        /** Quadratic ease in and out. */
        const val EASE_IN_OUT_QUAD = 2

        private val VALID_EASING_STYLES = listOf(EASE_IN_OUT_QUAD, EASE_OUT_QUAD)

        /** Don't allow the image to be panned off-screen. As much of the image as possible is always displayed, centered in the view when it is smaller. This is the best option for galleries. */
        const val PAN_LIMIT_INSIDE = 1

        /** Allows the image to be panned until it is just off-screen, but no further. The edge of the image will stop when it is flush with the screen edge. */
        const val PAN_LIMIT_OUTSIDE = 2

        /** Allows the image to be panned until a corner reaches the center of the screen but no further. Useful when you want to pan any spot on the image to the exact center of the screen. */
        const val PAN_LIMIT_CENTER = 3

        private val VALID_PAN_LIMITS = listOf(PAN_LIMIT_INSIDE, PAN_LIMIT_OUTSIDE, PAN_LIMIT_CENTER)

        /** Scale the image so that both dimensions of the image will be equal to or less than the corresponding dimension of the view. The image is then centered in the view. This is the default behavior and best for galleries. */
        const val SCALE_TYPE_CENTER_INSIDE = 1

        /** Scale the image uniformly so that both dimensions of the image will be equal to or larger than the corresponding dimension of the view. The image is then centered in the view. */
        const val SCALE_TYPE_CENTER_CROP = 2

        /** Scale the image so that both dimensions of the image will be equal to or less than the maxScale and equal to or larger than minScale. The image is then centered in the view. */
        const val SCALE_TYPE_CUSTOM = 3

        /** Scale the image so that both dimensions of the image will be equal to or larger than the corresponding dimension of the view. The top left is shown. */
        const val SCALE_TYPE_START = 4

        /** Scale the image so that the width of the image will be equal to the width of the view. */
        const val SCALE_TYPE_FIT_WIDTH = 5

        private val VALID_SCALE_TYPES = listOf(
            SCALE_TYPE_CENTER_CROP,
            SCALE_TYPE_CENTER_INSIDE,
            SCALE_TYPE_CUSTOM,
            SCALE_TYPE_START,
            SCALE_TYPE_FIT_WIDTH
        )

        /** State change originated from animation. */
        const val ORIGIN_ANIM = 1

        /** State change originated from touch gesture. */
        const val ORIGIN_TOUCH = 2

        /** State change originated from a fling momentum anim. */
        const val ORIGIN_FLING = 3

        /** State change originated from a double tap zoom anim. */
        const val ORIGIN_DOUBLE_TAP_ZOOM = 4

        // overrides for the dimensions of the generated tiles
        const val TILE_SIZE_AUTO = Int.MAX_VALUE
        private const val MESSAGE_LONG_CLICK = 1
        private const val REFRESH_THROTTLE_MS = 8L

        private const val DEFAULT_ANIM_DURATION = 500
        private const val DEFAULT_MIN_DPI = 160
        private const val DEFAULT_DOUBLE_TAP_ZOOM_DPI = 160
        private const val DEFAULT_MIN_TILE_DPI = 320
        private const val LONG_CLICK_DELAY = 600L
        private const val FLING_MIN_DISTANCE = 50
        private const val FLING_MIN_VELOCITY = 500
        private const val FLING_VELOCITY_MULTIPLIER = 0.25f
        private const val TOUCH_SLOP_PX = 5
        private const val QUICK_SCALE_SPAN_DIFF_THRESHOLD = 0.03f
        private const val QUICK_SCALE_MULTIPLIER_BASE = 0.5f

        private const val DEFAULT_QUICK_SCALE_THRESHOLD_DP = 20f
        private const val DEBUG_TEXT_SIZE_DP = 12
        private const val DEBUG_LINE_WIDTH_DP = 2
        private const val DEBUG_CIRCLE_RADIUS_PX = 10
        private const val DEBUG_OFFSET_PX = 5
        private const val DEBUG_LINE_SPACING_PX = 15
        private const val DEBUG_CIRCLE_20_PX = 20
        private const val DEBUG_CIRCLE_25_PX = 25
        private const val DEBUG_CIRCLE_30_PX = 30
        private const val DEBUG_CIRCLE_35_PX = 35
        private const val DEBUG_OFFSET_Y_TRANS_PX = 30
        private const val DEBUG_OFFSET_Y_CENTER_PX = 45
        private const val DEBUG_OFFSET_Y_LOADING_PX = 35
        private const val INIT_RESULT_COUNT = 3
        private const val MIN_PINCH_TOUCH_COUNT = 2
        private const val MAX_SINGLE_TOUCH_COUNT = 1
        private const val PAN_EDGE_THRESHOLD_MULTIPLIER = 3
        private const val DOUBLE_TAP_ZOOM_THRESHOLD = 0.9f
        private const val CENTER_RATIO = 0.5f
        private const val POLY_TO_POLY_COUNT = 4
        private const val FULL_IMAGE_SAMPLE_SIZE = 1
        private const val MATRIX_ARRAY_SIZE = 8
        private const val DEBUG_LINE_WIDTH_PX = 1

        private var preferredBitmapConfig: Bitmap.Config? = defaultBitmapConfig()

        private fun defaultBitmapConfig(): Bitmap.Config {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Bitmap.Config.HARDWARE
            } else {
                Bitmap.Config.ARGB_8888
            }
        }

        /**
         * Get the current preferred configuration for decoding bitmaps. [SSIVImageDecoder] and [ImageRegionDecoder]
         * instances can read this and use it when decoding images.
         * @return the preferred bitmap configuration, or null if none has been set.
         */
        @JvmStatic
        fun getPreferredBitmapConfig(): Bitmap.Config? {
            return preferredBitmapConfig
        }

        /**
         * Set a global preferred bitmap config shared by all view instances and applied to new
         * instances initialized after the call is made. This is a hint only; the bundled
         * [SSIVImageDecoder] and [ImageRegionDecoder] classes all respect this (except when
         * they were constructed with an instance-specific config) but custom decoder classes will not.
         *
         * @param preferredBitmapConfig the bitmap configuration to be used by future instances of the view.
         * Pass null to restore the default.
         */
        @JvmStatic
        fun setPreferredBitmapConfig(preferredBitmapConfig: Bitmap.Config?) {
            SubsamplingScaleImageView.preferredBitmapConfig = preferredBitmapConfig
        }
    }
}
