package com.champignoom.paperant

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Size
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.view.GestureDetectorCompat
import kotlin.math.max

class PdfPageView(ctx: Context, atts: AttributeSet?): FrameLayout(ctx, atts) {
    companion object {
        const val PAGE_DELTA_WIDTH = 0.1f
    }

    private val mFull = ImageView(ctx).also {
        it.scaleType = ImageView.ScaleType.MATRIX
        addView(it, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }
    private val mLocal = ImageView(ctx).also {
        it.scaleType = ImageView.ScaleType.MATRIX
        addView(it, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }
//    private val mThumbnail = ImageView(ctx).also { addView(it, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT) }
    private val mProgressBar = ProgressBar(ctx).also { addView(it, LayoutParams(150, 150, Gravity.CENTER)) }

    var onSizeChangeListener: ((w: Int, h: Int) -> Unit)? = null
    var onPatchChangeListener: ((Transform) -> Unit)? = null
    var onPageDeltaClicked: ((delta: Int) -> Unit)? = null
    var onSingleTapListener: (() -> Unit)? = null

    var imageSize: Size? = null
    val canvasSize get() = Size(mFull.width, mFull.height)

    private var mtxFull = Transform()
    private var mtxLocal = Transform()

    fun isLoading() =
        mProgressBar.visibility == VISIBLE


    private val moveDetector = GestureDetector(context, object: GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (isLoading())
                return true

            mtxFull.translate(-distanceX, -distanceY)
            val delta = mtxFull.fitDelta(imageSize!!, canvasSize)
            mtxFull.translate(delta)
            mtxLocal.translate(delta.x - distanceX, delta.y - distanceY)
            mFull.imageMatrix = mtxFull.toMatrix()
            mLocal.imageMatrix = mtxLocal.toMatrix()
            onPatchChangeListener?.invoke(mtxFull)
            return true
        }
    })


    private val scaleDetector = ScaleGestureDetector(context, object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (isLoading())
                return true

            val f = max(detector.scaleFactor, 1f/mtxFull.scale)
            val x = detector.focusX
            val y = detector.focusY

            mtxFull.postScale(f, x, y)
            mtxLocal.postScale(f, x, y)
            val delta = mtxFull.fitDelta(imageSize!!, canvasSize)
            mtxFull.translate(delta)
            mtxLocal.translate(delta)

            mFull.imageMatrix = mtxFull.toMatrix()
            mLocal.imageMatrix = mtxLocal.toMatrix()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            onPatchChangeListener?.invoke(mtxFull)
        }
    })

    private val turnOnePageDetector = GestureDetector(context, object: GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            when {
                e.x < width * PAGE_DELTA_WIDTH -> onPageDeltaClicked?.invoke(-1)
                e.x > width * (1 - PAGE_DELTA_WIDTH) -> onPageDeltaClicked?.invoke(1)
                else -> return false
            }
            return true
        }
    })

    private val singleTapDetector = GestureDetector(context, object: GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onSingleTapListener?.invoke()
            return true
        }
    })

    fun setLoading() {
        mFull.setImageDrawable(null)
        mLocal.setImageDrawable(null)
//        mThumbnail.setImageDrawable(null)
        mProgressBar.visibility = VISIBLE
    }

    fun setLoaded(fullBitmap: Bitmap) {
        mProgressBar.visibility = GONE
        mtxFull.reset()
        imageSize = Size(fullBitmap.width, fullBitmap.height)
        val offset = mtxFull.fitDelta(imageSize!!, canvasSize)
        mtxFull.translate(offset)
        mFull.imageMatrix = mtxFull.toMatrix()
        mFull.setImageBitmap(fullBitmap)

        mtxLocal.reset()
        mLocal.setImageDrawable(null)
        mLocal.imageMatrix = mtxLocal.toMatrix()  // not necessary, just for sanity
    }

    fun setScaleLoaded(localBitmap: Bitmap) {
        mLocal.setImageBitmap(localBitmap)
        mtxLocal.reset()
        val delta = mtxLocal.fitDelta(bitmapSize(localBitmap), canvasSize)
        mtxLocal.translate(delta)
        mLocal.imageMatrix = mtxLocal.toMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        onSizeChangeListener?.invoke(w, h)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        moveDetector.onTouchEvent(e)
        scaleDetector.onTouchEvent(e)
        if (!turnOnePageDetector.onTouchEvent(e)) {
            singleTapDetector.onTouchEvent(e)
        }
        return true
    }
}