package com.amogelang.safeconnect.app.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreIncidentRepository {

    private val firestore = FirebaseFirestore.getInstance()

    suspend fun reportSos(uid: String, latitude: Double, longitude: Double): FirebaseResult<String> {
        return try {
            val incident = mapOf(
                "userId" to uid,
                "type" to "sos",
                "latitude" to latitude,
                "longitude" to longitude,
                "status" to "sent",
                "createdAt" to FieldValue.serverTimestamp()
            )
            // Firestore's offline persistence is on by default on Android:
            // if the device has no connection right now, this write is
            // queued locally and sent automatically once it reconnects —
            // which happens to satisfy the "offline mode with sync" PoE
            // requirement essentially for free.
            val ref = firestore.collection("incidents").add(incident).await()
            FirebaseResult.Success(ref.id)
        } catch (e: Exception) {
            FirebaseResult.Failure(e.message ?: "Could not send alert")
        }
    }

    fun cancelIncident() {}
}
