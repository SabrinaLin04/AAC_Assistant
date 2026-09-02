AAC Assistant

L'applicazione integra il modello LLM locale gemma3-1b-it-int4.litertlm per generare suggerimenti di comunicazione in tempo reale. A causa delle dimensioni ridotte necessarie per garantire l'esecuzione direttamente on-device, il modello presenta capacità di ragionamento limitate e le frasi prodotte possono spesso risultare elementari, ripetitive o imprecise.  

Download del Modello

Può scaricare il file del modello necessario al seguente link:
https://huggingface.co/litert-community/Gemma3-1B-IT/blob/main/gemma3-1b-it-int4.litertlm

Installazione del Modello

Per far funzionare la generazione AI, il file deve essere trasferito in un percorso specifico del dispositivo prima di avviare l'inizializzazione. L'applicazione è programmata per cercare il file nella directory temporanea locale e copiarlo in automatico nello spazio di archiviazione interno dell'app.  

    Scaricare il file gemma3-1b-it-int4.litertlm.  

    Collegare il dispositivo Android al PC assicurandoti di avere il Debug USB/WIFI abilitato.

    Su Android Studio aprire l'hamburger in alto a sinistra -> view -> windows tool -> device Explorer
    Si aprirà una schermata con le varie cartelle del telefono, inserire il modello in data/local/tmp

Modalità Demo (Alternativa)

Se si preferisce non scaricare il modello, l'applicazione può comunque essere utilizzata. Se il sistema non rileva il file nel percorso /data/local/tmp/gemma3-1b-it-int4.litertlm durante l'avvio, passerà automaticamente alla Demo Mode.  

In questa modalità di fallback, la generazione tramite AI viene disattivata e l'assistente fornirà esclusivamente risposte e suggerimenti statici preimpostati per simulare il funzionamento in base al contesto selezionato.

Security rules

il file firestore.rules nella root riporta le regole attive sul progetto Firebase. Ogni utente può leggere e scrivere solo il proprio documento users/{uid} e
le relative sottocollezioni.