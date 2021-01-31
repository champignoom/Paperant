package com.champignoom.paperant.old

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.View
import android.widget.Scroller
import com.artifex.mupdf.fitz.Link
import com.artifex.mupdf.fitz.Quad
import java.lang.Integer.max

class PageView(ctx: Context?, atts: AttributeSet?) :
    View(ctx, atts), GestureDetector.OnGestureListener, OnScaleGestureListener {
    var viewScale = 1f
    var minScale = 1f
    var maxScale = 2f

    var actionListener: DocumentActivity? = null
    var pageScale = 1f
    var bitmap: Bitmap? = null
    var bitmapW = 0
    var bitmapH = 0
    var canvasW = 0
    var canvasH = 0
    var tScrollX = 0
    var tScrollY = 0
    var links: Array<Link>? = null
    var hits: Array<Quad>? = null
    var showLinks = false
    var error = false

    private val detector = GestureDetector(ctx, this)
    private val scaleDetector = ScaleGestureDetector(ctx, this)
    private val scroller: Scroller = Scroller(ctx)
    private val errorPaint = Paint().apply {
        setARGB(255, 255, 80, 80)
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }
    private val errorPath = Path().apply {
        moveTo(-100f, -100f)
        lineTo(100f, 100f)
        moveTo(100f, -100f)
        lineTo(-100f, 100f)
    }
    private val linkPaint = Paint().apply {
        setARGB(32, 0, 0, 255)
    }
    private val hitPaint = Paint().apply {
        setARGB(32, 255, 0, 0)
        style = Paint.Style.FILL
    }

    fun setError() {
        bitmap?.recycle()
        error = true
        links = null
        hits = null
        bitmap = null
        invalidate()
    }

    fun setBitmap(b: Bitmap?, zoom: Float, wentBack: Boolean, ls: Array<Link>?, hs: Array<Quad>?) {
        bitmap?.recycle()
        error = false
        links = ls
        hits = hs
        bitmap = b
        bitmapW = (bitmap!!.width * viewScale / zoom).toInt()
        bitmapH = (bitmap!!.height * viewScale / zoom).toInt()
        scroller.forceFinished(true)
        if (pageScale == zoom) {
            tScrollX = if (wentBack) bitmapW - canvasW else 0
            tScrollY = if (wentBack) bitmapH - canvasH else 0
        }
        pageScale = zoom
        invalidate()
    }

    fun resetHits() {
        hits = null
        invalidate()
    }

    public override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        canvasW = w
        canvasH = h
        actionListener!!.onPageViewSizeChanged(w, h)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        return true
    }

    override fun onDown(e: MotionEvent): Boolean {
        scroller.forceFinished(true)
        return true
    }

    override fun onShowPress(e: MotionEvent) {}
    override fun onLongPress(e: MotionEvent) {
        showLinks = !showLinks
        invalidate()
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        var foundLink = false
        val x = e.x
        val y = e.y
        if (showLinks && links != null) {
            val dx = (if (bitmapW <= canvasW) ((bitmapW - canvasW) / 2) else tScrollX).toFloat()
            val dy = (if (bitmapH <= canvasH) ((bitmapH - canvasH) / 2) else tScrollY).toFloat()
            val mx = (x + dx) / viewScale
            val my = (y + dy) / viewScale
            for (link in links!!) {
                val b = link.bounds
                if ((mx in b.x0 .. b.x1) && (my in b.y0 .. b.y1)) {
                    if (link.isExternal)
                        actionListener!!.gotoURI(link.uri)
                    else
                        actionListener!!.gotoPage(link.uri)
                    foundLink = true
                    break
                }
            }
        }
        if (!foundLink) {
            val a = canvasW / 3.toFloat()
            val b = a * 2
            when {
                x <= a -> goBackward()
                x >= b -> goForward()
                else -> actionListener!!.toggleUI()
            }
        }
        invalidate()
        return true
    }

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float): Boolean {
        if (bitmap != null) {
            tScrollX += dx.toInt()
            tScrollY += dy.toInt()
            scroller.forceFinished(true)
            invalidate()
        }
        return true
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float): Boolean {
        if (bitmap != null) {
            val maxX = max(bitmapW - canvasW, 0)
            val maxY = max(bitmapH - canvasH, 0)
            scroller.forceFinished(true)
            scroller.fling(tScrollX, tScrollY, (-dx).toInt(), (-dy).toInt(), 0, maxX, 0, maxY)
            invalidate()
        }
        return true
    }

    override fun onScaleBegin(det: ScaleGestureDetector): Boolean {
        return true
    }

    override fun onScale(det: ScaleGestureDetector): Boolean {
        if (bitmap != null) {
            val focusX = det.focusX
            val focusY = det.focusY
            val scaleFactor = det.scaleFactor
            val pageFocusX = (focusX + tScrollX) / viewScale
            val pageFocusY = (focusY + tScrollY) / viewScale
            viewScale *= scaleFactor
            viewScale = viewScale.coerceIn(minScale, maxScale)
            bitmapW = (bitmap!!.width * viewScale / pageScale).toInt()
            bitmapH = (bitmap!!.height * viewScale / pageScale).toInt()
            tScrollX = (pageFocusX * viewScale - focusX).toInt()
            tScrollY = (pageFocusY * viewScale - focusY).toInt()
            scroller.forceFinished(true)

            Log.d("Paperant", "onScale: viewScale=${viewScale}, bitmapWH=(${bitmapW},${bitmapH}), tScrollXY=(${tScrollX}, ${tScrollY})")
            invalidate()
        }
        return true
    }

    override fun onScaleEnd(det: ScaleGestureDetector) {
        actionListener!!.onPageViewZoomChanged(viewScale)
    }

    fun goBackward() {
        scroller.forceFinished(true)
        if (tScrollY <= 0) {
            if (tScrollX <= 0) {
                actionListener!!.goBackward()
                return
            }
            scroller.startScroll(
                tScrollX,
                tScrollY,
                -canvasW * 9 / 10,
                bitmapH - canvasH - tScrollY,
                500
            )
        } else {
            scroller.startScroll(tScrollX, tScrollY, 0, -canvasH * 9 / 10, 250)
        }
        invalidate()
    }

    fun goForward() {
        scroller.forceFinished(true)
        if (tScrollY + canvasH >= bitmapH) {
            if (tScrollX + canvasW >= bitmapW) {
                actionListener!!.goForward()
                return
            }
            scroller.startScroll(tScrollX, tScrollY, canvasW * 9 / 10, -tScrollY, 500)
        } else {
            scroller.startScroll(tScrollX, tScrollY, 0, canvasH * 9 / 10, 250)
        }
        invalidate()
    }

    val dst = Rect()
    val path = Path()
    public override fun onDraw(canvas: Canvas) {
        val x: Int
        val y: Int
        if (bitmap == null) {
            if (error) {
                canvas.translate(canvasW / 2.toFloat(), canvasH / 2.toFloat())
                canvas.drawPath(errorPath, errorPaint)
            }
            return
        }
        if (scroller.computeScrollOffset()) {
            tScrollX = scroller.currX
            tScrollY = scroller.currY
            invalidate() /* keep animating */
        }
        if (bitmapW <= canvasW) {
            tScrollX = 0
            x = (canvasW - bitmapW) / 2
        } else {
            tScrollX = tScrollX.coerceIn(0, bitmapW - canvasW)
            x = -tScrollX
        }
        if (bitmapH <= canvasH) {
            tScrollY = 0
            y = (canvasH - bitmapH) / 2
        } else {
            tScrollY = tScrollY.coerceIn(0, bitmapH - canvasH)
            y = -tScrollY
        }

        dst.set(x, y, x+bitmapW, y+bitmapH)
        canvas.drawBitmap(bitmap!!, null, dst, null)
        Log.d("Paperant", "onDraw: dst=(${dst.left}, ${dst.top}, ${dst.right}, ${dst.bottom})")
        if (showLinks) {
            links?.forEach {
                val b = it.bounds
                canvas.drawRect(
                    x + b.x0 * viewScale,
                    y + b.y0 * viewScale,
                    x + b.x1 * viewScale,
                    y + b.y1 * viewScale,
                    linkPaint
                )
            }
        }

        hits?.forEach {q ->
            path.apply {
                rewind()
                moveTo(x + q.ul_x * viewScale, y + q.ul_y * viewScale)
                lineTo(x + q.ll_x * viewScale, y + q.ll_y * viewScale)
                lineTo(x + q.lr_x * viewScale, y + q.lr_y * viewScale)
                lineTo(x + q.ur_x * viewScale, y + q.ur_y * viewScale)
                close()
            }
            canvas.drawPath(path, hitPaint)
        }
    }
}