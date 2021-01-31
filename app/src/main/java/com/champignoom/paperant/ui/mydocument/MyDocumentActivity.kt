    package com.champignoom.paperant.ui.mydocument

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
import android.util.Size
import android.view.GestureDetector
import android.view.ScaleGestureDetector
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.component1
import androidx.core.util.component2
import androidx.lifecycle.lifecycleScope
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import com.champignoom.paperant.MyDatabase
import com.champignoom.paperant.R
import com.champignoom.paperant.databinding.ActivityMyDocumentBinding
import com.champignoom.paperant.ui.recent.RecentItem
import kotlinx.android.synthetic.main.activity_my_document.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.concurrent.LinkedBlockingDeque

class MyDocumentActivity : AppCompatActivity() {
    companion object {
        const val BLUR_RADIUS = 15f
    }

    private var blurredCache: MutableMap<Int, ByteArray> = mutableMapOf()

    private var pageQueue = LinkedBlockingDeque<Int>()

    private lateinit var binding: ActivityMyDocumentBinding
    private lateinit var viewModel: MyDocumentViewModel

    // used both in UI thread and in background rendering thread, therefore needs locking
    private var viewSize = Size(-1, -1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyDocumentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val uri = intent.data ?: return
        viewModel = MyDocumentViewModel(Document.openDocument(uri.path)) // TODO: asynchronous loading

        // TODO: better threading
        lifecycleScope.launch(newSingleThreadContext("databaseThread")) {
            MyDatabase.getInstance(this@MyDocumentActivity).recentItemDao.insert(RecentItem(
                Instant.now().toEpochMilli(),
                uri.toString(),
                "",
            ))
        }

        binding.pageView.apply {
            onSizeListener = fun (w, h) {
                synchronized(viewSize) { viewSize = Size(w, h) }
                if (viewModel.currentPage < 0)  // initialization
                    viewModel.currentPage = 0
                showPage()
            }

            onPageDeltaClicked = fun (delta) {
                val newPageNum = viewModel.currentPage + delta
                if (newPageNum !in 0 until viewModel.numPage) return
                showPage(newPageNum)
            }
        }

        round_pad.init(4.0, viewModel.numPage - 1.0, 0)
        round_pad.onDeltaListener = fun (delta) {
            val newPageNum = (viewModel.currentPage + delta).coerceIn(0, viewModel.numPage)
            if (newPageNum != viewModel.currentPage) {
                showPage(newPageNum)
            }
        }

        lifecycleScope.launch(newSingleThreadContext("backgroundRendererThread")) {
            renderPagesInBackground()
        }
    }

    // supposed to be called in the UI thread
    private fun showPage(pageNum: Int = viewModel.currentPage) {
        viewModel.currentPage = pageNum
        binding.pageView.setLoading(pageNum, blurredCache[pageNum]?.let {decompressBitmap(it)})
        pageQueue.addLast(pageNum)
    }

    private fun renderPagesInBackground() {
        while (true) {
            val pageNum = pageQueue.takeLast()

            // viewModel.currentPage must either ==pageNum or present later in the queue
            if (pageNum in blurredCache && pageNum != viewModel.currentPage) continue

            val bitmap = loadPage(pageNum)
            runOnUiThread { binding.pageView.morphToFull(pageNum, bitmap) }
            if (pageNum !in blurredCache) {
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


    private fun loadPage(pageNum: Int): Bitmap {
        Log.i("Paperant", "loadPage ${pageNum}")
        val page = viewModel.doc.loadPage(pageNum)
        val (w, h) = synchronized(viewSize) {viewSize}
        val ctm = AndroidDrawDevice.fitPage(page, w, h)
        return AndroidDrawDevice.drawPage(page, ctm)
    }
}