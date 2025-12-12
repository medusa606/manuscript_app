package com.blackbriar.musicsupervisor.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackbriar.musicsupervisor.data.local.entity.ItemEntity
import com.blackbriar.musicsupervisor.data.local.repository.SearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val repository: SearchRepository) : ViewModel() {

    private val _results = MutableStateFlow<List<ItemEntity>>(emptyList())
    val results: StateFlow<List<ItemEntity>> = _results

    /**
     * Call this when the search query changes
     */
    fun search(query: String) {
        if (query.isBlank()) {
            _results.value = emptyList()
            return
        }

        viewModelScope.launch {
            val searchResults = repository.search(query)
            _results.value = searchResults
        }
    }
}
