package com.champignoom.paperant

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.champignoom.paperant.old.DocumentActivity
import com.champignoom.paperant.ui.mydocument.MyDocumentActivity
import com.champignoom.paperant.ui.mydocument.PdfDocumentActivity

fun myOpenDocument(context: Context, path: String, useExample: Boolean = false) {
    val cls = if (useExample) DocumentActivity::class else PdfDocumentActivity::class
    context.startActivity(Intent(context, cls.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse(path)
    })
}