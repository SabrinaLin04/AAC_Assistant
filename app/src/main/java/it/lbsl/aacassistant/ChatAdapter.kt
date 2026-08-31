package it.lbsl.aacassistant

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import it.lbsl.aacassistant.databinding.ItemChatMessageBinding

class ChatAdapter (
    private val isFavorite: (String) -> Boolean = { false },
    private val onToggleFavorite: (String, List<Int>) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
    private var messageList: List<ChatMessage> = emptyList()


    class ChatViewHolder(val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChatViewHolder(binding)
    }

    override fun getItemCount(): Int = messageList.size

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messageList[position]
        val isUser = message.author == "user" //determina l'autore per applicare l'allineamento corretto alle bolle della chat

        holder.binding.message = message
        holder.binding.executePendingBindings() //forza il re-layout immediato tramite Data Binding

        holder.binding.messageText.text = message.text

        val params = holder.binding.bubbleColumn.layoutParams
                as ConstraintLayout.LayoutParams
        params.horizontalBias = if (isUser) 1f else 0f
        holder.binding.bubbleColumn.layoutParams = params

        if (isUser) {
            val drawable = GradientDrawable().apply {
                setColor(ContextCompat.getColor(holder.itemView.context, R.color.bubble_user))
                cornerRadii = floatArrayOf(
                    36f, 36f,
                    36f, 36f,
                    4f, 4f,
                    36f, 36f
                )
            }
            holder.binding.messageText.background = drawable
            holder.binding.messageText.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.m1_primary))
        } else {
            val drawable = GradientDrawable().apply {
                setColor(ContextCompat.getColor(holder.itemView.context, R.color.bubble_assistant))
                cornerRadii = floatArrayOf(
                    4f, 4f,
                    36f, 36f,
                    36f, 36f,
                    36f, 36f
                )
            }
            holder.binding.messageText.background = drawable
            holder.binding.messageText.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.m1_surface))

            val strip = holder.binding.pictogramStrip
            val context = holder.itemView.context

            strip.removeAllViews() //svuota le view aggiunte in precedenza per prevenire duplicazioni dovute al "view recycling"

            if (message.pictogramIds.isEmpty()) {
                strip.visibility = View.GONE
            } else {
                strip.visibility = View.VISIBLE

                val size = context.resources.getDimensionPixelSize(R.dimen.pictogram_strip_size)
                val gap = context.resources.getDimensionPixelSize(R.dimen.pictogram_strip_gap)

                message.pictogramIds.forEachIndexed { index, id ->
                    val image = ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(size, size).apply {
                            if (index > 0) marginStart = gap
                        }
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = null
                        load(PictogramRepository.imageSource(context, id)) {   //preferisce il file locale
                            crossfade(true)
                            placeholder(R.drawable.ic_pictogram_placeholder)
                            error(R.drawable.ic_pictogram_placeholder)
                        }
                    }
                    strip.addView(image)
                }

                strip.contentDescription = message.text
            }
        }

        val star = holder.binding.favoriteStar
        val context = holder.itemView.context

        if (isUser || message.text.isBlank()) {
            star.visibility = View.GONE
        } else {
            star.visibility = View.VISIBLE
            val saved = isFavorite(message.text)

            star.setImageResource(
                if (saved) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
            star.contentDescription = context.getString(
                if (saved) R.string.favorite_deleted else R.string.favorite_added
            )
            star.setOnClickListener { onToggleFavorite(message.text, message.pictogramIds) }
        }
    }

    fun refreshStars() {
        notifyItemRangeChanged(0, itemCount) //aggiorna esclusivamente le viste visibili ricaricando lo stato corrente del pulsante "preferito"
    }
    fun updateMessages(newMessages: List<ChatMessage>) {
        val old= messageList
        messageList = newMessages

        when {
            //tutta la lista viene sostituita
            //un approccio più robusto per il futuro consisterebbe nell'estendere ListAdapter e usare DiffUtil.ItemCallback, delegando a lui questi calcoli per ottenere animazioni fluide in automatico
            newMessages.size == old.size && newMessages != old && newMessages.size > 1 -> notifyDataSetChanged()
            newMessages.size - old.size > 1 -> notifyDataSetChanged()
            newMessages.size > old.size -> notifyItemInserted(newMessages.lastIndex)
            newMessages.isNotEmpty() -> notifyItemChanged(newMessages.lastIndex)
            else -> notifyDataSetChanged()
        }
    }
}