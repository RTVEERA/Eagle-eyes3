package com.example.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ViraViewModel(private val repository: ViraRepository) : ViewModel() {

    // Theme & State settings
    private val _isDarkMode = MutableStateFlow(false) // Default to light mode for the pastel High Density look
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _appAccentColorHex = MutableStateFlow("#21005D") // Beautiful High Density Royal Velvet Purple!
    val appAccentColorHex: StateFlow<String> = _appAccentColorHex.asStateFlow()

    // Grid columns count (from 2 to 5)
    private val _gridColumnsCount = MutableStateFlow(4)
    val gridColumnsCount: StateFlow<Int> = _gridColumnsCount.asStateFlow()

    // File curation
    private val _selectedFolder = MutableStateFlow("All")
    val selectedFolder: StateFlow<String> = _selectedFolder.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow("date_desc") // "date_desc", "date_asc", "name_asc"
    val sortOrder: StateFlow<String> = _sortOrder.asStateFlow()

    // Gallery content state
    private val _galleryItems = MutableStateFlow<List<GalleryMediaItem>>(emptyList())
    val galleryItems: StateFlow<List<GalleryMediaItem>> = _galleryItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Selected Media Detail Viewer & Slideshow
    private val _activeMediaIndex = MutableStateFlow(-1)
    val activeMediaIndex: StateFlow<Int> = _activeMediaIndex.asStateFlow()

    private val _slideshowActive = MutableStateFlow(false)
    val slideshowActive: StateFlow<Boolean> = _slideshowActive.asStateFlow()

    private val _slideshowIntervalSec = MutableStateFlow(5) // default 5 seconds
    val slideshowIntervalSec: StateFlow<Int> = _slideshowIntervalSec.asStateFlow()

    // Vault PIN authentication
    private val _vaultPIN = MutableStateFlow("1947") // True Patriotic Indian Default PIN
    val vaultPIN: StateFlow<String> = _vaultPIN.asStateFlow()

    private val _isVaultAuthenticated = MutableStateFlow(false)
    val isVaultAuthenticated: StateFlow<Boolean> = _isVaultAuthenticated.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(true)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    fun setBiometricEnabled(enabled: Boolean) {
        _isBiometricEnabled.value = enabled
    }

    fun setVaultAuthenticated(authenticated: Boolean) {
        _isVaultAuthenticated.value = authenticated
    }

    private val _vaultItemsState = MutableStateFlow<List<VaultItem>>(emptyList())
    val vaultItemsState: StateFlow<List<VaultItem>> = _vaultItemsState.asStateFlow()

    // Editor configurations
    private val _editorSelectedMedia = MutableStateFlow<GalleryMediaItem?>(null)
    val editorSelectedMedia: StateFlow<GalleryMediaItem?> = _editorSelectedMedia.asStateFlow()

    private val _selectedFilter = MutableStateFlow("Original")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _editorTextOverlay = MutableStateFlow("")
    val editorTextOverlay: StateFlow<String> = _editorTextOverlay.asStateFlow()

    private val _editorTextColorHex = MutableStateFlow("#FFFFFF")
    val editorTextColorHex: StateFlow<String> = _editorTextColorHex.asStateFlow()

    private val _editorFontFamilyName = MutableStateFlow("Standard")
    val editorFontFamilyName: StateFlow<String> = _editorFontFamilyName.asStateFlow()

    private val _editorVideoEffect = MutableStateFlow("None")
    val editorVideoEffect: StateFlow<String> = _editorVideoEffect.asStateFlow()

    private val _editorTransition = MutableStateFlow("None")
    val editorTransition: StateFlow<String> = _editorTransition.asStateFlow()

    private var slideshowJob: Job? = null

    init {
        // Collect database operations
        viewModelScope.launch {
            repository.vaultItems.collect { list ->
                _vaultItemsState.value = list
            }
        }
    }

    /**
     * Refreshes gallery items using repository
     */
    fun refreshGallery(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            val items = repository.fetchAllGalleryItems(context, showVaulted = false)
            _galleryItems.value = filterAndSortGallery(items)
            _isLoading.value = false
        }
    }

    private fun filterAndSortGallery(items: List<GalleryMediaItem>): List<GalleryMediaItem> {
        var result = items

        // 1. Filter by search query
        val query = _searchQuery.value.trim()
        if (query.isNotEmpty()) {
            result = result.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.folderName.contains(query, ignoreCase = true)
            }
        }

        // 2. Filter by folder
        val folder = _selectedFolder.value
        if (folder != "All") {
            result = result.filter { it.folderName.equals(folder, ignoreCase = true) }
        }

        // 3. Sort
        return when (_sortOrder.value) {
            "date_desc" -> result.sortedByDescending { it.dateAdded }
            "date_asc" -> result.sortedBy { it.dateAdded }
            "name_asc" -> result.sortedBy { it.title.lowercase() }
            else -> result.sortedByDescending { it.dateAdded }
        }
    }

    fun setSearchQuery(context: Context, query: String) {
        _searchQuery.value = query
        refreshGallery(context)
    }

    fun setSelectedFolder(context: Context, folder: String) {
        _selectedFolder.value = folder
        refreshGallery(context)
    }

    fun setSortOrder(context: Context, order: String) {
        _sortOrder.value = order
        refreshGallery(context)
    }

    fun setGridColumns(columns: Int) {
        if (columns in 2..5) {
            _gridColumnsCount.value = columns
        }
    }

    fun changeTheme(dark: Boolean) {
        _isDarkMode.value = dark
    }

    fun changeAccentColor(colorHex: String) {
        _appAccentColorHex.value = colorHex
    }

    // Media Slideshow logic
    fun startSlideshow() {
        if (_slideshowActive.value) return
        _slideshowActive.value = true
        slideshowJob?.cancel()
        slideshowJob = viewModelScope.launch {
            while (_slideshowActive.value && _galleryItems.value.isNotEmpty()) {
                delay(_slideshowIntervalSec.value * 1000L)
                val current = _activeMediaIndex.value
                val next = if (current >= _galleryItems.value.size - 1) 0 else current + 1
                _activeMediaIndex.value = next
            }
        }
    }

    fun stopSlideshow() {
        _slideshowActive.value = false
        slideshowJob?.cancel()
    }

    fun setSlideshowInterval(seconds: Int) {
        if (seconds > 0) {
            _slideshowIntervalSec.value = seconds
        }
    }

    fun setActiveMediaIndex(index: Int) {
        _activeMediaIndex.value = index
    }

    // Vault PIN processing
    fun verifyVaultPIN(entered: String): Boolean {
        return if (entered == _vaultPIN.value) {
            _isVaultAuthenticated.value = true
            true
        } else {
            false
        }
    }

    fun setupNewPIN(newPin: String) {
        if (newPin.length == 4) {
            _vaultPIN.value = newPin
            _isVaultAuthenticated.value = true
        }
    }

    fun logOutVault() {
        _isVaultAuthenticated.value = false
    }

    fun moveItemToVault(context: Context, item: GalleryMediaItem) {
        viewModelScope.launch {
            val vaultItem = VaultItem(
                title = item.title,
                pathOrUri = item.uriOrPath,
                isLocalSample = item.isLocalSample,
                mediaType = if (item.isVideo) "video" else "image"
            )
            repository.insertVaultItem(vaultItem)
            refreshGallery(context)
        }
    }

    fun restoreItemFromVault(context: Context, uriOrPath: String) {
        viewModelScope.launch {
            repository.deleteVaultItemByPath(uriOrPath)
            refreshGallery(context)
        }
    }

    // Editing selection
    fun startEditing(item: GalleryMediaItem) {
        _editorSelectedMedia.value = item
        // Reset custom overlays
        _selectedFilter.value = "Original"
        _editorTextOverlay.value = ""
        _editorTextColorHex.value = "#FFFFFF"
        _editorFontFamilyName.value = "Standard"
        _editorVideoEffect.value = "None"
        _editorTransition.value = "None"
    }

    fun setEditorFilter(filterName: String) {
        _selectedFilter.value = filterName
    }

    fun setEditorTextOverlay(text: String) {
        _editorTextOverlay.value = text
    }

    fun setEditorFontAndColor(font: String, colorHex: String) {
        _editorFontFamilyName.value = font
        _editorTextColorHex.value = colorHex
    }

    fun setEditorVideoEffect(effect: String) {
        _editorVideoEffect.value = effect
    }

    fun setEditorTransition(transition: String) {
        _editorTransition.value = transition
    }

    fun exportCurrentCreation(context: Context, customTitle: String) {
        val selected = _editorSelectedMedia.value ?: return
        viewModelScope.launch {
            val creation = MediaCreation(
                title = customTitle.ifBlank { "Edit_${selected.title}" },
                originalSourceUri = selected.uriOrPath,
                filterApplied = _selectedFilter.value,
                textOverlay = _editorTextOverlay.value,
                mediaType = if (selected.isVideo) "video" else "image"
            )
            repository.insertMediaCreation(creation)
            refreshGallery(context)
        }
    }
}

class ViraViewModelFactory(private val repository: ViraRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ViraViewModel::class.java)) {
            return ViraViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
