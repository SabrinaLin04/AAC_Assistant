package it.lbsl.aacassistant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import it.lbsl.aacassistant.BuildConfig
import it.lbsl.aacassistant.databinding.FragmentCreditsBinding

class CreditsFragment : Fragment() {

    private var _binding: FragmentCreditsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreditsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.appVersion.text = getString(
            R.string.credits_version,
            BuildConfig.VERSION_NAME
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}