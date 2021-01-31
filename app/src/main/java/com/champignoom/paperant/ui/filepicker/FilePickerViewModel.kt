package com.champignoom.paperant.ui.filepicker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class FilePickerViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is file picker Fragment"
    }
    val text: LiveData<String> = _text
}