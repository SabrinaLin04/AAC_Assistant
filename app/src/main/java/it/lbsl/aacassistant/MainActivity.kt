package it.lbsl.aacassistant

import androidx.activity.enableEdgeToEdge
import androidx.core.view.updatePadding
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import androidx.navigation.NavController
import androidx.navigation.navOptions
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import it.lbsl.aacassistant.databinding.ActivityMainBinding
import androidx.navigation.ui.navigateUp
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val llmViewModel: LlmViewModel by viewModels()
    private var caregiverConfirmed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        caregiverConfirmed = savedInstanceState?.getBoolean(KEY_CAREGIVER_CONFIRMED) ?: false

        setupWindowInsets()
        setSupportActionBar(binding.toolbar)
        setupNavigation()

        setupDrawerBack()
        schedulePictogramPrefetch()

        //il modello si carica all'avvio, mentre l'utente sceglie il contesto nella schermata iniziale
        if (llmViewModel.modelState.value is ModelState.Idle) {
            llmViewModel.loadModel(applicationContext)
        }
    }

    //pianifica un task in background per precaricare i pittogrammi quando il dispositivo è connesso a internet per ottimizzare le prestazioni
    private fun schedulePictogramPrefetch() {
        val prefetch = OneTimeWorkRequestBuilder<PictogramPrefetchWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

        WorkManager.getInstance(this)
            .enqueueUniqueWork("pictogram_prefetch", ExistingWorkPolicy.KEEP, prefetch)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.toolbar.updatePadding(
                top = systemBars.top,
                left = systemBars.left,
                right = systemBars.right
            )

            insets
        }
    }

    //configura il sistema di navigazione collegando il nav controller alla toolbar e al menu laterale per gestire gli spostamenti tra i vari fragment
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        //contesti, suggerimenti e frasi salvate mostrano il menu; le altre schermate la freccia indietro
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.contextsFragment, R.id.suggestFragment, R.id.favoritesFragment),
            binding.drawerLayout
        )

        binding.toolbar.setupWithNavController(navController, appBarConfiguration)
        binding.drawerMenuView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.switchModel -> confirmCaregiver { showModelSwitchDialog() }
                R.id.promptsFragment -> confirmCaregiver { navigateFromDrawer(R.id.promptsFragment) }
                else -> navigateFromDrawer(item.itemId)
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            //la voce evidenziata la decide il cambio di schermata, non il tocco:
            //una conferma annullata non deve lasciare selezionata una voce
            false
        }

        //listener per ogni cambio di destinazione, abilita la chiusura automatica del drawer
        //una volta selezionata una nuova destinazione
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.drawerMenuView.setCheckedItem(destination.id)
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        setupDrawerHeader()
        setupLogoutRow()
        setupProfileRow()
    }

    //le voci del cassetto ripartono dalla scelta del contesto, che resta sempre in fondo allo stack.
    //Niente salvataggio degli stack come in NavigationUI: la chat si apre sopra i contesti,
    //e ripristinare lo stack dei contesti riaprirebbe la chat
    private fun navigateFromDrawer(destinationId: Int) {
        if (destinationId == R.id.contextsFragment) {
            navController.popBackStack(R.id.contextsFragment, false)
            return
        }
        navController.navigate(
            destinationId,
            null,
            navOptions {
                launchSingleTop = true
                popUpTo(R.id.contextsFragment) { inclusive = false }
            }
        )
    }

    //il tasto indietro chiude il cassetto quando è aperto, altrimenti torna alla navigazione normale
    private fun setupDrawerBack() {
        val closeDrawer = onBackPressedDispatcher.addCallback(this, enabled = false) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                closeDrawer.isEnabled = true
            }

            override fun onDrawerClosed(drawerView: View) {
                closeDrawer.isEnabled = false
            }
        })
    }

    //mostra nel cassetto l'account su cui vengono sincronizzate le frasi
    private fun setupDrawerHeader() {
        val email = FirebaseAuth.getInstance().currentUser?.email
        binding.navHeader.headerEmail.text = email
        binding.navHeader.headerEmail.isVisible = !email.isNullOrBlank()
    }

    private fun showModelSwitchDialog() {
        val models = llmViewModel.availableModels

        if (models.size < 2) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_switch_model)
                .setMessage(
                    getString(
                        R.string.model_switch_unavailable_message,
                        models.firstOrNull()?.label ?: getString(R.string.model_switch_none)
                    )
                )
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val current = llmViewModel.currentModel
        val names = models.map {
            if (it == current) getString(R.string.model_switch_current, it.label) else it.label
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.model_switch_title)
            .setItems(names) { _, which ->
                val chosen = models[which]
                if (chosen != current) {
                    llmViewModel.switchModel(chosen)
                }
            }
            .show()
    }
    private fun setupProfileRow() {
        binding.profileRow.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            navigateFromDrawer(R.id.profileFragment)
        }
    }
    private fun setupLogoutRow() {
        binding.logoutRow.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            confirmLogout()
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

    //le impostazioni che cambiano il comportamento dell'app chiedono conferma, una volta per sessione
    private fun confirmCaregiver(action: () -> Unit) {
        if (caregiverConfirmed) {
            action()
            return
        }
        showConfirmationDialog(
            title = getString(R.string.caregiver_confirm_title),
            message = getString(R.string.caregiver_confirm_message),
            positiveButtonText = getString(R.string.action_continue),
            negativeButtonText = getString(R.string.action_cancel),
            onConfirm = {
                caregiverConfirmed = true
                action()
            }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_CAREGIVER_CONFIRMED, caregiverConfirmed)
    }

    //crea e mostra un dialog di conferma per l'uscita dall'account
    private fun confirmLogout() {
        showConfirmationDialog(
            title = getString(R.string.logout_confirm_title),
            message = getString(R.string.logout_confirm_message),
            positiveButtonText = getString(R.string.action_logout),
            negativeButtonText = getString(R.string.action_cancel),
            onConfirm = { logout() }
        )
    }

    //esegue la disconnessione dell'utente tramite firebase auth e lo reindirizza alla schermata di benvenuto ripulendo lo stack di navigazione
    private fun logout() {
        AuthUI.getInstance()
            .signOut(this)
            .addOnCompleteListener {
                val intent = Intent(this, WelcomeActivity::class.java)
                intent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
    }

    private companion object {
        const val KEY_CAREGIVER_CONFIRMED = "caregiver_confirmed"
    }
}
