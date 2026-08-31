package it.lbsl.aacassistant

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.snackbar.Snackbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import it.lbsl.aacassistant.databinding.FragmentSuggestBinding

class SuggestFragment: Fragment() {

    private var _binding: FragmentSuggestBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LlmViewModel by activityViewModels()
    private val favoritesViewModel: FavoritesViewModel by activityViewModels()

    private val contextsViewModel: ContextsViewModel by activityViewModels()
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSuggestBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupInputBar()
        setupSuggestButton()
        setupContextBar()
        observeViewModel()

        if (viewModel.modelState.value is ModelState.Idle){
            viewModel.getModel(requireContext().applicationContext)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Usiamo il bottomContainer (che ha sfondo bianco) per gestire le insets.
            // Quando la tastiera è chiusa, bars.bottom aggiunge spazio per la barra di navigazione.
            // Quando è aperta, ime.bottom sposta tutto sopra la tastiera.
            binding.bottomContainer.updatePadding(
                bottom = maxOf(bars.bottom, ime.bottom)
            )

            insets
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding=null
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            isFavorite = { text -> favoritesViewModel.isFavorite(text)},
            onToggleFavorite = { text, pictogramIds ->
                val wasSaved = favoritesViewModel.isFavorite(text)
                favoritesViewModel.toggleFavorite(text, pictogramIds)
                Snackbar.make(
                    binding.root,
                    if (wasSaved) R.string.favorite_removed else R.string.favorite_added,
                    Snackbar.LENGTH_SHORT
                ).setAction(R.string.action_view) {
                    findNavController().navigate(R.id.favoritesFragment)
                }.show()
            }
        )
        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = chatAdapter
        binding.recyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                binding.recyclerView.postDelayed({
                    if (chatAdapter.itemCount > 0) {
                        binding.recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                    }
                }, 100)
            }
        }
    }

    private fun setupSuggestButton() {
        binding.suggestButton.setOnClickListener {
            viewModel.requestSuggestions()
        }
    }

    private fun setupInputBar() {
        updateSendButtonTint(false)

        binding.sendButton.setOnClickListener {
            val text = binding.messageInput.text.toString().trim()
            if (text.isNotBlank()) {
                viewModel.sendMessage(text)
                binding.messageInput.text?.clear()
            }
        }

        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val isGenerating = viewModel.chatState.value is ChatState.Generating
                val enabled = !s.isNullOrBlank() && !isGenerating
                binding.sendButton.isEnabled = enabled
                updateSendButtonTint(enabled)
            }
        })
    }

    private fun setupContextBar() {
        binding.contextBar.setOnClickListener {
            findNavController().navigate(
                R.id.contextsFragment,
                null,
                navOptions {
                    launchSingleTop = true
                    popUpTo(R.id.suggestFragment) { inclusive = false }
                }
            )
        }
    }

    private fun updateSendButtonTint(enabled: Boolean) {
        val color = if (enabled) Color.WHITE else ContextCompat.getColor(requireContext(), R.color.m_outline)
        binding.sendButton.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    private fun observeViewModel() {

        viewModel.modelState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ModelState.Idle -> { }
                is ModelState.Initializing -> showLoading(getString(state.messageRes))
                is ModelState.Ready -> showChat( demo = false)
                is ModelState.DemoMode -> showChat(demo = true)
                is ModelState.Error -> showError(
                    buildString {
                        append(getString(state.messageRes))
                        state.detail?.let { append("\n").append(it)}
                    }
                )
            }
        }

        favoritesViewModel.favorites.observe(viewLifecycleOwner){
            chatAdapter.refreshStars()
        }
        favoritesViewModel.errorMessage.observe(viewLifecycleOwner){ resId ->
            resId ?: return@observe
            Snackbar.make(binding.root, getString(resId), Snackbar.LENGTH_SHORT).show()
            favoritesViewModel.clearError()
        }

        contextsViewModel.activeContext.observe(viewLifecycleOwner) { ctx ->
            viewModel.setContext(ctx?.description)
            binding.contextLabel.text = ctx?.name ?: getString(R.string.context_none)
        }

        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            chatAdapter.updateMessages(messages)
            if (messages.isNotEmpty()) {
                binding.recyclerView.smoothScrollToPosition(messages.lastIndex)
            }
        }

        viewModel.chatState.observe(viewLifecycleOwner) { state ->
            val isGenerating = state is ChatState.Generating
            binding.messageInput.isEnabled = !isGenerating
            binding.suggestButton.isEnabled = !isGenerating
            val enabled = !isGenerating && !binding.messageInput.text.isNullOrBlank()
            binding.sendButton.isEnabled = enabled
            updateSendButtonTint(enabled)

            if (isGenerating) {
                binding.statusIndicator.text = getString(R.string.chat_status_generating)
                binding.statusIndicator.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_generating))
            } else {
                binding.statusIndicator.text = getString(R.string.chat_status_available)
                binding.statusIndicator.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_available))
            }

            if (state is ChatState.Error) {
                Snackbar.make(binding.root, getString(state.messageRes), Snackbar.LENGTH_LONG).show()
                viewModel.clearChatError()
            }
        }
    }

    private fun showLoading(message: String) {
        binding.loadingGroup.visibility = View.VISIBLE
        binding.errorGroup.visibility = View.GONE
        binding.chatGroup.visibility = View.GONE
        binding.loadingText.text = message
    }

    private fun showChat(demo: Boolean = false) {
        binding.loadingGroup.visibility = View.GONE
        binding.errorGroup.visibility = View.GONE
        binding.chatGroup.visibility = View.VISIBLE
        binding.demoBanner.visibility = if (demo) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        binding.loadingGroup.visibility = View.GONE
        binding.errorGroup.visibility = View.VISIBLE
        binding.chatGroup.visibility = View.GONE
        binding.errorMessage.text = message
    }


}