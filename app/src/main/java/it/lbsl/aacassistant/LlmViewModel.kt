package it.lbsl.aacassistant

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.annotation.StringRes

sealed interface ModelState {
    object Idle : ModelState
    data class Downloading(val percent: Int) : ModelState
    data class Initializing(@StringRes val messageRes: Int) : ModelState
    object Ready : ModelState
    data class Error(@StringRes val messageRes: Int, val detail: String? = null) : ModelState
}

sealed interface ChatState {
    object Idle : ChatState
    object Generating : ChatState
    data class Error(@StringRes val messageRes: Int, val detail: String? = null) : ChatState
}

data class ChatMessage(
    val author: String,
    val text: String,
    val pictogramId: Int? = null
)


class LlmViewModel : ViewModel() {


    private val _modelState = MutableLiveData<ModelState>(ModelState.Idle)
    val modelState: LiveData<ModelState> = _modelState

    private val _chatState = MutableLiveData<ChatState>(ChatState.Idle)
    val chatState: LiveData<ChatState> = _chatState

    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private var contextDescription: String? = null

    companion object {
        private const val MODEL_FILENAME = "gemma3-1b-it-int4.litertlm"
    }

    fun getModel(context: Context) {
        viewModelScope.launch {
            try {
                val modelFile = File(context.filesDir, MODEL_FILENAME)

                if (!modelFile.exists()) {
                    val source = File("/tmp/$MODEL_FILENAME")
                    if (source.exists()) {
                        _modelState.value = ModelState.Initializing(R.string.model_copying)
                        withContext(Dispatchers.IO) {
                            source.copyTo(modelFile)
                        }
                    } else {
                        _modelState.value = ModelState.Error(R.string.error_model_not_found)
                        return@launch
                    }
                }

                loadEngine(modelFile.absolutePath, context)

            } catch (e: Exception) {
                _modelState.value = ModelState.Error(
                    messageRes = R.string.error_unknown,
                    detail = e.localizedMessage
                )
            }
        }
    }

    private fun buildSystemPrompt(): String {
        val base = "Sei un assistente per la comunicazione aumentativa e alternativa. " +
                "Suggerisci brevi frasi in prima persona che l'utente potrebbe voler dire. " +
                "Usa frasi semplici, dirette, di poche parole. Rispondi sempre in italiano."

        return contextDescription
            ?.takeIf { it.isNotBlank() }
            ?.let { "$base La situazione attuale è: $it" }
            ?: base
    }

    private fun createConversation() {
        val eng = engine ?: return

        conversation?.close()

        val convConfig = ConversationConfig(
            systemInstruction = Contents.of(buildSystemPrompt()),
            samplerConfig = SamplerConfig(
                topK = 20,
                topP = 0.95,
                temperature = 0.7
            )
        )
        conversation = eng.createConversation(convConfig)
    }

    private suspend fun loadEngine(modelPath: String, context: Context) {
        _modelState.value = ModelState.Initializing(R.string.model_initializing)

        val loadedEngine = withContext(Dispatchers.IO) {
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU(),
                cacheDir = context.cacheDir.absolutePath
            )
            val eng = Engine(engineConfig)
            eng.initialize()
            eng
        }

        engine = loadedEngine
        createConversation()
        _modelState.value = ModelState.Ready
    }

    fun setContext(description: String?) {
        if (description == contextDescription) return

        contextDescription = description

        if (engine != null) {
            createConversation()
            _messages.value = emptyList()
            _chatState.value = ChatState.Idle
        }
    }

    fun sendMessage(text: String) {
        val conv = conversation ?: return

        viewModelScope.launch {
            _messages.value = _messages.value.orEmpty() + ChatMessage("user", text)
            _chatState.value = ChatState.Generating
            _messages.value = _messages.value.orEmpty() + ChatMessage("model", "")

            val accumulated = StringBuilder()

            conv.sendMessageAsync(text)
                .catch { error ->
                    _chatState.value = ChatState.Error(
                        messageRes = R.string.error_generation,
                        detail = error.localizedMessage
                    )
                }
                .collect { chunk ->
                    accumulated.append(chunk.toString())

                    val updatedList = _messages.value.orEmpty().toMutableList()
                    updatedList[updatedList.lastIndex] = ChatMessage(
                        author = "model",
                        text = accumulated.toString()
                    )
                    _messages.value = updatedList
                }

            val finalText = accumulated.toString()
            val firstWord = finalText
                .split(" ", ",",".")
                .firstOrNull{ it.length > 3 } //salto articoli e preposizioni (demo), placeholder per la lemmatizzazione
            val pictogramId = firstWord?.let { PictogramRepository.findPictogram(it) }

            if (pictogramId!=null) {
                val list = _messages.value.orEmpty().toMutableList()
                list[list.lastIndex] = list.last().copy(pictogramId= pictogramId)
                _messages.value = list
            }

            if (_chatState.value is ChatState.Generating) {
                _chatState.value = ChatState.Idle
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        conversation?.close()
        engine?.close()
        conversation = null
        engine = null
    }
}