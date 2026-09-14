package it.lbsl.aacassistant

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable

fun AlertDialog.styleCustomButtons(
    positiveColorRes: Int = R.color.aac_primary,
    positiveTextColorRes: Int = R.color.aac_on_primary
) {
    val positiveButton = getButton(AlertDialog.BUTTON_POSITIVE) as? MaterialButton
    val negativeButton = getButton(AlertDialog.BUTTON_NEGATIVE) as? MaterialButton

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

    listOfNotNull(positiveButton, negativeButton).forEach { button ->
        button.isAllCaps = false
        button.minHeight = dpToPx(44)
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

//mostra un dialog di conferma personalizzato con uno stile grafico coerente per le azioni di eliminazione o disconnessione
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

//mostra il testo ingrandito con i suoi pittogrammi, la vista che si gira verso l'interlocutore
fun Context.showSpeakDialog(text: String, pictogramIds: List<Int>) {
    val view = LayoutInflater.from(this).inflate(R.layout.dialog_speak, null)

    view.findViewById<TextView>(R.id.speakText).text = text

    val row = view.findViewById<LinearLayout>(R.id.pictogramRow)
    val scroll = view.findViewById<HorizontalScrollView>(R.id.pictogramScroll)

    row.removeAllViews()

    if (pictogramIds.isEmpty()) {
        scroll.visibility = View.GONE
    } else {
        scroll.visibility = View.VISIBLE
        val size = resources.getDimensionPixelSize(R.dimen.pictogram_max_size)
        val gap = resources.getDimensionPixelSize(R.dimen.pictogram_gap)

        pictogramIds.forEach { id ->
            val image = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = gap
                    marginEnd = gap
                }
                load(PictogramRepository.imageSource(this@showSpeakDialog, id))
            }
            row.addView(image)
        }

        row.contentDescription = text
    }

    val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_AACAssistant_Dialog)
        .setView(view)
        .setPositiveButton(R.string.action_close, null)
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
    }

    dialog.show()
}