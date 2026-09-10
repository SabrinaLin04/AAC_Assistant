package it.lbsl.aacassistant

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

//vero solo quando la lista e' vuota E il caricamento e' finito: senza la seconda condizione
//il messaggio "nessun elemento" comparirebbe per un istante a ogni apertura, prima che
//Firestore risponda. Stessa logica per preferiti e contesti, tenuta in un posto solo.
fun emptyStateOf(
    items: LiveData<out List<*>>,
    isLoading: LiveData<Boolean>
): LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
    fun update() {
        value = (items.value?.isEmpty() == true) && (isLoading.value != true)
    }
    addSource(items) { update() }
    addSource(isLoading) { update() }
}
