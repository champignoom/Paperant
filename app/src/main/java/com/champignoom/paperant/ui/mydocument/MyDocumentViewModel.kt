package com.champignoom.paperant.ui.mydocument

import androidx.lifecycle.ViewModel
import com.artifex.mupdf.fitz.Document

class MyDocumentViewModel(var doc: Document): ViewModel() {
    val numPage = doc.countPages()
    var currentPage = -1
}