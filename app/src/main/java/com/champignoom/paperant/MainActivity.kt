package com.champignoom.paperant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider

import java.io.File
import java.util.*


class MainActivity : AppCompatActivity() {
    companion object ResultCode {
        const val CHOOSE_FILE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // FIXME awful workaround
        val builder = VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())

        val button = findViewById<Button>(R.id.button)
//        button.setOnClickListener {
//            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
//            intent.type = "*/*"
//            intent.addCategory(Intent.CATEGORY_OPENABLE)
//            startActivityForResult(intent, CHOOSE_FILE)
//        }
        val uri = Uri.parse("file:///storage/emulated/0/Download/slides.pdf")
//        val uri0 = Uri.parse("file:/storage/emulated/0/DCIM/Camera/20200719_155458.jpg")
//        val uri = FileProvider.getUriForFile(
//            this, applicationContext.packageName + ".provider",
//            File(uri0.path!!)
//        )
        button.setOnClickListener { openPdfFile(uri) }

//        val text = findViewById<TextView>(R.id.text)
//        text.text = Environment.getExternalStorageDirectory().toURI().toString()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            CHOOSE_FILE -> {
                if (resultCode != Activity.RESULT_OK) return

                val fileUri = data!!.data!!
                val text = findViewById<TextView>(R.id.text)
                text.text = fileUri.path
                Toast.makeText(this, "done", Toast.LENGTH_SHORT).show()

                // FIXME
                // The uri returned from Intent.ACTION_OPEN_DOCUMENT is of the format /document/home:Download/slides.pdf
                // but the real functioning uri is of the format file:/storage/emulated/0/Download/slides.pdf

                openPdfFile(fileUri)
            }
        }
    }

    fun openPdfFile(uri: Uri) {
        val intent = Intent(this, DocumentActivity::class.java).apply {
            setAction(Intent.ACTION_VIEW)
            setData(uri)
//            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
//        val intent = Intent()
//        intent.setAction(Intent.ACTION_VIEW)
//        intent.setDataAndType(uri, "application/*")
//        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val text = findViewById<TextView>(R.id.text)
//        text.text = Environment.getExternalStorageDirectory().toURI().toString()
//        val file = File(uri.toString())
        val file = File(uri.path!!)
        text.text = Date(file.lastModified()).toString()
        startActivity(intent)
    }
}

