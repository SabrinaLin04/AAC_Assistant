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
        val ids = PictogramRepository.coreIds(applicationContext)
        if (ids.isEmpty()) return@withContext Result.success()

        val dir= PictogramRepository.pictogramDir(applicationContext)
        var failures= 0

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

        //riproviamo solo se faila tutto
        if (failures == ids.size) Result.retry() else Result.success()
    }
}