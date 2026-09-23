package it.lbsl.aacassistant

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class PictogramPrefetchWorker(context: Context, params: WorkerParameters)
    : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        //i pittogrammi delle parole più comuni, scaricati una volta per averli anche senza rete
        val ids = PictogramRepository.coreIds(applicationContext)
        if (ids.isEmpty()) return@withContext Result.success()

        val dir = PictogramRepository.pictogramDir(applicationContext)
        var failures = 0

        //prima in un file temporaneo e poi rinominato: un download interrotto non lascia immagini rotte
        ids.forEach { id ->
            val file = File(dir, "$id.png")
            if (file.exists()) return@forEach
            try {
                val temp = File(dir, "$id.png.tmp")
                URL(PictogramRepository.imageUrl(id)).openStream().use { input ->
                    temp.outputStream().use { input.copyTo(it) }
                }
                if (!temp.renameTo(file)) temp.delete()
            } catch (e: Exception) {
                failures++
            }
        }

        //se non ne è arrivato nemmeno uno probabilmente manca la rete: si riprova più tardi
        if (failures == ids.size) Result.retry() else Result.success()
    }
}