package com.champignoom.paperant

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.*
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.graphics.scaleMatrix
import androidx.core.view.GestureDetectorCompat
import kotlin.math.max
import kotlin.math.min

class MyPageViewWithBlur(ctx: Context, atts: AttributeSet?): FrameLayout(ctx, atts) {
    companion object {
        private fun timestamp() = SystemClock.uptimeMillis()
        const val PAGE_DELTA_WIDTH = 1/8f
        const val FACTOR_STEP = 1.1f
    }

    private val mBlurredImage = ImageView(ctx).also { addView(it, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT) }
    private val mMyPageView  = ImageView(ctx).also {
        it.scaleType = ImageView.ScaleType.MATRIX
        addView(it, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }
    private val mProgressBar = ProgressBar(ctx).also { addView(it, LayoutParams(150, 150, Gravity.CENTER)) }

    private var lastTimestampMs = 0L

    var maxAnimationDurationMs = 100L

    private var pageMatrix = Matrix()
    private var bitmapPrecision = 1f
    private var bitmapPrecisionPending = 1f  // pending scale before finishing loading

    private val pageMatrixValueBuffer = FloatArray(9)
    private fun scaleFactor(): Float {
        pageMatrix.getValues(pageMatrixValueBuffer)
        return pageMatrixValueBuffer[Matrix.MSCALE_X]  // assume same scale for x, y
    }

    var onPageDeltaClicked: ((Int) -> Unit)? = null
    var reloader: ((w: Int, h: Int) -> Unit)? = null

    private val turnOnePageDetector = GestureDetectorCompat(context, object: GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            when {
                e.x < width * PAGE_DELTA_WIDTH -> onPageDeltaClicked?.invoke(-1)
                e.x > width * (1 - PAGE_DELTA_WIDTH) -> onPageDeltaClicked?.invoke(1)
                else -> return false
            }
            return true
        }
    })

    private val scaleDetector = ScaleGestureDetector(context, object: ScaleGestureDetector.OnScaleGestureListener {
        override fun onScaleBegin(detector: ScaleGestureDetector) = true

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            Log.d("Paperant", "onScale: factor=${detector.scaleFactor}, position=(${detector.focusX}, ${detector.focusY})")
            if (!isLoading()) {
                val currentFactor = scaleFactor()
                val deltaFactor = min(detector.scaleFactor, 3f / (bitmapPrecision * currentFactor))
                if (deltaFactor == 1f)
                    return true

                pageMatrix.postScale(deltaFactor, deltaFactor, detector.focusX, detector.focusY)
                mMyPageView.imageMatrix = pageMatrix

                if (currentFactor > bitmapPrecisionPending) {
                    while (bitmapPrecisionPending < currentFactor)
                        bitmapPrecisionPending *= FACTOR_STEP
                    val newPrecision = bitmapPrecision * bitmapPrecisionPending
                    reloader?.invoke((width*newPrecision).toInt(), (height*newPrecision).toInt())
                }
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector?) { }
    })

    private val moveDetector = GestureDetector(context, object: GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent?): Boolean {
            return true
        }
        override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!isLoading()) {
                pageMatrix.postTranslate(-distanceX, -distanceY)
                mMyPageView.imageMatrix = pageMatrix
            }
            return true
        }
    })

    fun canvasSize() = Size(width, height)

    override fun onTouchEvent(e: MotionEvent): Boolean {
        turnOnePageDetector.onTouchEvent(e)
        scaleDetector.onTouchEvent(e)
        moveDetector.onTouchEvent(e)
        return true
    }

    fun isLoading() =
        mProgressBar.visibility == VISIBLE

    // UI thread
    fun setLoading(blurredBitmap: Bitmap?) {
        mMyPageView.apply {
            setImageDrawable(null)
            clearAnimation()
            alpha = 1f
        }
        mProgressBar.visibility = VISIBLE
        if (blurredBitmap != null) {
            mBlurredImage.setImageBitmap(blurredBitmap)
        } else {
            mBlurredImage.setImageDrawable(null)
        }
        lastTimestampMs = timestamp()
    }

//    private var animationTokenTimestamp = -1L
//    private fun isProperAnimationEnd(timestamp: Long) = timestamp == animationTokenTimestamp

    private fun animateBlurred(durationMs: Long) {
        mMyPageView.alpha = 0f
        mMyPageView.animate().alpha(1f).setDuration(durationMs)
    }

    fun getBitmapPrecision(bitmap: Bitmap): Float =
        max(1f * bitmap.width /  mMyPageView.width, 1f * bitmap.height / mMyPageView.height)

    // UI thread
    fun setLoaded(bitmap: Bitmap, preserveMatrix: Boolean) {
        val oldBitmapPrecision = bitmapPrecision
        bitmapPrecision = getBitmapPrecision(bitmap)

        if (preserveMatrix) {
            val deltaFactor = oldBitmapPrecision / bitmapPrecision
            pageMatrix.preScale(deltaFactor, deltaFactor)
        } else {
            val scale = 1f / bitmapPrecision
            pageMatrix = scaleMatrix(scale, scale)
            pageMatrix.postTranslate(
                (mMyPageView.width - scale * bitmap.width) / 2,
                (mMyPageView.height - scale * bitmap.height) / 2
            )
        }

        bitmapPrecisionPending = 1f
        mMyPageView.imageMatrix = pageMatrix
        mMyPageView.setImageBitmap(bitmap)
        val wasLoading = isLoading()
        mProgressBar.visibility = GONE

        if (wasLoading && mBlurredImage.drawable != null) {
            val msElapsed = timestamp() - lastTimestampMs
            animateBlurred(min(msElapsed/2, maxAnimationDurationMs))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        reloader?.invoke(w, h)
    }
}