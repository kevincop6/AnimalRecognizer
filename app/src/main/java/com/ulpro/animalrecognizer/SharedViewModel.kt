package com.ulpro.animalrecognizer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {
    private val _searchText = MutableLiveData<String>()
    val searchText: LiveData<String> get() = _searchText

    fun updateSearchText(text: String) {
        _searchText.value = text
    }
}