package it.lbsl.aacassistant


import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
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

    //vero quando la lista è vuota e il caricamento è finito
    val showEmptyState: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        fun update() {
            value = (_favorites.value?.isEmpty() == true) && (_isLoading.value != true)
        }
        addSource(_favorites) { update() }
        addSource(_isLoading) { update() }
    }

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

    fun toggleFavorite(text: String, pictogramIds: List<Int> = emptyList(), contextId: String? = null) {
        viewModelScope.launch {
            try {
                val existing = _favorites.value?.firstOrNull { it.text == text }
                if (existing != null) {
                    repository.deleteFavorite(existing.id)
                } else {
                    repository.addFavorite(text, pictogramIds, contextId)
                }
                loadFavorites()
            } catch (e: Exception) {
                _errorMessage.value = R.string.error_toggle_favorite
            }
        }
    }

    //ricrea un preferito appena eliminato, per l'annulla dello swipe
    fun restoreFavorite(text: String, pictogramIds: List<Int> = emptyList(), contextId: String? = null) {
        viewModelScope.launch {
            try {
                repository.addFavorite(text, pictogramIds, contextId)
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
                //il conteggio d'uso non è critico, si registra senza avvisare l'utente
                Log.w("FavoritesViewModel", "incrementFavoriteUsage fallita", e)
            }
        }
    }

    fun isFavorite(text: String) : Boolean =
        _favorites.value?.any {it.text == text} == true

    //frasi salvate in un posto, già ordinate per uso
    fun phrasesFor(contextId: String): List<Favorite> =
        _favorites.value.orEmpty().filter { it.contextId == contextId }

    //quante frasi sono state salvate in ciascun posto
    fun phraseCounts(): Map<String, Int> =
        _favorites.value.orEmpty()
            .mapNotNull { it.contextId }
            .groupingBy { it }
            .eachCount()


    fun clearError() {
        _errorMessage.value = null
    }

}