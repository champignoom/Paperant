package com.champignoom.paperant

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.graphics.scaleMatrix
import androidx.core.graphics.times
import androidx.core.graphics.transform
import androidx.core.view.GestureDetectorCompat
import kotlin.math.max
import kotlin.math.min

class MyPageViewWithBlur(ctx: Context, atts: AttributeSet?): FrameLayout(ctx, atts) {
    companion object {
        private fun timestamp() = SystemClock.uptimeMillis()
        const val PAGE_DELTA_WIDTH = 1/8f
        const val FACTOR_STEP = 1.1f
        const val MAX_SCALE_FACTOR = 3f
    }

    private val mBlurredImage = ImageView(ctx).also { addView(it, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT) }
    private val mMyPageView  = ImageView(ctx).also {
        it.scaleType = ImageView.ScaleType.MATRIX
        addView(it, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }
    private val mProgressBar = ProgressBar(ctx).also { addView(it, LayoutParams(150, 150, Gravity.CENTER)) }

    private var lastTimestampMs = 0L

    var maxAnimationDurationMs = 100L

    private var overallMatrix = Matrix()
    private var bitmapPrecision = 1f
    private var bitmapPrecisionPending = 1f  // pending scale before finishing loading
    private var bitmapSize = Size(0, 0)

    private val overallMatrixValueBuffer = FloatArray(9)
    private fun scaleFactor(): Float {
        overallMatrix.getValues(overallMatrixValueBuffer)
        return overallMatrixValueBuffer[Matrix.MSCALE_X]  // assume same scale for x, y
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
                val currentVisualFactor = scaleFactor() * bitmapPrecision
                val deltaFactor = detector.scaleFactor.coerceIn(1f / currentVisualFactor, MAX_SCALE_FACTOR / currentVisualFactor)
                Log.d("Paperant", "onScale coersed = ${deltaFactor}")
                if (deltaFactor == 1f)
                    return true

                overallMatrix.postScale(deltaFactor, deltaFactor, detector.focusX, detector.focusY)
                reifyMatrix(overallMatrix)
                mMyPageView.imageMatrix = overallMatrix

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

    private fun deltaReifyBound(span: Float, a: Float, b: Float) =
        if (b-a < span)  // has margin
            (span - (a+b)) * 0.5f  // to center
        else
            min(0f, -a) + max(0f, span - b)  // clear magin

    private fun reifyMatrix(m: Matrix) {
        val visualRect = RectF(0f, 0f, bitmapSize.width.toFloat(), bitmapSize.height.toFloat()).transform(m)
        Log.d("Paperant", "reified: ${visualRect}, width=${width}, height=${height}")

        // at most one of two sides of + could be non zero when the visual scale >=1f
        val dx = deltaReifyBound(mMyPageView.width.toFloat(), visualRect.left, visualRect.right)
        val dy = deltaReifyBound(mMyPageView.height.toFloat(), visualRect.top, visualRect.bottom)
        Log.d("Paperant", "reify: dx=${dx}, dy=${dy}")
        m.postTranslate(dx, dy)
    }

    private val moveDetector = GestureDetector(context, object: GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent?): Boolean {
            return true
        }
        override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!isLoading()) {
                overallMatrix.postTranslate(-distanceX, -distanceY)
                reifyMatrix(overallMatrix)
                mMyPageView.imageMatrix = overallMatrix
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
        bitmapPrecisionPending = 1f
        bitmapSize = Size(bitmap.width, bitmap.height)

        if (preserveMatrix) {
            val deltaFactor = oldBitmapPrecision / bitmapPrecision
            overallMatrix.preScale(deltaFactor, deltaFactor)
        } else {
            val scale = 1f / bitmapPrecision
            overallMatrix = scaleMatrix(scale, scale)
            overallMatrix.postTranslate(
                (mMyPageView.width - scale * bitmap.width) / 2,
                (mMyPageView.height - scale * bitmap.height) / 2
            )
        }

        mMyPageView.imageMatrix = overallMatrix
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