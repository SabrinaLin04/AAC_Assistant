package it.lbsl.aacassistant

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration

class PromptsViewModel : ViewModel() {

    private val repo = FirestoreRepository()
    private var registration: ListenerRegistration? = null

    private val _config = MutableLiveData(PromptConfig())
    val config: LiveData<PromptConfig> = _config

    private val _saveResult = MutableLiveData<Boolean?>(null)
    val saveResult: LiveData<Boolean?> = _saveResult

    init {
        registration = repo.observePrompts { _config.value = it }
    }

    fun save(system: String, intention: String, withIncoming: String, generic: String) {
        val config = PromptConfig(
            systemPrompt = system.ifBlank { PromptConfig.DEFAULT_SYSTEM },
            promptIntention = intention.ifBlank { PromptConfig.DEFAULT_INTENTION },
            promptWithIncoming = withIncoming.ifBlank { PromptConfig.DEFAULT_WITH_INCOMING },
            promptGeneric = generic.ifBlank { PromptConfig.DEFAULT_GENERIC }
        )
        repo.savePrompts(config) { _saveResult.value = it }
    }

    fun resetToDefaults() {
        repo.savePrompts(PromptConfig()) { _saveResult.value = it }
    }

    fun clearSaveResult() { _saveResult.value = null }

    override fun onCleared() {
        registration?.remove()
    }
}