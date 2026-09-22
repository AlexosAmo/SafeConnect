package com.amogelang.safeconnect.app.model

data class UserProfile(
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val preferredLanguage: String = "English",
    val notificationsEnabled: Boolean = true,
    val safetyScore: Int = 100,
    val createdAt: Long = System.currentTimeMillis()
)

data class Incident(
    val id: String = "",
    val userId: String = "",
    val type: String = "sos",
    val category: String = "Emergency",
    val description: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val status: String = "Active",
    val responderName: String = "",
    val responderStatus: String = "Alert sent",
    val responderLatitude: Double? = null,
    val responderLongitude: Double? = null,
    val responderUpdatedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class EmergencyContact(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val relationship: String = "Emergency contact"
)

// ---------- Auth ----------

data class RegisterRequest(
    val fullName: String,
    val email: String,
    val phone: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val user: UserProfile
)

// ---------- User Updates ----------

data class UpdateSettingsRequest(
    val preferredLanguage: String,
    val notificationsEnabled: Boolean
)

// ---------- Incidents (API) ----------

data class IncidentRequest(
    val type: String = "sos",
    val latitude: Double,
    val longitude: Double
)

data class IncidentResponse(
    val incidentId: String,
    val status: String
)

// ---------- Generic error body ----------

data class ApiError(
    val message: String
)

