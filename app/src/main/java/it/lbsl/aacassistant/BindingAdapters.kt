package it.lbsl.aacassistant

import android.view.View
import androidx.databinding.BindingAdapter

//permette di legare la visibilità di una vista a un booleano direttamente nel layout
@BindingAdapter("isVisible")
fun View.bindIsVisible(visible: Boolean?) {
    visibility = if (visible == true) View.VISIBLE else View.GONE
}
