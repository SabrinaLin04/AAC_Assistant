package it.lbsl.aacassistant

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

data class MetricsEntry(
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("ttft_ms") val ttftMs: Long,
    @SerializedName("total_ms") val totalMs: Long,
    @SerializedName("n_chars") val nChars: Int,
    @SerializedName("n_chunks") val nChunks: Int,
    @SerializedName("char_s") val charPerSec: Double,
    @SerializedName("backend") val backend: String,
    @SerializedName("model") val model: String,
    @SerializedName("context_id") val contextId: String
)

//gestisce la creazione della cartella e la scrittura dei log
class MetricsLogger(filesDir: File) {
    private val metricsDir = File(filesDir, "metrics").apply { mkdirs() }
    private val gson = Gson()

    //una riga JSON per generazione: il formato jsonl si legge da pandas senza conversioni
    fun log(entry: MetricsEntry) {
        val file = File(metricsDir, "llm_metrics.jsonl")
        file.appendText(gson.toJson(entry) + "\n")
    }
}
