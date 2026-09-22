package com.amogelang.safeconnect.app.repository

import com.amogelang.safeconnect.app.model.UpdateSettingsRequest
import com.amogelang.safeconnect.app.model.UserProfile
import com.amogelang.safeconnect.app.network.RetrofitClient

class UserRepository {

    private val api = RetrofitClient.api

    suspend fun updateSettings(
        userId: String,
        bearerToken: String,
        preferredLanguage: String,
        notificationsEnabled: Boolean
    ): ApiResult<UserProfile> {
        return try {
            val response = api.updateSettings(
                userId,
                bearerToken,
                UpdateSettingsRequest(preferredLanguage, notificationsEnabled)
            )
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Failure("Could not save settings (${response.code()})")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}