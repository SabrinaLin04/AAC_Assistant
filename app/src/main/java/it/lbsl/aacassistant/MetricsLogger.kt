package it.lbsl.aacassistant

import org.json.JSONObject
import java.io.File

data class MetricsEntry(
    val timestamp: String,
    val ttftMs: Long,
    val totalMs: Long,
    val nChars: Int,
    val nChunks: Int,
    val charPerSec: Double,
    val backend: String,
    val model: String,
    val contextId: String
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("timestamp", timestamp)
            put("ttft_ms", ttftMs)
            put("total_ms", totalMs)
            put("n_chars", nChars)
            put("n_chunks", nChunks)
            put("char_s", charPerSec)
            put("backend", backend)
            put("model", model)
            put("context_id", contextId)
        }.toString()
    }
}

//gestisce la creazione della cartella e la scrittura dei log
class MetricsLogger(private val filesDir: File) {
    private val metricsDir = File(filesDir, "metrics").apply { mkdirs() }

    fun log(entry: MetricsEntry) {
        val file = File(metricsDir, "llm_metrics.jsonl")
        file.appendText(entry.toJson() + "\n")
    }
}