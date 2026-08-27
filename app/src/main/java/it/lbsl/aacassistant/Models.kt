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
    val createdAt: Timestamp = Timestamp.now(),
)

data class UserContext(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val isActive: Boolean = false,
    val createdAt: Timestamp = Timestamp.now()
)