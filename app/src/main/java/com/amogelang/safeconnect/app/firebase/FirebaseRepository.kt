package com.amogelang.safeconnect.app.firebase

import android.net.Uri
import com.amogelang.safeconnect.app.model.EmergencyContact
import com.amogelang.safeconnect.app.model.Incident
import com.amogelang.safeconnect.app.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

object FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    fun currentUserId(): String? = auth.currentUser?.uid

    suspend fun register(fullName: String, email: String, phone: String, password: String, preferredLanguage: String = "en"): UserProfile {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user ?: error("Could not create the account")
        val profile = UserProfile(
            userId = user.uid,
            fullName = fullName,
            email = email,
            phoneNumber = phone,
            preferredLanguage = preferredLanguage
        )
        db.collection("users").document(user.uid).set(profile).await()
        return profile
    }

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun getProfile(): UserProfile? {
        val uid = currentUserId() ?: return null
        val snapshot = db.collection("users").document(uid).get().await()
        if (!snapshot.exists()) return null
        return runCatching { snapshot.toObject(UserProfile::class.java) }.getOrNull()
            ?: UserProfile(
                userId = snapshot.getString("userId") ?: uid,
                fullName = snapshot.getString("fullName") ?: "",
                email = snapshot.getString("email") ?: "",
                phoneNumber = snapshot.getString("phoneNumber") ?: "",
                preferredLanguage = snapshot.getString("preferredLanguage") ?: "English",
                notificationsEnabled = snapshot.getBoolean("notificationsEnabled") ?: true,
                safetyScore = (snapshot.getLong("safetyScore") ?: 100L).toInt(),
                createdAt = timestampMillis(snapshot.get("createdAt"))
            )
    }

    suspend fun updateProfile(profile: UserProfile) {
        db.collection("users").document(profile.userId).set(profile).await()
    }

    suspend fun createIncident(incident: Incident): String {
        val uid = currentUserId() ?: error("Please sign in first")
        val ref = db.collection("incidents").document()
        val saved = incident.copy(id = ref.id, userId = uid)
        ref.set(saved).await()
        return ref.id
    }

    suspend fun updateIncidentStatus(id: String, status: String) {
        db.collection("incidents").document(id).update("status", status).await()
    }

    suspend fun getIncident(id: String): Incident? {
        val uid = currentUserId() ?: return null
        val snapshot = db.collection("incidents").document(id).get().await()
        if (!snapshot.exists() || snapshot.getString("userId") != uid) return null
        return Incident(
            id = snapshot.id,
            userId = uid,
            type = snapshot.getString("type") ?: "sos",
            category = snapshot.getString("category") ?: "Emergency",
            description = snapshot.getString("description") ?: "",
            latitude = snapshot.getDouble("latitude"),
            longitude = snapshot.getDouble("longitude"),
            status = snapshot.getString("status") ?: "Active",
            responderName = snapshot.getString("responderName") ?: "",
            responderStatus = snapshot.getString("responderStatus") ?: "Alert sent",
            responderLatitude = snapshot.getDouble("responderLatitude"),
            responderLongitude = snapshot.getDouble("responderLongitude"),
            responderUpdatedAt = timestampMillisOrNull(snapshot.get("responderUpdatedAt")),
            createdAt = timestampMillis(snapshot.get("createdAt"))
        )
    }

    fun observeIncident(id: String, onChange: (Incident?) -> Unit, onError: (Exception) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        val uid = currentUserId() ?: return db.collection("incidents").document(id).addSnapshotListener { _, _ -> }
        return db.collection("incidents").document(id).addSnapshotListener { snapshot, error ->
            if (error != null) { onError(error); return@addSnapshotListener }
            if (snapshot == null || !snapshot.exists() || snapshot.getString("userId") != uid) { onChange(null); return@addSnapshotListener }
            onChange(
                Incident(
                    id = snapshot.id,
                    userId = uid,
                    type = snapshot.getString("type") ?: "sos",
                    category = snapshot.getString("category") ?: "Emergency",
                    description = snapshot.getString("description") ?: "",
                    latitude = snapshot.getDouble("latitude"),
                    longitude = snapshot.getDouble("longitude"),
                    status = snapshot.getString("status") ?: "Active",
                    responderName = snapshot.getString("responderName") ?: "",
                    responderStatus = snapshot.getString("responderStatus") ?: "Alert sent",
                    responderLatitude = snapshot.getDouble("responderLatitude"),
                    responderLongitude = snapshot.getDouble("responderLongitude"),
                    responderUpdatedAt = timestampMillisOrNull(snapshot.get("responderUpdatedAt")),
                    createdAt = timestampMillis(snapshot.get("createdAt"))
                )
            )
        }
    }

    suspend fun getMyIncidents(): List<Incident> {
        val uid = currentUserId() ?: return emptyList()
        return db.collection("incidents").whereEqualTo("userId", uid).get().await()
            .documents.mapNotNull { snapshot ->
                runCatching {
                    Incident(
                        id = snapshot.id,
                        userId = snapshot.getString("userId") ?: uid,
                        type = snapshot.getString("type") ?: "sos",
                        category = snapshot.getString("category") ?: "Emergency",
                        description = snapshot.getString("description") ?: "",
                        latitude = snapshot.getDouble("latitude"),
                        longitude = snapshot.getDouble("longitude"),
                        status = snapshot.getString("status") ?: "Active",
                        responderName = snapshot.getString("responderName") ?: "",
                        responderStatus = snapshot.getString("responderStatus") ?: "Alert sent",
                        responderLatitude = snapshot.getDouble("responderLatitude"),
                        responderLongitude = snapshot.getDouble("responderLongitude"),
                        responderUpdatedAt = timestampMillisOrNull(snapshot.get("responderUpdatedAt")),
                        createdAt = timestampMillis(snapshot.get("createdAt"))
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.createdAt }
    }

    suspend fun addEmergencyContact(contact: EmergencyContact) {
        val uid = currentUserId() ?: error("Please sign in first")
        val ref = db.collection("users").document(uid).collection("emergencyContacts").document()
        ref.set(contact.copy(id = ref.id)).await()
    }

    suspend fun getEmergencyContacts(): List<EmergencyContact> {
        val uid = currentUserId() ?: return emptyList()
        return db.collection("users").document(uid).collection("emergencyContacts")
            .orderBy("name").get().await().documents.mapNotNull { it.toObject(EmergencyContact::class.java) }
    }

    suspend fun deleteEmergencyContact(id: String) {
        val uid = currentUserId() ?: return
        db.collection("users").document(uid).collection("emergencyContacts").document(id).delete().await()
    }

    suspend fun uploadIncidentPhoto(uri: Uri, incidentId: String): String {
        val uid = currentUserId() ?: error("Please sign in first")
        val ref = storage.reference.child("incidentPhotos/$uid/$incidentId.jpg")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    private fun timestampMillis(value: Any?): Long = when (value) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> 0L
    }

    private fun timestampMillisOrNull(value: Any?): Long? = when (value) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> null
    }

    fun signOut() = auth.signOut()
}
