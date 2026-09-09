package it.lbsl.aacassistant

import android.content.Context
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE) as? MaterialButton
        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE) as? MaterialButton

        val density = resources.displayMetrics.density
        fun dpToPx(dp: Int): Int = (dp * density).toInt()

        positiveButton?.apply {
            backgroundTintList = ContextCompat.getColorStateList(context, R.color.m_primary)
            setTextColor(ContextCompat.getColor(context, R.color.m1_primary))
            cornerRadius = dpToPx(22)
            insetTop = 0
            insetBottom = 0
        }

        negativeButton?.apply {
            backgroundTintList = ContextCompat.getColorStateList(context, android.R.color.transparent)
            setTextColor(ContextCompat.getColor(context, R.color.m_primary))
            strokeColor = ContextCompat.getColorStateList(context, R.color.m_primary)
            strokeWidth = dpToPx(1)
            cornerRadius = dpToPx(22)
            insetTop = 0
            insetBottom = 0
        }

        listOfNotNull(positiveButton, negativeButton).forEach { button ->
            button.isAllCaps = false
            button.minHeight = dpToPx(44)
            button.setPadding(dpToPx(20), 0, dpToPx(20), 0)

            (button.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                params.width = 0
                params.weight = 1f
                params.marginStart = dpToPx(6)
                params.marginEnd = dpToPx(6)
                button.layoutParams = params
            }
        }
    }

    dialog.show()
}
