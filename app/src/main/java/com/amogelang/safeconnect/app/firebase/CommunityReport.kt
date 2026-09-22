package com.amogelang.safeconnect.app.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date

data class CommunityReport(
    val reportId: String = "",
    val userId: String = "",
    val category: String = "",
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val createdAt: Date? = null
)

class FirestoreCommunityRepository {

    private val firestore = FirebaseFirestore.getInstance()

    suspend fun getRecentReports(limit: Long = 30): FirebaseResult<List<CommunityReport>> {
        return try {
            val snapshot = firestore.collection("communityReports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            val reports = snapshot.documents.map { doc ->
                CommunityReport(
                    reportId = doc.id,
                    userId = doc.getString("userId") ?: "",
                    category = doc.getString("category") ?: "",
                    description = doc.getString("description") ?: "",
                    latitude = doc.getDouble("latitude") ?: 0.0,
                    longitude = doc.getDouble("longitude") ?: 0.0,
                    createdAt = doc.getDate("createdAt")
                )
            }
            FirebaseResult.Success(reports)
        } catch (e: Exception) {
            FirebaseResult.Failure(e.message ?: "Could not load community reports")
        }
    }

    suspend fun postReport(uid: String, category: String, description: String, latitude: Double, longitude: Double): FirebaseResult<String> {
        return try {
            val report = mapOf(
                "userId" to uid,
                "category" to category,
                "description" to description.take(280),
                "latitude" to latitude,
                "longitude" to longitude,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val ref = firestore.collection("communityReports").add(report).await()
            FirebaseResult.Success(ref.id)
        } catch (e: Exception) {
            FirebaseResult.Failure(e.message ?: "Could not post report")
        }
    }
}
