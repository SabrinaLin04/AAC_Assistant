package it.lbsl.aacassistant

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import it.lbsl.aacassistant.databinding.ItemFavoriteBinding

class FavoritesAdapter (
    private val onUse: (Favorite) -> Unit
) : ListAdapter<Favorite, FavoritesAdapter.VH> (DIFF) {

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

        //svuota sempre il contenitore prima di aggiungere nuovi elementi
        //per evitare duplicati causati dal riciclo delle viste della RecyclerView
        holder.binding.pictogramRow.removeAllViews()

        if (favorite.pictogramIds.isNotEmpty()) {
            holder.binding.pictogramScroll.visibility = View.VISIBLE

            val density = context.resources.displayMetrics.density
            val sizePx = (48 * density).toInt()
            val marginPx = (8 * density).toInt()

            favorite.pictogramIds.forEach { pictogramId ->
                val imageView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                        marginEnd = marginPx
                    }

                    setOnClickListener { onUse(favorite) }

                    load(PictogramRepository.imageSource(context, pictogramId)) {
                        crossfade(true)
                        placeholder(R.drawable.ic_pictogram_placeholder)
                        error(R.drawable.ic_pictogram_placeholder)
                    }
                }

                holder.binding.pictogramRow.addView(imageView)
            }

            holder.binding.pictogramScroll.setOnClickListener { onUse(favorite) }
            holder.binding.pictogramRow.setOnClickListener { onUse(favorite) }

        } else {
            holder.binding.pictogramScroll.visibility = View.GONE
        }

        holder.binding.root.setOnClickListener { onUse(favorite) }
    }

    fun itemAt(position: Int): Favorite = getItem(position)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Favorite>() {
            override fun areItemsTheSame(old: Favorite, new: Favorite) = old.id == new.id
            override fun areContentsTheSame(old: Favorite, new: Favorite) = old == new
        }
    }
}