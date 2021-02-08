package com.champignoom.paperant.ui.mydocument

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import com.champignoom.paperant.KillableThread
import com.champignoom.paperant.databinding.ActivityPdfDocumentBinding


class PdfDocumentViewModel : ViewModel() {
    var doc: Document? = null
    var nPage: Int = 0
    var currentPageNum: Int = -1
}

class PdfDocumentActivity : AppCompatActivity() {
    private val viewModel: PdfDocumentViewModel by viewModels()
    private lateinit var binding: ActivityPdfDocumentBinding

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
    }

    private fun loadPage(pageNum: Int) {
        binding.pageView.setLoading()
        viewModel.currentPageNum = pageNum
        val page = viewModel.doc!!.loadPage(viewModel.currentPageNum)
        val size = binding.pageView.canvasSize
        val ctm = AndroidDrawDevice.fitPage(page, size.width, size.height)
        val bitmap = AndroidDrawDevice.drawPage(page, ctm)
        binding.pageView.setLoaded(bitmap)
    }

    private fun onPageDelta(delta: Int) {
        val newPageNum = (viewModel.currentPageNum + delta).coerceIn(0 until viewModel.nPage)
        if (viewModel.currentPageNum == newPageNum)
            return
        loadPage(newPageNum)
    }
}