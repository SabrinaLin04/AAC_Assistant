package it.lbsl.aacassistant

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale

object PictogramRepository {
    private const val TAG = "Pictogram"
    private const val LANG = "it"

    private val cache = mutableMapOf<String, Int?>()
    private var coreIndex: Map<String, Int>? = null
    private var lemmatizer: Lemmatizer? = null
    private val loadMutex = Mutex()


    private const val LUNGHEZZA_MATCH_PARZIALE = 4

    suspend fun loadCoreIndex(context: Context) {
        if (coreIndex != null) return
        loadMutex.withLock {
            if (coreIndex != null) return@withLock
            val caricato = withContext(Dispatchers.IO) {
                try {
                    val json = context.assets.open("indice.json")
                        .bufferedReader().use { it.readText() }
                    val obj = JSONObject(json)
                    obj.keys().asSequence().associateWith { obj.getInt(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "Core index not loaded", e)
                    null   // null e non emptyMap(): così un errore transitorio
                    // viene ritentato invece di essere congelato
                }
            }
            if (caricato != null) {
                coreIndex = caricato
                Log.d(TAG, "core index: ${caricato.size} entries")
            }
        }
    }

    suspend fun loadLemmatizer(context: Context) {
        if (lemmatizer != null) return
        loadMutex.withLock {
            if (lemmatizer != null) return@withLock
            lemmatizer = try {
                Lemmatizer.load(context)
            } catch (e: Exception) {
                Log.w(TAG, "Lemmatizer not loaded (asset missing?)", e)
                null
            }
            Log.d(TAG, "lemmatizer: ${lemmatizer?.size ?: 0} forms")
        }
    }

    private fun normalizza(word: String): String =
        word.lowercase(Locale.ITALIAN)
            .trim()
            .trim('.', ',', ';', ':', '!', '?', '"', '\'', '(', ')', '\u00AB', '\u00BB')


    private fun senzaAccenti(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    private fun cercaNellIndice(forma: String, lemma: String): Pair<Int, String>? {
        val idx = coreIndex ?: return null
        val chiavi = listOf(
            forma to "indice_forma",
            senzaAccenti(forma) to "indice_forma_senza_accenti",
            lemma to "indice_lemma",
            senzaAccenti(lemma) to "indice_lemma_senza_accenti"
        )
        for ((chiave, via) in chiavi) {
            idx[chiave]?.let { return it to via }
        }
        return null
    }

    suspend fun findPictogram(context: Context, word: String): Int? {
        loadCoreIndex(context)
        loadLemmatizer(context)
        return findPictogram(word)
    }

    suspend fun findPictogram(word: String): Int? {
        val forma = normalizza(word)
        if (forma.isBlank()) return null

        val currLemmatizer = lemmatizer
        val lemma = currLemmatizer?.lemmatize(forma) ?: forma
        val haLemma = lemma != forma

        cercaNellIndice(forma, lemma)?.let { (id, via) ->
            Log.d(TAG, "'$forma' (lemma '$lemma') -> id $id  via $via")
            return id
        }

        val chiaveCache = if (haLemma) "$forma|$lemma" else forma
        if (cache.containsKey(chiaveCache)) {
            Log.d(TAG, "'$forma' -> cache ${cache[chiaveCache]}")
            return cache[chiaveCache]
        }

        var networkFailed = false
        val result = withContext(Dispatchers.IO) {
            try {
                cascataApi(forma, lemma, haLemma)
            } catch (e: Exception) {
                Log.w(TAG, "API fallita per '$forma'", e)
                networkFailed = true
                null
            }
        }

        if (!networkFailed && currLemmatizer != null) cache[chiaveCache] = result
        return result
    }

    private fun keywordPertinente(keyword: String, forma: String, lemma: String): Boolean {
        val lem = lemmatizer
        val k = normalizza(keyword).replace(Regex("\\s+"), " ").trim()
        if (k.isEmpty()) return false

        if (k.startsWith("non ") || k.startsWith("senza ")) return false

        // Insieme bersaglio: forma, lemma e le rispettive varianti senza accenti
        val bersagli = setOf(forma, lemma, senzaAccenti(forma), senzaAccenti(lemma))
            .filter { it.isNotEmpty() }
            .toSet()

        fun combacia(s: String): Boolean {
            if (s in bersagli) return true
            val sl = lem?.lemmatize(s) ?: s
            return sl in bersagli || senzaAccenti(sl) in bersagli
        }

        // keyword intera
        if (combacia(k)) return true

        // singolo token, solo per parole lessicali
        val abbastanzaLunga = forma.length >= LUNGHEZZA_MATCH_PARZIALE ||
                lemma.length >= LUNGHEZZA_MATCH_PARZIALE
        if (abbastanzaLunga) {
            return k.split(' ').any { it.isNotEmpty() && combacia(it) }
        }
        return false
    }

    private suspend fun cascataApi(forma: String, lemma: String, haLemma: Boolean): Int? {
        data class Tentativo(val nome: String, val query: String, val best: Boolean)

        val tentativi = buildList {
            add(Tentativo("bestsearch_forma", forma, true))
            if (haLemma) add(Tentativo("bestsearch_lemma", lemma, true))
            if (haLemma) add(Tentativo("search_lemma", lemma, false))
            add(Tentativo("search_forma", forma, false))
        }

        for ((nome, query, best) in tentativi) {
            val risultati = try {
                if (best) ArasaacClient.api.bestsearch(LANG, query)
                else ArasaacClient.api.search(LANG, query)
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) emptyList() else throw e
            }

            if (risultati.isEmpty()) {
                Log.d(TAG, "'$forma'  $nome -> 0 risultati")
                continue
            }

            //senza questa riga non si distingue "keywords non
            //parsate da Gson" da "keywords presenti ma non pertinenti".
            Log.d(TAG, "'$forma'  $nome -> ${risultati.size} risultati, " +
                    "keywords primo = ${risultati.first().keywords.map { it.keyword }}")

            val pertinente = risultati.firstOrNull { dto ->
                dto.keywords.any { keywordPertinente(it.keyword, forma, lemma) }
            }

            if (pertinente != null) {
                Log.d(TAG, "'$forma' -> id ${pertinente.id}  via $nome  PERTINENTE")
                return pertinente.id
            }
            Log.d(TAG, "'$forma'  $nome -> nessuno pertinente, scartati")
        }

        Log.d(TAG, "'$forma' -> NESSUN PITTOGRAMMA")
        return null
    }


    fun imageUrl(id: Int): String =
        "https://static.arasaac.org/pictograms/$id/${id}_300.png"

    fun pictogramDir(context: Context): File =
        File(context.filesDir, "pictograms").apply { mkdirs() }

    fun imageSource(context: Context, id: Int): Any {
        val file = File(pictogramDir(context), "$id.png")
        return if (file.exists()) file else imageUrl(id)
    }

    suspend fun coreIds(context: Context): List<Int> {
        loadCoreIndex(context)
        loadLemmatizer(context)
        return coreIndex?.values?.distinct().orEmpty()
    }
}