package it.lbsl.aacassistant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import it.lbsl.aacassistant.databinding.FragmentPromptsBinding

class PromptsFragment : Fragment() {

    private var _binding: FragmentPromptsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PromptsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPromptsBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // popola i campi solo quando il testo cambia davvero,
        // altrimenti il cursore salta mentre l'utente scrive
        viewModel.config.observe(viewLifecycleOwner) { config ->
            if (binding.systemPromptInput.text.toString() != config.systemPrompt) {
                binding.systemPromptInput.setText(config.systemPrompt)
            }
            if (binding.incomingPromptInput.text.toString() != config.promptWithIncoming) {
                binding.incomingPromptInput.setText(config.promptWithIncoming)
            }
            if (binding.genericPromptInput.text.toString() != config.promptGeneric) {
                binding.genericPromptInput.setText(config.promptGeneric)
            }
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            val msg = if (result) "Prompt salvati" else "Errore nel salvataggio"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            viewModel.clearSaveResult()
        }

        binding.saveButton.setOnClickListener {
            viewModel.save(
                binding.systemPromptInput.text.toString(),
                binding.incomingPromptInput.text.toString(),
                binding.genericPromptInput.text.toString()
            )
        }

        binding.resetButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Ripristina predefiniti")
                .setMessage("I prompt personalizzati verranno sostituiti con quelli originali.")
                .setPositiveButton("Ripristina") { _, _ -> viewModel.resetToDefaults() }
                .setNegativeButton("Annulla", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}