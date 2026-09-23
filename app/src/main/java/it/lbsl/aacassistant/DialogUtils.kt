package it.lbsl.aacassistant

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.snackbar.Snackbar

//i pulsanti dei dialog: pieno quello principale, contornato l'altro, tutti grandi da toccare
fun AlertDialog.styleCustomButtons(
    positiveColorRes: Int = R.color.aac_primary,
    positiveTextColorRes: Int = R.color.aac_on_primary
) {
    val positiveButton = getButton(AlertDialog.BUTTON_POSITIVE) as? MaterialButton
    val negativeButton = getButton(AlertDialog.BUTTON_NEGATIVE) as? MaterialButton
    val neutralButton = getButton(AlertDialog.BUTTON_NEUTRAL) as? MaterialButton

    val density = context.resources.displayMetrics.density
    fun dpToPx(dp: Int): Int = (dp * density).toInt()

    positiveButton?.apply {
        backgroundTintList = ContextCompat.getColorStateList(context, positiveColorRes)
        setTextColor(ContextCompat.getColor(context, positiveTextColorRes))
        cornerRadius = dpToPx(22)
        insetTop = 0
        insetBottom = 0
    }

    negativeButton?.apply {
        backgroundTintList = ContextCompat.getColorStateList(context, android.R.color.transparent)
        setTextColor(ContextCompat.getColor(context, R.color.aac_primary))
        strokeColor = ContextCompat.getColorStateList(context, R.color.aac_primary)
        strokeWidth = dpToPx(1)
        cornerRadius = dpToPx(22)
        insetTop = 0
        insetBottom = 0
    }

    neutralButton?.apply {
        setTextColor(ContextCompat.getColor(context, R.color.aac_primary))
        cornerRadius = dpToPx(22)
        insetTop = 0
        insetBottom = 0
    }

    listOfNotNull(positiveButton, negativeButton, neutralButton).forEach { button ->
        button.isAllCaps = false
        button.minHeight = context.resources.getDimensionPixelSize(R.dimen.touch_target_min)
        button.setPadding(dpToPx(16), 0, dpToPx(16), 0)

        (button.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.width = LinearLayout.LayoutParams.WRAP_CONTENT
            params.weight = 0f
            params.marginStart = dpToPx(4)
            params.marginEnd = dpToPx(4)
            button.layoutParams = params
        }
    }
}

//chiede conferma prima di un'azione che non si può annullare
fun Context.showConfirmationDialog(
    title: String,
    message: String,
    positiveButtonText: String = getString(R.string.action_delete),
    negativeButtonText: String = getString(R.string.action_cancel),
    onConfirm: () -> Unit,
    onCancel: () -> Unit = {}
) {
    val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_AACAssistant_Dialog)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(positiveButtonText) { _, _ -> onConfirm() }
        .setNegativeButton(negativeButtonText) { _, _ -> onCancel() }
        .setOnCancelListener { onCancel() }
        .create()

    dialog.setOnShowListener {
        dialog.styleCustomButtons()
    }

    dialog.show()
}

//sfondo arrotondato dietro la parola in lettura: sborda sugli spazi accanto invece di
//allargare la parola, così le altre non si spostano mentre la lettura avanza
private class PillSpan(private val background: Int, private val textColor: Int) : ReplacementSpan() {

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?) =
        paint.measureText(text, start, end).toInt()

    override fun draw(
        canvas: Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val padding = paint.textSize / 4f
        val pill = RectF(x - padding, top.toFloat(), x + getSize(paint, text, start, end, null) + padding, bottom.toFloat())
        val original = paint.color

        paint.color = background
        canvas.drawRoundRect(pill, pill.height() / 2f, pill.height() / 2f, paint)
        paint.color = textColor
        canvas.drawText(text, start, end, x, y.toFloat(), paint)
        paint.color = original
    }
}

//finestra della frase, la vista che si gira verso l'interlocutore.
//Resta in mano a chi la apre per poter evidenziare la parola mentre viene letta
class SpeakDialog internal constructor(
    private val dialog: AlertDialog,
    private val speakText: TextView,
    private val scroll: HorizontalScrollView,
    private val text: String,
    private val shown: List<Pair<WordPictogram, ImageView>>,
    private val highlightColor: Int,
    private val onHighlightColor: Int
) {

    //evidenzia la parola in lettura e il suo pittogramma. Le parole senza pittogramma, come
    //articoli e preposizioni, lasciano acceso quello di prima invece di spegnere tutto
    fun highlight(range: IntRange?) {
        if (range == null) {
            clear()
            return
        }
        if (range.first < 0 || range.last >= text.length) return

        //il motore vocale pronuncia "d'acqua" come una parola sola, mentre il pittogramma
        //è associato ad "acqua": si confrontano le parole contenute, non l'intero intervallo
        val words = wordsOf(text.substring(range.first, range.last + 1))
        val match = shown.firstOrNull { (pictogram, _) -> pictogram.word in words } ?: return

        speakText.text = SpannableString(text).apply {
            setSpan(
                PillSpan(highlightColor, onHighlightColor),
                range.first,
                range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        shown.forEach { (_, image) -> image.setBackgroundResource(0) }
        match.second.setBackgroundResource(R.drawable.bg_pictogram_highlight)
        //con molti pittogrammi quello in lettura potrebbe essere fuori dallo schermo
        scroll.smoothScrollTo(match.second.left, 0)
    }

    //a fine lettura la frase torna com'era
    private fun clear() {
        speakText.text = text
        shown.forEach { (_, image) -> image.setBackgroundResource(0) }
    }

    fun onDismiss(action: () -> Unit) {
        dialog.setOnDismissListener { action() }
    }
}

//mostra il testo ingrandito con i suoi pittogrammi.
//onRepeat aggiunge il pulsante "Ripeti", che fa ridire la frase senza chiudere il dialog
fun Context.showSpeakDialog(
    text: String,
    pictograms: List<WordPictogram>,
    onRepeat: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null
): SpeakDialog {
    val view = LayoutInflater.from(this).inflate(R.layout.dialog_speak, null)

    val speakText = view.findViewById<TextView>(R.id.speakText)
    speakText.text = text

    val share = view.findViewById<MaterialButton>(R.id.shareButton)
    share.visibility = if (onShare == null) View.GONE else View.VISIBLE
    share.setOnClickListener { onShare?.invoke() }

    val row = view.findViewById<LinearLayout>(R.id.pictogramRow)
    val scroll = view.findViewById<HorizontalScrollView>(R.id.pictogramScroll)

    val shown = mutableListOf<Pair<WordPictogram, ImageView>>()

    if (pictograms.isEmpty()) {
        scroll.visibility = View.GONE
    } else {
        scroll.visibility = View.VISIBLE
        val size = resources.getDimensionPixelSize(R.dimen.pictogram_max_size)
        val gap = resources.getDimensionPixelSize(R.dimen.pictogram_gap)

        pictograms.forEach { pictogram ->
            val image = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = gap
                    marginEnd = gap
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                load(PictogramRepository.imageSource(this@showSpeakDialog, pictogram.pictogramId))
            }
            row.addView(image)
            shown += pictogram to image
        }

        row.contentDescription = text
    }

    val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_AACAssistant_Dialog)
        .setView(view)
        .setPositiveButton(R.string.action_close, null)
        .apply { if (onRepeat != null) setNeutralButton(R.string.action_repeat, null) }
        .create()

    val shape = MaterialShapeDrawable().apply {
        fillColor = ColorStateList.valueOf(
            ContextCompat.getColor(this@showSpeakDialog, R.color.aac_surface_container)
        )
        setCornerSize(28 * resources.displayMetrics.density)
    }
    dialog.window?.setBackgroundDrawable(shape)

    dialog.setOnShowListener {
        dialog.styleCustomButtons()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { onRepeat?.invoke() }
    }

    dialog.show()

    return SpeakDialog(
        dialog = dialog,
        speakText = speakText,
        scroll = scroll,
        text = text,
        shown = shown,
        highlightColor = ContextCompat.getColor(this, R.color.aac_highlight),
        onHighlightColor = ContextCompat.getColor(this, R.color.aac_on_highlight)
    )
}

//scorrere una riga di lato la elimina: la conferma la chiede la schermata che usa la lista
fun RecyclerView.onSwipe(onSwiped: (position: Int) -> Unit) {
    val callback = object : ItemTouchHelper.SimpleCallback(
        0,
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
    ) {
        override fun onMove(
            rv: RecyclerView,
            vh: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ) = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) =
            onSwiped(viewHolder.bindingAdapterPosition)
    }
    ItemTouchHelper(callback).attachToRecyclerView(this)
}

//colori della barra che permette di annullare una cancellazione, uguali in tutte le schermate
fun Snackbar.withUndoColors(): Snackbar = apply {
    setBackgroundTint(ContextCompat.getColor(context, R.color.aac_undo_container))
    setTextColor(ContextCompat.getColor(context, R.color.aac_on_undo_container))
    setActionTextColor(ContextCompat.getColor(context, R.color.aac_primary_container))
}
