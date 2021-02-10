package com.champignoom.paperant.ui.mydocument

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.RectI
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import com.champignoom.paperant.databinding.ActivityPdfDocumentBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.math.max
import kotlin.time.measureTime
import kotlin.time.measureTimedValue


class PdfDocumentViewModel : ViewModel() {
    var doc: Document? = null
    var nPage: Int = 0
    var currentPageNum: Int = -1
}

private data class PageLoadRequest(val pageNum: Int, val token: Long);

@OptIn(kotlin.time.ExperimentalTime::class)
class PdfDocumentActivity : AppCompatActivity() {
    private val viewModel: PdfDocumentViewModel by viewModels()
    private lateinit var binding: ActivityPdfDocumentBinding
    private var cookie: Cookie? = null
    private var pageToLoad = Channel<PageLoadRequest>(Channel.CONFLATED)

    private var waitingToken: Long = -1
    private var nextToken: Long = 0
    private fun newToken() = nextToken++

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfDocumentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (viewModel.doc == null) {
            while (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_DENIED)
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    0
                )

            val uri = intent.data ?: return
            viewModel.doc = Document.openDocument(uri.path) as PDFDocument
            viewModel.nPage = viewModel.doc!!.countPages()
        }

        binding.roundPad.setStep(4.0, viewModel.nPage.toDouble())
        binding.roundPad.onDeltaListener = ::onPageDelta

        binding.pageView.onSizeChangeListener = { _, _ -> loadPage(max(0, viewModel.currentPageNum))}

        lifecycleScope.launch(Dispatchers.Default, block = pageRendererDaemon)
    }

    private val pageRendererDaemon: suspend CoroutineScope.() -> Unit = {
        while (isActive) {
            val (pageNum, token) = pageToLoad.receive()
            val (page, timePage) = measureTimedValue { viewModel.doc!!.loadPage(pageNum) }
            val canvasSize = binding.pageView.canvasSize
            val ctm = AndroidDrawDevice.fitPage(page, canvasSize.width, canvasSize.height)
            val box = RectI(page.bounds.transform(ctm))
            val w = box.x1 - box.x0
            val h = box.y1 - box.y0
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val dev = AndroidDrawDevice(bmp, box.x0, box.y0)
            cookie = Cookie()
            val timeRun = measureTime { page.run(dev, ctm, cookie) }  // FIXME TODO: test, retrieve the exception name and handle it
            dev.close()
            dev.destroy()

            Log.d("Paperant", "page ${pageNum}: timePage=${timePage}, timeRun=${timeRun}")

            if (!isActive)
                throw CancellationException()

            runOnUiThread {
                if (waitingToken != token)
                    return@runOnUiThread

                binding.pageView.setLoaded(bmp)
            }
        }
    }

    override fun onDestroy() {
        cookie?.abort()
        super.onDestroy()
    }

    private fun loadPage(pageNum: Int) {
        cookie?.abort()
        binding.pageView.setLoading()
        viewModel.currentPageNum = pageNum
        waitingToken = newToken()
        pageToLoad.offer(PageLoadRequest(pageNum, waitingToken))  // always return true for CONFLATED channel
    }

    private fun onPageDelta(delta: Int) {
        val newPageNum = (viewModel.currentPageNum + delta).coerceIn(0 until viewModel.nPage)
        if (viewModel.currentPageNum == newPageNum)
            return
        loadPage(newPageNum)
    }
}