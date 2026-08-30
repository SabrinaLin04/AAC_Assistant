package it.lbsl.aacassistant


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class FavoritesViewModel : ViewModel() {
    private val repository = FirestoreRepository()
    private val _favorites = MutableLiveData<List<Favorite>>(emptyList())
    val favorites: LiveData<List<Favorite>> = _favorites
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Int?>(null)
    val errorMessage: LiveData<Int?> = _errorMessage

    val isEmpty: LiveData<Boolean> = _favorites.map { it.isEmpty() }

    init {
        loadFavorites()
    }

    fun loadFavorites() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _favorites.value = repository.getFavorites().sortedByDescending { it.usageCount }
            } catch (e: Exception) {
                _errorMessage.value = R.string.error_load_favorites
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleFavorite(text: String, pictogramId: Int? = null) {
        viewModelScope.launch {
            try {
                val existing = _favorites.value?.firstOrNull { it.text == text }
                if (existing != null) {
                    repository.deleteFavorite(existing.id)
                } else {
                    repository.addFavorite(text, listOfNotNull(pictogramId))
                }
                loadFavorites()
            } catch (e: Exception) {
                _errorMessage.value = R.string.error_toggle_favorite
            }
        }
    }

    fun deleteFavorite(favoriteId: String) {
        viewModelScope.launch {
            try {
                repository.deleteFavorite(favoriteId)
                loadFavorites()
            } catch (e: Exception) {
                _errorMessage.value = R.string.error_delete_favorite
            }
        }
    }

    fun markAsUsed(favoriteId: String) {
        viewModelScope.launch {
            try {
                repository.incrementFavoriteUsage(favoriteId)
                loadFavorites()
            } catch (e: Exception) {
            }
        }
    }

    fun isFavorite(text: String) : Boolean =
        _favorites.value?.any {it.text == text} == true


    fun clearError() {
        _errorMessage.value = null
    }

}