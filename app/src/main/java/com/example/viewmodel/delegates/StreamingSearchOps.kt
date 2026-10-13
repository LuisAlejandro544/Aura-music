package com.example.viewmodel.delegates

import com.example.core.data.streaming.StreamingApiService
import com.example.core.model.StreamingSearchFilter
import kotlinx.coroutines.launch

/**
 * Búsqueda y tendencias del catálogo con cancelación de carrera.
 * Rol arquitectónico: extensiones internas de StreamingModeCoordinator; la API pública no cambia.
 */
internal fun StreamingModeCoordinator.loadInitialTrendingIfNeededInternal() {
    if (_searchResults.value.isNotEmpty() || _isSearching.value) return
    performSearchInternal(query = "", filter = _selectedFilter.value)
}

internal fun StreamingModeCoordinator.updateSearchQueryInternal(query: String) {
    _searchQuery.value = query
}

internal fun StreamingModeCoordinator.setSearchFilterInternal(filter: StreamingSearchFilter) {
    if (_selectedFilter.value == filter) return
    _selectedFilter.value = filter
    performSearchInternal(_searchQuery.value, filter)
}

/**
 * Ejecuta la búsqueda en la API de YouTube Music y/o YouTube según el filtro activo.
 */
internal fun StreamingModeCoordinator.performSearchInternal(query: String = _searchQuery.value, filter: StreamingSearchFilter = _selectedFilter.value) {
    searchJob?.cancel()
    searchJob = scope.launch {
        _isSearching.value = true
        try {
            cacheManager.evictExpiredAndOverLimit()
            val items = StreamingApiService.searchStreamingCatalog(query, filter, context)
            items.forEach { itemRegistry[it.videoId] = it }
            _searchResults.value = items
        } catch (_: Exception) {
        } finally {
            _isSearching.value = false
        }
    }
}
