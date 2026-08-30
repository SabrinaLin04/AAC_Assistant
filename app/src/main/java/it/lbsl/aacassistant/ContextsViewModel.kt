package it.lbsl.aacassistant

import androidx.lifecycle.LiveData
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
                repository.setActiveContext(contextId)
                _activeContextId.value = contextId
            } catch (e: Exception) {
                _errorMessage.value = R.string.error_select_context
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}