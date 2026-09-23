package it.lbsl.aacassistant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import it.lbsl.aacassistant.databinding.FragmentContextsBinding
import it.lbsl.aacassistant.databinding.ItemSavedChipBinding
import kotlinx.coroutines.launch

class ContextsFragment : Fragment() {

    private var _binding: FragmentContextsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ContextsViewModel by activityViewModels()
    private val favoritesViewModel: FavoritesViewModel by activityViewModels()
    private val speechViewModel: SpeechViewModel by activityViewModels()
    private val llmViewModel: LlmViewModel by activityViewModels()
    private val hintsViewModel: HintsViewModel by activityViewModels()
    private lateinit var adapter: ContextsAdapter

    private val quickPictograms = mutableMapOf<String, List<WordPictogram>>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContextsBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hintsViewModel.dismissed.observe(viewLifecycleOwner) { dismissed ->
            dismissed ?: return@observe
            binding.hintBarInclude.bindHint(Hints.CONTEXTS, R.string.hint_contexts, dismissed) {
                hintsViewModel.dismiss(it)
            }
        }

        setupRecyclerView()
        setupSwipeToDelete()
        setupFab()
        setupWriteOwnButton()
        setupQuickPhrases()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = ContextsAdapter(
            onSelect = { userContext ->
                viewModel.activateContext(userContext.id)
                openSuggestions()
            },
            onEdit = { userContext -> showContextDialog(userContext)}
        )
        binding.contextsRecycler.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAddContext.setOnClickListener {
            showContextDialog(null)
        }
    }

    //frasi pronte: si toccano e vengono dette subito, senza passare da un posto
    private fun setupQuickPhrases() {
        val phrases = resources.getStringArray(R.array.quick_phrases)

        phrases.forEach { phrase ->
            val chip = ItemSavedChipBinding.inflate(layoutInflater, binding.quickChips, false).root
            chip.text = phrase
            chip.setOnClickListener {
                speakAndShow(speechViewModel, phrase, quickPictograms[phrase].orEmpty())
            }
            binding.quickChips.addView(chip)
        }

        //i pittogrammi si cercano una volta sola, così al tocco il dialog è già completo
        viewLifecycleOwner.lifecycleScope.launch {
            phrases.forEach { phrase -> quickPictograms[phrase] = llmViewModel.pictogramsFor(phrase) }
        }
    }

    //nessuno dei posti in elenco: si scrive la frase senza un contesto attivo
    private fun setupWriteOwnButton() {
        binding.writeOwnButton.setOnClickListener {
            viewModel.activateContext(null)
            openSuggestions()
        }
    }

    //apre i suggerimenti tenendo i contesti sotto nello stack, così indietro riporta qui
    private fun openSuggestions() {
        findNavController().navigate(
            R.id.suggestFragment,
            null,
            navOptions {
                launchSingleTop = true
                popUpTo(R.id.contextsFragment) { inclusive = false }
            }
        )
    }

    private fun observeViewModel() {
        viewModel.contexts.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
        }
        viewModel.activeContextId.observe(viewLifecycleOwner) { id ->
            adapter.setActiveId(id)
        }
        favoritesViewModel.favorites.observe(viewLifecycleOwner) {
            adapter.setPhraseCounts(favoritesViewModel.phraseCounts())
        }
        viewModel.errorMessage.observe(viewLifecycleOwner) { resId ->
            resId ?: return@observe
            Snackbar.make(binding.root, getString(resId), Snackbar.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    //cancella il posto dopo una conferma; la barra in basso permette di rimetterlo com'era
    private fun confirmDelete(contextItem: UserContext, onCancel: () -> Unit = {}) {
        requireContext().showConfirmationDialog(
            title = getString(R.string.delete_context_confirm_title),
            message = getString(R.string.delete_context_confirm_message, contextItem.name),
            positiveButtonText = getString(R.string.action_delete),
            negativeButtonText = getString(R.string.action_cancel),
            onConfirm = {
                viewModel.deleteContext(contextItem.id)

                Snackbar.make(binding.root, R.string.context_deleted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo) {
                        viewModel.addContext(
                            contextItem.name,
                            contextItem.description,
                            contextItem.colorIndex,
                            contextItem.pictogramId
                        )
                    }
                    .withUndoColors()
                    .show()
            },
            onCancel = onCancel
        )
    }

    //scorrendo una riga di lato si cancella il posto, con una conferma prima
    private fun setupSwipeToDelete() {
        binding.contextsRecycler.onSwipe { position ->
            confirmDelete(
                adapter.itemAt(position),
                //annullando, la riga scorsa via torna al suo posto
                onCancel = { adapter.notifyItemChanged(position) }
            )
        }
    }

    //finestra per creare un posto nuovo o cambiare quello che si è toccato
    private fun showContextDialog(contextToEdit: UserContext?) {
        val isEditing = contextToEdit != null

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_context, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.editContextName)
        val descInput = dialogView.findViewById<EditText>(R.id.editContextDescription)

        if (isEditing) {
            nameInput.setText(contextToEdit.name)
            descInput.setText(contextToEdit.description)
        }

        val titleRes = if (isEditing) R.string.edit_context_title else R.string.add_context_title

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_AACAssistant_Dialog)
            .setTitle(titleRes)
            .setView(dialogView)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val name = nameInput.text.toString().trim()
                val desc = descInput.text.toString().trim()

                if (name.isNotEmpty()) {
                    if (isEditing) {
                        viewModel.updateContext(contextToEdit.id, name, desc)
                    } else {
                        viewModel.addContext(name, desc)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .apply {
                contextToEdit?.let { toEdit ->
                    setNeutralButton(R.string.action_delete) { _, _ -> confirmDelete(toEdit) }
                }
            }
            .create()

        dialog.setOnShowListener {
            dialog.styleCustomButtons()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
