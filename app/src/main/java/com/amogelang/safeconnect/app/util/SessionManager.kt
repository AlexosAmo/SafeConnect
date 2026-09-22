package com.amogelang.safeconnect.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.amogelang.safeconnect.app.model.UserProfile
import com.google.gson.Gson
import androidx.core.content.edit

/**
 * Stores the session token and cached user profile in
 * EncryptedSharedPreferences, backed by the Android Keystore, instead of
 * plain SharedPreferences. This is the on-device half of the "encrypt the
 * user's data" requirement — the password itself is never stored on the
 * device at all; only the server-issued session token is kept here.
 */
class SessionManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "safeconnect_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val gson = Gson()

    fun saveSession(token: String, user: UserProfile) {
        prefs.edit {
            putString(KEY_TOKEN, token)
                .putString(KEY_USER, gson.toJson(user))
        }
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getUser(): UserProfile? {
        val json = prefs.getString(KEY_USER, null) ?: return null
        return gson.fromJson(json, UserProfile::class.java)
    }

    fun updateUser(user: UserProfile) {
        prefs.edit { putString(KEY_USER, gson.toJson(user)) }
    }

    fun isLoggedIn(): Boolean = getToken() != null

    fun clearSession() {
        prefs.edit { clear() }
    }

    fun bearerToken(): String = "Bearer ${getToken().orEmpty()}"

    companion object {
        private const val KEY_TOKEN = "session_token"
        private const val KEY_USER = "cached_user_profile"
    }
}
