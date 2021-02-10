package com.champignoom.paperant.ui.mydocument

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import com.champignoom.paperant.databinding.ActivityPdfDocumentBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.max


class PdfDocumentViewModel : ViewModel() {
    var doc: Document? = null
    var nPage: Int = 0
    var currentPageNum: Int = -1
}

class PdfDocumentActivity : AppCompatActivity() {
    private val viewModel: PdfDocumentViewModel by viewModels()
    private lateinit var binding: ActivityPdfDocumentBinding
    private var mCookie: Cookie? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfDocumentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (viewModel.doc == null) {
            while (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED)
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)

            val uri = intent.data ?: return
            viewModel.doc = Document.openDocument(uri.path) as PDFDocument
            viewModel.nPage = viewModel.doc!!.countPages()
        }

        binding.roundPad.setStep(4.0, viewModel.nPage.toDouble())
        binding.roundPad.onDeltaListener = ::onPageDelta

        binding.pageView.onSizeChangeListener = {_, _ -> loadPage(max(0, viewModel.currentPageNum))}

        val testIfThreadTerminateAfterDestroyActivity = object: KillableThread() {
            override fun slowAsync() {
                var i=0
                while (true) {
                    i += 1
                    Log.d("Paperant", "test thread: i=${i}")
                    Thread.sleep(1000)
                }
            }
            override fun fastSynced() {
                TODO("Not yet implemented")
            }
        }
        lifecycleScope.launch(Dispatchers.Default) {
            while (isActive) {

            }
        }

//        testThread.run()
    }

    private val pageRendererDaemon: suspend CoroutineScope.() -> Unit = {
        while (isActive) {
            // pop out the pagenum and token, block if doesn't exist
            // new cookie
            // get page
            // draw
            // post refresher to ui thread with token

        }
    }

    private fun pageLoaderDaemon() {
        while (coroutineContext.isActive) {

        }
    }

    override fun onDestroy() {
        renderThread?.stop()
        super.onDestroy()
    }
    private fun loadPage(pageNum: Int) {
        binding.pageView.setLoading()
        viewModel.currentPageNum = pageNum

        renderThread?.stop()
        renderThread = object: KillableThread() {
            private lateinit var mBitmap: Bitmap
            override fun slowAsync() {
                // FIXME: duplicate MuPDF context
                val page = viewModel.doc!!.loadPage(viewModel.currentPageNum)
                val size = binding.pageView.canvasSize
                val ctm = AndroidDrawDevice.fitPage(page, size.width, size.height)
                try {
                    mBitmap = AndroidDrawDevice.drawPage(page, ctm)
                }
                catch (e: RuntimeException) {
                    Log.d("Paperant", "exception while drawing page ${viewModel.currentPageNum}")
                    e.printStackTrace()
                }
            }

            override fun fastSynced() {
                runOnUiThread {
                    binding.pageView.setLoaded(mBitmap)
                }
            }
        }
        renderThread!!.start()
    }

    private fun onPageDelta(delta: Int) {
        val newPageNum = (viewModel.currentPageNum + delta).coerceIn(0 until viewModel.nPage)
        if (viewModel.currentPageNum == newPageNum)
            return
        loadPage(newPageNum)
    }
}