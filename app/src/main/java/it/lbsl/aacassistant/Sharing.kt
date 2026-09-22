package it.lbsl.aacassistant

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withTranslation
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

//l'immagine esce dall'app e finisce su telefoni con temi qualsiasi: colori fissi, non del tema
private const val WIDTH = 1080
private const val PADDING = 48
private const val PICTOGRAM = 240
private const val GAP = 24
private const val TEXT_SIZE = 64f
private const val TEXT_COLOR = 0xFF2B2B2B.toInt()

//manda la frase a un'altra app come immagine: pittogrammi in fila e testo sotto, così chi
//la riceve vede la stessa cosa che vede l'interlocutore davanti al telefono
suspend fun Context.sharePhrase(text: String, pictograms: List<WordPictogram>) {
    val file = renderPhrase(text, pictograms)
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        //WhatsApp e simili usano il testo come didascalia dell'immagine
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
}

//disegna la frase su una tela bianca e la salva come PNG nella cache
private suspend fun Context.renderPhrase(text: String, pictograms: List<WordPictogram>): File {
    val images = pictograms.mapNotNull { pictogram ->
        val request = ImageRequest.Builder(this)
            .data(PictogramRepository.imageSource(this, pictogram.pictogramId))
            .allowHardware(false) //serve un bitmap da disegnare, non una texture
            .build()
        imageLoader.execute(request).drawable?.toBitmap(PICTOGRAM, PICTOGRAM)
    }

    val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        textSize = TEXT_SIZE
        typeface = Typeface.DEFAULT_BOLD
    }
    val textLayout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, WIDTH - PADDING * 2)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .build()

    val perRow = maxOf(1, (WIDTH - PADDING * 2 + GAP) / (PICTOGRAM + GAP))
    val rows = if (images.isEmpty()) 0 else (images.size + perRow - 1) / perRow
    val pictogramsHeight = if (rows == 0) 0 else rows * PICTOGRAM + (rows - 1) * GAP + GAP * 2

    val bitmap = createBitmap(WIDTH, PADDING * 2 + pictogramsHeight + textLayout.height)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    //ogni riga di pittogrammi resta centrata, anche l'ultima se è incompleta
    images.forEachIndexed { index, image ->
        val row = index / perRow
        val inRow = index % perRow
        val inThisRow = minOf(images.size - row * perRow, perRow)
        val rowWidth = inThisRow * PICTOGRAM + (inThisRow - 1) * GAP
        canvas.drawBitmap(
            image,
            (WIDTH - rowWidth) / 2f + inRow * (PICTOGRAM + GAP),
            (PADDING + row * (PICTOGRAM + GAP)).toFloat(),
            null
        )
    }

    canvas.withTranslation(PADDING.toFloat(), (PADDING + pictogramsHeight).toFloat()) {
        textLayout.draw(this)
    }

    val file = File(File(cacheDir, "condivisioni").apply { mkdirs() }, "frase.png")
    withContext(Dispatchers.IO) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    return file
}
