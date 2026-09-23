package it.lbsl.aacassistant

import android.app.Application
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale

//più lenta del parlato normale: chi ascolta deve poter seguire la frase insieme ai pittogrammi
private const val SPEECH_RATE = 0.7f

//sintesi vocale condivisa dalle schermate: "Leggi" fa pronunciare la frase al telefono
class SpeechViewModel(application: Application) : AndroidViewModel(application),
    TextToSpeech.OnInitListener {

    //null finché il motore si avvia, poi vero o falso a seconda che la voce italiana ci sia
    @Volatile
    private var ready: Boolean? = null
    private var pendingText: String? = null

    private val _available = MutableLiveData<Boolean?>(null)
    val available: LiveData<Boolean?> = _available

    //intervallo di caratteri della parola che il motore sta pronunciando, null quando tace
    private val _spokenRange = MutableLiveData<IntRange?>(null)
    val spokenRange: LiveData<IntRange?> = _spokenRange

    private val tts = TextToSpeech(application, this)

    override fun onInit(status: Int) {
        val ok = status == TextToSpeech.SUCCESS &&
                tts.setLanguage(Locale.ITALIAN) >= TextToSpeech.LANG_AVAILABLE
        if (!ok) Log.w(TAG, "voce italiana non disponibile, stato del motore: $status")

        if (ok) {
            tts.setSpeechRate(SPEECH_RATE)
            tts.setOnUtteranceProgressListener(progressListener)
        }

        ready = ok
        _available.postValue(ok)

        //una frase chiesta mentre il motore si avviava viene detta appena è pronto
        val pending = pendingText
        pendingText = null
        if (ok && pending != null) say(pending)
    }

    //il motore segnala la parola in corso; non tutti i motori lo fanno, in quel caso
    //la frase viene letta lo stesso e non si evidenzia niente
    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = _spokenRange.postValue(null)

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            _spokenRange.postValue(start until end)
        }

        override fun onDone(utteranceId: String?) = _spokenRange.postValue(null)

        override fun onStop(utteranceId: String?, interrupted: Boolean) =
            _spokenRange.postValue(null)

        @Deprecated("richiesto dalla classe astratta", ReplaceWith(""))
        override fun onError(utteranceId: String?) = _spokenRange.postValue(null)
    }

    fun speak(text: String) {
        when (ready) {
            true -> say(text)
            null -> pendingText = text
            false -> Unit
        }
    }

    fun stop() {
        tts.stop()
        _spokenRange.postValue(null)
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

//dice la frase e la mostra in grande, evidenziando parola e pittogramma man mano che la legge
fun Fragment.speakAndShow(speech: SpeechViewModel, text: String, pictograms: List<WordPictogram>) {
    if (speech.available.value == false) {
        Toast.makeText(requireContext(), R.string.speech_unavailable, Toast.LENGTH_LONG).show()
    }

    val dialog = requireContext().showSpeakDialog(
        text = text,
        pictograms = pictograms,
        onRepeat = { speech.speak(text) },
        onShare = {
            viewLifecycleOwner.lifecycleScope.launch {
                //la preparazione dell'immagine può fallire se un pittogramma non si carica
                runCatching { requireContext().sharePhrase(text, pictograms) }
                    .onFailure {
                        Log.w("Sharing", "condivisione non riuscita", it)
                        Toast.makeText(requireContext(), R.string.share_failed, Toast.LENGTH_LONG).show()
                    }
            }
        }
    )

    val observer = Observer<IntRange?> { range -> dialog.highlight(range) }
    speech.spokenRange.observe(viewLifecycleOwner, observer)
    dialog.onDismiss {
        speech.spokenRange.removeObserver(observer)
        speech.stop()
    }

    speech.speak(text)
}
