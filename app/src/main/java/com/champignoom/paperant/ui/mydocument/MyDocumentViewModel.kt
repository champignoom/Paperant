package com.champignoom.paperant.ui.mydocument

import android.graphics.Bitmap
import android.util.Size
import androidx.lifecycle.ViewModel
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.Page

data class PageToken(val pageNum: Int, val size: Size)

class MyDocumentViewModel: ViewModel() {
    var doc: PDFDocument? = null
    var numPage: Int = 0
    var token: PageToken? = null
    var currentPage: PDFPage? = null
    var currentBitmap: Bitmap? = null

    fun setCurrentConfig(token: PageToken? = this.token, page: PDFPage? = this.currentPage, bitmap: Bitmap? = this.currentBitmap) {
        synchronized(this) {
            this.token = token
            this.currentPage = page
            this.currentBitmap = bitmap
        }
    }

    fun getBitmapByPageNum(pageNum: Int): Bitmap? {
        synchronized(this) {
            val tokenPageNum = token?.pageNum ?: return null
            return if (tokenPageNum == pageNum) currentBitmap else null
        }
    }

    fun getBitmapByToken(token: PageToken): Bitmap? =
        synchronized(this) { if (token == this.token) currentBitmap else null }

    fun getPage(pageNum: Int): PDFPage? {
        synchronized(this) {
            val tokenPageNum = token?.pageNum ?: return null
            return if (tokenPageNum == pageNum) currentPage else null
        }
    }
}