package it.lbsl.aacassistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.util.Locale
import java.util.zip.GZIPInputStream

//Lemmatizzazione viene precalcolata tramite lo script tools/genera_tabella_lemmi.py e fornita come asset
//tempo di esecuzione questa classe effettua solo una ricerca in un dizionario
//tabella filtrata contente le forme contenute in ARASAAC
class Lemmatizer private constructor(
    private val forms: Map<String, String>
) {

    //numero di coppie forma-lemma caricate
    val size: Int get() = forms.size

    //restituisce la forma base della parola se non e' presente una voce per essa
    fun lemmatize(word: String): String {
        val normalized = normalize(word)
        if (normalized.isEmpty()) return ""

        forms[normalized]?.let { return it }

        //la parola non è nella tabella
        //genera candidati verosimili e ne accetta uno solo se la tabella lo conferma
        for (candidate in candidates(normalized)) {
            forms[candidate]?.let { return it }
        }

        return normalized
    }

    private fun normalize(word: String): String =
        word.lowercase(Locale.ITALIAN)
            .trim()
            .trim('.', ',', ';', ':', '!', '?', '"', '\'', '(', ')', '\u00AB', '\u00BB')


    //candidati per la flessione italiana
    private fun candidates(word: String): List<String> {
        val out = mutableListOf<String>()
        val n = word.length

        //plurali
        if (n > 3) {
            when (word.last()) {
                'i' -> {
                    val stem = word.dropLast(1)
                    when {
                        word.endsWith("chi") -> out += stem.dropLast(1) + "co"
                        word.endsWith("ghi") -> out += stem.dropLast(1) + "go"
                        word.endsWith("ci")  -> out += stem + "o"
                        word.endsWith("gi")  -> out += stem + "o"
                    }
                    out += stem + "o"
                    out += stem + "e"
                    out += stem + "a"
                }
                'e' -> {
                    val stem = word.dropLast(1)
                    when {
                        word.endsWith("che") -> out += stem.dropLast(1) + "ca"
                        word.endsWith("ghe") -> out += stem.dropLast(1) + "ga"
                    }
                    out += stem + "a"
                }
            }
        }

        //diminutivi accrescitivi
        if (n > 6) {
            for (suffix in listOf("ino", "ina", "etto", "etta", "one", "ona")) {
                if (word.endsWith(suffix)) {
                    val stem = word.dropLast(suffix.length)
                    out += stem + "o"
                    out += stem + "a"
                    out += stem + "e"
                }
            }
        }

        return out
    }

    companion object {
        private const val ASSET = "lemmi_it.tsv.gz"

        //carico la tabella fuori dal thread principale, conservo un'unica istanza per l'intero ciclo di viita dell'app
        suspend fun load(context: Context, asset: String = ASSET): Lemmatizer =
            withContext(Dispatchers.IO) {
                val map = HashMap<String, String>(400_000)
                val rawStream = try {
                    context.assets.open(asset)
                } catch (e: FileNotFoundException) {
                    if (asset.endsWith(".gz")) {
                        context.assets.open(asset.removeSuffix(".gz"))
                    } else {
                        throw e
                    }
                }

                val buffered = rawStream.buffered(65536)
                buffered.mark(2)
                val b1 = buffered.read()
                val b2 = buffered.read()
                buffered.reset()

                val isGzip = (b1 == 0x1f && b2 == 0x8b)
                val inputStream = if (isGzip) GZIPInputStream(buffered, 65536) else buffered

                inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val tab = line.indexOf('\t')
                        if (tab > 0) {
                            val form = line.substring(0, tab).trim().lowercase(Locale.ITALIAN)
                            val lemma = line.substring(tab + 1).trim().lowercase(Locale.ITALIAN)
                            if (form.isNotEmpty() && lemma.isNotEmpty()) {
                                map[form] = lemma
                            }
                        }
                    }
                }
                Lemmatizer(map)
            }
    }
}
