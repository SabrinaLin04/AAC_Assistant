package it.lbsl.aacassistant

import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import it.lbsl.aacassistant.databinding.IncludeHintBarBinding

//chiavi degli aiuti contestuali, una per schermata
object Hints {
    const val CONTEXTS = "hint_contexts"
    const val WRITE = "hint_write"
    const val FAVORITES = "hint_favorites"
    const val SPEAK = "hint_speak"
}

//gli aiuti chiusi seguono l'account e non il telefono: chi entra con un account nuovo li rivede,
//e chi cambia telefono non se li ritrova daccapo
class HintsViewModel : ViewModel() {

    private val repository = FirestoreRepository()
    private var registration: ListenerRegistration? = null

    //null finché Firestore non risponde, così la striscia non compare per un istante
    //su un aiuto che era già stato chiuso
    private val _dismissed = MutableLiveData<Set<String>?>(null)
    val dismissed: LiveData<Set<String>?> = _dismissed

    init {
        registration = repository.observeDismissedHints { _dismissed.value = it }
        if (registration == null) _dismissed.value = emptySet()
    }

    fun dismiss(key: String) {
        //la striscia sparisce subito, senza aspettare la scrittura
        _dismissed.value = _dismissed.value.orEmpty() + key
        repository.dismissHint(key)
    }

    override fun onCleared() {
        registration?.remove()
    }
}

//mostra la striscia se quell'aiuto non è ancora stato chiuso
fun IncludeHintBarBinding.bindHint(
    key: String,
    @StringRes textRes: Int,
    dismissed: Set<String>,
    onDismiss: (String) -> Unit
) {
    root.isVisible = key !in dismissed
    hintText.setText(textRes)
    hintDismiss.setOnClickListener { onDismiss(key) }
}
