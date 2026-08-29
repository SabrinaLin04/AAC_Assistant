package it.lbsl.aacassistant
import android.content.ClipData
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
            viewModel.markAsUsed(favorite.id)
            Snackbar.make(binding.root, R.string.favorite_used, Snackbar.LENGTH_SHORT).show()
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

    private fun updateEmptyState(){
        val empty = viewModel.isEmpty.value == true
        val loading = viewModel.isLoading.value == true
        binding.emptyState.isVisible = empty && !loading
        binding.favoritesRecycler.isVisible = !empty
    }

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