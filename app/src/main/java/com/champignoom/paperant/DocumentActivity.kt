package com.champignoom.paperant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView.OnEditorActionListener
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.artifex.mupdf.fitz.*
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

class DocumentActivity : Activity() {
    private val APP = "MuPDF"
    val NAVIGATE_REQUEST = 1
    val PERMISSION_REQUEST = 42
    var worker: Worker? = null
    var prefs: SharedPreferences? = null
    var doc: Document? = null
    var key: String? = null
    var path: String? = null
    var mimetype: String? = null
    var buffer: ByteArray? = null
    var hasLoaded = false
    var isReflowable = false
    var fitPage = false
    var title: String? = null
    var flatOutline: ArrayList<OutlineActivity.Item>? = null
    var layoutW = 0f
    var layoutH = 0f
    var layoutEm = 0f
    var displayDPI = 0f
    var canvasW = 0
    var canvasH = 0
    var pageZoom = 0f
    var currentBar: View? = null
    var pageView: PageView? = null
    var actionBar: View? = null
    var titleLabel: TextView? = null
    var searchButton: View? = null
    var searchBar: View? = null
    var searchText: EditText? = null
    var searchCloseButton: View? = null
    var searchBackwardButton: View? = null
    var searchForwardButton: View? = null
    var zoomButton: View? = null
    var layoutButton: View? = null
    var layoutPopupMenu: PopupMenu? = null
    var outlineButton: View? = null
    var navigationBar: View? = null
    var pageLabel: TextView? = null
    var pageSeekbar: SeekBar? = null
    var pageCount = 0
    var currentPage = 0
    var searchHitPage = 0
    var searchNeedle: String? = null
    var stopSearch = false
    var history: Stack<Int>? = null
    var wentBack = false
    private fun toHex(digest: ByteArray): String {
        val builder = StringBuilder(2 * digest.size)
        for (b in digest) builder.append(String.format("%02x", b))
        return builder.toString()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        displayDPI = metrics.densityDpi.toFloat()
        setContentView(R.layout.activity_document)
        actionBar = findViewById(R.id.action_bar)
        searchBar = findViewById(R.id.search_bar)
        navigationBar = findViewById(R.id.navigation_bar)
        currentBar = actionBar
        val uri = intent.data
        mimetype = intent.type
        key = uri.toString()
        if (uri!!.scheme == "file") {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_DENIED
            ) ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ), PERMISSION_REQUEST
            )
            title = uri.lastPathSegment
            path = uri.path
        } else {
            title = uri.toString()
            try {
                val stm = contentResolver.openInputStream(uri)
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16384)
                var n: Int
                while (stm!!.read(buf).also { n = it } != -1) out.write(buf, 0, n)
                out.flush()
                buffer = out.toByteArray()
                key = toHex(MessageDigest.getInstance("MD5").digest(buffer))
            } catch (x: IOException) {
                Log.e(APP, x.toString())
                Toast.makeText(this, x.message, Toast.LENGTH_SHORT).show()
            } catch (x: NoSuchAlgorithmException) {
                Log.e(APP, x.toString())
                Toast.makeText(this, x.message, Toast.LENGTH_SHORT).show()
            }
        }
        titleLabel = findViewById<View>(R.id.title_label) as TextView
        titleLabel!!.text = title
        history = Stack()
        worker = Worker(this)
        worker!!.start()
        prefs = getPreferences(MODE_PRIVATE)
        layoutEm = prefs!!.getFloat("layoutEm", 8f)
        fitPage = prefs!!.getBoolean("fitPage", false)
        currentPage = prefs!!.getInt(key, 0)
        searchHitPage = -1
        hasLoaded = false
        pageView = findViewById<View>(R.id.page_view) as PageView
        pageView!!.actionListener = this
        pageLabel = findViewById<View>(R.id.page_label) as TextView
        pageSeekbar = findViewById<View>(R.id.page_seekbar) as SeekBar
        pageSeekbar!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            var newProgress = -1
            override fun onProgressChanged(seekbar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    newProgress = progress
                    pageLabel!!.text = (progress + 1).toString() + " / " + pageCount
                }
            }

            override fun onStartTrackingTouch(seekbar: SeekBar) {}
            override fun onStopTrackingTouch(seekbar: SeekBar) {
                gotoPage(newProgress)
            }
        })
        searchButton = findViewById(R.id.search_button)
        searchButton!!.setOnClickListener(View.OnClickListener { showSearch() })
        searchText = findViewById<View>(R.id.search_text) as EditText
        searchText!!.setOnEditorActionListener(OnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_NULL && event.action == KeyEvent.ACTION_DOWN) {
                search(1)
                return@OnEditorActionListener true
            }
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(1)
                return@OnEditorActionListener true
            }
            false
        })
        searchText!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                resetSearch()
            }
        })
        searchCloseButton = findViewById(R.id.search_close_button)
        searchCloseButton!!.setOnClickListener(View.OnClickListener { hideSearch() })
        searchBackwardButton = findViewById(R.id.search_backward_button)
        searchBackwardButton!!.setOnClickListener(View.OnClickListener { search(-1) })
        searchForwardButton = findViewById(R.id.search_forward_button)
        searchForwardButton!!.setOnClickListener(View.OnClickListener { search(1) })
        outlineButton = findViewById(R.id.outline_button)
        outlineButton!!.setOnClickListener(View.OnClickListener {
            val intent = Intent(this@DocumentActivity, OutlineActivity::class.java)
            val bundle = Bundle()
            bundle.putInt("POSITION", currentPage)
            bundle.putSerializable("OUTLINE", flatOutline)
            intent.putExtras(bundle)
            startActivityForResult(intent, NAVIGATE_REQUEST)
        })
        zoomButton = findViewById(R.id.zoom_button)
        zoomButton!!.setOnClickListener(View.OnClickListener {
            fitPage = !fitPage
            loadPage()
        })
        layoutButton = findViewById(R.id.layout_button)
        layoutPopupMenu = PopupMenu(this, layoutButton)
        layoutPopupMenu!!.menuInflater.inflate(R.menu.layout_menu, layoutPopupMenu!!.menu)
        layoutPopupMenu!!.setOnMenuItemClickListener { item ->
            val oldLayoutEm = layoutEm
            val id = item.itemId
            if (id == R.id.action_layout_6pt) layoutEm =
                6f else if (id == R.id.action_layout_7pt) layoutEm =
                7f else if (id == R.id.action_layout_8pt) layoutEm =
                8f else if (id == R.id.action_layout_9pt) layoutEm =
                9f else if (id == R.id.action_layout_10pt) layoutEm =
                10f else if (id == R.id.action_layout_11pt) layoutEm =
                11f else if (id == R.id.action_layout_12pt) layoutEm =
                12f else if (id == R.id.action_layout_13pt) layoutEm =
                13f else if (id == R.id.action_layout_14pt) layoutEm =
                14f else if (id == R.id.action_layout_15pt) layoutEm =
                15f else if (id == R.id.action_layout_16pt) layoutEm = 16f
            if (oldLayoutEm != layoutEm) relayoutDocument()
            true
        }
        layoutButton!!.setOnClickListener(View.OnClickListener { layoutPopupMenu!!.show() })
    }

    fun onPageViewSizeChanged(w: Int, h: Int) {
        pageZoom = 1f
        canvasW = w
        canvasH = h
        layoutW = canvasW * 72 / displayDPI
        layoutH = canvasH * 72 / displayDPI
        if (!hasLoaded) {
            hasLoaded = true
            openDocument()
        } else if (isReflowable) {
            relayoutDocument()
        } else {
            loadPage()
        }
    }

    fun onPageViewZoomChanged(zoom: Float) {
        if (zoom != pageZoom) {
            pageZoom = zoom
            loadPage()
        }
    }

    fun openDocument() {
        worker!!.add(object : Worker.Task() {
            var needsPassword = false
            override fun work() {
                Log.i(APP, "open document")
                doc = if (path != null) Document.openDocument(path) else Document.openDocument(
                    buffer,
                    mimetype
                )
                needsPassword = doc!!.needsPassword()
            }

            override fun run() {
                if (needsPassword) askPassword(R.string.dlog_password_message) else loadDocument()
            }
        })
    }

    fun askPassword(message: Int) {
        val passwordView = EditText(this)
        passwordView.inputType = EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
        passwordView.transformationMethod = PasswordTransformationMethod.getInstance()
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.dlog_password_title)
        builder.setMessage(message)
        builder.setView(passwordView)
        builder.setPositiveButton(
            android.R.string.ok
        ) { dialog, id -> checkPassword(passwordView.text.toString()) }
        builder.setNegativeButton(
            android.R.string.cancel
        ) { dialog, id -> finish() }
        builder.setOnCancelListener { finish() }
        builder.create().show()
    }

    fun checkPassword(password: String?) {
        worker!!.add(object : Worker.Task() {
            var passwordOkay = false
            override fun work() {
                Log.i(APP, "check password")
                passwordOkay = doc!!.authenticatePassword(password)
            }

            override fun run() {
                if (passwordOkay) loadDocument() else askPassword(R.string.dlog_password_retry)
            }
        })
    }

    public override fun onPause() {
        super.onPause()
        val editor = prefs!!.edit()
        editor.putFloat("layoutEm", layoutEm)
        editor.putBoolean("fitPage", fitPage)
        editor.putInt(key, currentPage)
        editor.apply()
    }

    override fun onBackPressed() {
        if (history!!.empty()) {
            super.onBackPressed()
        } else {
            currentPage = history!!.pop()
            loadPage()
        }
    }

    public override fun onActivityResult(request: Int, result: Int, data: Intent) {
        if (request == NAVIGATE_REQUEST && result >= RESULT_FIRST_USER) gotoPage(result - RESULT_FIRST_USER)
    }

    fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm?.showSoftInput(searchText, 0)
    }

    fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm?.hideSoftInputFromWindow(searchText!!.windowToken, 0)
    }

    fun resetSearch() {
        stopSearch = true
        searchHitPage = -1
        searchNeedle = null
        pageView!!.resetHits()
    }

    fun runSearch(startPage: Int, direction: Int, needle: String) {
        stopSearch = false
        worker!!.add(object : Worker.Task() {
            var searchPage = startPage
            override fun work() {
                if (stopSearch || needle !== searchNeedle) return
                for (i in 0..8) {
                    Log.i(APP, "search page $searchPage")
                    val page = doc!!.loadPage(searchPage)
                    val hits = page.search(searchNeedle)
                    page.destroy()
                    if (hits != null && hits.size > 0) {
                        searchHitPage = searchPage
                        break
                    }
                    searchPage += direction
                    if (searchPage < 0 || searchPage >= pageCount) break
                }
            }

            override fun run() {
                if (stopSearch || needle !== searchNeedle) {
                    pageLabel!!.text = (currentPage + 1).toString() + " / " + pageCount
                } else if (searchHitPage == currentPage) {
                    loadPage()
                } else if (searchHitPage >= 0) {
                    history!!.push(currentPage)
                    currentPage = searchHitPage
                    loadPage()
                } else {
                    if (searchPage >= 0 && searchPage < pageCount) {
                        pageLabel!!.text = (searchPage + 1).toString() + " / " + pageCount
                        worker!!.add(this)
                    } else {
                        pageLabel!!.text = (currentPage + 1).toString() + " / " + pageCount
                        Log.i(APP, "search not found")
                        Toast.makeText(
                            this@DocumentActivity,
                            getString(R.string.toast_search_not_found),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    fun search(direction: Int) {
        hideKeyboard()
        val startPage: Int
        startPage = if (searchHitPage == currentPage) currentPage + direction else currentPage
        searchHitPage = -1
        searchNeedle = searchText!!.text.toString()
        if (searchNeedle!!.length == 0) searchNeedle = null
        if (searchNeedle != null) if (startPage >= 0 && startPage < pageCount) runSearch(
            startPage, direction,
            searchNeedle!!
        )
    }

    fun loadDocument() {
        worker!!.add(object : Worker.Task() {
            override fun work() {
                try {
                    Log.i(APP, "load document")
                    val metaTitle = doc!!.getMetaData(Document.META_INFO_TITLE)
                    if (metaTitle != null) title = metaTitle
                    isReflowable = doc!!.isReflowable
                    if (isReflowable) {
                        Log.i(APP, "layout document")
                        doc!!.layout(layoutW, layoutH, layoutEm)
                    }
                    pageCount = doc!!.countPages()
                } catch (x: Throwable) {
                    doc = null
                    pageCount = 1
                    currentPage = 0
                    throw x
                }
            }

            override fun run() {
                if (currentPage < 0 || currentPage >= pageCount) currentPage = 0
                titleLabel!!.text = title
                if (isReflowable) layoutButton!!.visibility =
                    View.VISIBLE else zoomButton!!.visibility =
                    View.VISIBLE
                loadPage()
                loadOutline()
            }
        })
    }

    fun relayoutDocument() {
        worker!!.add(object : Worker.Task() {
            override fun work() {
                try {
                    val mark = doc!!.makeBookmark(doc!!.locationFromPageNumber(currentPage))
                    Log.i(APP, "relayout document")
                    doc!!.layout(layoutW, layoutH, layoutEm)
                    pageCount = doc!!.countPages()
                    currentPage = doc!!.pageNumberFromLocation(doc!!.findBookmark(mark))
                } catch (x: Throwable) {
                    pageCount = 1
                    currentPage = 0
                    throw x
                }
            }

            override fun run() {
                loadPage()
                loadOutline()
            }
        })
    }

    private fun loadOutline() {
        worker!!.add(object : Worker.Task() {
            private fun flattenOutline(outline: Array<Outline>, indent: String) {
                for (node in outline) {
                    if (node.title != null) {
                        val outlinePage = doc!!.pageNumberFromLocation(doc!!.resolveLink(node))
                        flatOutline!!.add(
                            OutlineActivity.Item(
                                indent + node.title,
                                node.uri,
                                outlinePage
                            )
                        )
                    }
                    if (node.down != null) flattenOutline(node.down, "$indent    ")
                }
            }

            override fun work() {
                Log.i(APP, "load outline")
                val outline = doc!!.loadOutline()
                if (outline != null) {
                    flatOutline = ArrayList()
                    flattenOutline(outline, "")
                } else {
                    flatOutline = null
                }
            }

            override fun run() {
                if (flatOutline != null) outlineButton!!.visibility = View.VISIBLE
            }
        })
    }

    fun loadPage() {
        val pageNumber = currentPage
        val zoom = pageZoom
        stopSearch = true
        worker!!.add(object : Worker.Task() {
            var bitmap: Bitmap? = null
            var links: Array<Link>? = null
            var hits: Array<Quad>? = null
            override fun work() {
                try {
                    Log.i(APP, "load page $pageNumber")
                    val page = doc!!.loadPage(pageNumber)
                    Log.i(APP, "draw page $pageNumber zoom=$zoom")
                    val ctm: Matrix
                    ctm =
                        if (fitPage) AndroidDrawDevice.fitPage(
                            page,
                            canvasW,
                            canvasH
                        ) else AndroidDrawDevice.fitPageWidth(page, canvasW)
                    links = page.links
                    if (links != null) for (link in links!!) link.bounds.transform(ctm)
                    if (searchNeedle != null) {
                        hits = page.search(searchNeedle)
                        if (hits != null) for (hit in hits!!) hit.transform(ctm)
                    }
                    if (zoom != 1f) ctm.scale(zoom)
                    bitmap = AndroidDrawDevice.drawPage(page, ctm)
                } catch (x: Throwable) {
                    Log.e(APP, x.message)
                }
            }

            override fun run() {
                if (bitmap != null) pageView!!.setBitmap(
                    bitmap,
                    zoom,
                    wentBack,
                    links,
                    hits
                ) else pageView!!.setError()
                pageLabel!!.text = (currentPage + 1).toString() + " / " + pageCount
                pageSeekbar!!.max = pageCount - 1
                pageSeekbar!!.progress = pageNumber
                wentBack = false
            }
        })
    }

    fun showSearch() {
        currentBar = searchBar
        actionBar!!.visibility = View.GONE
        searchBar!!.visibility = View.VISIBLE
        searchBar!!.requestFocus()
        showKeyboard()
    }

    fun hideSearch() {
        currentBar = actionBar
        actionBar!!.visibility = View.VISIBLE
        searchBar!!.visibility = View.GONE
        hideKeyboard()
        resetSearch()
    }

    fun toggleUI() {
        if (navigationBar!!.visibility == View.VISIBLE) {
            currentBar!!.visibility = View.GONE
            navigationBar!!.visibility = View.GONE
            if (currentBar === searchBar) hideKeyboard()
        } else {
            currentBar!!.visibility = View.VISIBLE
            navigationBar!!.visibility = View.VISIBLE
            if (currentBar === searchBar) {
                searchBar!!.requestFocus()
                showKeyboard()
            }
        }
    }

    fun goBackward() {
        if (currentPage > 0) {
            wentBack = true
            currentPage--
            loadPage()
        }
    }

    fun goForward() {
        if (currentPage < pageCount - 1) {
            currentPage++
            loadPage()
        }
    }

    fun gotoPage(p: Int) {
        if (p >= 0 && p < pageCount && p != currentPage) {
            history!!.push(currentPage)
            currentPage = p
            loadPage()
        }
    }

    fun gotoPage(uri: String?) {
        gotoPage(doc!!.pageNumberFromLocation(doc!!.resolveLink(uri)))
    }

    fun gotoURI(uri: String?) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET) // FLAG_ACTIVITY_NEW_DOCUMENT in API>=21
        try {
            startActivity(intent)
        } catch (x: Throwable) {
            Log.e(APP, x.message)
            Toast.makeText(this@DocumentActivity, x.message, Toast.LENGTH_SHORT).show()
        }
    }
}