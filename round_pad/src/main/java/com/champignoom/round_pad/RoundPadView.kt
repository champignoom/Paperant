package com.champignoom.round_pad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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

class RoundPadView(context: Context, attrs: AttributeSet): View(context, attrs) {
    var onDeltaListener: ((delta: Int) -> Unit)? = null
    var lastPolar = Polar(0.0, 0.0)

    var minStep = 4.0
    var maxStep = 100.0
    val minLength = 0.2

    var residual = 0.0

    fun init(minStep: Double, maxStep: Double, startPosition: Int) {
        if (minStep > maxStep) {
            throw IllegalArgumentException("empty step range: [${minStep}, ${maxStep}]")
        }

        this.minStep = minStep
        this.maxStep = maxStep
    }

    fun setRange(newMinValue: Int, newMaxValue: Int) {
    }

    fun setStep(newMinStep: Double, newMaxStep: Double) {
    }

//    val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//        style = Paint.Style.FILL
//        color = Color.MAGENTA
//        alpha = 0x10
//    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
//        canvas.apply {
//            drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
//        }
        setBackgroundColor(Color.MAGENTA)
        background.alpha = 0x10
    }

    private fun toPolar(x: Float, y: Float): Polar {
        return Polar.fromCartesian(x/width*2 - 1.0, 1.0 - y/height)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Toast.makeText(context, "Down", Toast.LENGTH_SHORT).show()
//                onDeltaListener?.invoke(-100.0)
                lastPolar = toPolar(event.x, event.y)
                setBackgroundColor(Color.BLUE)
                background.alpha = 0x10
                residual = 0.0
            }

            MotionEvent.ACTION_UP -> {
                lastPolar = Polar(0.0, 0.0)
                background.alpha = 0x10
            }

            MotionEvent.ACTION_MOVE -> run {
                val newPolar = toPolar(event.x, event.y)
                if (newPolar.angle < 0)
                    return@run

                val scalePow = (min(newPolar.length, lastPolar.length)-minLength).coerceIn(0.0, 0.9)/0.9
                val scale = minStep * (maxStep/minStep).pow(scalePow)
                val delta = (newPolar.angle - lastPolar.angle) / PI
                val scaledDelta = scale * delta

//                Log.i("Paperant", "${event.x/width}, ${event.y/height}, ${atan2(event.x*2-1.0, 1.0-event.y)}")

                Log.i("Paperant", "scalePow=$scalePow, scale=$scale, newAngle=${newPolar.angle}, scaledDelta=$scaledDelta")
                setBackgroundColor(if (scaledDelta < 0) Color.GREEN else Color.RED)
                background.alpha = 0x10

                val newPosition = scaledDelta + residual
                val newIntegral = newPosition.toInt()
                residual = newPosition - newIntegral

                if (newIntegral != 0) {
                    onDeltaListener?.invoke(newIntegral)
                }

                lastPolar = newPolar
            }
        }
//        super.onTouchEvent(event)
        return true
    }
}