package com.amogelang.safeconnect.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.LocaleListCompat
import com.amogelang.safeconnect.app.firebase.FirebaseAuthRepository
import com.amogelang.safeconnect.app.util.LanguageHelper

/**
 * Launcher activity, matching the "Splash / Language Selection" screen from
 * the Part 1 design doc. Returning, already-signed-in users skip straight
 * to Home — their language preference was already applied on a previous
 * run and AppCompat persists that choice automatically across restarts.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val authRepository = FirebaseAuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (authRepository.isLoggedIn()) {
            goToHome()
            return
        }

        setContentView(R.layout.activity_splash)

        val radioGroup = findViewById<RadioGroup>(R.id.radioGroupLanguage)

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
