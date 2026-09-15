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
        ModelInfo("gemma3-1b-it-int4.litertlm", "Gemma 3 1B INT4 GPU", Backend.GPU()),
        ModelInfo("gemma3-1b-it-int4.litertlm", "Gemma 3 1B INT4 CPU", Backend.CPU()),
        ModelInfo("gemma-4-E2B-it.litertlm", "Gemma 4 E2B GPU", Backend.GPU()),
        ModelInfo("gemma-4-E2B-it.litertlm", "Gemma 4 E2B CPU", Backend.CPU()),
    )

    //il modello in uso, valorizzato da prepareModel quando il file è pronto in filesDir
    var selected: ModelInfo? = null
        private set

    //cerca i modelli: prima in filesDir, se non c'è prova a copiarlo da /data/local/tmp/
    fun discoverModels(): List<ModelInfo> =
        knownModels.filter { model ->
            File(filesDir, model.filename).exists() || stagedFile(model).exists()
        }

    //copia il modello da /data/local/tmp/ a filesDir se necessario restituisce il path finale, o null se il file non esiste da nessuna parte
    fun prepareModel(model: ModelInfo): String? {
        val target = File(filesDir, model.filename)
        if (target.exists()) {
            selected = model
            return target.absolutePath
        }

        val source = stagedFile(model)
        if (!source.exists()) return null
        source.copyTo(target)
        selected = model
        return target.absolutePath
    }

    private fun stagedFile(model: ModelInfo) = File(STAGING_DIR, model.filename)

    companion object {
        //i modelli vengono spinti qui con adb push, non essendo distribuibili con l'apk
        private const val STAGING_DIR = "/data/local/tmp"
    }

}
