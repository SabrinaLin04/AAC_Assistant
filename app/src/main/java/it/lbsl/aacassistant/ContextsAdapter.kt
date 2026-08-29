package it.lbsl.aacassistant

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.lbsl.aacassistant.databinding.ItemContextBinding

class ContextsAdapter(
    private val onClick: (UserContext) -> Unit
) : ListAdapter<UserContext, ContextsAdapter.VH>(DIFF) {

    class VH(val binding: ItemContextBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemContextBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.contextName.text = item.name
        holder.binding.contextDescription.text = item.description
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    fun itemAt(position: Int): UserContext = getItem(position)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<UserContext>() {
            override fun areItemsTheSame(a: UserContext, b: UserContext) = a.id == b.id
            override fun areContentsTheSame(a: UserContext, b: UserContext) = a == b
        }
    }
}