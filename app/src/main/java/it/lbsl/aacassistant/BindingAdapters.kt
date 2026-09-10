package it.lbsl.aacassistant

import android.view.View
import android.widget.TextView
import androidx.databinding.BindingAdapter

//lego la visibilita' di un view direttamente a un boolean nel layout xml
@BindingAdapter("isVisible")
fun View.bindIsVisible(visible: Boolean?) {
    visibility = if (visible == true) View.VISIBLE else View.GONE
}


@BindingAdapter("isGone")
fun View.bindIsGone(gone: Boolean?) {
    visibility = if (gone == true) View.GONE else View.VISIBLE
}

@BindingAdapter("bubbleForAuthor")
fun bindBubbleBackground(view: TextView, author: String?) {
    val l = view.paddingLeft
    val t = view.paddingTop
    val r = view.paddingRight
    val b = view.paddingBottom

    view.setBackgroundResource(
        if (author == "user") R.drawable.bg_bubble_user
        else R.drawable.bg_bubble_assistant
    )

    view.setPadding(l, t, r, b)
}