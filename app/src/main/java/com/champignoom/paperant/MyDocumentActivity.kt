package com.champignoom.paperant

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import kotlinx.android.synthetic.main.activity_my_document.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.LinkedBlockingDeque

class MyDocumentActivity : AppCompatActivity() {
    companion object {
        const val BLUR_RADIUS = 15f
    }

    private var currentPosition = -1.0
    private var numPage = 0
    private var doc: Document? = null
    private var blurredCache: MutableMap<Int, ByteArray> = mutableMapOf()

    private var pageQueue = LinkedBlockingDeque<Int>()
    private var lastCacheTimestamp: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_document)

        val uri = intent.data ?: return
        doc = Document.openDocument(uri.path)
        numPage = doc!!.countPages()
        Log.i("Paperant", "numPage = $numPage")

        // wait for the image_view to be inflated with size, otherwise pdf not loaded
        pdf_image_view.viewTreeObserver.addOnGlobalLayoutListener {
            if (currentPosition < 0) {
                renderPageIfDifferent(0.0)
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
            renderPageIfDifferent((currentPosition + it).coerceIn(0.0, numPage-1.0))
        }

        lifecycleScope.launch(newSingleThreadContext("backgroundRendererThread")) {
            renderPagesInBackground()
        }
    }

    private fun setPageLoading(compressedCache: ByteArray?) {
        progress_indicator.visibility = View.VISIBLE
        if (compressedCache == null) {
            pdf_image_view.setImageDrawable(null)
        } else {
            pdf_image_view.setImageBitmap(decompressBitmap(compressedCache))
        }
        Log.d("Paperant", "setPageLoading() changing pdf_image_view")
        lastCacheTimestamp = System.currentTimeMillis()
    }

    private fun setPageLoaded(bitmap: Bitmap) {
        progress_indicator.visibility = View.GONE
        val durationFromCache = System.currentTimeMillis() - lastCacheTimestamp
        if (pdf_image_view.drawable == null || durationFromCache < 200) {
            pdf_image_view.setImageBitmap(bitmap)
        } else {
            val crossfader = TransitionDrawable(
                arrayOf(
                    pdf_image_view.drawable,
                    BitmapDrawable(resources, bitmap)
                )
            )
            pdf_image_view.setImageDrawable(crossfader)
            crossfader.startTransition(100)
        }
        Log.d("Paperant", "setPageLoaded() changing pdf_image_view")
    }

    private fun renderPagesInBackground() {
        while (true) {
            val pageNum = pageQueue.takeLast()
            if (pageNum in blurredCache && pageNum != currentPosition.toInt()) continue
            Log.d("Paperant", "background renderer start rendering page ${pageNum}")

            val bitmap = loadPage(pageNum)

            runOnUiThread {
                synchronized(currentPosition) {
                    // TODO necessary?
                    if (pageNum == currentPosition.toInt()) {
                        setPageLoaded(bitmap)
                    }
                }
            }

            if (!blurredCache.contains(pageNum)) {
                blurredCache[pageNum] = compressBitmap(blurredImage(bitmap))
            }
        }
    }

    private fun blurredImage(bitmap: Bitmap): Bitmap {
        val rs = RenderScript.create(this)
        val blurrer = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        val input = Allocation.createFromBitmap(rs, bitmap)
        val output = Allocation.createTyped(rs, input.type)
        blurrer.setRadius(BLUR_RADIUS)
        blurrer.setInput(input)
        blurrer.forEach(output)

        // TODO: try 565
        val outputBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        output.copyTo(outputBitmap)
        input.destroy()
        output.destroy()
        rs.destroy()

        return outputBitmap
    }

    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        val s = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP, 0, s)
        return s.toByteArray()
    }

    private fun decompressBitmap(b: ByteArray) =
        BitmapFactory.decodeByteArray(b, 0, b.size)


    private fun turnPage(delta: Int) {
        val newPosition = (currentPosition + delta).coerceIn(0.0, numPage-1.0)
        renderPageIfDifferent(newPosition)
    }

    private fun loadPage(pageNum: Int): Bitmap {
        Log.i("Paperant", "loadPage ${pageNum}")
        val page = doc!!.loadPage(pageNum)
        val ctm = AndroidDrawDevice.fitPage(page, pdf_image_view.width, pdf_image_view.height)
        return AndroidDrawDevice.drawPage(page, ctm)
    }

    private fun renderPageIfDifferent(newPosition: Double) {
        synchronized(currentPosition) {
            val pageNumChanged = (newPosition.toInt() != currentPosition.toInt())
            currentPosition = newPosition
            if (!pageNumChanged) return

            val pageNum = currentPosition.toInt()
            setPageLoading(blurredCache[pageNum])
            pageQueue.addLast(pageNum)
        }
    }
}