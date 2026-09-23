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
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import it.lbsl.aacassistant.databinding.ItemChatMessageBinding

class ChatAdapter (
    private val isFavorite: (String) -> Boolean = { false },
    private val onToggleFavorite: (ChatMessage) -> Unit = { },
    private val onPictogramsClick: (ChatMessage) -> Unit = { },
    private val onSpeak: (ChatMessage) -> Unit = { }
) : ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(DIFF) {

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
        val isUser = message.author == AUTHOR_USER

        holder.binding.message = message
        holder.binding.executePendingBindings()

        holder.binding.authorLabel.setText(
            when (message.author) {
                AUTHOR_USER -> R.string.chat_author_user
                AUTHOR_PARTNER -> R.string.chat_author_partner
                else -> R.string.chat_author_assistant
            }
        )

        alignToSpeaker(holder, isUser)
        bindBubble(holder, message, isUser)
        bindPictograms(holder, message, isUser)
        bindActions(holder, message)
    }

    //quello che dice l'utente sta a destra, quello che arriva dagli altri a sinistra
    private fun alignToSpeaker(holder: ChatViewHolder, isUser: Boolean) {
        val side = if (isUser) Gravity.END else Gravity.START

        holder.binding.authorLabel.gravity = side
        holder.binding.pictogramStrip.gravity = side or Gravity.CENTER_VERTICAL

        listOf(
            holder.binding.bubbleColumn,
            holder.binding.messageText,
            holder.binding.pictogramScrollView
        ).forEach { view ->
            val params = view.layoutParams as LinearLayout.LayoutParams
            params.gravity = side
            view.layoutParams = params
        }
    }

    //la bolla ha l'angolo squadrato dalla parte di chi parla, come nelle app di messaggi
    private fun bindBubble(holder: ChatViewHolder, message: ChatMessage, isUser: Boolean) {
        val context = holder.itemView.context

        //quello che ha detto l'altra persona su fondo grigio, i suggerimenti su fondo bianco
        val bubbleColor = when {
            isUser -> R.color.aac_bubble_user
            message.author == AUTHOR_PARTNER -> R.color.aac_surface_variant
            else -> R.color.aac_bubble_assistant
        }

        holder.binding.messageText.background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(context, bubbleColor))
            //angoli in senso orario a partire da quello in alto a sinistra
            cornerRadii = if (isUser) {
                floatArrayOf(36f, 36f, 36f, 36f, 4f, 4f, 36f, 36f)
            } else {
                floatArrayOf(4f, 4f, 36f, 36f, 36f, 36f, 36f, 36f)
            }
        }
        holder.binding.messageText.setTextColor(
            ContextCompat.getColor(
                context,
                if (isUser) R.color.aac_on_primary_container else R.color.aac_on_surface
            )
        )
    }

    //striscia dei pittogrammi sotto la frase, toccandola la frase viene letta
    private fun bindPictograms(holder: ChatViewHolder, message: ChatMessage, isUser: Boolean) {
        val context = holder.itemView.context
        val scrollView = holder.binding.pictogramScrollView
        val strip = holder.binding.pictogramStrip

        strip.removeAllViews()
        scrollView.isVisible = message.pictogramIds.isNotEmpty()
        if (message.pictogramIds.isEmpty()) return

        val size = context.resources.getDimensionPixelSize(R.dimen.pictogram_strip_size)
        val gap = context.resources.getDimensionPixelSize(R.dimen.pictogram_strip_gap)
        val onClick = View.OnClickListener { onPictogramsClick(message) }

        scrollView.setOnClickListener(onClick)
        strip.setOnClickListener(onClick)

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
                setOnClickListener(onClick)
            }
            strip.addView(image)
        }

        //per lo screen reader la striscia è un solo elemento, con la frase come descrizione
        scrollView.contentDescription = context.getString(R.string.pictograms_of, message.text)
        strip.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

        //la striscia parte dal lato da cui si comincia a leggerla
        scrollView.post {
            scrollView.fullScroll(if (isUser) View.FOCUS_RIGHT else View.FOCUS_LEFT)
        }
    }

    //Leggi e Salva compaiono solo sulle frasi suggerite, non su quelle scritte dall'utente
    private fun bindActions(holder: ChatViewHolder, message: ChatMessage) {
        val context = holder.itemView.context
        val isSuggestion = message.author == AUTHOR_MODEL && message.text.isNotBlank()

        holder.binding.actionRow.isVisible = isSuggestion
        if (!isSuggestion) return

        holder.binding.speakButton.setOnClickListener { onSpeak(message) }

        val save = holder.binding.saveButton
        val saved = isFavorite(message.text)

        //lo stato si legge nella parola, non solo nell'icona
        save.setText(if (saved) R.string.action_saved else R.string.action_save)
        save.setIconResource(if (saved) R.drawable.ic_star_filled else R.drawable.ic_star)

        //contorno per l'azione ancora da fare, riempimento tenue quando è già fatta
        save.backgroundTintList = ColorStateList.valueOf(
            if (saved) ContextCompat.getColor(context, R.color.aac_primary_container)
            else Color.TRANSPARENT
        )
        save.strokeWidth = if (saved) 0 else holder.saveStrokeWidth

        save.setOnClickListener { onToggleFavorite(message) }
    }

    //il pulsante Salva cambia aspetto quando la lista dei preferiti cambia
    fun refreshSavedState() {
        notifyItemRangeChanged(0, itemCount)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(old: ChatMessage, new: ChatMessage) = old.id == new.id
            override fun areContentsTheSame(old: ChatMessage, new: ChatMessage) = old == new
        }
    }
}
