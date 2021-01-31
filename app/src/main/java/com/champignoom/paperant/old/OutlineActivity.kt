package com.champignoom.paperant.old

import android.app.ListActivity
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ListView
import java.io.Serializable
import java.util.*

class OutlineActivity : ListActivity() {
    class Item(var title: String, var uri: String, var page: Int) : Serializable {
        override fun toString(): String {
            return title
        }
    }

    private var adapter: ArrayAdapter<Item>? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        listAdapter = adapter
        val bundle = intent.extras!!
        val currentPage = bundle.getInt("POSITION")
        val outline = bundle.getSerializable("OUTLINE") as ArrayList<Item>
        var found = -1
        for (i in outline.indices) {
            val item = outline[i]
            if (found < 0 && item.page >= currentPage) found = i
            adapter!!.add(item)
        }
        if (found >= 0) setSelection(found)
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val item = adapter!!.getItem(position)
        setResult(RESULT_FIRST_USER + item!!.page)
        finish()
    }
}