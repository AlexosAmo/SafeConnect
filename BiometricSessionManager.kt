package com.amogelang.safeconnect.app

import android.content.Context
import androidx.biometric.BiometricManager

/** BiometricPrompt is a local app-unlock gate for an already-persisted Firebase session.
 * It does not replace Firebase authentication or send biometric data to SafeConnect.
 */
object BiometricSessionManager {
    private const val PREFS = "safeconnect_device_security"
    private const val KEY_ENABLED = "biometric_unlock_enabled"
    fun isEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, enabled: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply() }
    fun isAvailable(context: Context): Boolean {
        val authenticators = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else BiometricManager.Authenticators.BIOMETRIC_STRONG
        return BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
