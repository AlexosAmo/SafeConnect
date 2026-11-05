package com.amogelang.safeconnect.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth

class BiometricUnlockActivity : AppCompatActivity() {
    private var promptStarted = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FirebaseAuth.getInstance().currentUser == null) { openLogin(); return }
        setContentView(R.layout.activity_biometric_unlock)
        findViewById<TextView>(R.id.tvBiometricStatus).text = getString(R.string.biometric_unlock_description)
        findViewById<Button>(R.id.btnUsePassword).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            BiometricSessionManager.setEnabled(this, false)
            openLogin()
        }
        if (savedInstanceState == null) authenticate()
        findViewById<Button>(R.id.btnTryAgain).setOnClickListener { authenticate() }
    }
    private fun authenticate() {
        if (promptStarted) return
        promptStarted = true
        if (!BiometricSessionManager.isAvailable(this)) {
            promptStarted = false
            findViewById<TextView>(R.id.tvBiometricStatus).text = getString(R.string.biometric_unavailable)
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                startActivity(Intent(this@BiometricUnlockActivity, HomeActivity::class.java))
                finish()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { promptStarted = false }
            override fun onAuthenticationFailed() { promptStarted = false }
        })
        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            promptBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            promptBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText(getString(R.string.not_now))
        }
        prompt.authenticate(promptBuilder.build())
    }
    private fun openLogin() { startActivity(Intent(this, LoginActivity::class.java)); finish() }
}
