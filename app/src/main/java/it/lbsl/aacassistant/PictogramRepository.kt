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
import java.util.Collections
import java.util.Locale

object PictogramRepository {
    private const val TAG = "Pictogram"
    private const val LANG = "it"

    //i risultati già cercati online; sincronizzata perché le parole di una frase si cercano insieme
    private val cache = Collections.synchronizedMap(mutableMapOf<String, Int?>())
    private var coreIndex: Map<String, Int>? = null
    private var lemmatizer: Lemmatizer? = null
    private val loadMutex = Mutex()

    //sotto le quattro lettere una parola è troppo corta perché basti un pezzo di keyword
    private const val MIN_PARTIAL_LENGTH = 4

    suspend fun loadCoreIndex(context: Context) {
        if (coreIndex != null) return
        loadMutex.withLock {
            if (coreIndex != null) return@withLock
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val json = context.assets.open("indice.json")
                        .bufferedReader().use { it.readText() }
                    val obj = JSONObject(json)
                    obj.keys().asSequence().associateWith { obj.getInt(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "indice locale non caricato", e)
                    null   // null e non emptyMap(): così un errore transitorio
                    // viene ritentato invece di essere congelato
                }
            }
            if (loaded != null) {
                coreIndex = loaded
                Log.d(TAG, "indice locale: ${loaded.size} parole")
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
                Log.w(TAG, "tabella dei lemmi non caricata", e)
                null
            }
            Log.d(TAG, "tabella dei lemmi: ${lemmatizer?.size ?: 0} forme")
        }
    }

    private fun stripAccents(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    //cerca nell'indice locale la parola com'è, la sua forma base e le varianti senza accenti
    private fun lookupIndex(form: String, lemma: String): Pair<Int, String>? {
        val index = coreIndex ?: return null
        val keys = listOf(
            form to "indice_forma",
            stripAccents(form) to "indice_forma_senza_accenti",
            lemma to "indice_lemma",
            stripAccents(lemma) to "indice_lemma_senza_accenti"
        )
        for ((key, source) in keys) {
            index[key]?.let { return it to source }
        }
        return null
    }

    //cerca il pittogramma di una parola, caricando prima indice e tabella dei lemmi
    suspend fun findPictogram(context: Context, word: String): Int? {
        loadCoreIndex(context)
        loadLemmatizer(context)
        return findPictogram(word)
    }

    suspend fun findPictogram(word: String): Int? {
        val form = normalizeWord(word)
        if (form.isBlank()) return null

        val currentLemmatizer = lemmatizer
        val lemma = currentLemmatizer?.lemmatize(form) ?: form
        val hasLemma = lemma != form

        lookupIndex(form, lemma)?.let { (id, source) ->
            Log.d(TAG, "'$form' (lemma '$lemma') -> id $id da $source")
            return id
        }

        val cacheKey = if (hasLemma) "$form|$lemma" else form
        if (cache.containsKey(cacheKey)) {
            Log.d(TAG, "'$form' -> cache ${cache[cacheKey]}")
            return cache[cacheKey]
        }

        var networkFailed = false
        val result = withContext(Dispatchers.IO) {
            try {
                searchOnline(form, lemma, hasLemma)
            } catch (e: Exception) {
                Log.w(TAG, "ricerca online fallita per '$form'", e)
                networkFailed = true
                null
            }
        }

        if (!networkFailed && currentLemmatizer != null) cache[cacheKey] = result
        return result
    }

    //ARASAAC restituisce anche pittogrammi vagamente affini: si tengono solo quelli che
    //hanno davvero fra le keyword la parola cercata
    private fun keywordMatches(keyword: String, form: String, lemma: String): Boolean {
        val currentLemmatizer = lemmatizer
        val k = normalizeWord(keyword).replace(Regex("\\s+"), " ").trim()
        if (k.isEmpty()) return false

        //"non dormire" non è un buon pittogramma per "dormire"
        if (k.startsWith("non ") || k.startsWith("senza ")) return false

        //la parola cercata in tutte le sue varianti
        val targets = setOf(form, lemma, stripAccents(form), stripAccents(lemma))
            .filter { it.isNotEmpty() }
            .toSet()

        fun matches(candidate: String): Boolean {
            if (candidate in targets) return true
            val base = currentLemmatizer?.lemmatize(candidate) ?: candidate
            return base in targets || stripAccents(base) in targets
        }

        //prima la keyword intera
        if (matches(k)) return true

        //poi una sua singola parola, ma solo se quella cercata è abbastanza lunga
        val longEnough = form.length >= MIN_PARTIAL_LENGTH ||
                lemma.length >= MIN_PARTIAL_LENGTH
        if (longEnough) {
            return k.split(' ').any { it.isNotEmpty() && matches(it) }
        }
        return false
    }

    //la parola non è nell'indice locale: si chiede ad ARASAAC, dal tentativo più mirato al più largo
    private suspend fun searchOnline(form: String, lemma: String, hasLemma: Boolean): Int? {
        data class Attempt(val name: String, val query: String, val best: Boolean)

        val attempts = buildList {
            add(Attempt("bestsearch_forma", form, true))
            if (hasLemma) add(Attempt("bestsearch_lemma", lemma, true))
            if (hasLemma) add(Attempt("search_lemma", lemma, false))
            add(Attempt("search_forma", form, false))
        }

        for ((name, query, best) in attempts) {
            val results = try {
                if (best) ArasaacClient.api.bestsearch(LANG, query)
                else ArasaacClient.api.search(LANG, query)
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) emptyList() else throw e
            }

            if (results.isEmpty()) {
                Log.d(TAG, "'$form'  $name -> nessun risultato")
                continue
            }

            //le keyword nel log distinguono "non sono arrivate" da "sono arrivate ma non c'entrano"
            Log.d(TAG, "'$form'  $name -> ${results.size} risultati, " +
                    "keywords del primo = ${results.first().keywords.map { it.keyword }}")

            val match = results.firstOrNull { dto ->
                dto.keywords.any { keywordMatches(it.keyword, form, lemma) }
            }

            if (match != null) {
                Log.d(TAG, "'$form' -> id ${match.id} da $name")
                return match.id
            }
            Log.d(TAG, "'$form'  $name -> nessun risultato pertinente")
        }

        Log.d(TAG, "'$form' -> NESSUN PITTOGRAMMA")
        return null
    }


    //id dell'indice locale per le parole richieste, nell'ordine dato; quelle assenti vengono saltate
    suspend fun indexIds(context: Context, words: List<String>): List<Pair<String, Int>> {
        loadCoreIndex(context)
        val index = coreIndex ?: return emptyList()
        return words.mapNotNull { word -> index[word]?.let { word to it } }
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