package com.champignoom.paperant

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MotionEvent
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import kotlinx.android.synthetic.main.activity_my_document.*

class MyDocumentActivity : AppCompatActivity() {
    private var currentPosition = -1.0
    private var numPage = 0
    private var doc: Document? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_document)

        val uri = intent.data ?: run {return}
        doc = Document.openDocument(uri.path)
        numPage = doc!!.countPages()

        pdf_image_view.viewTreeObserver.addOnGlobalLayoutListener {
            renderPageIfDifferent(0)
            currentPosition = 0.0
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

        round_pad.onDeltaListener = {
            // TODO: queue in another thread
            val newPosition = (currentPosition + it).coerceIn(0.0, (numPage-1).toDouble())
            renderPageIfDifferent(newPosition.toInt())
            currentPosition = newPosition
        }
    }

    private fun turnPage(delta: Int) {
        val newPosition = (currentPosition.toInt() + delta).coerceIn(0, numPage-1)
        renderPageIfDifferent(newPosition)
        currentPosition = newPosition.toDouble()
    }

    private fun renderPageIfDifferent(pageNum: Int) {
        if (pageNum == currentPosition.toInt()) return

        val page = doc!!.loadPage(pageNum)
        val ctm = AndroidDrawDevice.fitPage(page, pdf_image_view.width, pdf_image_view.height)
        val bitmap = AndroidDrawDevice.drawPage(page, ctm)
        pdf_image_view.setImageBitmap(bitmap)
    }
}