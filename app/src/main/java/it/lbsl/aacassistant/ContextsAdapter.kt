package it.lbsl.aacassistant

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import it.lbsl.aacassistant.databinding.ItemContextBinding

class ContextsAdapter (
    private val onSelect: (UserContext) -> Unit,
    private val onEdit: (UserContext) -> Unit
) : ListAdapter<UserContext, ContextsAdapter.VH>(DIFF) {
    private var activeId: String? = null
    private var phraseCounts: Map<String, Int> = emptyMap()

    //aggiorna l'indicatore sulla riga che si spegne e su quella che si accende
    fun setActiveId(id: String?) {
        if (id == activeId) return

        val previous = activeId
        activeId = id

        currentList.indexOfFirst { it.id == previous }
            .takeIf { it >= 0 }
            ?.let { notifyItemChanged(it) }

        currentList.indexOfFirst { it.id == id }
            .takeIf { it >= 0 }
            ?.let { notifyItemChanged(it) }
    }

    //quante frasi sono salvate in ogni posto, mostrate sotto il nome
    fun setPhraseCounts(counts: Map<String, Int>) {
        if (counts == phraseCounts) return
        phraseCounts = counts
        notifyItemRangeChanged(0, itemCount)
    }

    class VH(val binding: ItemContextBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemContextBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        holder.binding.contextName.text = item.name
        holder.binding.contextDescription.text = item.description
        holder.binding.activeIndicator.isVisible = item.id == activeId

        val count = phraseCounts[item.id] ?: 0
        holder.binding.phraseCount.isVisible = count > 0
        holder.binding.phraseCount.text =
            context.resources.getQuantityString(R.plurals.context_phrase_count, count, count)

        holder.binding.colorBar.setBackgroundColor(contextColor(context, item))

        val pictogram = holder.binding.contextPictogram
        if (item.pictogramId == null) {
            pictogram.isVisible = false
        } else {
            pictogram.isVisible = true
            pictogram.load(PictogramRepository.imageSource(context, item.pictogramId)) {
                crossfade(true)
                placeholder(R.drawable.ic_pictogram_placeholder)
                error(R.drawable.ic_pictogram_placeholder)
            }
        }

        holder.binding.root.setOnClickListener { onSelect(item) }
        holder.binding.root.setOnLongClickListener { onEdit(item); true }
        holder.binding.editButton.contentDescription = context.getString(R.string.edit_context_named, item.name)
        holder.binding.editButton.setOnClickListener { onEdit(item) }
    }

    fun itemAt(position: Int): UserContext = getItem(position)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<UserContext>() {
            override fun areItemsTheSame(old: UserContext, new: UserContext) = old.id == new.id
            override fun areContentsTheSame(old: UserContext, new: UserContext) = old == new
        }
    }
}

//colore del contesto nella palette: quelli salvati senza colorIndex lo ricavano dall'id, in modo stabile
fun contextColor(context: Context, userContext: UserContext): Int {
    val palette = context.resources.obtainTypedArray(R.array.aac_context_colors)
    val index = Math.floorMod(userContext.colorIndex ?: userContext.id.hashCode(), palette.length())
    val color = palette.getColor(index, 0)
    palette.recycle()
    return color
}
