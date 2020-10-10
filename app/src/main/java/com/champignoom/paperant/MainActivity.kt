package com.champignoom.paperant

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_test_round_pad.*

import java.io.File
import java.util.*


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val uri = Uri.parse("file:///storage/emulated/0/Download/slides.pdf")
        button.setOnClickListener { openPdfFile(uri) }

//        button2.alpha = 0.5f

//        test_view.setBackgroundColor(Color.CYAN)
//        test_view.alpha = 0.5f
//        test_view.setOnTouchListener { v, event ->
//            if (false) v.performClick()
//
//            text_view.text = "${event.x}, ${event.y}"
//            true
//        }

        var v = 0.0
        test_view.onDeltaListener = {
            v += it
            text_view.text = "$v"
        }
    }

    private fun openPdfFile(uri: Uri) {
        val file = File(uri.path!!)
        text_view.text = Date(file.lastModified()).toString()

        val intent = Intent(this, DocumentActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
        }
        startActivity(intent)
    }
}

