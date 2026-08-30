package it.lbsl.aacassistant

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object PictogramRepository {
    private const val LANG = "it"
    //cache in memoria -> non ricerco la stessa parola due volte
    private val cache = mutableMapOf<String, Int?>()
    private var coreIndex: Map<String, Int>? = null

    suspend fun loadCoreIndex(context: Context) {
        if (coreIndex != null) return
        coreIndex=withContext(Dispatchers.IO) {
            try {
                val json = context.assets.open("indice.json").bufferedReader().use { it.readText() }
                val obj = JSONObject(json)
                obj.keys().asSequence().associateWith { obj.getInt(it) }
            } catch (e: Exception) {
                Log.w("Pictogram", "Core index not loaded", e)
                emptyMap()
            }
        }
        Log.d("Pictogram", "core index: ${coreIndex?.size} entries")
    }

    suspend fun findPictogram(word: String): Int? {
        val key = word.trim().lowercase()
        if (key.isBlank()) return null

        coreIndex?.get(key)?.let { return it } //prima controllo l'indice locale
        if (cache.containsKey(key)) return cache[key] //poi nella cache

        var networkFailed = false

        val result = withContext(Dispatchers.IO) {
            try {
                    ArasaacClient.api.bestsearch(LANG, key).firstOrNull()?.id
                        ?: ArasaacClient.api.search(LANG,key ).firstOrNull()?.id
            }
            catch (e: Exception) {
                networkFailed = true
                null
            } //se ARASAAC e' irragiungibile l'utente vede il testo senza pittogramma
        }
        if (!networkFailed) cache[key] = result //solo le vere risposte vengono messe in cache, i fail da network no
        return result
    }
    fun imageUrl(id: Int): String = "https://static.arasaac.org/pictograms/$id/${id}_300.png"

    fun pictogramDir(context: Context): File =
        File(context.filesDir, "pictograms").apply { mkdirs() }

    fun imageSource(context: Context, id: Int): Any {
        val file = File(pictogramDir(context), "$id.png")
        return if (file.exists()) file else imageUrl(id)
    }

    suspend fun coreIds(context: Context): List<Int> {
        loadCoreIndex(context)
        return coreIndex?.values?.distinct().orEmpty()
    }
}