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
import kotlin.random.Random

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

    private var contextName: String? = null

    private var lastIncoming: String? = null


    private var metricsLogger: MetricsLogger? = null

    private var appContext: Context? = null

    private var engineProvider: LlmEngineProvider? = null

    //inizializza il modello linguistico copiando il file se mancante e avviando il motore o passando alla modalità demo in caso di errore
    fun getModel(context: Context) {
        metricsLogger = MetricsLogger(context.filesDir)
        engineProvider= LlmEngineProvider(context.filesDir)
        val appCtx = context.applicationContext
        appContext = appCtx
        viewModelScope.launch {
            PictogramRepository.loadCoreIndex(appCtx)
            PictogramRepository.loadLemmatizer(appCtx)

            val provider = engineProvider!!
            val available = provider.discoverModels()

            if (available.isEmpty()) {
                _modelState.value = ModelState.DemoMode
                return@launch
            }

            val model=available.first()

            try {
                _modelState.value = ModelState.Initializing(R.string.model_copying)
                val modelPath = withContext(Dispatchers.IO) {
                    provider.prepareModel(model)
                }

                if (modelPath == null) {
                    _modelState.value = ModelState.DemoMode
                    return@launch
                }

                loadEngine(modelPath, context, model)
            } catch (e: Exception) {
                _modelState.value = ModelState.DemoMode
            }
        }
    }

    //costruisce le istruzioni di sistema per il modello integrando la descrizione del contesto attuale se disponibile
    private fun buildSystemPrompt(): String {
        val base = "Sei un assistente per la comunicazione aumentativa e alternativa. " +
                "Suggerisci frasi che una persona potrebbe voler dire, in prima persona. " +
                "Ogni frase: 3-10 parole, italiano semplice, una per riga. " +
                "Nessuna numerazione, nessuna virgoletta, nessun commento."

        return contextDescription
            ?.takeIf { it.isNotBlank() }
            ?.let { "$base\n\nSituazione: $it" }
            ?: base
    }

    //seleziona un set di frasi preimpostate per la modalità demo in base alla parola chiave del contesto attivo
    private fun pickDemoSuggestions(): List<String> {
        val ctx = contextDescription?.lowercase() ?: return demoFallback
        return demoSuggestions.entries
            .firstOrNull { (key, _) -> ctx.contains(key) }
            ?.value
            ?: demoFallback
    }

    //chiude l'eventuale conversazione precedente e ne avvia una nuova configurando il prompt di sistema e i parametri di campionamento
    private fun createConversation() {
        val eng = engine ?: return

        conversation?.close()

        val convConfig = ConversationConfig(
            systemInstruction = Contents.of(buildSystemPrompt()),
            samplerConfig = SamplerConfig(
                topK = 40,
                topP = 0.95,
                temperature = 0.85,
                seed = Random.nextInt()
            )
        )
        conversation = eng.createConversation(convConfig)
    }

    //carica e inizializza il motore litert in un thread secondario specificando il percorso del modello e l'uso della gpu
    private suspend fun loadEngine(modelPath: String, context: Context, model: ModelInfo) {
        _modelState.value = ModelState.Initializing(R.string.model_initializing)

        val loadedEngine = withContext(Dispatchers.IO) {
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = model.backend,
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
        "il", "lo", "la", "i", "gli", "le", "un", "uno", "una",
        "di", "a", "da", "in", "con", "su", "per", "tra", "fra",
        "del", "dello", "della", "dei", "degli", "delle",
        "al", "allo", "alla", "ai", "agli", "alle",
        "dal", "dallo", "dalla", "dai", "dagli", "dalle",
        "nel", "nello", "nella", "nei", "negli", "nelle",
        "sul", "sullo", "sulla", "sui", "sugli", "sulle",
        "e", "ed", "o", "od", "ma", "che", "se",
        "mi", "ti", "ci", "vi", "si", "ne", "ce", "ve", "me", "te",
        "qui", "qua", "li", "la"
    )

    //analizza la frase filtrando le stopword per trovare e restituire fino a dieci identificatori di pittogrammi univoci corrispondenti
    private suspend fun findPictogramsFor(sentence: String): List<Int> {
        val words = sentence
            .lowercase()
            .split(Regex("[^\\p{L}]+"))
            .filter { it.length >= 2 && it !in stopwords }
            .take(20)

        val ids = mutableListOf<Int>()
        val ctx = appContext

        for (word in words) {
            val id = if (ctx != null) {
                PictogramRepository.findPictogram(ctx, word)
            } else {
                PictogramRepository.findPictogram(word)
            }

            if (id != null && id !in ids) { //evita duplicati perche' due parole diverse possono corrispondere allo stesso pittogramma
                ids.add(id)
            }
            if (ids.size >= 10) break
        }

        return ids
    }

    //cerca i pittogrammi per la frase passata e li associa all'ultimo messaggio presente nella cronologia della chat
    private suspend fun resolvePictogram(sentence: String) {
        val ids = findPictogramsFor(sentence)
        if (ids.isEmpty()) return

        val list = _messages.value.orEmpty().toMutableList()
        if (list.isEmpty()) return
        list[list.lastIndex] = list.last().copy(pictogramIds = ids)
        _messages.value = list
    }

    //aggiorna la descrizione del contesto corrente svuotando la cronologia dei messaggi e ricreando la conversazione per applicare le modifiche
    fun setContext(name: String?, description: String?) {
        if (description == contextDescription) return

        contextName = name
        contextDescription = description
        lastIncoming = null

        if (engine != null) {
            createConversation()
        }
        _messages.value = emptyList()
        _chatState.value = ChatState.Idle
    }

    fun sendMessage(text: String) {
        viewModelScope.launch {
            lastIncoming = text
            val ids = findPictogramsFor(text)
            _messages.value = _messages.value.orEmpty() + ChatMessage("user", text, ids)
        }
    }

    //invia un prompt generico al modello per farsi suggerire quattro nuove frasi contestuali da mostrare all'utente
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

            val prompt = lastIncoming?.let {
                //se qualcuno ha scritto qualcosa il primo suggerisci da frasi inerenti
                "Qualcuno mi ha detto: \"$it\". Suggerisci 5 frasi che potrei rispondere."
            } ?: listOf(
                "Suggerisci 5 frasi.",
                "Proponi 5 frasi utili adesso.",
                "Scrivi 5 frasi possibili.",
                "Genera 5 frasi per questo momento."
            ).random()

            val accumulated = StringBuilder()

            val startTime = System.nanoTime()
            var firstTokenTime: Long? = null
            var tokenCount = 0

            conv.sendMessageAsync(prompt)
                .catch { error ->
                    _chatState.value = ChatState.Error(
                        messageRes = R.string.error_generation,
                        detail = error.localizedMessage
                    )
                }
                .collect { chunk ->
                    if (firstTokenTime == null) {
                        firstTokenTime = System.nanoTime()
                    }
                    tokenCount++
                    accumulated.append(chunk.toString()) }

            val endTime = System.nanoTime()
            val totalMs = (endTime - startTime) / 1_000_000
            val ttftMs = ((firstTokenTime ?: endTime) - startTime) / 1_000_000

            withContext(Dispatchers.IO) {metricsLogger?.log(MetricsEntry(
                timestamp = java.time.Instant.now().toString(),
                ttftMs = ttftMs,
                totalMs = totalMs,
                nChars = accumulated.length,
                nChunks = tokenCount,
                charPerSec = if (totalMs > 0) tokenCount * 1000.0 / totalMs else 0.0,
                backend = when (engineProvider?.selected?.backend) {
                    is Backend.GPU -> "GPU"
                    is Backend.CPU -> "CPU"
                    else -> "unknown"
                },
                model = engineProvider?.selected?.label ?: "none",
                contextId = contextName  ?: "none"
            ))
            }


            publishSuggestions(splitSuggestions(accumulated.toString()))

            lastIncoming = null

            if (_chatState.value is ChatState.Generating) {
                _chatState.value = ChatState.Idle
            }
        }
    }

    //simula la richiesta di suggerimenti pubblicando le opzioni preimpostate della modalità demo
    private fun requestDemoSuggestions() {
        viewModelScope.launch {
            _chatState.value = ChatState.Generating
            delay(600)

            val suggestions = if (lastIncoming != null) {
                listOf(
                    "Sì.", "No.",
                    "Non lo so.", "Non ho capito."
                )
            } else {
                pickDemoSuggestions()
            }

            publishSuggestions(suggestions)
            lastIncoming = null
            _chatState.value = ChatState.Idle
        }
    }

    //pulisce l'output testuale grezzo del modello pulendo le frasi
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

        val newMessages = suggestions.map { text ->
            ChatMessage("model", text, findPictogramsFor(text))
        }

        //se c'è una frase la mantiene
        _messages.value = if (lastIncoming != null) {
            _messages.value.orEmpty() + newMessages
        } else {
            newMessages
        }
    }

    fun switchModel(model: ModelInfo) {
        val provider = engineProvider ?: return
        val ctx = appContext ?: return

        viewModelScope.launch {
            _modelState.value = ModelState.Initializing(R.string.model_initializing)
            _messages.value = emptyList()
            _chatState.value = ChatState.Idle

            withContext(Dispatchers.IO) {
                conversation?.close()
                conversation = null
                engine?.close()
                engine = null
            }

            val path = withContext(Dispatchers.IO) {
                provider.prepareModel(model)
            }

            if (path == null) {
                _modelState.value = ModelState.DemoMode
                return@launch
            }

            try {
                loadEngine(path, ctx, model)
            } catch (e: Exception) {
                _modelState.value = ModelState.Error(
                    messageRes = R.string.error_generation,
                    detail = e.localizedMessage
                )
            }
        }
    }

    // esponi la lista dei modelli disponibili per la UI
    fun getAvailableModels(): List<ModelInfo> =
        engineProvider?.discoverModels() ?: emptyList()

    fun getCurrentModel(): ModelInfo? =
        engineProvider?.selected

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