package it.lbsl.aacassistant

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.lbsl.aacassistant.databinding.ItemFavoriteBinding

class FavoritesAdapter (
    private val onUse: (Favorite) -> Unit
) : ListAdapter<Favorite, FavoritesAdapter.VH> (DIFF){

    class VH(val binding: ItemFavoriteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
       VH(ItemFavoriteBinding.inflate(
           LayoutInflater.from(parent.context), parent, false
       ))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val favorite = getItem(position)
        val context = holder.itemView.context

        holder.binding.favoriteText.text = favorite.text
        holder.binding.usageCount.text = context.resources.getQuantityString(
            R.plurals.favorite_usage_count,
            favorite.usageCount,
            favorite.usageCount
        )
        holder.binding.root.setOnClickListener {onUse(favorite)}
    }

    fun itemAt(position: Int): Favorite = getItem(position)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Favorite>() {
            override fun areItemsTheSame(old: Favorite, new: Favorite) = old.id == new.id
            override fun areContentsTheSame(old: Favorite, new: Favorite) = old == new
        }
    }
}