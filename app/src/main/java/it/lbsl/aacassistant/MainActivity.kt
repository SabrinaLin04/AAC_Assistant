package it.lbsl.aacassistant

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import it.lbsl.aacassistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: LlmViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            val bottomPadding = maxOf(systemBars.bottom, imeInsets.bottom)

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                bottomPadding
            )
            insets
        }

        setupRecyclerView()
        setupInputBar()
        observeViewModel()

        viewModel.getModel(applicationContext)
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        val layoutManager = LinearLayoutManager(this)
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

    private fun updateSendButtonTint(enabled: Boolean) {
        val color = if (enabled) Color.WHITE else Color.parseColor("#BBBBBB")
        binding.sendButton.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    private fun observeViewModel() {

        viewModel.modelState.observe(this) { state ->
            when (state) {
                is ModelState.Idle -> { }
                is ModelState.Initializing -> showLoading(state.message)
                is ModelState.Downloading -> showLoading("Downloading: ${state.percent}%")
                is ModelState.Ready -> showChat()
                is ModelState.Error -> showError(state.cause)
            }
        }

        viewModel.messages.observe(this) { messages ->
            chatAdapter.updateMessages(messages)
            if (messages.isNotEmpty()) {
                binding.recyclerView.smoothScrollToPosition(messages.lastIndex)
            }
        }

        viewModel.chatState.observe(this) { state ->
            val isGenerating = state is ChatState.Generating
            binding.messageInput.isEnabled = !isGenerating
            val enabled = !isGenerating && !binding.messageInput.text.isNullOrBlank()
            binding.sendButton.isEnabled = enabled
            updateSendButtonTint(enabled)

            if (isGenerating) {
                binding.statusIndicator.text = "Generating..."
                binding.statusIndicator.setTextColor(Color.parseColor("#FF9800"))
            } else {
                binding.statusIndicator.text = "Available"
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