package com.champignoom.paperant

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.net.Uri
import android.util.Size
import android.util.SizeF
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.RectI
import com.champignoom.paperant.Transform.Companion.toSizeF
import com.champignoom.paperant.old.DocumentActivity
import com.champignoom.paperant.ui.mydocument.MyDocumentActivity
import com.champignoom.paperant.ui.mydocument.PdfDocumentActivity
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.time.measureTimedValue

fun myOpenDocument(context: Context, path: String, useExample: Boolean = false) {
    val cls = if (useExample) DocumentActivity::class else PdfDocumentActivity::class
    context.startActivity(Intent(context, cls.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse(path)
    })
}

fun rectIntersection(r0: RectI, r1: RectI) =
    RectI(
        max(r0.x0, r1.x0),
        max(r0.y0, r1.y0),
        min(r0.x1, r1.x1),
        min(r0.y1, r1.y1),
    )

fun rectIntersection(r0: Rect, r1: Rect) =
    Rect(
        max(r0.x0, r1.x0),
        max(r0.y0, r1.y0),
        min(r0.x1, r1.x1),
        min(r0.y1, r1.y1),
    )

fun RectI.width() = x1 - x0
fun RectI.height() = y1 - y0
fun Rect.width() = x1 - x0
fun Rect.height() = y1 - y0

//fun RectI.add(offset: PointF) {
//    x0 += floor(offset.x).toInt()
//    x1 += ceil(offset.x).toInt()
//    y0 += floor(offset.y).toInt()
//    y1 += floor(offset.y).toInt()
//}

fun Rect.add(offset: PointF) {
    x0 += offset.x
    x1 += offset.x
    y0 += offset.y
    y1 += offset.y
}

fun com.artifex.mupdf.fitz.Matrix.postTranslate(dx: Float, dy: Float) {
    e += dx
    f += dy
}

private val mtxBuffer = FloatArray(9)
fun Matrix.scaleFactor(): Float {
    synchronized(mtxBuffer) {
        getValues(mtxBuffer)
        return mtxBuffer[Matrix.MSCALE_X]  // assume same scale for x, y
    }
}

fun bitmapSize(bitmap: Bitmap) = Size(bitmap.width, bitmap.height)

data class Transform(var scale: Float, var offset: PointF) {
    constructor(): this(1f, PointF(0f, 0f))

    companion object {
        fun fitDeltaSpan(to: Float, from: Float, offset: Float) =
            if (from < to)
                (to - from) * 0.5f - offset
            else
                min(0f, -offset) + max(0f, to - (from + offset))

        fun toSizeF(s: Size) = SizeF(s.width.toFloat(), s.height.toFloat())
    }

    fun reset() {
        scale = 1f
        offset.x = 0f
        offset.y = 0f
    }

    fun toMatrix(): Matrix {
        val m = Matrix()
        m.postScale(scale, scale)
        m.postTranslate(offset.x, offset.y)
        return m
    }

    fun toMupdfMatrix(): com.artifex.mupdf.fitz.Matrix =
        com.artifex.mupdf.fitz.Matrix(scale, 0f, 0f, scale, offset.x, offset.y)

    fun postScale(dScale: Float, dx: Float, dy: Float) {
        offset.x = (offset.x - dx) * dScale + dx
        offset.y = (offset.y - dy) * dScale + dy
        scale *= dScale
    }

    fun translate(dx: Float, dy: Float) {
        offset.x += dx
        offset.y += dy
    }

    fun translate(d: PointF) =
        translate(d.x, d.y)

    fun fitDelta(imageSize: SizeF, canvasSize: SizeF) =
        PointF(
            fitDeltaSpan(canvasSize.width, imageSize.width*scale, offset.x),
            fitDeltaSpan(canvasSize.height, imageSize.height*scale, offset.y),
        )
    fun fitDelta(imageSize: Size, canvasSize: Size) =
        fitDelta(toSizeF(imageSize), toSizeF(canvasSize))
}