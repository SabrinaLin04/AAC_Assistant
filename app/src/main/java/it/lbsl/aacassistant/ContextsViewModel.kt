package it.lbsl.aacassistant

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
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

    val isEmpty: LiveData<Boolean> = _contexts.map { it.isEmpty() }

    //vero  quando la lista e' vuota e il caricamento e' finito
    val showEmptyState: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        fun update() {
            value = (_contexts.value?.isEmpty() == true) && (_isLoading.value != true)
        }
        addSource(_contexts) { update() }
        addSource(_isLoading) { update() }
    }

    val activeContext: LiveData<UserContext?> = _activeContextId.map { id ->
        _contexts.value?.firstOrNull { it.id == id }
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

    fun addContext(name: String, description: String) {
        viewModelScope.launch {
            try {
                repository.addContext(name, description)
                loadContexts()
            } catch (e: Exception) {
                _errorMessage.value = R.string.error_add_context
            }
        }
    }

    fun updateContext(contextId: String, name: String, description: String) {
        viewModelScope.launch {
            try {
                repository.updateContext(contextId, name, description)
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

    fun selectContext(contextId: String) {
        viewModelScope.launch {
            try {
                val newActiveId = if (_activeContextId.value == contextId) null else contextId
                repository.setActiveContext(newActiveId)
                _activeContextId.value = newActiveId
            } catch (e: Exception) {
                _errorMessage.value = R.string.error_select_context
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}