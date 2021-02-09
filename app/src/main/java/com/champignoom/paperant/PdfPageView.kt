package com.champignoom.paperant

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.util.Size
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar

class PdfPageView(ctx: Context, atts: AttributeSet?): FrameLayout(ctx, atts) {
    private val mFull = ImageView(ctx).also { addView(it, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT) }
    private val mLocal = ImageView(ctx).also { addView(it, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT) }
    private val mThumbnail = ImageView(ctx).also { addView(it, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT) }
    private val mProgressBar = ProgressBar(ctx).also { addView(it, LayoutParams(150, 150, Gravity.CENTER)) }

    var onSizeChangeListener: ((w: Int, h: Int) -> Unit)? = null

    val canvasSize get() = Size(mFull.width, mFull.height)

    fun setLoading() {
        mFull.setImageDrawable(null)
        mLocal.setImageDrawable(null)
        mThumbnail.setImageDrawable(null)
        mProgressBar.visibility = VISIBLE
    }

    fun setLoaded(fullBitmap: Bitmap) {
        mProgressBar.visibility = GONE
        mFull.setImageBitmap(fullBitmap)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        onSizeChangeListener?.invoke(w, h)
    }
}