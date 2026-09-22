package it.lbsl.aacassistant

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import it.lbsl.aacassistant.databinding.ItemPictogramTileBinding

//vocabolario della tavola: solo parole valide in qualunque posto, tutte presenti in assets/indice.json.
//L'ordine è quello di lettura: chi, sì e no, richieste, come ci si sente
val CORE_WORDS = listOf(
    "io", "tu", "sì", "no",
    "volere", "aiuto", "dolore", "paura",
    "felice", "stanco", "male", "bene"
)

data class BoardWord(val word: String, val pictogramId: Int)

//tavola di pittogrammi: ogni tocco aggiunge la parola alla frase da inviare
class PictogramBoardAdapter(
    private val onTap: (BoardWord) -> Unit
) : ListAdapter<BoardWord, PictogramBoardAdapter.VH>(DIFF) {

    class VH(val binding: ItemPictogramTileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPictogramTileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        holder.binding.tileLabel.text = item.word
        holder.binding.root.contentDescription = item.word
        holder.binding.tileImage.load(PictogramRepository.imageSource(context, item.pictogramId)) {
            crossfade(true)
            placeholder(R.drawable.ic_pictogram_placeholder)
            error(R.drawable.ic_pictogram_placeholder)
        }
        holder.binding.root.setOnClickListener { onTap(item) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<BoardWord>() {
            override fun areItemsTheSame(old: BoardWord, new: BoardWord) = old.word == new.word
            override fun areContentsTheSame(old: BoardWord, new: BoardWord) = old == new
        }
    }
}
