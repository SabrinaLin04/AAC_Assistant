package it.lbsl.aacassistant

import androidx.appcompat.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import coil.load
import com.google.android.material.snackbar.Snackbar
import it.lbsl.aacassistant.databinding.FragmentFavoritesBinding

class FavoritesFragment: Fragment() {
    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FavoritesViewModel by activityViewModels()
    private lateinit var adapter: FavoritesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSwipeToDelete()
        observeViewModel()
    }

    private fun setupRecyclerView(){
        adapter = FavoritesAdapter{ favorite ->
            showFavorite(favorite)
        }
        binding.favoritesRecycler.layoutManager= LinearLayoutManager(requireContext())
        binding.favoritesRecycler.adapter = adapter
    }

    private fun observeViewModel(){
        viewModel.favorites.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.isVisible = loading
            updateEmptyState()
        }
        viewModel.isEmpty.observe(viewLifecycleOwner){
            updateEmptyState()
        }
        viewModel.errorMessage.observe(viewLifecycleOwner){ resId ->
            resId ?: return@observe
            Snackbar.make(binding.root, getString(resId), Snackbar.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    //gestisce la visibilità degli elementi dell'interfaccia alternando tra l'elenco dei preferiti e il messaggio di stato vuoto in base ai caricamenti
    private fun updateEmptyState(){
        val empty = viewModel.isEmpty.value == true
        val loading = viewModel.isLoading.value == true
        binding.emptyState.isVisible = empty && !loading
        binding.favoritesRecycler.isVisible = !empty
    }

    //mostra un dialog contenente il testo e i pittogrammi del preferito selezionato e notifica il view model per incrementarne l'utilizzo
    private fun showFavorite(favorite: Favorite) {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_speak, null)

        view.findViewById<TextView>(R.id.speakText).text = favorite.text

        val row = view.findViewById<LinearLayout>(R.id.pictogramRow)
        val scroll = view.findViewById<HorizontalScrollView>(R.id.pictogramScroll)

        row.removeAllViews()

        if (favorite.pictogramIds.isEmpty()) {
            scroll.visibility = View.GONE
        } else {
            scroll.visibility = View.VISIBLE
            val size = resources.getDimensionPixelSize(R.dimen.pictogram_max_size)
            val gap = resources.getDimensionPixelSize(R.dimen.pictogram_gap)

            favorite.pictogramIds.forEach { id ->
                val image = ImageView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        marginStart = gap
                        marginEnd = gap
                    }
                    load(PictogramRepository.imageSource(requireContext(), id))
                }
                row.addView(image)
            }

            row.contentDescription = favorite.text
        }

        AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton(R.string.action_close, null)
            .show()

        viewModel.markAsUsed(favorite.id)
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
                val favorite = adapter.itemAt(viewHolder.bindingAdapterPosition)
                viewModel.deleteFavorite(favorite.id)

                Snackbar.make(binding.root, R.string.favorite_deleted, Snackbar.LENGTH_LONG).setAction(R.string.action_undo)
                {viewModel.toggleFavorite(favorite.text, favorite.pictogramIds)}.show()
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.favoritesRecycler)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}