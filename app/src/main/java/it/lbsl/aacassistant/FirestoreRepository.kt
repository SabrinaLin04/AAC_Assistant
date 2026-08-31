package it.lbsl.aacassistant

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
            "Pasto" to "Sono a tavola con altre persone. Voglio poter chiedere quello che desidero, rifiutare quello che non voglio, dire quando ho finito e commentare quello che sto mangiando. Voglio anche poter fare domande a chi è con me.",

            "Visita medica" to "Sono dal medico. Voglio poter dire dove e quanto mi fa male, rispondere alle domande che mi vengono fatte, chiedere cosa sta succedendo e dire quando qualcosa mi spaventa o non voglio farlo.",

            "Scuola" to "Sono a scuola con i compagni e l'insegnante. Voglio poter chiedere aiuto, dire quando non ho capito, chiedere di andare in bagno, dire quando ho finito e partecipare a quello che succede in classe.",

            "Casa" to "Sono a casa con la mia famiglia. Voglio poter chiedere le cose che mi servono, dire di no a quello che non voglio fare, raccontare come mi sento e chiedere di fare qualcosa insieme."
        )

        defaults.forEach { (name, description)  -> addContext(name, description)}
    }
    suspend fun addFavorite(text: String, pictogramIds: List<Int> = emptyList()) : String {
        return favorites().add(Favorite(text=text, pictogramIds = pictogramIds)).await().id
    }

    suspend fun addContext(name: String, description: String) : String {
        return contexts().add(UserContext(name = name, description = description)).await().id
    }

    suspend fun getFavorites() : List<Favorite> =
        favorites().get().await().toObjects(Favorite::class.java)

    suspend fun getContexts() : List<UserContext> =
        contexts().get().await().toObjects(UserContext::class.java)

    suspend fun getActiveContextId(): String? =
        userDoc().get().await().getString("activeContextId")

    suspend fun  incrementFavoriteUsage (favoriteId: String) {
        favorites().document(favoriteId).update("usageCount", FieldValue.increment(1)).await()
    }

    suspend fun updateContext (contextId: String, name : String, description: String) {
        contexts().document(contextId).update(mapOf("name" to name, "description" to description)).await()
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
}
