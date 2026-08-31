package it.lbsl.aacassistant

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val repository = FirestoreRepository()

    private val _userProfile = MutableLiveData<UserProfile?>()
    val userProfile: LiveData<UserProfile?> = _userProfile

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _statusMessage = MutableLiveData<Int?>(null)
    val statusMessage: LiveData<Int?> = _statusMessage

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _userProfile.value = repository.getProfile()
            } catch (e: Exception) {
                _statusMessage.value = R.string.error_load_profile
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateProfile(
        displayName: String,
        email: String,
        newPassword: String?
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val user = auth.currentUser ?: throw IllegalStateException("User not logged in")
                
                val profileUpdates = userProfileChangeRequest {
                    this.displayName = displayName
                }
                user.updateProfile(profileUpdates).await()

                if (email != user.email && email.isNotBlank()) {
                    user.updateEmail(email).await()
                }

                if (!newPassword.isNullOrBlank()) {
                    user.updatePassword(newPassword).await()
                }

                val updates = mutableMapOf<String, Any?>(
                    "displayName" to displayName,
                    "email" to email
                )
                repository.updateProfile(updates)

                _userProfile.value = repository.getProfile()
                _statusMessage.value = R.string.profile_updated_success
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                _statusMessage.value = R.string.error_recent_login_required
            } catch (e: Exception) {
                val message = e.message ?: ""
                if (message.contains("RECENT_LOGIN_REQUIRED") || message.contains("CREDENTIAL_TOO_OLD")) {
                    _statusMessage.value = R.string.error_recent_login_required
                } else if (message.contains("no-password-for-user") || message.contains("social accounts")) {
                    _statusMessage.value = R.string.error_provider_password_unsupported
                } else {
                    android.util.Log.e("ProfileViewModel", "Error updating profile", e)
                    _statusMessage.value = R.string.error_update_profile
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }
}
