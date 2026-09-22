package it.lbsl.aacassistant

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
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
    val pictograms: List<WordPictogram> = emptyList(),
    val id: Long = System.nanoTime()
) {
    val pictogramIds: List<Int> get() = pictograms.map { it.pictogramId }
}

//autori dei messaggi in chat: cosa vuole dire l'utente, cosa gli hanno detto, cosa suggerisce il modello
const val AUTHOR_USER = "user"
const val AUTHOR_PARTNER = "partner"
const val AUTHOR_MODEL = "model"

//come va letto il testo inviato: una cosa da dire o una cosa detta dall'altra persona
enum class InputKind { INTENTION, REPLY }

private data class PendingInput(val text: String, val kind: InputKind)

private const val TAG = "LlmViewModel"

//articoli, preposizioni e pronomi: non hanno un pittogramma corrispondente in ARASAAC
//e cercarli significherebbe solo sprecare chiamate di rete
internal val STOPWORDS = setOf(
    "il", "lo", "la", "i", "gli", "le", "un", "uno", "una",
    "di", "a", "da", "in", "con", "su", "per", "tra", "fra",
    "del", "dello", "della", "dei", "degli", "delle",
    "al", "allo", "alla", "ai", "agli", "alle",
    "dal", "dallo", "dalla", "dai", "dagli", "dalle",
    "nel", "nello", "nella", "nei", "negli", "nelle",
    "sul", "sullo", "sulla", "sui", "sugli", "sulle",
    "e", "ed", "o", "od", "ma", "che", "se",
    "mi", "ti", "ci", "vi", "si", "ne", "ce", "ve", "me", "te",
    "qui", "qua", "li"
)

//frasi usate quando nessun modello è presente sul dispositivo, scelte in base al contesto attivo
private val DEMO_SUGGESTIONS = mapOf(
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

private val DEMO_FALLBACK = listOf(
    "Ho bisogno di aiuto.", "Sì, grazie.",
    "No, preferisco di no.", "Vorrei riposare un momento."
)

private val DEMO_REPLIES = listOf("Sì.", "No.", "Non lo so.", "Non ho capito.")

//il modello incornicia spesso le frasi con virgolette tipografiche o caporali
private val TRIM_CHARS = charArrayOf(
    '"', '“', '”', '«', '»', '\'', '’', '.'
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
    private var contextName: String? = null
    private var pendingInput: PendingInput? = null

    private var metricsLogger: MetricsLogger? = null
    private var appContext: Context? = null
    private var engineProvider: LlmEngineProvider? = null
    private var promptConfig = PromptConfig()
    private var promptsRegistration: ListenerRegistration? = null

    //inizializza il modello linguistico copiando il file se mancante e avviando il motore o passando alla modalità demo in caso di errore
    fun loadModel(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        metricsLogger = MetricsLogger(appCtx.filesDir)

        val provider = LlmEngineProvider(appCtx.filesDir)
        engineProvider = provider

        //rimuovo il listener precedente prima di riagganciarne uno nuovo
        promptsRegistration?.remove()
        promptsRegistration = FirestoreRepository().observePrompts { config ->
            promptConfig = config
            // il system prompt si applica alla creazione della conversazione,
            // quindi va ricreata perché la modifica abbia effetto
            if (engine != null) createConversation()
        }

        viewModelScope.launch {
            PictogramRepository.loadCoreIndex(appCtx)
            PictogramRepository.loadLemmatizer(appCtx)

            val model = provider.discoverModels().firstOrNull()
            if (model == null) {
                Log.i(TAG, "nessun modello sul dispositivo, avvio in modalità demo")
                _modelState.value = ModelState.DemoMode
                return@launch
            }

            try {
                _modelState.value = ModelState.Initializing(R.string.model_copying)
                val modelPath = withContext(Dispatchers.IO) { provider.prepareModel(model) }

                if (modelPath == null) {
                    Log.w(TAG, "copia di ${model.filename} non riuscita, avvio in modalità demo")
                    _modelState.value = ModelState.DemoMode
                    return@launch
                }

                loadEngine(modelPath, appCtx, model)
            } catch (e: Exception) {
                //un fallimento del motore è altrimenti indistinguibile dall'assenza del modello
                Log.e(TAG, "inizializzazione di ${model.label} fallita", e)
                _modelState.value = ModelState.DemoMode
            }
        }
    }

    //costruisce le istruzioni di sistema per il modello integrando la descrizione del contesto attuale se disponibile
    private fun buildSystemPrompt(): String {
        val base = promptConfig.systemPrompt

        return contextDescription
            ?.takeIf { it.isNotBlank() }
            ?.let { "$base\n\nSituazione: $it" }
            ?: base
    }

    //seleziona un set di frasi preimpostate per la modalità demo in base alla parola chiave del contesto attivo
    private fun pickDemoSuggestions(): List<String> {
        if (pendingInput?.kind == InputKind.REPLY) return DEMO_REPLIES
        val ctx = contextDescription?.lowercase() ?: return DEMO_FALLBACK
        return DEMO_SUGGESTIONS.entries
            .firstOrNull { (key, _) -> ctx.contains(key) }
            ?.value
            ?: DEMO_FALLBACK
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

    //analizza la frase filtrando le stopword per trovare e restituire fino a dieci identificatori di pittogrammi univoci corrispondenti
    //le parole assenti dall'indice locale richiedono una chiamata di rete, quindi si risolvono in parallelo
    private suspend fun findPictogramsFor(sentence: String): List<WordPictogram> = coroutineScope {
        val words = sentence
            .lowercase()
            .split(Regex("[^\\p{L}]+"))
            .filter { it.length >= 2 && it !in STOPWORDS }
            .take(20)

        val ctx = appContext

        words
            .map { word ->
                async {
                    val id = if (ctx != null) {
                        PictogramRepository.findPictogram(ctx, word)
                    } else {
                        PictogramRepository.findPictogram(word)
                    }
                    id?.let { WordPictogram(word, it) }
                }
            }
            .awaitAll() //l'ordine delle parole nella frase va conservato
            .filterNotNull()
            .distinctBy { it.pictogramId } //due parole diverse possono corrispondere allo stesso pittogramma
            .take(10)
    }

    //stessa ricerca usata per i messaggi, per le frasi pronte che nascono già scritte
    suspend fun pictogramsFor(text: String): List<WordPictogram> = findPictogramsFor(text)

    //aggiorna la descrizione del contesto corrente svuotando la cronologia dei messaggi e ricreando la conversazione per applicare le modifiche
    fun setContext(name: String?, description: String?) {
        //il nome serve solo alle metriche, si aggiorna anche a descrizione invariata
        contextName = name

        //solo un cambio di descrizione tocca il system prompt e giustifica il reset
        if (description == contextDescription) return

        contextDescription = description
        pendingInput = null

        if (engine != null) {
            createConversation()
        }
        _messages.value = emptyList()
        _chatState.value = ChatState.Idle
    }

    //invia quello che l'utente vuole dire, o quello che gli hanno detto, e chiede subito i suggerimenti
    fun sendMessage(text: String, kind: InputKind) {
        if (_chatState.value is ChatState.Generating) return
        _chatState.value = ChatState.Generating

        viewModelScope.launch {
            pendingInput = PendingInput(text, kind)
            val author = if (kind == InputKind.REPLY) AUTHOR_PARTNER else AUTHOR_USER
            val pictograms = findPictogramsFor(text)
            _messages.value = _messages.value.orEmpty() + ChatMessage(author, text, pictograms)
            generateSuggestions()
        }
    }

    //chiede frasi adatte al contesto attivo, senza un testo di partenza
    fun requestSuggestions() {
        if (_chatState.value is ChatState.Generating) return
        _chatState.value = ChatState.Generating
        viewModelScope.launch { generateSuggestions() }
    }

    //genera quattro frasi col modello, oppure le prende da quelle preimpostate in modalità demo
    private suspend fun generateSuggestions() {
        _chatState.value = ChatState.Generating

        if (_modelState.value is ModelState.DemoMode) {
            delay(600)
            publishSuggestions(pickDemoSuggestions())
        } else {
            createConversation()
            val conv = conversation
            if (conv != null) {
                val generation = streamResponse(conv, buildSuggestionPrompt())
                logMetrics(generation)
                publishSuggestions(splitSuggestions(generation.text))
            }
        }

        pendingInput = null
        if (_chatState.value is ChatState.Generating) {
            _chatState.value = ChatState.Idle
        }
    }

    //il prompt dipende da cosa è stato inviato: un'intenzione, una frase ricevuta o niente
    private fun buildSuggestionPrompt(): String {
        val input = pendingInput ?: return promptConfig.promptGeneric
        val template = when (input.kind) {
            InputKind.INTENTION -> promptConfig.promptIntention
            InputKind.REPLY -> promptConfig.promptWithIncoming
        }
        return template.replace("{messaggio}", input.text)
    }

    //esito di una generazione, con i tempi che servono alle misure della tesi
    private data class Generation(
        val text: String,
        val ttftMs: Long,
        val totalMs: Long,
        val chunks: Int
    )

    //accumula i chunk dello stream misurando il tempo al primo token e quello totale
    private suspend fun streamResponse(conv: Conversation, prompt: String): Generation {
        val accumulated = StringBuilder()
        val startTime = System.nanoTime()
        var firstTokenTime: Long? = null
        var chunks = 0

        conv.sendMessageAsync(prompt)
            .catch { error ->
                _chatState.value = ChatState.Error(
                    messageRes = R.string.error_generation,
                    detail = error.localizedMessage
                )
            }
            .collect { chunk ->
                if (firstTokenTime == null) firstTokenTime = System.nanoTime()
                chunks++
                accumulated.append(chunk.toString())
            }

        val endTime = System.nanoTime()
        return Generation(
            text = accumulated.toString(),
            ttftMs = ((firstTokenTime ?: endTime) - startTime) / 1_000_000,
            totalMs = (endTime - startTime) / 1_000_000,
            chunks = chunks
        )
    }

    private suspend fun logMetrics(generation: Generation) {
        val logger = metricsLogger ?: return
        val model = engineProvider?.selected

        val entry = MetricsEntry(
            timestamp = Instant.now().toString(),
            ttftMs = generation.ttftMs,
            totalMs = generation.totalMs,
            nChars = generation.text.length,
            nChunks = generation.chunks,
            charPerSec = if (generation.totalMs > 0) {
                generation.chunks * 1000.0 / generation.totalMs
            } else {
                0.0
            },
            backend = when (model?.backend) {
                is Backend.GPU -> "GPU"
                is Backend.CPU -> "CPU"
                else -> "unknown"
            },
            model = model?.label ?: "none",
            contextId = contextName ?: "none"
        )

        withContext(Dispatchers.IO) { logger.log(entry) }
    }

    //ripulisce l'output grezzo del modello da elenchi puntati, numerazione e virgolette
    private fun splitSuggestions(raw: String): List<String> =
        raw.lines()
            .map { line ->
                line.trim()
                    .removePrefix("-")
                    .removePrefix("*")
                    .trim()
                    .replace(Regex("^\\d+[.)]\\s*"), "")
                    .trim(*TRIM_CHARS)
            }
            .filterNot { it.contains("[") || it.contains("/") || it.contains(":") }
            .filter { it.length in 3..80 }
            .take(4)

    private suspend fun publishSuggestions(suggestions: List<String>) = coroutineScope {
        if (suggestions.isEmpty()) return@coroutineScope

        //ogni frase cerca i propri pittogrammi, le quattro ricerche procedono insieme
        val newMessages = suggestions
            .map { text -> async { ChatMessage(AUTHOR_MODEL, text, findPictogramsFor(text)) } }
            .awaitAll()

        //se c'è una frase la mantiene
        _messages.value = if (pendingInput != null) {
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
    val availableModels: List<ModelInfo>
        get() = engineProvider?.discoverModels() ?: emptyList()

    val currentModel: ModelInfo?
        get() = engineProvider?.selected

    fun clearChatError() {
        if (_chatState.value is ChatState.Error) {
            _chatState.value = ChatState.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        promptsRegistration?.remove()
        conversation?.close()
        engine?.close()
        conversation = null
        engine = null
    }
}