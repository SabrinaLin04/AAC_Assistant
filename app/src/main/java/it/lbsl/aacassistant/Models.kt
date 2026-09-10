package it.lbsl.aacassistant

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class UserProfile(
    val displayName: String = "",
    val email: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val lastAccess: Timestamp = Timestamp.now(),
)

data class Favorite(
    @DocumentId val id: String ="",
    val text: String = "",
    val pictogramIds: List<Int> = emptyList(),
    val usageCount: Int = 0,
    val createdAt: Timestamp = Timestamp.now(),
)

data class UserContext(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val isActive: Boolean = false,
    val createdAt: Timestamp = Timestamp.now()
)

data class PromptConfig(
    val systemPrompt: String = DEFAULT_SYSTEM,
    val promptWithIncoming: String = DEFAULT_WITH_INCOMING,
    val promptGeneric: String = DEFAULT_GENERIC,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_SYSTEM =
            "Sei un assistente per la comunicazione aumentativa e alternativa. " +
                    "Suggerisci frasi che una persona potrebbe voler dire, in prima persona. " +
                    "Ogni frase: 3-4 parole, italiano semplice, una per riga. " +
                    "Nessuna numerazione, nessuna virgoletta, nessun commento."

        const val DEFAULT_WITH_INCOMING =
            "Qualcuno mi ha detto: \"{messaggio}\". Suggerisci 5 frasi che potrei rispondere."

        const val DEFAULT_GENERIC = "Suggerisci 5 frasi."
    }
}