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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.snackbar.Snackbar
import it.lbsl.aacassistant.databinding.FragmentSuggestBinding
import it.lbsl.aacassistant.databinding.ItemSavedChipBinding
import kotlinx.coroutines.launch

class SuggestFragment: Fragment() {

    private var _binding: FragmentSuggestBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LlmViewModel by activityViewModels()
    private val favoritesViewModel: FavoritesViewModel by activityViewModels()
    private val contextsViewModel: ContextsViewModel by activityViewModels()
    private val speechViewModel: SpeechViewModel by activityViewModels()
    private val hintsViewModel: HintsViewModel by activityViewModels()

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var boardAdapter: PictogramBoardAdapter
    private var imeWasVisible = false

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
        setupModeToggle()
        setupPictogramBoard()
        setupSuggestButton()
        setupContextBar()
        observeViewModel()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            binding.bottomContainer.updatePadding(
                bottom = maxOf(bars.bottom, ime.bottom)
            )

            //quando si apre la tastiera la tavola si chiude, altrimenti la chat resterebbe senza spazio
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (imeVisible && !imeWasVisible) showBoard(false)
            imeWasVisible = imeVisible

            insets
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    //configura la recycler view per la chat impostando l'adapter, la logica per salvare le frasi e lo scorrimento automatico all'ultimo messaggio quando cambia il layout
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            isFavorite = { text -> favoritesViewModel.isFavorite(text) },
            onToggleFavorite = { message ->
                val wasSaved = favoritesViewModel.isFavorite(message.text)
                favoritesViewModel.toggleFavorite(
                    message.text,
                    message.pictograms,
                    contextsViewModel.activeContextId.value
                )
                Snackbar.make(
                    binding.root,
                    if (wasSaved) R.string.removed_phrase else R.string.saved_in_my_phrases,
                    Snackbar.LENGTH_SHORT
                ).setAction(R.string.action_view) {
                    findNavController().navigate(R.id.favoritesFragment)
                }.show()
            },
            onPictogramsClick = { message ->
                speakAndShow(speechViewModel, message.text, message.pictograms)
            },
            onSpeak = { message ->
                speakAndShow(speechViewModel, message.text, message.pictograms)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
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

    //con del testo nel campo il pulsante lo invia, altrimenti chiede frasi adatte al posto
    private fun setupSuggestButton() {
        binding.suggestButton.setOnClickListener {
            if (binding.messageInput.text.isNullOrBlank()) {
                viewModel.requestSuggestions()
            } else {
                sendInput()
            }
        }
    }

    //imposta il comportamento della barra di testo, gestendo l'invio dei messaggi e abilitando il pulsante solo se è presente del testo e il modello non è in elaborazione
    private fun setupInputBar() {
        updateSendButton()

        binding.sendButton.setOnClickListener { sendInput() }
        binding.eraseWordButton.setOnClickListener { eraseLastWord() }

        binding.messageInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.messageInput.hint = ""
            } else {
                updateInputHint(binding.modeToggle.checkedButtonId)
            }
        }

        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updateSendButton()
        })
    }

    //invia il testo nella modalità scelta: i suggerimenti partono subito, senza un secondo tocco
    private fun sendInput() {
        val text = binding.messageInput.text.toString().trim()
        if (text.isBlank()) return
        viewModel.sendMessage(text, selectedKind())
        binding.messageInput.text?.clear()
    }

    //"Voglio dire" o "Mi hanno detto": il campo cambia suggerimento a seconda della scelta
    private fun setupModeToggle() {
        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) updateInputHint(checkedId)
        }
        updateInputHint(binding.modeToggle.checkedButtonId)
    }

    private fun selectedKind(): InputKind =
        if (binding.modeToggle.checkedButtonId == R.id.modeReply) InputKind.REPLY else InputKind.INTENTION

    private fun updateInputHint(checkedId: Int) {
        if (!binding.messageInput.hasFocus()) {
            binding.messageInput.setHint(
                if (checkedId == R.id.modeReply) R.string.input_hint_reply else R.string.input_hint_intention
            )
        }
    }

    //tavola di pittogrammi: si compone la frase toccando, senza bisogno della tastiera
    private fun setupPictogramBoard() {
        boardAdapter = PictogramBoardAdapter { item ->
            //mentre arrivano i suggerimenti il campo è bloccato, anche per i pittogrammi
            if (viewModel.chatState.value is ChatState.Generating) return@PictogramBoardAdapter
            appendWord(item.word)
            speechViewModel.speak(item.word)
        }

        val tileWidth = resources.getDimensionPixelSize(R.dimen.board_tile_width)
        val columns = maxOf(3, resources.displayMetrics.widthPixels / tileWidth)
        binding.pictogramBoard.layoutManager = GridLayoutManager(requireContext(), columns)
        binding.pictogramBoard.adapter = boardAdapter

        binding.boardToggle.setOnClickListener {
            if (binding.pictogramBoard.isVisible) {
                showBoard(false)
                binding.messageInput.requestFocus()
                WindowCompat.getInsetsController(requireActivity().window, binding.messageInput)
                    .show(WindowInsetsCompat.Type.ime())
            } else {
                showBoard(true)
            }
        }
        updateBoardToggle()

        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val words = PictogramRepository.indexIds(appContext, CORE_WORDS)
            boardAdapter.submitList(words.map { (word, id) -> BoardWord(word, id) })
        }
    }

    //pittogrammi e tastiera si alternano: insieme lascerebbero troppo poco spazio alla chat
    private fun showBoard(visible: Boolean) {
        if (binding.pictogramBoard.isVisible == visible) return
        binding.pictogramBoard.isVisible = visible
        if (visible) {
            binding.messageInput.clearFocus()
            WindowCompat.getInsetsController(requireActivity().window, binding.messageInput)
                .hide(WindowInsetsCompat.Type.ime())
        }
        updateBoardToggle()
    }

    private fun updateBoardToggle() {
        val boardVisible = binding.pictogramBoard.isVisible
        binding.boardToggle.setImageResource(
            if (boardVisible) R.drawable.ic_keyboard else R.drawable.ic_grid_view
        )
        binding.boardToggle.contentDescription =
            getString(if (boardVisible) R.string.board_hide else R.string.board_show)
    }

    private fun appendWord(word: String) {
        val current = binding.messageInput.text.toString().trimEnd()
        setInput(if (current.isEmpty()) word else "$current $word")
    }

    private fun eraseLastWord() {
        val current = binding.messageInput.text.toString().trimEnd()
        setInput(current.substringBeforeLast(' ', ""))
    }

    private fun setInput(text: String) {
        binding.messageInput.setText(text)
        binding.messageInput.setSelection(text.length)
    }

    //abilita l'invio solo se c'è del testo scritto e nessuna generazione è in corso
    private fun updateSendButton() {
        val isGenerating = viewModel.chatState.value is ChatState.Generating
        val hasText = !binding.messageInput.text.isNullOrBlank()
        val enabled = hasText && !isGenerating
        binding.sendButton.isEnabled = enabled
        binding.eraseWordButton.isVisible = hasText
        updateSendButtonTint(enabled)
    }

    //configura l'azione al tocco sulla barra del contesto per tornare alla scelta dei contesti rimuovendo il fragment corrente dallo stack
    private fun setupContextBar() {
        binding.contextBar.setOnClickListener {
            val navController = findNavController()
            if (!navController.popBackStack(R.id.contextsFragment, false)) {
                navController.navigate(R.id.contextsFragment)
            }
        }
    }

    //le frasi salvate per questo posto compaiono per prime, finché la conversazione non è iniziata
    private fun updateSavedPhrases() {
        val contextId = contextsViewModel.activeContextId.value
        val saved = if (contextId == null) {
            emptyList()
        } else {
            favoritesViewModel.phrasesFor(contextId).take(MAX_SAVED_CHIPS)
        }

        binding.savedChips.removeAllViews()
        binding.savedGroup.isVisible = saved.isNotEmpty() && viewModel.messages.value.isNullOrEmpty()

        if (binding.savedGroup.isVisible) {
            saved.forEach { favorite ->
                val chip = ItemSavedChipBinding.inflate(layoutInflater, binding.savedChips, false).root
                chip.text = favorite.text
                chip.setOnClickListener {
                    speakAndShow(speechViewModel, favorite.text, favorite.pictograms)
                    favoritesViewModel.markAsUsed(favorite.id)
                }
                binding.savedChips.addView(chip)
            }
        }
    }

    //il primo aiuto spiega come scrivere, quello successivo come far parlare il telefono
    private fun updateHint() {
        val dismissed = hintsViewModel.dismissed.value ?: return
        val messages = viewModel.messages.value.orEmpty()

        val key = if (messages.any { it.author == AUTHOR_MODEL }) Hints.SPEAK else Hints.WRITE
        val textRes = if (key == Hints.SPEAK) R.string.hint_speak else R.string.hint_write

        binding.hintBarInclude.bindHint(key, textRes, dismissed) { hintsViewModel.dismiss(it) }
    }

    private fun updateSendButtonTint(enabled: Boolean) {
        val color = if (enabled) Color.WHITE else ContextCompat.getColor(requireContext(), R.color.aac_outline)
        binding.sendButton.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    private fun observeViewModel() {

        viewModel.modelState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ModelState.Idle -> { }
                is ModelState.Initializing -> showLoading(getString(state.messageRes))
                is ModelState.Ready -> showChat(demo = false)
                is ModelState.DemoMode -> showChat(demo = true)
                is ModelState.Error -> showError(
                    buildString {
                        append(getString(state.messageRes))
                        state.detail?.let { append("\n").append(it)}
                    }
                )
            }
        }

        hintsViewModel.dismissed.observe(viewLifecycleOwner) { updateHint() }

        favoritesViewModel.favorites.observe(viewLifecycleOwner){
            chatAdapter.refreshSavedState()
            updateSavedPhrases()
        }
        favoritesViewModel.errorMessage.observe(viewLifecycleOwner){ resId ->
            resId ?: return@observe
            Snackbar.make(binding.root, getString(resId), Snackbar.LENGTH_SHORT).show()
            favoritesViewModel.clearError()
        }

        contextsViewModel.activeContext.observe(viewLifecycleOwner) { ctx ->
            viewModel.setContext(ctx?.name, ctx?.description)
            binding.contextLabel.text = ctx?.name ?: getString(R.string.context_none)
            bindContextBar(ctx)
            updateSavedPhrases()
        }

        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            updateSavedPhrases()
            updateHint()

            val oldSize = chatAdapter.itemCount
            chatAdapter.updateMessages(messages) {
                if (messages.isNotEmpty()) {
                    val targetIndex = if (messages.size > oldSize) {
                        oldSize
                    } else if (messages.first().author != AUTHOR_MODEL && messages.size > 1) {
                        1
                    } else {
                        0
                    }
                    val lm = binding.recyclerView.layoutManager as? LinearLayoutManager
                    lm?.scrollToPositionWithOffset(targetIndex, 0)
                }
            }
        }

        viewModel.chatState.observe(viewLifecycleOwner) { state ->
            val isGenerating = state is ChatState.Generating
            binding.messageInput.isEnabled = !isGenerating
            binding.suggestButton.isEnabled = !isGenerating
            binding.suggestProgressBar.visibility = if (isGenerating) View.VISIBLE else View.GONE
            updateSendButton()

            if (isGenerating) {
                binding.statusIndicator.text = getString(R.string.chat_status_generating)
                binding.statusIndicator.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_busy))
            } else {
                binding.statusIndicator.text = getString(R.string.chat_status_available)
                binding.statusIndicator.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_available))
            }

            if (state is ChatState.Error) {
                val message = buildString {
                    append(getString(state.messageRes))
                    state.detail?.let { append(": ").append(it) }
                }
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                viewModel.clearChatError()
            }
        }
    }

    //ripete nella barra il colore e il pittogramma del posto appena scelto
    private fun bindContextBar(userContext: UserContext?) {
        binding.contextColorBar.isVisible = userContext != null
        if (userContext != null) {
            binding.contextColorBar.setBackgroundColor(contextColor(requireContext(), userContext))
        }

        val pictogramId = userContext?.pictogramId
        binding.contextBarPictogram.isVisible = pictogramId != null
        if (pictogramId != null) {
            binding.contextBarPictogram.load(PictogramRepository.imageSource(requireContext(), pictogramId)) {
                crossfade(true)
                placeholder(R.drawable.ic_pictogram_placeholder)
                error(R.drawable.ic_pictogram_placeholder)
            }
        }
    }

    //nasconde la chat principale e le schermate di errore per visualizzare l'animazione di caricamento
    private fun showLoading(message: String) {
        binding.loadingGroup.visibility = View.VISIBLE
        binding.errorGroup.visibility = View.GONE
        binding.chatGroup.visibility = View.GONE
        binding.loadingText.text = message
    }

    //rende visibile la lista dei messaggi nascondendo gli indicatori di stato e mostrando un banner specifico se il modello è avviato in modalità dimostrativa
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

    private companion object {
        const val MAX_SAVED_CHIPS = 6
    }
}
