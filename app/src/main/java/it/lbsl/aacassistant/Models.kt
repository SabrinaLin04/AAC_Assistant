package it.lbsl.aacassistant

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

//una parola della frase e il pittogramma che le corrisponde: serve per evidenziarli insieme
data class WordPictogram(val word: String = "", val pictogramId: Int = 0)

data class UserProfile(
    val displayName: String = "",
    val email: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val lastAccess: Timestamp = Timestamp.now(),
)

data class Favorite(
    @DocumentId val id: String = "",
    val text: String = "",
    val pictogramIds: List<Int> = emptyList(),
    //parola di ogni pittogramma, nello stesso ordine; vuoto per le frasi salvate prima del campo
    val pictogramWords: List<String> = emptyList(),
    val usageCount: Int = 0,
    //il posto in cui la frase è stata salvata, null per quelle salvate prima del campo
    val contextId: String? = null,
    val createdAt: Timestamp = Timestamp.now(),
)

//ricompone le coppie parola-pittogramma di una frase salvata
val Favorite.pictograms: List<WordPictogram>
    get() = pictogramIds.mapIndexed { index, id ->
        WordPictogram(pictogramWords.getOrElse(index) { "" }, id)
    }

data class UserContext(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    //indice in aac_context_colors, null per i contesti salvati prima che esistesse il campo
    val colorIndex: Int? = null,
    val pictogramId: Int? = null,
    val createdAt: Timestamp = Timestamp.now()
)

data class PromptConfig(
    val systemPrompt: String = DEFAULT_SYSTEM,
    val promptIntention: String = DEFAULT_INTENTION,
    val promptWithIncoming: String = DEFAULT_WITH_INCOMING,
    val promptGeneric: String = DEFAULT_GENERIC,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_SYSTEM =
            "Sei un assistente per la comunicazione aumentativa e alternativa. " +
                    "Suggerisci frasi che una persona potrebbe voler dire, in prima persona. " +
                    "Parti sempre dalle parole che ti vengono date: la situazione è solo lo sfondo. " +
                    "Ogni frase: 3-4 parole, italiano semplice, una per riga. " +
                    "Nessuna numerazione, nessuna virgoletta, nessun commento."

        //quattro frasi, quante ne mostra la schermata dei suggerimenti
        const val DEFAULT_INTENTION =
            "Voglio dire questo: \"{messaggio}\". Scrivi 4 modi diversi di dirlo in prima persona, " +
                    "restando fedele a queste parole."

        const val DEFAULT_WITH_INCOMING =
            "Qualcuno mi ha detto: \"{messaggio}\". Scrivi 4 risposte diverse che potrei dare, " +
                    "pertinenti a quello che mi ha detto."

        const val DEFAULT_GENERIC = "Suggerisci 4 frasi."
    }
}