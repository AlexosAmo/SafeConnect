package com.amogelang.safeconnect.app.repository

import com.amogelang.safeconnect.app.model.IncidentRequest
import com.amogelang.safeconnect.app.model.IncidentResponse
import com.amogelang.safeconnect.app.network.RetrofitClient

class IncidentRepository {

    private val api = RetrofitClient.api

    suspend fun reportSos(bearerToken: String, latitude: Double, longitude: Double): ApiResult<IncidentResponse> = reportIncident(bearerToken, latitude, longitude, "sos")

    suspend fun reportIncident(bearerToken: String, latitude: Double, longitude: Double, type: String): ApiResult<IncidentResponse> {
        return try {
            val response = api.reportIncident(bearerToken,
                IncidentRequest(type = type, latitude = latitude, longitude = longitude)
            )
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Failure("Could not send alert (${response.code()})")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}