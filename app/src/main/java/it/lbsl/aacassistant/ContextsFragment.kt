package it.lbsl.aacassistant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import it.lbsl.aacassistant.databinding.FragmentContextsBinding

class ContextsFragment : Fragment() {

    private var _binding: FragmentContextsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ContextsViewModel by activityViewModels()
    private lateinit var adapter: ContextsAdapter

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

        setupRecyclerView()
        setupSwipeToDelete()
        setupFab()
        observeViewModel()

        viewModel.loadContexts()
    }

    private fun setupRecyclerView() {
        adapter = ContextsAdapter(
            onSelect = { userContext ->
                val isCurrentlyActive = viewModel.activeContextId.value == userContext.id
                viewModel.selectContext(userContext.id)
                val message = if (isCurrentlyActive) {
                    getString(R.string.context_deactivated)
                } else {
                    getString(R.string.context_selected, userContext.name)
                }
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
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

    private fun observeViewModel() {
        viewModel.contexts.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
        }
        viewModel.activeContextId.observe(viewLifecycleOwner) { id ->
            adapter.setActiveId(id)
        }
        viewModel.errorMessage.observe(viewLifecycleOwner) { resId ->
            resId ?: return@observe
            Snackbar.make(binding.root, getString(resId), Snackbar.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    //implementa la funzionalità di scorrimento laterale sugli elementi della lista per eliminarli offrendo un'opzione di annullamento
    private fun setupSwipeToDelete() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val contextItem = adapter.itemAt(position)

                requireContext().showConfirmationDialog(
                    title = getString(R.string.delete_context_confirm_title),
                    message = getString(R.string.delete_context_confirm_message, contextItem.name),
                    positiveButtonText = getString(R.string.action_delete),
                    negativeButtonText = getString(R.string.action_cancel),
                    onConfirm = {
                        val deletedName = contextItem.name
                        val deletedDesc = contextItem.description

                        viewModel.deleteContext(contextItem.id)

                        Snackbar.make(binding.root, R.string.context_deleted, Snackbar.LENGTH_LONG)
                            .setAction(R.string.action_undo) {
                                viewModel.addContext(deletedName, deletedDesc)
                            }
                            .show()
                    },
                    onCancel = {
                        adapter.notifyItemChanged(position)
                    }
                )
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.contextsRecycler)
    }

    //mostra una finestra di dialogo che permette all'utente di inserire i dati per un nuovo contesto o di modificarne uno esistente
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