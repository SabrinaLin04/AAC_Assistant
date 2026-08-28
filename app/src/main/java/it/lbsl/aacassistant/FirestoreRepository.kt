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

    suspend fun  incrementFavoriteUsage (favoriteId: String) {
        favorites().document(favoriteId).update("usageCount", FieldValue.increment(1)).await()
    }

    suspend fun updateContext (contextId: String, name : String, description: String) {
        contexts().document(contextId).update(mapOf("name" to name, "description" to description)).await()
    }

    suspend fun deleteFavorite(favoriteId: String) {
        favorites().document(favoriteId).delete().await()
    }

    suspend fun deleteContext(contextId: String) {
        contexts().document(contextId).delete().await()
    }
}
