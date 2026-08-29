package it.lbsl.aacassistant

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.FragmentTransitionSupport
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
        setupContextBar()
        observeViewModel()

        if (viewModel.modelState.value is ModelState.Idle){
            viewModel.getModel(requireContext().applicationContext)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding=null
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            isFavorite = { text -> favoritesViewModel.isFavorite(text)},
            onToggleFavorite = { text ->
                val wasSaved = favoritesViewModel.isFavorite(text)
                favoritesViewModel.toggleFavorite(text)
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
        val color = if (enabled) Color.WHITE else Color.parseColor("#BBBBBB")
        binding.sendButton.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    private fun observeViewModel() {

        viewModel.modelState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ModelState.Idle -> { }
                is ModelState.Initializing -> showLoading(getString(state.messageRes))
                is ModelState.Downloading -> showLoading(getString(R.string.model_downloading, state.percent))
                is ModelState.Ready -> showChat()
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
            val enabled = !isGenerating && !binding.messageInput.text.isNullOrBlank()
            binding.sendButton.isEnabled = enabled
            updateSendButtonTint(enabled)

            if (isGenerating) {
                binding.statusIndicator.text = getString(R.string.chat_status_generating)
                binding.statusIndicator.setTextColor(Color.parseColor("#FF9800"))
            } else {
                binding.statusIndicator.text = getString(R.string.chat_status_available)
                binding.statusIndicator.setTextColor(Color.parseColor("#4CAF50"))
            }
        }
    }

    private fun showLoading(message: String) {
        binding.loadingGroup.visibility = View.VISIBLE
        binding.errorGroup.visibility = View.GONE
        binding.chatGroup.visibility = View.GONE
        binding.loadingText.text = message
    }

    private fun showChat() {
        binding.loadingGroup.visibility = View.GONE
        binding.errorGroup.visibility = View.GONE
        binding.chatGroup.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        binding.loadingGroup.visibility = View.GONE
        binding.errorGroup.visibility = View.VISIBLE
        binding.chatGroup.visibility = View.GONE
        binding.errorMessage.text = message
    }
}