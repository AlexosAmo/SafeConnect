package com.amogelang.safeconnect.app.firebase

import com.google.firebase.auth.FirebaseAuth

sealed class FirebaseResult<out T> {
    data class Success<T>(val data: T) : FirebaseResult<T>()
    data class Failure(val message: String) : FirebaseResult<Nothing>()
}

/**
 * Wraps Firebase Authentication + the matching Firestore profile document.
 * Firebase Auth itself hashes and stores the password (using script) on
 * Google's servers — the app never sees, stores, or transmits it in any
 * form other than the initial HTTPS call to createUserWithEmailAndPassword
 * / signInWithEmailAndPassword.
 */
class FirebaseAuthRepository {

    private val auth = FirebaseAuth.getInstance()

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun currentUserId(): String? = auth.currentUser?.uid

}