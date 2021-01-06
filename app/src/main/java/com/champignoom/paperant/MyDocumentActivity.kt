package com.champignoom.paperant

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import kotlinx.android.synthetic.main.activity_my_document.*
import java.io.ByteArrayOutputStream
import kotlin.system.measureTimeMillis
import kotlin.time.measureTime

class MyDocumentActivity : AppCompatActivity() {
    companion object {
        val BLUR_RADIUS = 15f
    }

    private var currentPosition = -1.0
    private var numPage = 0
    private var doc: Document? = null
    private var blurredCache: MutableMap<Int, ByteArray> = mutableMapOf()
    private var bitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_document)

        val uri = intent.data ?: run {return}
        doc = Document.openDocument(uri.path)
        numPage = doc!!.countPages()
        Log.i("Paperant", "numPage = $numPage")

        pdf_image_view.viewTreeObserver.addOnGlobalLayoutListener {
            if (currentPosition < 0) {
                renderPageIfDifferent(0)
                currentPosition = 0.0
            }
        }

        pdf_image_view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val delta = if (event.x < v.width/2) -1 else 1
                    turnPage(delta)
                    true
                }

                else -> {
                    if (false) v.performClick()
                    true
                }
            }
        }

        round_pad.maxStep = numPage.toDouble()

        round_pad.onDeltaListener = {
            // TODO: queue in another thread
            val newPosition = (currentPosition + it).coerceIn(0.0, (numPage-1).toDouble())
            renderPageIfDifferent(newPosition.toInt())
            currentPosition = newPosition
        }

        button_show_compressed.setOnClickListener {
            val byteArray = blurredCache[currentPosition.toInt()]!!
            var decompressedBitmap: Bitmap
            val decompressTime = measureTimeMillis {
                decompressedBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            }
            pdf_image_view.setImageBitmap(decompressedBitmap)
            Toast.makeText(this, "decompressTime=${decompressTime} ms, ${byteArray.size} / ${bitmap!!.width * bitmap!!.height} / ${bitmap!!.allocationByteCount}", Toast.LENGTH_LONG).show()
        }
    }

    private fun blurImage(bitmap: Bitmap) {
        val rs = RenderScript.create(this)
        val blurrer = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        val input = Allocation.createFromBitmap(rs, bitmap)
        val output = Allocation.createFromBitmap(rs, bitmap)
        blurrer.setRadius(BLUR_RADIUS)
        blurrer.setInput(input)
        blurrer.forEach(output)
        output.copyTo(bitmap)
        input.destroy()
        output.destroy()
        rs.destroy()
    }

    private fun turnPage(delta: Int) {
        val newPosition = (currentPosition.toInt() + delta).coerceIn(0, numPage-1)
        renderPageIfDifferent(newPosition)
        currentPosition = newPosition.toDouble()
    }

    private fun renderPageIfDifferent(pageNum: Int) {
        if (pageNum == currentPosition.toInt()) return

        Log.i("Paperant", "turn page ${currentPosition.toInt()} -> $pageNum")

        val page = doc!!.loadPage(pageNum)
        val ctm = AndroidDrawDevice.fitPage(page, pdf_image_view.width, pdf_image_view.height)
        bitmap = AndroidDrawDevice.drawPage(page, ctm)
        pdf_image_view.setImageBitmap(bitmap)

        var compressionTime = 0L
        var blurringTime = 0L
        var copyTime = 0L
        if (!(pageNum in blurredCache)) {
//            val tmpBitmap = Bitmap.createBitmap(pdf_image_view.width, pdf_image_view.height, Bitmap.Config.ARGB_8888)
            val tmpBitmap = bitmap!!.copy(bitmap!!.config, true)

//            copyTime = measureTimeMillis {
//                val canvas = Canvas(tmpBitmap)
//                pdf_image_view.draw(canvas)
//            }

            blurringTime = measureTimeMillis {
                blurImage(tmpBitmap)
            }

            compressionTime = measureTimeMillis {
                val s = ByteArrayOutputStream()
                tmpBitmap.compress(Bitmap.CompressFormat.WEBP, 0, s)
                blurredCache[pageNum] = s.toByteArray()
            }
        }

        Toast.makeText(this, "copyTime = $copyTime ms, blurTime = $blurringTime ms, compressionTime = $compressionTime ms", Toast.LENGTH_SHORT).show()
    }
}