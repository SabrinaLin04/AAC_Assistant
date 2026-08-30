package it.lbsl.aacassistant

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.ErrorCodes
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth
import it.lbsl.aacassistant.databinding.ActivityWelcomeBinding
import kotlinx.coroutines.launch
import androidx.core.view.updatePadding

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private var isNavigating = false
    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { res ->
        this.onSignInResult(res)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // La fascia viola si estende sotto la status bar,
            // ma il logo resta dentro l'area sicura
            binding.appLogo.updatePadding(top = systemBars.top / 2)

            // Il bottone sta sopra la barra di navigazione
            binding.signInButton.updatePadding(bottom = systemBars.bottom)

            insets
        }
        binding.signInButton.setOnClickListener {
            startSignInFlow()
        }
    }

    override fun onStart() {
        super.onStart()
        if (FirebaseAuth.getInstance().currentUser != null) {
            navigateToMain()
        }
    }

    private fun startSignInFlow() {
        setLoading(true)
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build(),
        )
        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .setTheme(R.style.Theme_AACAssistant_FirebaseUI)
            .setLogo(R.mipmap.ic_launcher_round)
            .build()

        signInLauncher.launch(signInIntent)
    }
    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        setLoading(false)
        if (FirebaseAuth.getInstance().currentUser != null) {
            navigateToMain()
            return
        }
        val response = result.idpResponse
        if (response == null) return
        val message = when (response.error?.errorCode) {
            ErrorCodes.NO_NETWORK -> getString(R.string.no_network)
            else -> getString(R.string.sign_in_error, response.error?.message.orEmpty())
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun navigateToMain() {
        if (isNavigating || isFinishing) return
        isNavigating = true
        lifecycleScope.launch {
            try {
                FirestoreRepository().createOrUpdateProfile()
            } catch (e: Exception) {
                Log.w("WelcomeActivity", "Profile sync failed", e)
            }
            startActivity(Intent(this@WelcomeActivity, MainActivity::class.java))
            finish()
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.signInButton.visibility = if (loading) View.INVISIBLE else View.VISIBLE
    }
}