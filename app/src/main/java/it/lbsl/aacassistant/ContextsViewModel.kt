package it.lbsl.aacassistant

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class ContextsViewModel : ViewModel() {

    private val repository = FirestoreRepository()

    private val _contexts = MutableLiveData<List<UserContext>>(emptyList())
    val contexts: LiveData<List<UserContext>> = _contexts

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Int?>(null)
    val errorMessage: LiveData<Int?> = _errorMessage

    private val _activeContextId = MutableLiveData<String?>(null)
    val activeContextId: LiveData<String?> = _activeContextId

    //vero quando la lista è vuota e il caricamento è finito
    val showEmptyState: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        fun update() {
            value = (_contexts.value?.isEmpty() == true) && (_isLoading.value != true)
        }
        addSource(_contexts) { update() }
        addSource(_isLoading) { update() }
    }

    //si ricalcola anche quando cambia la lista, così un contesto modificato arriva aggiornato ai suggerimenti
    val activeContext: LiveData<UserContext?> = MediatorLiveData<UserContext?>().apply {
        fun update() {
            value = _contexts.value?.firstOrNull { it.id == _activeContextId.value }
        }
        addSource(_contexts) { update() }
        addSource(_activeContextId) { update() }
    }

    init {
        loadContexts()
    }

    fun loadContexts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.defaultContexts()
                _contexts.value = repository.getContexts().sortedBy { it.name }
                _activeContextId.value = repository.getActiveContextId()
            } catch (e: Exception) {
                _errorMessage.value = R.string.error_load_contexts
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addContext(
        name: String,
        description: String,
        colorIndex: Int? = null,
        pictogramId: Int? = null
    ) {
        viewModelScope.launch {
            try {
                //il colore ruota sulla palette in ordine di creazione
                val color = colorIndex ?: (_contexts.value?.size ?: 0)
                val pictogram = pictogramId ?: pictogramForName(name)
                repository.addContext(name, description, color, pictogram)
                loadContexts()
            } catch (e: Exception) {
                _errorMessage.value = R.string.error_add_context
            }
        }
    }

    fun updateContext(contextId: String, name: String, description: String) {
        viewModelScope.launch {
            try {
                val previous = _contexts.value?.firstOrNull { it.id == contextId }
                repository.updateContext(contextId, name, description)

                //il pittogramma si ricalcola se cambia il nome o se il contesto non ne ha ancora uno
                if (previous == null || previous.name != name || previous.pictogramId == null) {
                    repository.setContextPictogram(contextId, pictogramForName(name))
                }
                loadContexts()
            } catch (e: Exception) {
                _errorMessage.value = R.string.error_update_context
            }
        }
    }

    fun deleteContext(contextId: String) {
        viewModelScope.launch {
            try {
                repository.deleteContext(contextId)
                if (_activeContextId.value == contextId) {
                    repository.setActiveContext(null)
                }
                loadContexts()
            } catch (e: Exception) {
                _errorMessage.value = R.string.error_delete_context
            }
        }
    }

    //imposta il contesto attivo, null significa nessun contesto
    fun activateContext(contextId: String?) {
        //aggiorno subito il valore locale: la schermata dei suggerimenti si apre
        //nello stesso momento e deve già mostrare il posto scelto
        val previous = _activeContextId.value
        _activeContextId.value = contextId

        viewModelScope.launch {
            try {
                repository.setActiveContext(contextId)
            } catch (e: Exception) {
                _activeContextId.value = previous
                _errorMessage.value = R.string.error_select_context
            }
        }
    }

    //cerca un pittogramma per il nome del contesto, fermandosi alla prima parola che ne ha uno
    private suspend fun pictogramForName(name: String): Int? =
        name.lowercase()
            .split(Regex("[^\\p{L}]+"))
            .filter { it.length >= 2 && it !in STOPWORDS }
            .firstNotNullOfOrNull { PictogramRepository.findPictogram(it) }

    fun clearError() {
        _errorMessage.value = null
    }
}