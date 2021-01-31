package com.champignoom.paperant.ui.mydocument

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import android.util.Size
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import com.champignoom.paperant.MyDatabase
import com.champignoom.paperant.databinding.ActivityMyDocumentBinding
import com.champignoom.paperant.ui.recent.RecentItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.concurrent.LinkedBlockingDeque

class MyDocumentActivity : AppCompatActivity() {
    companion object {
        const val BLUR_RADIUS = 15f
//        const val MIN_CACHE_BITMAP_SIZE = 1200  // px
    }

    private var blurredCache: MutableMap<Int, ByteArray> = mutableMapOf()

    private var tokenQueue = LinkedBlockingDeque<PageToken>()

    private lateinit var binding: ActivityMyDocumentBinding
    private lateinit var viewModel: MyDocumentViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyDocumentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val uri = intent.data ?: return
        viewModel =
            MyDocumentViewModel(Document.openDocument(uri.path) as PDFDocument) // TODO: asynchronous loading

        // TODO: better threading
        lifecycleScope.launch(newSingleThreadContext("databaseThread")) {
            MyDatabase.getInstance(this@MyDocumentActivity).recentItemDao.insert(
                RecentItem(
                    Instant.now().toEpochMilli(),
                    uri.toString(),
                    "",
                )
            )
        }

        binding.pageView.apply {
            reloader = fun(w, h) {
                showPage(PageToken(viewModel.token?.pageNum ?: 0, Size(w, h)))
            }

            onPageDeltaClicked = ::onPageDelta
        }

        binding.roundPad.setStep(4.0, viewModel.numPage.toDouble())
        binding.roundPad.onDeltaListener = ::onPageDelta

        lifecycleScope.launch(newSingleThreadContext("backgroundRendererThread")) {
            renderPagesInBackground()
        }
    }

    private fun onPageDelta(delta: Int) {
        val oldPageNum = viewModel.token!!.pageNum
        val newPageNum = (oldPageNum + delta).coerceIn(0 until viewModel.numPage)
        if (oldPageNum != newPageNum) {
            showPage(PageToken(newPageNum, binding.pageView.canvasSize()))
        }
    }

    // UI thread
    private fun setLoading(token: PageToken, blurredBitmap: Bitmap?) {
    }

    // UI thread
    private fun setLoaded(token: PageToken, page: PDFPage, fullBitmap: Bitmap) {
        if (viewModel.token != token) return
        val pageNotReloaded = viewModel.currentPage != null
        viewModel.setCurrentConfig(token, page, fullBitmap)
        binding.pageView.setLoaded(fullBitmap, preserveMatrix = pageNotReloaded)
    }

    // supposed to be called in the UI thread
    private fun showPage(token: PageToken) {
        if (viewModel.getBitmapByToken(token) != null)
            return

        val page = viewModel.getPage(token.pageNum)
        if (page == null) {
            val placeholderBitmap = blurredCache[token.pageNum]?.let { decompressBitmap(it) }
            binding.pageView.setLoading(placeholderBitmap)
        } // otherwise assume that a bitmap is already shown

        viewModel.setCurrentConfig(token, page, null)
        tokenQueue.addLast(token)
    }

//    private fun bitmapCachable(bitmap: Bitmap) =
//        max(bitmap.width, bitmap.height) >= MIN_CACHE_BITMAP_SIZE
//
//    private fun toCacheSize(bitmap: Bitmap): Bitmap {
//        val f = MIN_CACHE_BITMAP_SIZE.toDouble() / max(bitmap.width, bitmap.height)
//        return Bitmap.createScaledBitmap(bitmap, (bitmap.width*f).toInt(), (bitmap.height*f).toInt(), true)
//    }

    private fun renderPagesInBackground() {
        while (true) {
            val token = tokenQueue.takeLast()
            val (pageNum, size) = token

            // viewModel.currentPage must either ==pageNum or present later in the queue
            if (pageNum in blurredCache && token.pageNum != viewModel.token?.pageNum) continue

            val page = viewModel.getPage(pageNum) ?: (viewModel.doc.loadPage(pageNum) as PDFPage)
            Log.d("Paperant", "rendering with size ${size}")
            val ctm = AndroidDrawDevice.fitPage(page, size.width, size.height)
            val bitmap = AndroidDrawDevice.drawPage(page, ctm)

            runOnUiThread { setLoaded(token, page, bitmap) }
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


    private fun loadPage(token: PageToken): Bitmap {
        Log.i("Paperant", "loadPage ${token.pageNum}")
        val (pageNum, size) = token
        val page = viewModel.doc.loadPage(pageNum)
        val ctm = AndroidDrawDevice.fitPage(page, size.width, size.height)
        return AndroidDrawDevice.drawPage(page, ctm)
    }
}