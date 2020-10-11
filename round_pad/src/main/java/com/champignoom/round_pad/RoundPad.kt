package com.champignoom.round_pad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.renderscript.Float2
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import kotlin.math.*

data class Polar(val length: Double, val angle: Double) {
    companion object {
        fun fromCartesian(x: Double, y: Double): Polar {
             return Polar(sqrt(x*x + y*y), atan2(y, x))
        }
    }
}

class RoundPad(context: Context, attrs: AttributeSet): View(context, attrs) {
    var onDeltaListener: ((Double) -> Unit)? = null
    var lastPolar = Polar(0.0, 0.0)
    val center = Float2()
    var maxLength = 0.0

    val minStep = 4.0
    val maxStep = 100.0

    val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.MAGENTA
        alpha = 0x33
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.apply {
            drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        center.x = width.toFloat()/2
        center.y = height.toFloat()
        maxLength = center.x.toDouble()
    }

    private fun toPolar(x: Float, y: Float): Polar {
        return Polar.fromCartesian((x - center.x).toDouble(), (center.y - y).toDouble())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Toast.makeText(context, "Down", Toast.LENGTH_SHORT).show()
//                onDeltaListener?.invoke(-100.0)
                lastPolar = toPolar(event.x, event.y)
                setBackgroundColor(Color.BLUE)
            }

            MotionEvent.ACTION_UP -> {
                lastPolar = Polar(0.0, 0.0)
                setBackgroundColor(Color.MAGENTA)
            }

            MotionEvent.ACTION_MOVE -> run {
//                Log.i("Paperant", "${event.x - center.x}, ${center.y - event.y}, ${atan2(event.x-center.x, center.y-event.y)}")

                val newPolar = toPolar(event.x, event.y)
                if (newPolar.angle < 0)
                    return@run

                val scalePow = (min(newPolar.length, lastPolar.length) / maxLength).coerceAtMost(1.0)
                val scale = minStep * (maxStep/minStep).pow(scalePow)
                val delta = (newPolar.angle - lastPolar.angle) / PI
                val scaledDelta = scale * delta

                setBackgroundColor(if (scaledDelta < 0) Color.GREEN else Color.RED)

//                Log.i("Paperant", "scalePow=${scalePow}, scale=${scale}, delta=${delta}, result=${scale*delta}")
                onDeltaListener?.invoke(scaledDelta)
                lastPolar = newPolar
            }
        }
//        super.onTouchEvent(event)
        return true
    }
}