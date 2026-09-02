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
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import it.lbsl.aacassistant.databinding.ActivityMainBinding
import androidx.navigation.ui.navigateUp
import com.firebase.ui.auth.AuthUI
import androidx.appcompat.app.AlertDialog
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
        binding.drawerMenuView.setupWithNavController(navController)

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
        val dialog = MaterialAlertDialogBuilder(
            this,
            R.style.ThemeOverlay_AACAssistant_Dialog
        )
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setPositiveButton(R.string.action_logout) { _, _ -> logout() }
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            val exitButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE) as? com.google.android.material.button.MaterialButton
            val cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE) as? com.google.android.material.button.MaterialButton

            exitButton?.apply {
                backgroundTintList = ContextCompat.getColorStateList(context, R.color.m_primary)
                setTextColor(ContextCompat.getColor(context, R.color.m1_primary))
                cornerRadius = dpToPx(22)
                insetTop = 0
                insetBottom = 0
            }

            cancelButton?.apply {
                backgroundTintList = ContextCompat.getColorStateList(context, android.R.color.transparent)
                setTextColor(ContextCompat.getColor(context, R.color.m_primary))
                strokeColor = ContextCompat.getColorStateList(context, R.color.m_primary)
                strokeWidth = dpToPx(1)
                cornerRadius = dpToPx(22)
                insetTop = 0
                insetBottom = 0
            }

            listOfNotNull(exitButton, cancelButton).forEach { button ->
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

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

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
