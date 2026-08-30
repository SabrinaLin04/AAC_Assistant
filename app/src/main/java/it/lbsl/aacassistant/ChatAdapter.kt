package it.lbsl.aacassistant

import android.graphics.Color
import android.view.View
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import it.lbsl.aacassistant.databinding.ItemChatMessageBinding

class ChatAdapter (
    private val isFavorite: (String) -> Boolean = { false },
    private val onToggleFavorite: (String, Int?) -> Unit = { _, _ -> }
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
        val isUser = message.author == "user"

        holder.binding.message = message
        holder.binding.executePendingBindings()

        holder.binding.messageText.text = message.text

        holder.binding.messageContainer.gravity = if (isUser) Gravity.END else Gravity.START

        if (isUser) {
            val drawable = GradientDrawable().apply {
                setColor(Color.parseColor("#1976D2"))
                cornerRadii = floatArrayOf(
                    36f, 36f,
                    36f, 36f,
                    4f, 4f,
                    36f, 36f
                )
            }
            holder.binding.messageText.background = drawable
            holder.binding.messageText.setTextColor(Color.WHITE)
        } else {
            val drawable = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(
                    4f, 4f,
                    36f, 36f,
                    36f, 36f,
                    36f, 36f
                )
            }
            holder.binding.messageText.background = drawable
            holder.binding.messageText.setTextColor(Color.parseColor("#1A1A1A"))

            val picto = holder.binding.pictogram

            if (message.pictogramId != null) {
                picto.visibility = View.VISIBLE
                picto.load(PictogramRepository.imageSource(holder.itemView.context, message.pictogramId)) {
                    crossfade(true)
                    placeholder(R.drawable.ic_pictogram_placeholder)
                    error(R.drawable.ic_pictogram_placeholder)
                }
                picto.contentDescription = message.text
            } else {
                picto.visibility = View.GONE
                picto.setImageDrawable(null)
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
            star.setOnClickListener { onToggleFavorite(message.text, message.pictogramId) }
        }
    }

    fun refreshStars() {
        notifyItemRangeChanged(0, itemCount)
    }
    fun updateMessages(newMessages: List<ChatMessage>) {
        val old= messageList
        messageList = newMessages

        when {
            //tutta la lista viene sostituita
            newMessages.size == old.size && newMessages != old && newMessages.size > 1 -> notifyDataSetChanged()
            newMessages.size -old.size > 1 -> notifyDataSetChanged()
            newMessages.size > old.size -> notifyItemChanged(newMessages.lastIndex)
            newMessages.isNotEmpty() -> notifyItemChanged(newMessages.lastIndex)
            else -> notifyDataSetChanged()
        }
    }
}