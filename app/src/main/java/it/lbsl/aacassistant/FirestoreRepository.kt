package it.lbsl.aacassistant

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val uid: String
        get() = auth.currentUser?.uid
            ?: throw IllegalStateException("No authenticated user")
    private fun userDoc() = db.collection("users").document(uid)
    private fun favorites() = userDoc().collection("favorites")
    private fun contexts() = userDoc().collection("contexts")

    suspend fun createOrUpdateProfile() {
        val user = auth.currentUser ?: return
        val profile = UserProfile(
            displayName = user.displayName ?: "",
            email = user.email ?: ""
        )
        userDoc().set(profile, SetOptions.merge()).await()
    }

    suspend fun getProfile(): UserProfile? =
        userDoc().get().await().toObject(UserProfile::class.java)

    suspend fun updateProfile(updates: Map<String, Any?>) {
        userDoc().update(updates).await()
    }

    suspend fun defaultContexts() {
        if (contexts().limit(1).get().await().isEmpty.not()) return

        val defaults = listOf(
            //pittogrammi presi da assets/indice.json: "pasto" e "visita" non ci sono, si usano mangiare e dottore
            Triple("Pasto", "Sono a tavola con altre persone. Voglio poter chiedere quello che desidero, rifiutare quello che non voglio, dire quando ho finito e commentare quello che sto mangiando. Voglio anche poter fare domande a chi è con me.", 2349),

            Triple("Visita medica", "Sono dal medico. Voglio poter dire dove e quanto mi fa male, rispondere alle domande che mi vengono fatte, chiedere cosa sta succedendo e dire quando qualcosa mi spaventa o non voglio farlo.", 2467),

            Triple("Scuola", "Sono a scuola con i compagni e l'insegnante. Voglio poter chiedere aiuto, dire quando non ho capito, chiedere di andare in bagno, dire quando ho finito e partecipare a quello che succede in classe.", 3082),

            Triple("Casa", "Sono a casa con la mia famiglia. Voglio poter chiedere le cose che mi servono, dire di no a quello che non voglio fare, raccontare come mi sento e chiedere di fare qualcosa insieme.", 2317)
        )

        defaults.forEachIndexed { index, (name, description, pictogramId) ->
            addContext(name, description, colorIndex = index, pictogramId = pictogramId)
        }
    }
    suspend fun addFavorite(
        text: String,
        pictograms: List<WordPictogram> = emptyList(),
        contextId: String? = null
    ) : String {
        val favorite = Favorite(
            text = text,
            pictogramIds = pictograms.map { it.pictogramId },
            pictogramWords = pictograms.map { it.word },
            contextId = contextId
        )
        return favorites().add(favorite).await().id
    }

    suspend fun addContext(
        name: String,
        description: String,
        colorIndex: Int? = null,
        pictogramId: Int? = null
    ) : String {
        val context = UserContext(
            name = name,
            description = description,
            colorIndex = colorIndex,
            pictogramId = pictogramId
        )
        return contexts().add(context).await().id
    }

    suspend fun getFavorites() : List<Favorite> =
        favorites().get().await().toObjects(Favorite::class.java)

    suspend fun getContexts() : List<UserContext> =
        contexts().get().await().toObjects(UserContext::class.java)

    suspend fun getActiveContextId(): String? =
        userDoc().get().await().getString("activeContextId")

    suspend fun incrementFavoriteUsage(favoriteId: String) {
        favorites().document(favoriteId).update("usageCount", FieldValue.increment(1)).await()
    }

    suspend fun updateContext(contextId: String, name: String, description: String) {
        contexts().document(contextId).update(mapOf("name" to name, "description" to description)).await()
    }

    suspend fun setContextPictogram(contextId: String, pictogramId: Int?) {
        contexts().document(contextId).update("pictogramId", pictogramId).await()
    }

    suspend fun setActiveContext(contextId: String?) {
        userDoc().set(mapOf("activeContextId" to contextId), SetOptions.merge()).await()
    }

    suspend fun deleteFavorite(favoriteId: String) {
        favorites().document(favoriteId).delete().await()
    }

    suspend fun deleteContext(contextId: String) {
        contexts().document(contextId).delete().await()
    }

    //restituisce null senza utente: i prompt vengono osservati anche prima del login
    private fun promptsDoc(): DocumentReference? =
        auth.currentUser?.uid?.let { currentUid ->
            db.collection("users").document(currentUid)
                .collection("settings").document("prompts")
        }

    private fun hintsDoc(): DocumentReference? =
        auth.currentUser?.uid?.let { currentUid ->
            db.collection("users").document(currentUid)
                .collection("settings").document("hints")
        }

    //un campo per aiuto, messo a true quando l'utente tocca "Ho capito"
    fun observeDismissedHints(onChange: (Set<String>) -> Unit): ListenerRegistration? {
        val doc = hintsDoc() ?: return null
        return doc.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            val dismissed = snapshot?.data.orEmpty()
                .filterValues { it == true }
                .keys
            onChange(dismissed)
        }
    }

    fun dismissHint(key: String) {
        hintsDoc()?.set(mapOf(key to true), SetOptions.merge())
    }

    fun savePrompts(config: PromptConfig, onDone: (Boolean) -> Unit) {
        val doc = promptsDoc() ?: run { onDone(false); return }
        doc.set(config, SetOptions.merge())
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    // listener in tempo reale: se l'utente salva, chi ascolta viene aggiornato subito
    fun observePrompts(onChange: (PromptConfig) -> Unit): ListenerRegistration? {
        val doc = promptsDoc() ?: return null
        return doc.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            val config = snapshot?.toObject(PromptConfig::class.java) ?: PromptConfig()
            onChange(config)
        }
    }
}
