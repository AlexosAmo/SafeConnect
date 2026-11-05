package com.amogelang.safeconnect.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.amogelang.safeconnect.app.firebase.FirebaseAuthRepository
import com.amogelang.safeconnect.app.util.LanguageHelper

/**
 * Launcher activity, matching the "Splash / Language Selection" screen from
 * the Part 1 design doc. Returning, already-signed-in users skip straight
 * to Home, or through the optional local biometric gate when it has been
 * enabled on this device. AppCompat persists the language choice across restarts.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val authRepository = FirebaseAuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkTheme = getSharedPreferences("safeconnect_appearance", MODE_PRIVATE).getBoolean("dark_mode", false)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            if (darkTheme) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )

        if (authRepository.isLoggedIn()) {
            if (BiometricSessionManager.isEnabled(this)) {
                startActivity(Intent(this, BiometricUnlockActivity::class.java))
                finish()
            } else goToHome()
            return
        }

        setContentView(R.layout.activity_splash)

        val welcomeContainer = findViewById<android.view.View>(R.id.welcomeContainer)
        val languageContainer = findViewById<android.view.View>(R.id.languageContainer)
        val radioGroup = findViewById<RadioGroup>(R.id.radioGroupLanguage)

        findViewById<Button>(R.id.btnProceedLanguage).setOnClickListener {
            welcomeContainer.visibility = android.view.View.GONE
            languageContainer.visibility = android.view.View.VISIBLE
        }

        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            val langCode = when (radioGroup.checkedRadioButtonId) {
                R.id.radioZulu -> "zu"
                R.id.radioAfrikaans -> "af"
                R.id.radioSesotho -> "st"
                else -> "en"
            }
            LanguageHelper.applyLocale(langCode)
            goToLogin()
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun goToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
