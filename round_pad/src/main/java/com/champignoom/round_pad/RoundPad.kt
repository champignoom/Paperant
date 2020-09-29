package com.champignoom.round_pad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Toast

class RoundPad(context: Context, attrs: AttributeSet): View(context, attrs) {
    val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.MAGENTA
        alpha = 0x33
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas.apply {
            this!!.drawRect(0.toFloat(), 0.toFloat(), width.toFloat()*2, height.toFloat(), paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        Toast.makeText(context, "${event?.x}, ${event?.y}", Toast.LENGTH_SHORT).show();
        return super.onTouchEvent(event)
    }
}