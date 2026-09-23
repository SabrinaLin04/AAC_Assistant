package it.lbsl.aacassistant

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
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

    //la chat: ogni frase si può leggere ad alta voce o salvare fra le proprie
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
        //quando la tastiera si apre la lista si accorcia: l'ultima frase resta in vista
        binding.recyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom >= oldBottom) return@addOnLayoutChangeListener
            binding.recyclerView.postDelayed({
                if (chatAdapter.itemCount > 0) {
                    binding.recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                }
            }, SCROLL_DELAY_MS)
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

    //campo di scrittura: invio, cancella l'ultima parola e invito che cambia con la modalità
    private fun setupInputBar() {
        updateSendButton()

        binding.sendButton.setOnClickListener { sendInput() }
        binding.eraseWordButton.setOnClickListener { eraseLastWord() }

        binding.messageInput.setOnFocusChangeListener { _, _ -> updateInputHint() }

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
        binding.modeToggle.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) updateInputHint()
        }
        updateInputHint()
    }

    private fun isReplyMode(): Boolean = binding.modeToggle.checkedButtonId == R.id.modeReply

    private fun selectedKind(): InputKind =
        if (isReplyMode()) InputKind.REPLY else InputKind.INTENTION

    //l'invito nel campo segue la modalità scelta e sparisce mentre si scrive
    private fun updateInputHint() {
        binding.messageInput.hint = when {
            binding.messageInput.hasFocus() -> ""
            isReplyMode() -> getString(R.string.input_hint_reply)
            else -> getString(R.string.input_hint_intention)
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
            showBoard(!binding.pictogramBoard.isVisible)
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

    //l'icona resta quella dei pittogrammi: lo stesso tocco li apre e li richiude
    private fun updateBoardToggle() {
        binding.boardToggle.contentDescription = getString(
            if (binding.pictogramBoard.isVisible) R.string.board_hide else R.string.board_show
        )
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

    //toccando il nome del posto si torna a sceglierlo
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
        observeModel()
        observeChat()
        observeSavedPhrases()
        observeContext()
        hintsViewModel.dismissed.observe(viewLifecycleOwner) { updateHint() }
    }

    //il caricamento del modello decide cosa si vede: attesa, errore o chat
    private fun observeModel() {
        viewModel.modelState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ModelState.Idle -> { }
                is ModelState.Initializing -> showLoading(getString(state.messageRes))
                is ModelState.Ready -> showChat(demo = false)
                is ModelState.DemoMode -> showChat(demo = true)
                is ModelState.Error -> showError(message(state.messageRes, state.detail, "\n"))
            }
        }
    }

    private fun observeChat() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            updateSavedPhrases()
            updateHint()

            val oldSize = chatAdapter.itemCount
            chatAdapter.submitList(messages) { scrollToNewMessages(messages, oldSize) }
        }

        viewModel.chatState.observe(viewLifecycleOwner) { state ->
            showGenerating(state is ChatState.Generating)

            if (state is ChatState.Error) {
                Snackbar.make(
                    binding.root,
                    message(state.messageRes, state.detail, ": "),
                    Snackbar.LENGTH_LONG
                ).show()
                viewModel.clearChatError()
            }
        }
    }

    private fun observeSavedPhrases() {
        favoritesViewModel.favorites.observe(viewLifecycleOwner) {
            chatAdapter.refreshSavedState()
            updateSavedPhrases()
        }
        favoritesViewModel.errorMessage.observe(viewLifecycleOwner) { resId ->
            resId ?: return@observe
            Snackbar.make(binding.root, getString(resId), Snackbar.LENGTH_LONG).show()
            favoritesViewModel.clearError()
        }
    }

    private fun observeContext() {
        contextsViewModel.activeContext.observe(viewLifecycleOwner) { ctx ->
            viewModel.setContext(ctx?.name, ctx?.description)
            binding.contextLabel.text = ctx?.name ?: getString(R.string.context_none)
            bindContextBar(ctx)
            updateSavedPhrases()
        }
    }

    //mentre il modello lavora il campo è bloccato e il pallino accanto al nome diventa arancione
    private fun showGenerating(isGenerating: Boolean) {
        binding.messageInput.isEnabled = !isGenerating
        binding.suggestButton.isEnabled = !isGenerating
        binding.suggestProgressBar.isVisible = isGenerating
        updateSendButton()

        binding.statusIndicator.setText(
            if (isGenerating) R.string.chat_status_generating else R.string.chat_status_available
        )
        binding.statusIndicator.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isGenerating) R.color.status_busy else R.color.status_available
            )
        )
    }

    //porta in cima la prima frase nuova, così si legge dall'inizio senza scorrere
    private fun scrollToNewMessages(messages: List<ChatMessage>, oldSize: Int) {
        if (messages.isEmpty()) return

        val target = when {
            messages.size > oldSize -> oldSize
            //la prima riga è quella scritta dall'utente, i suggerimenti cominciano dopo
            messages.first().author != AUTHOR_MODEL && messages.size > 1 -> 1
            else -> 0
        }
        (binding.recyclerView.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(target, 0)
    }

    //testo dell'errore, con il dettaglio tecnico in coda quando c'è
    private fun message(@StringRes messageRes: Int, detail: String?, separator: String): String =
        getString(messageRes) + detail?.let { separator + it }.orEmpty()

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

    private fun showLoading(message: String) {
        showOnly(binding.loadingGroup)
        binding.loadingText.text = message
    }

    //senza modello sul telefono la chat funziona lo stesso, con frasi preimpostate: il banner lo dice
    private fun showChat(demo: Boolean) {
        showOnly(binding.chatGroup)
        binding.demoBanner.isVisible = demo
    }

    private fun showError(message: String) {
        showOnly(binding.errorGroup)
        binding.errorMessage.text = message
    }

    //attesa, errore e chat sono le tre facce della schermata: se ne vede una per volta
    private fun showOnly(group: View) {
        listOf(binding.loadingGroup, binding.errorGroup, binding.chatGroup)
            .forEach { it.isVisible = it == group }
    }

    private companion object {
        const val MAX_SAVED_CHIPS = 6

        //il tempo che la lista impiega a rimpicciolirsi quando si apre la tastiera
        const val SCROLL_DELAY_MS = 100L
    }
}
