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

sealed interface ModelState {
    object Idle : ModelState
    data class Downloading(val percent: Int) : ModelState
    data class Initializing(val message: String) : ModelState
    object Ready : ModelState
    data class Error(val cause: String) : ModelState
}

sealed interface ChatState {
    object Idle : ChatState
    object Generating : ChatState
    data class Error(val cause: String) : ChatState
}

data class ChatMessage(
    val author: String,
    val text: String
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
                        _modelState.value = ModelState.Initializing("Copying model...")
                        withContext(Dispatchers.IO) {
                            source.copyTo(modelFile)
                        }
                    } else {
                        _modelState.value = ModelState.Error("Model not found on device")
                        return@launch
                    }
                }

                loadEngine(modelFile.absolutePath, context)

            } catch (e: Exception) {
                _modelState.value = ModelState.Error(
                    e.localizedMessage ?: "Unknown error"
                )
            }
        }
    }

    private suspend fun loadEngine(modelPath: String, context: Context) {
        _modelState.value = ModelState.Initializing("Loading model...")

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

        val convConfig = ConversationConfig(
            systemInstruction = Contents.of(
                "You are a helpful assistant. Answer in Italian, clearly and concisely."
            ),
            samplerConfig = SamplerConfig(
                topK = 20,
                topP = 0.95,
                temperature = 0.7
            )
        )
        conversation = loadedEngine.createConversation(convConfig)

        _modelState.value = ModelState.Ready
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
                        error.localizedMessage ?: "Generation error"
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