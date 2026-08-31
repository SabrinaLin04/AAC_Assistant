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
import kotlinx.coroutines.delay

sealed interface ModelState {
    object Idle : ModelState
    data class Initializing(@StringRes val messageRes: Int) : ModelState
    object Ready : ModelState

    object DemoMode : ModelState
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
    val pictogramIds: List<Int> = emptyList(),
    val id: Long = System.nanoTime()
)


class LlmViewModel : ViewModel() {


    private val _modelState = MutableLiveData<ModelState>(ModelState.Idle)
    val modelState: LiveData<ModelState> = _modelState

    private val _chatState = MutableLiveData<ChatState>(ChatState.Idle)
    val chatState: LiveData<ChatState> = _chatState
    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    private val demoSuggestions = mapOf(
        "pasto" to listOf(
            "Ho fame.", "Vorrei ancora un po', per favore.",
            "Ho finito, grazie.", "Posso avere dell'acqua?"
        ),
        "medico" to listOf(
            "Mi fa male qui.", "Il dolore è forte.",
            "Non capisco, può ripetere?", "Vorrei che mia madre restasse con me."
        ),
        "scuola" to listOf(
            "Non ho capito l'esercizio.", "Posso andare in bagno?",
            "Ho bisogno di aiuto.", "Ho finito il compito."
        ),
        "casa" to listOf(
            "Vorrei riposare.", "Ho voglia di uscire.",
            "Posso guardare la televisione?", "Mi sento stanco."
        )
    )

    private val demoFallback = listOf(
        "Ho bisogno di aiuto.", "Sì, grazie.",
        "No, preferisco di no.", "Vorrei riposare un momento."
    )

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private var contextDescription: String? = null

    private var lastDemoReply: String? = null

    companion object {
        private const val MODEL_FILENAME = "gemma3-1b-it-int4.litertlm"
    }

    fun getModel(context: Context) {
        viewModelScope.launch {
            PictogramRepository.loadCoreIndex(context)

            try {
                val modelFile = File(context.filesDir, MODEL_FILENAME)

                if (!modelFile.exists()) {
                    val source = File("/data/local/tmp/$MODEL_FILENAME")
                    if (source.exists()) {
                        _modelState.value = ModelState.Initializing(R.string.model_copying)
                        withContext(Dispatchers.IO) {
                            source.copyTo(modelFile)
                        }
                    } else {
                        _modelState.value = ModelState.DemoMode
                        return@launch
                    }
                }

                loadEngine(modelFile.absolutePath, context)

            } catch (e: Exception) {
                _modelState.value = ModelState.DemoMode
            }
        }
    }

    private fun buildSystemPrompt(): String {
        val base = "Sei un assistente per la comunicazione aumentativa e alternativa. " +
                "Suggerisci frasi che una persona potrebbe voler dire, in prima persona. " +
                "Ogni frase: 2-5 parole, italiano semplice, una per riga. " +
                "Nessuna numerazione, nessuna virgoletta, nessun commento."

        return contextDescription
            ?.takeIf { it.isNotBlank() }
            ?.let { "$base\n\nSituazione: $it" }
            ?: base
    }

    //prende le frasi demo in base al contesto attivo
    private fun pickDemoSuggestions(): List<String> {
        val ctx = contextDescription?.lowercase() ?: return demoFallback
        return demoSuggestions.entries
            .firstOrNull { (key, _) -> ctx.contains(key) }
            ?.value
            ?: demoFallback
    }

    private fun createConversation() {
        val eng = engine ?: return

        conversation?.close()

        val convConfig = ConversationConfig(
            systemInstruction = Contents.of(buildSystemPrompt()),
            samplerConfig = SamplerConfig(
                topK = 40,
                topP = 0.95,
                temperature = 0.8
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

    private val stopwords = setOf(
        "gli", "uno", "una", "con", "per", "tra", "fra",
        "del", "dello", "della", "dei", "degli", "delle",
        "all", "allo", "alla", "agli", "alle",
        "dal", "dallo", "dalla", "dai", "dagli", "dalle",
        "nel", "nello", "nella", "nei", "negli", "nelle",
        "sul", "sullo", "sulla", "sui", "sugli", "sulle",
        "che"
    )

    private suspend fun findPictogramsFor(sentence: String): List<Int> {
        val words = sentence
            .lowercase()
            .split(Regex("[^\\p{L}]+"))
            .filter { it.length > 2 && it !in stopwords }
            .take(6)                      // ordine della frase, non per lunghezza

        val ids = mutableListOf<Int>()

        for (word in words) {
            val id = PictogramRepository.findPictogram(word)

            if (id != null && id !in ids) { //evita duplicati perche' due parole diverse possono corrispondere allo stesso pittogramma
                ids.add(id)
            }
            if (ids.size >= 3) break
        }

        return ids
    }

    private suspend fun resolvePictogram(sentence: String) {
        val ids = findPictogramsFor(sentence)
        if (ids.isEmpty()) return

        val list = _messages.value.orEmpty().toMutableList()
        if (list.isEmpty()) return
        list[list.lastIndex] = list.last().copy(pictogramIds = ids)
        _messages.value = list
    }

    fun setContext(description: String?) {
        if (description == contextDescription) return

        contextDescription = description

        if (engine != null) {
            createConversation()
        }
        _messages.value = emptyList()
        _chatState.value = ChatState.Idle
    }

    fun sendMessage(text: String) {
        if (_modelState.value is ModelState.DemoMode) {
            sendDemoMessage(text)
            return
        }
        val conv = conversation ?: return

        viewModelScope.launch {
            _messages.value = _messages.value.orEmpty() + ChatMessage("user", text)
            _chatState.value = ChatState.Generating
            _messages.value = _messages.value.orEmpty() + ChatMessage("model", "")

            val accumulated = StringBuilder()

            conv.sendMessageAsync(text)
                .catch { error ->
                    val list = _messages.value.orEmpty().toMutableList()
                    if (list.isNotEmpty() && list.last().text.isBlank()) {
                        list.removeAt(list.lastIndex)
                        _messages.value = list
                    }
                    _chatState.value = ChatState.Error(
                        messageRes = R.string.error_generation,
                        detail = error.localizedMessage
                    )
                }
                .collect { chunk ->
                    accumulated.append(chunk.toString())

                    val updatedList = _messages.value.orEmpty().toMutableList()
                    val last = updatedList.last()
                    updatedList[updatedList.lastIndex] = last.copy(
                        text = accumulated.toString()
                    )
                    _messages.value = updatedList
                }

            resolvePictogram(accumulated.toString())

            if (_chatState.value is ChatState.Generating) {
                _chatState.value = ChatState.Idle
            }
        }
    }

    private fun sendDemoMessage(text: String) {
        viewModelScope.launch {
            _messages.value = _messages.value.orEmpty() + ChatMessage("user", text)
            _chatState.value = ChatState.Generating

            delay(600)

            val pool = pickDemoSuggestions()
            val reply = pool.filterNot { it == lastDemoReply }.randomOrNull()
                ?: pool.random()
            lastDemoReply = reply

            _messages.value= _messages.value.orEmpty() + ChatMessage("model", reply)
            _chatState.value= ChatState.Idle

            resolvePictogram(reply)
        }
    }

    fun requestSuggestions() {
        if (_chatState.value is ChatState.Generating) return
        if (_modelState.value is ModelState.DemoMode) {
            requestDemoSuggestions()
            return
        }

        viewModelScope.launch {
            _chatState.value = ChatState.Generating

            createConversation()
            val conv = conversation ?: run {
                _chatState.value = ChatState.Idle
                return@launch
            }

            val prompt = listOf(
                "Suggerisci 4 frasi.",
                "Proponi 4 frasi utili adesso.",
                "Scrivi 4 frasi possibili.",
                "Genera 4 frasi per questo momento."
            ).random()

            val accumulated = StringBuilder()

            conv.sendMessageAsync(prompt)
                .catch { error ->
                    _chatState.value = ChatState.Error(
                        messageRes = R.string.error_generation,
                        detail = error.localizedMessage
                    )
                }
                .collect { chunk -> accumulated.append(chunk.toString()) }

            publishSuggestions(splitSuggestions(accumulated.toString()))

            if (_chatState.value is ChatState.Generating) {
                _chatState.value = ChatState.Idle
            }
        }
    }

    private fun requestDemoSuggestions() {
        viewModelScope.launch {
            _chatState.value = ChatState.Generating
            delay(600)
            publishSuggestions(pickDemoSuggestions())
            _chatState.value= ChatState.Idle
        }
    }

    private fun splitSuggestions(raw: String): List<String> =
        raw.lines()
            .map { it.trim().removePrefix("-").removePrefix("*").trim() }
            .map { it.replace(Regex("^\\d+[.)]\\s*"), "") }
            .map { it.trim('"', '"', '"', '\'', '.') }
            .filterNot { it.contains("[") || it.contains("/") || it.contains(":") }
            .filter { it.length in 3..80 }
            .take(4)

    private suspend fun publishSuggestions(suggestions: List<String>) {
        if (suggestions.isEmpty()) return
        
        val messages = suggestions.map { text ->
            ChatMessage("model", text, findPictogramsFor(text))
        }
        _messages.value = messages
    }

    fun clearChatError() {
        if (_chatState.value is ChatState.Error) {
            _chatState.value = ChatState.Idle
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