package it.lbsl.aacassistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PictogramRepository {
    private const val LANG = "it"
    private val cache = mutableMapOf<String, Int?>() //cache in memoria -> non ricerco mai la stessa parola due volte

    suspend fun findPictogram(word: String): Int? {
        val key = word.trim().lowercase()
        if (key.isBlank()) return null
        cache[key]?.let {return it}
        if (cache.containsKey(key)) return null //ho cercato e non ho trovato nulla

        val result = withContext(Dispatchers.IO) {
            try {
                    ArasaacClient.api.bestsearch(LANG, key).firstOrNull()?.id
                        ?: ArasaacClient.api.search(LANG,key ).firstOrNull()?.id
            }
            catch (e: Exception) {null} //se ARASAAC e' irragiungibile l'utente vede il testo senza pittogramma
        }
        cache[key] = result
        return result
    }
    fun imageUrl(id: Int): String = "https://static.arasaac.org/pictograms/$id/${id}_300.png"
}