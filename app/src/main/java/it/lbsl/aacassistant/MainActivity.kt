package it.lbsl.aacassistant

import androidx.activity.enableEdgeToEdge
import androidx.core.view.updatePadding
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import androidx.navigation.NavController
import androidx.activity.addCallback
import androidx.core.view.GravityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import it.lbsl.aacassistant.databinding.ActivityMainBinding
import androidx.navigation.ui.navigateUp
import com.firebase.ui.auth.AuthUI
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this

        setupWindowInsets()
        setSupportActionBar(binding.toolbar)
        setupNavigation()

        onBackPressedDispatcher.addCallback(this) {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
        schedulePictogramPrefetch()
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

        appBarConfiguration = AppBarConfiguration(navController.graph, binding.drawerLayout)

        binding.toolbar.setupWithNavController(navController, appBarConfiguration)
        binding.drawerMenuView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.switchModel -> showModelSwitchDialog()
                else -> androidx.navigation.ui.NavigationUI
                    .onNavDestinationSelected(item, navController)
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        //listener per ogni cambio di destinazione, abilita la chiusura automatica del drawer
        //una volta selezionata una nuova destinazione
        navController.addOnDestinationChangedListener { _, _, _ ->
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        setupLogoutRow()
        setupProfileRow()
    }

    private fun showModelSwitchDialog() {
        val llmViewModel = androidx.lifecycle.ViewModelProvider(this)[LlmViewModel::class.java]
        val models = llmViewModel.getAvailableModels()

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

        val current = llmViewModel.getCurrentModel()
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
            navController.navigate(R.id.profileFragment)
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
}
