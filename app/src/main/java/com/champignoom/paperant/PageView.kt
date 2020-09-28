package com.champignoom.paperant

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.View
import android.widget.Scroller
import com.artifex.mupdf.fitz.Link
import com.artifex.mupdf.fitz.Quad

class PageView(ctx: Context?, atts: AttributeSet?) :
    View(ctx, atts), GestureDetector.OnGestureListener, OnScaleGestureListener {
    var actionListener: DocumentActivity? = null
    var pageScale: Float
    var viewScale: Float
    var minScale: Float
    var maxScale: Float
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
    var detector: GestureDetector
    var scaleDetector: ScaleGestureDetector
    var scroller: Scroller
    var error = false
    var errorPaint: Paint
    var errorPath: Path
    var linkPaint: Paint
    var hitPaint: Paint

    fun setError() {
        if (bitmap != null) bitmap!!.recycle()
        error = true
        links = null
        hits = null
        bitmap = null
        invalidate()
    }

    fun setBitmap(b: Bitmap?, zoom: Float, wentBack: Boolean, ls: Array<Link>?, hs: Array<Quad>?) {
        if (bitmap != null) bitmap!!.recycle()
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
            val dx =
                if (bitmapW <= canvasW) ((bitmapW - canvasW) / 2).toFloat() else tScrollX.toFloat()
            val dy =
                if (bitmapH <= canvasH) ((bitmapH - canvasH) / 2).toFloat() else tScrollY.toFloat()
            val mx = (x + dx) / viewScale
            val my = (y + dy) / viewScale
            for (link in links!!) {
                val b = link.bounds
                if (mx >= b.x0 && mx <= b.x1 && my >= b.y0 && my <= b.y1) {
                    if (link.isExternal) actionListener!!.gotoURI(link.uri) else actionListener!!.gotoPage(
                        link.uri
                    )
                    foundLink = true
                    break
                }
            }
        }
        if (!foundLink) {
            val a = canvasW / 3.toFloat()
            val b = a * 2
            if (x <= a) goBackward()
            if (x >= b) goForward()
            if (x > a && x < b) actionListener!!.toggleUI()
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
            val maxX = if (bitmapW > canvasW) bitmapW - canvasW else 0
            val maxY = if (bitmapH > canvasH) bitmapH - canvasH else 0
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
            if (viewScale < minScale) viewScale = minScale
            if (viewScale > maxScale) viewScale = maxScale
            bitmapW = (bitmap!!.width * viewScale / pageScale).toInt()
            bitmapH = (bitmap!!.height * viewScale / pageScale).toInt()
            tScrollX = (pageFocusX * viewScale - focusX).toInt()
            tScrollY = (pageFocusY * viewScale - focusY).toInt()
            scroller.forceFinished(true)
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
            if (tScrollX < 0) tScrollX = 0
            if (tScrollX > bitmapW - canvasW) tScrollX = bitmapW - canvasW
            x = -tScrollX
        }
        if (bitmapH <= canvasH) {
            tScrollY = 0
            y = (canvasH - bitmapH) / 2
        } else {
            if (tScrollY < 0) tScrollY = 0
            if (tScrollY > bitmapH - canvasH) tScrollY = bitmapH - canvasH
            y = -tScrollY
        }
        dst[x, y, x + bitmapW] = y + bitmapH
        canvas.drawBitmap(bitmap!!, null, dst, null)
        if (showLinks && links != null && links!!.size > 0) {
            for (link in links!!) {
                val b = link.bounds
                canvas.drawRect(
                    x + b.x0 * viewScale,
                    y + b.y0 * viewScale,
                    x + b.x1 * viewScale,
                    y + b.y1 * viewScale,
                    linkPaint
                )
            }
        }
        if (hits != null && hits!!.size > 0) {
            for (q in hits!!) {
                path.rewind()
                path.moveTo(x + q.ul_x * viewScale, y + q.ul_y * viewScale)
                path.lineTo(x + q.ll_x * viewScale, y + q.ll_y * viewScale)
                path.lineTo(x + q.lr_x * viewScale, y + q.lr_y * viewScale)
                path.lineTo(x + q.ur_x * viewScale, y + q.ur_y * viewScale)
                path.close()
                canvas.drawPath(path, hitPaint)
            }
        }
    }

    init {
        scroller = Scroller(ctx)
        detector = GestureDetector(ctx, this)
        scaleDetector = ScaleGestureDetector(ctx, this)
        pageScale = 1f
        viewScale = 1f
        minScale = 1f
        maxScale = 2f
        linkPaint = Paint()
        linkPaint.setARGB(32, 0, 0, 255)
        hitPaint = Paint()
        hitPaint.setARGB(32, 255, 0, 0)
        hitPaint.style = Paint.Style.FILL
        errorPaint = Paint()
        errorPaint.setARGB(255, 255, 80, 80)
        errorPaint.strokeWidth = 5f
        errorPaint.style = Paint.Style.STROKE
        errorPath = Path()
        errorPath.moveTo(-100f, -100f)
        errorPath.lineTo(100f, 100f)
        errorPath.moveTo(100f, -100f)
        errorPath.lineTo(-100f, 100f)
    }
}