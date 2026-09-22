package com.amogelang.safeconnect.app.network

import com.amogelang.safeconnect.app.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("incidents")
    suspend fun reportIncident(
        @Header("Authorization") token: String,
        @Body request: IncidentRequest
    ): Response<IncidentResponse>

    @PATCH("users/{id}")
    suspend fun updateSettings(
        @Path("id") userId: String,
        @Header("Authorization") token: String,
        @Body request: UpdateSettingsRequest
    ): Response<UserProfile>
}
