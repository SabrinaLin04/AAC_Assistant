package it.lbsl.aacassistant

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.OnSelectionChangedListener
import com.google.firebase.firestore.auth.User
import it.lbsl.aacassistant.databinding.ItemContextBinding

class ContextsAdapter (
    private val onSelect: (UserContext) -> Unit,
    private val onEdit: (UserContext) -> Unit
) : ListAdapter<UserContext, ContextsAdapter.VH>(DIFF) {
    private var activeId: String?= null

    fun setActiveId(id: String?) {
        if (id == activeId) return
        activeId = id
        notifyDataSetChanged()
    }

    class VH(val binding: ItemContextBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemContextBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.contextName.text = item.name
        holder.binding.contextDescription.text = item.description
        holder.binding.activeIndicator.isVisible = item.id == activeId
        holder.binding.root.setOnClickListener { onSelect(item) }
        holder.binding.root.setOnLongClickListener { onEdit(item); true }
    }

    fun itemAt(position: Int) : UserContext = getItem(position)

    companion object {
        private val DIFF= object : DiffUtil.ItemCallback<UserContext>() {
            override fun areItemsTheSame(oldItem: UserContext, newItem: UserContext): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: UserContext, newItem: UserContext): Boolean {
                return oldItem == newItem
            }
        }
    }
}