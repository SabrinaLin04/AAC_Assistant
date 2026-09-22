package it.lbsl.aacassistant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import it.lbsl.aacassistant.databinding.FragmentFavoritesBinding

class FavoritesFragment: Fragment() {
    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FavoritesViewModel by activityViewModels()
    private val speechViewModel: SpeechViewModel by activityViewModels()
    private val hintsViewModel: HintsViewModel by activityViewModels()
    private lateinit var adapter: FavoritesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hintsViewModel.dismissed.observe(viewLifecycleOwner) { dismissed ->
            dismissed ?: return@observe
            binding.hintBarInclude.bindHint(Hints.FAVORITES, R.string.hint_favorites, dismissed) {
                hintsViewModel.dismiss(it)
            }
        }

        setupRecyclerView()
        setupSwipeToDelete()
        observeViewModel()
    }

    private fun setupRecyclerView(){
        adapter = FavoritesAdapter(
            onUse = { favorite -> showFavorite(favorite) },
            onRemove = { favorite -> removeWithUndo(favorite) }
        )
        binding.favoritesRecycler.adapter = adapter
    }

    private fun observeViewModel(){
        viewModel.favorites.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
        }
        viewModel.errorMessage.observe(viewLifecycleOwner){ resId ->
            resId ?: return@observe
            Snackbar.make(binding.root, getString(resId), Snackbar.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    //mostra un dialog contenente il testo e i pittogrammi del preferito selezionato e notifica il view model per incrementarne l'utilizzo
    private fun showFavorite(favorite: Favorite) {
        speakAndShow(speechViewModel, favorite.text, favorite.pictograms)
        viewModel.markAsUsed(favorite.id)
    }

    //rimuove subito la frase lasciando dalla snackbar la possibilità di ripristinarla
    private fun removeWithUndo(favorite: Favorite) {
        viewModel.deleteFavorite(favorite.id)
        Snackbar.make(binding.root, R.string.removed_phrase, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_undo) {
                viewModel.restoreFavorite(favorite.text, favorite.pictograms, favorite.contextId)
            }
            .withUndoColors()
            .show()
    }

    //implementa la funzionalità di scorrimento laterale per eliminare un elemento dalla lista offrendo la possibilità di annullare l'azione
    private fun setupSwipeToDelete(){
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT){
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            )= false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val favorite = adapter.itemAt(position)

                requireContext().showConfirmationDialog(
                    title = getString(R.string.delete_favorite_confirm_title),
                    message = getString(R.string.delete_favorite_confirm_message, favorite.text),
                    positiveButtonText = getString(R.string.action_delete),
                    negativeButtonText = getString(R.string.action_cancel),
                    onConfirm = { removeWithUndo(favorite) },
                    onCancel = {
                        adapter.notifyItemChanged(position)
                    }
                )
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.favoritesRecycler)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}