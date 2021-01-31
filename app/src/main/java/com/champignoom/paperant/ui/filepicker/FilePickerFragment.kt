package com.champignoom.paperant.ui.filepicker

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.champignoom.paperant.ui.mydocument.MyDocumentActivity
import com.champignoom.paperant.R
import com.champignoom.paperant.databinding.FragmentFilepickerBinding
import java.io.File
import java.util.*

class MyAdapter(var path: String): RecyclerView.Adapter<MyAdapter.MyViewHolder>() {
    class MyViewHolder(var file: File?, val textView: TextView, val onClick: (File) -> Unit): RecyclerView.ViewHolder(textView) {
        init {
            textView.setOnClickListener { onClick(file!!) }
        }
    }

    var files = File(path).listFiles()!!
    val backStack = Stack<String>().apply { push(path) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val textView = TextView(parent.context)
        return MyViewHolder(null, textView) {
            when {
                it.isDirectory->  {
                    backStack.push(it.canonicalPath)
                    setDirectory(it.canonicalPath)
                }
                it.name.run {substring(lastIndexOf('.').coerceAtLeast(0)).decapitalize(Locale.getDefault()) in arrayOf(".pdf",/* ".epub"*/)} -> {
                    parent.context.startActivity(Intent(parent.context, MyDocumentActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = Uri.fromFile(it)
                    })
                }
                else -> {
                    Toast.makeText(
                        parent.context,
                        "${it.canonicalPath} not a directory nor a pdf/epub",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.file = files[position]
        holder.textView.apply {
            text = holder.file!!.name
            val (iconResource, iconColor) = when {
                files[position].isDirectory -> Pair(R.drawable.baseline_folder_24, Color.YELLOW)
                else -> Pair(R.drawable.baseline_insert_drive_file_24, Color.RED)
            }
            setCompoundDrawablesWithIntrinsicBounds(iconResource, 0, 0, 0)
            compoundDrawables[0].setTint(iconColor)
            setTextAppearance(android.R.style.TextAppearance_Large)
        }
    }

    override fun getItemCount(): Int {
        return files.size
    }

    private fun setDirectory(path: String) {
        files = File(path).listFiles()!!
        notifyDataSetChanged()
    }

    fun goBack(): Boolean {
        backStack.pop()
        if (backStack.empty()) {
            return false
        }
        setDirectory(backStack.peek())
        return true
    }
}

class FilePickerFragment : Fragment() {
    private lateinit var filePickerViewModel: FilePickerViewModel
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var viewAdapter: MyAdapter
    private var _binding: FragmentFilepickerBinding? = null
    private val binding get() = _binding!!

    private val onBackPressedCallback = object: OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!viewAdapter.goBack()) {
                isEnabled = false
                requireActivity().onBackPressed()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        filePickerViewModel =
            ViewModelProvider(this).get(FilePickerViewModel::class.java)
        _binding = FragmentFilepickerBinding.inflate(inflater, container, false)
//        val textView: TextView = root.findViewById(R.id.text_gallery)
//        galleryViewModel.text.observe(viewLifecycleOwner, Observer {
//            textView.text = it
//        })
//
        viewManager = LinearLayoutManager(context)
        viewAdapter = MyAdapter(Environment.getExternalStorageDirectory().canonicalPath)
        binding.recyclerView.apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}