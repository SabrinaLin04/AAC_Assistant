package it.lbsl.aacassistant

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import it.lbsl.aacassistant.databinding.ItemChatMessageBinding

class ChatAdapter (
    private val isFavorite: (String) -> Boolean = { false },
    private val onToggleFavorite: (String, List<Int>) -> Unit = { _, _ -> },
    private val onPictogramsClick: (ChatMessage) -> Unit = { },
    private val onSpeak: (ChatMessage) -> Unit = { }
) : ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(ChatMessageDiffCallback()) {

    class ChatViewHolder(val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        //spessore del bordo dello stile Outlined, da rimettere quando una frase non è più salvata
        val saveStrokeWidth = binding.saveButton.strokeWidth
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = getItem(position)
        val isUser = message.author == AUTHOR_USER //determina l'autore per applicare l'allineamento corretto alle bolle della chat

        holder.binding.message = message
        holder.binding.executePendingBindings() //forza il re-layout immediato tramite Data Binding

        holder.binding.authorLabel.setText(
            when (message.author) {
                AUTHOR_USER -> R.string.chat_author_user
                AUTHOR_PARTNER -> R.string.chat_author_partner
                else -> R.string.chat_author_assistant
            }
        )

        holder.binding.authorLabel.gravity = if (isUser) Gravity.END else Gravity.START

        val params = holder.binding.bubbleColumn.layoutParams
                as LinearLayout.LayoutParams
        params.gravity = if (isUser) Gravity.END else Gravity.START
        holder.binding.bubbleColumn.layoutParams = params

        val context = holder.itemView.context

        if (isUser) {
            val drawable = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.aac_bubble_user))
                cornerRadii = floatArrayOf(
                    36f, 36f,
                    36f, 36f,
                    4f, 4f,
                    36f, 36f
                )
            }
            holder.binding.messageText.background = drawable
            holder.binding.messageText.setTextColor(ContextCompat.getColor(context, R.color.aac_on_primary_container))
        } else {
            val drawable = GradientDrawable().apply {
                //quello che ha detto l'altra persona su fondo grigio, i suggerimenti su fondo bianco
                val bubbleColor = if (message.author == AUTHOR_PARTNER) {
                    R.color.aac_surface_variant
                } else {
                    R.color.aac_bubble_assistant
                }
                setColor(ContextCompat.getColor(context, bubbleColor))
                cornerRadii = floatArrayOf(
                    4f, 4f,
                    36f, 36f,
                    36f, 36f,
                    36f, 36f
                )
            }
            holder.binding.messageText.background = drawable
            holder.binding.messageText.setTextColor(ContextCompat.getColor(context, R.color.aac_on_surface))
        }

        val textParams = holder.binding.messageText.layoutParams as LinearLayout.LayoutParams
        textParams.gravity = if (isUser) Gravity.END else Gravity.START
        holder.binding.messageText.layoutParams = textParams

        val scrollView = holder.binding.pictogramScrollView
        val strip = holder.binding.pictogramStrip
        strip.removeAllViews()

        val scrollParams = scrollView.layoutParams as LinearLayout.LayoutParams
        scrollParams.gravity = if (isUser) Gravity.END else Gravity.START
        scrollView.layoutParams = scrollParams

        strip.gravity = if (isUser) (Gravity.END or Gravity.CENTER_VERTICAL) else (Gravity.START or Gravity.CENTER_VERTICAL)

        if (message.pictogramIds.isEmpty()) {
            scrollView.visibility = View.GONE
        } else {
            scrollView.visibility = View.VISIBLE

            val size = context.resources.getDimensionPixelSize(R.dimen.pictogram_strip_size)
            val gap = context.resources.getDimensionPixelSize(R.dimen.pictogram_strip_gap)

            val onPictogramsClickListener = View.OnClickListener {
                onPictogramsClick(message)
            }
            scrollView.setOnClickListener(onPictogramsClickListener)
            strip.setOnClickListener(onPictogramsClickListener)

            message.pictogramIds.forEachIndexed { index, id ->
                val image = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        if (index > 0) marginStart = gap
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    load(PictogramRepository.imageSource(context, id)) {
                        crossfade(true)
                        placeholder(R.drawable.ic_pictogram_placeholder)
                        error(R.drawable.ic_pictogram_placeholder)
                    }
                    setOnClickListener(onPictogramsClickListener)
                }
                strip.addView(image)
            }

            //per lo screen reader la striscia è un solo elemento, con la frase come descrizione
            scrollView.contentDescription = context.getString(R.string.pictograms_of, message.text)
            strip.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

            if (isUser) {
                scrollView.post {
                    scrollView.fullScroll(View.FOCUS_RIGHT)
                }
            } else {
                scrollView.post {
                    scrollView.fullScroll(View.FOCUS_LEFT)
                }
            }
        }

        //Leggi e Salva compaiono solo sulle frasi suggerite, non su quelle scritte dall'utente
        if (message.author != AUTHOR_MODEL || message.text.isBlank()) {
            holder.binding.actionRow.visibility = View.GONE
        } else {
            holder.binding.actionRow.visibility = View.VISIBLE
            holder.binding.speakButton.setOnClickListener { onSpeak(message) }

            val save = holder.binding.saveButton
            val saved = isFavorite(message.text)

            //lo stato si legge nella parola, non solo nell'icona
            save.setText(if (saved) R.string.action_saved else R.string.action_save)
            save.setIconResource(
                if (saved) R.drawable.ic_star_filled
                else R.drawable.ic_star
            )

            //contorno per l'azione ancora da fare, riempimento tenue quando è già fatta
            save.backgroundTintList = ColorStateList.valueOf(
                if (saved) ContextCompat.getColor(context, R.color.aac_primary_container)
                else Color.TRANSPARENT
            )
            save.strokeWidth = if (saved) 0 else holder.saveStrokeWidth

            save.setOnClickListener { onToggleFavorite(message.text, message.pictogramIds) }
        }
    }

    fun refreshSavedState() {
        notifyItemRangeChanged(0, itemCount) //aggiorna esclusivamente le viste visibili ricaricando lo stato corrente del pulsante Salva
    }

    fun updateMessages(newMessages: List<ChatMessage>, onCommit: () -> Unit = {}) {
        submitList(newMessages, onCommit)
    }

    class ChatMessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}
