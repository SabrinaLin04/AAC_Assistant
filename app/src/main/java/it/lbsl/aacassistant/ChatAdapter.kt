package it.lbsl.aacassistant

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import it.lbsl.aacassistant.databinding.ItemChatMessageBinding

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

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
        }
    }

    fun updateMessages(newMessages: List<ChatMessage>) {
        val oldSize = messageList.size
        messageList = newMessages

        when {
            newMessages.size - oldSize > 1 -> notifyDataSetChanged()
            newMessages.size > oldSize -> notifyItemInserted((newMessages.lastIndex))
            newMessages.isNotEmpty() -> notifyItemChanged(newMessages.lastIndex)
            else -> notifyDataSetChanged()
        }
    }
}