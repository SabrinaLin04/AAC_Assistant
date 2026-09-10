package it.lbsl.aacassistant

import android.view.View
import androidx.databinding.BindingAdapter

//lego la visibilita' di un view direttamente a un boolean nel layout xml
@BindingAdapter("isVisible")
fun View.bindIsVisible(visible: Boolean?) {
    visibility = if (visible == true) View.VISIBLE else View.GONE
}
