package com.champignoom.paperant

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.view.GestureDetectorCompat
import kotlin.math.min

class MyPageViewWithBlur(ctx: Context, atts: AttributeSet?): FrameLayout(ctx, atts) {
    companion object {
        const val NOT_WAITING = -1
        private fun timestamp() = SystemClock.uptimeMillis()
        const val PAGE_DELTA_WIDTH = 1/8f
    }

    private val mMyPageView  = ImageView(ctx).also {
        it.scaleType = ImageView.ScaleType.MATRIX
        addView(it, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }
    private val mBlurredImage = ImageView(ctx).also { addView(it, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT) }
    private val mProgressBar = ProgressBar(ctx).also { addView(it, LayoutParams(150, 150, Gravity.CENTER)) }

    private var waitingPageNum: Int = NOT_WAITING
    private var lastTimestampMs = 0L

    var maxAnimationDurationMs = 100L

    private var pageMatrix = Matrix()

    var onPageDeltaClicked: ((Int) -> Unit)? = null
    var onSizeListener: ((w: Int, h: Int) -> Unit)? = null

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
                val factor = detector.scaleFactor
                pageMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
//                pageMatrix.postScale(2f, 2f)
                mMyPageView.imageMatrix = pageMatrix
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector?) { }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        return turnOnePageDetector.onTouchEvent(e) || scaleDetector.onTouchEvent(e)
    }

    private fun resetBlurred() {
        mBlurredImage.animation = null
        mBlurredImage.apply {
            setImageDrawable(null)
            alpha = 1f
            visibility = GONE
        }
    }

    fun isLoading() =
        mProgressBar.visibility == VISIBLE

    // not threadsafe
    fun setLoading(pageNum: Int, blurredBitmap: Bitmap?) {
        waitingPageNum = pageNum
        mMyPageView.setImageDrawable(null)
        mProgressBar.visibility = VISIBLE
        resetBlurred()
        if (blurredBitmap != null) {
            mBlurredImage.setImageBitmap(blurredBitmap)
            mBlurredImage.visibility = VISIBLE
        }
        lastTimestampMs = timestamp()
    }

//    private var animationTokenTimestamp = -1L
//    private fun isProperAnimationEnd(timestamp: Long) = timestamp == animationTokenTimestamp

    private fun animateBlurred(durationMs: Long) {
        mBlurredImage.startAnimation(AlphaAnimation(1f, 0f).apply {
            duration = durationMs
            setAnimationListener(object: Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) { }
                override fun onAnimationRepeat(animation: Animation?) { }
                override fun onAnimationEnd(animation: Animation?) {
                    resetBlurred()
                }
            })
        })
    }

    fun morphToFull(pageNum: Int, bitmap: Bitmap) {
        if (pageNum != waitingPageNum) return

        waitingPageNum = NOT_WAITING  // method applys only after loading
        mMyPageView.setImageBitmap(bitmap)
        val wasLoading = isLoading()
        mProgressBar.visibility = GONE

        if (wasLoading && mBlurredImage.drawable != null) {
            val msElapsed = timestamp() - lastTimestampMs
            animateBlurred(min(msElapsed/2, maxAnimationDurationMs))
        } else {
            resetBlurred()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        onSizeListener?.invoke(w, h)
    }
}