package it.lbsl.aacassistant

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import it.lbsl.aacassistant.databinding.FragmentGuideBinding

//guida per chi prepara l'app e accompagna la persona che la usa
class GuideFragment : Fragment() {

    private var _binding: FragmentGuideBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGuideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.openSpeechSettings.setOnClickListener { openSettings(TTS_SETTINGS) }
        binding.openDisplaySettings.setOnClickListener { openSettings(Settings.ACTION_DISPLAY_SETTINGS) }
    }

    //non tutti i produttori espongono la pagina della sintesi vocale: in quel caso si apre l'accessibilità
    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
    }
}
