package com.champignoom.paperant.ui.recent

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.champignoom.paperant.MyDatabase
import com.champignoom.paperant.databinding.FragmentRecentBinding
import com.champignoom.paperant.myOpenDocument
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import java.nio.file.Path


private class MyAdapter: RecyclerView.Adapter<MyAdapter.MyViewHolder>() {
    var items: List<RecentItem> = listOf()
    
    class MyViewHolder(val textView: TextView, val onClick: (String) -> Unit): RecyclerView.ViewHolder(textView) {
        private lateinit var mItem: RecentItem
        init {
            textView.setOnClickListener {
                onClick(mItem.filePath)
            }
        }

        fun updateItem(item: RecentItem) {
            mItem = item
            textView.text = Uri.decode(mItem.filePath)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val textView = TextView(parent.context)
        return MyViewHolder(textView) {
            myOpenDocument(parent.context, it)
        }
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.updateItem(items[position])
    }

    override fun getItemCount(): Int {
        return items.size
    }
}

class RecentFragment : Fragment() {

    private lateinit var viewModel: RecentViewModel
    private var _binding: FragmentRecentBinding? = null
    private val binding get() = _binding!!

    private lateinit var myAdapter: MyAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel =
            ViewModelProvider(this).get(RecentViewModel::class.java)
        _binding = FragmentRecentBinding.inflate(inflater, container, false)
//        val textView: TextView = root.findViewById(R.id.text_home)
//        RecentViewModel.text.observe(viewLifecycleOwner, Observer {
//            textView.text = it
//        })
        setupRecyclerView()
        viewModel.recentItems.observe(viewLifecycleOwner) {
            myAdapter.apply {
                items = it
                notifyDataSetChanged()
            }
        }

        return binding.root
    }

    private fun setupRecyclerView() {
        myAdapter = MyAdapter()
        binding.recyclerView.apply { 
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = myAdapter
        }
    }
}