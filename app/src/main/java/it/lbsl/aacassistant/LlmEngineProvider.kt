package it.lbsl.aacassistant

import com.google.ai.edge.litertlm.Backend
import java.io.File

data class ModelInfo(
    val filename: String,
    val label: String,
    val backend: Backend = Backend.GPU()
)

//gestisce il rilevamento e la selezione dei modelli linguistici fisicamente disponibili all'interno dello spazio di archiviazione dell'applicazione
class LlmEngineProvider(private val filesDir: File) {

    private val knownModels = listOf(
        ModelInfo("gemma3-1b-it-int4.litertlm", "Gemma 3 1B INT4"),
        ModelInfo("gemma-4-E2B-it.litertlm", "Gemma 4 E2B")
    )

    fun availableModels(): List<ModelInfo> =
        knownModels.filter { File(filesDir, it.filename).exists() }

    var selected: ModelInfo? = availableModels().firstOrNull()
        private set

    //cerca i modelli: prima in filesDir, se non c'è prova a copiarlo da /data/local/tmp/
    fun discoverModels(): List<ModelInfo> {
        return knownModels.filter { model ->
            val target = File(filesDir, model.filename)
            if (target.exists()) return@filter true

            val source = File("/data/local/tmp/${model.filename}")
            source.exists()
        }
    }

    //copia il modello da /data/local/tmp/ a filesDir se necessario restituisce il path finale, o null se il file non esiste da nessuna parte
    fun prepareModel(model: ModelInfo): String? {
        val target = File(filesDir, model.filename)
        if (target.exists()) {
            selected = model
            return target.absolutePath
        }
         val source = File("/data/local/tmp/${model.filename}")
        if (!source.exists()) return null
        source.copyTo(target)
        selected = model
        return target.absolutePath
    }

    fun select(model: ModelInfo) {
        selected = model
    }

}
