package it.lbsl.aacassistant

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.Locale

//sintesi vocale condivisa dalle schermate: "Leggi" fa pronunciare la frase al telefono
class SpeechViewModel(application: Application) : AndroidViewModel(application),
    TextToSpeech.OnInitListener {

    //null finché il motore si avvia, poi vero o falso a seconda che la voce italiana ci sia
    @Volatile
    private var ready: Boolean? = null
    private var pendingText: String? = null

    private val _available = MutableLiveData<Boolean?>(null)
    val available: LiveData<Boolean?> = _available

    private val tts = TextToSpeech(application, this)

    override fun onInit(status: Int) {
        val ok = status == TextToSpeech.SUCCESS &&
                tts.setLanguage(Locale.ITALIAN) >= TextToSpeech.LANG_AVAILABLE
        if (!ok) Log.w(TAG, "voce italiana non disponibile, stato del motore: $status")

        ready = ok
        _available.postValue(ok)

        //una frase chiesta mentre il motore si avviava viene detta appena è pronto
        val pending = pendingText
        pendingText = null
        if (ok && pending != null) say(pending)
    }

    fun speak(text: String) {
        when (ready) {
            true -> say(text)
            null -> pendingText = text
            false -> Unit
        }
    }

    private fun say(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aac-${System.nanoTime()}")
    }

    override fun onCleared() {
        tts.stop()
        tts.shutdown()
    }

    private companion object {
        const val TAG = "SpeechViewModel"
    }
}

//dice la frase e la mostra in grande: la voce per chi ascolta, testo e pittogrammi per chi guarda
fun Fragment.speakAndShow(speech: SpeechViewModel, text: String, pictogramIds: List<Int>) {
    speech.speak(text)
    if (speech.available.value == false) {
        Toast.makeText(requireContext(), R.string.speech_unavailable, Toast.LENGTH_LONG).show()
    }
    requireContext().showSpeakDialog(text, pictogramIds, onRepeat = { speech.speak(text) })
}
