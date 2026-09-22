package com.amogelang.safeconnect.app.repository

import com.amogelang.safeconnect.app.model.ApiError
import com.amogelang.safeconnect.app.model.AuthResponse
import com.amogelang.safeconnect.app.model.LoginRequest
import com.amogelang.safeconnect.app.model.RegisterRequest
import com.amogelang.safeconnect.app.network.RetrofitClient
import com.google.gson.Gson
import retrofit2.Response

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val message: String) : ApiResult<Nothing>()
}

class AuthRepository {

    private val api = RetrofitClient.api
    private val gson = Gson()

    suspend fun register(fullName: String, email: String, phone: String, password: String): ApiResult<AuthResponse> {
        return try {
            // The password travels once, over HTTPS in production, straight to the
            // server, which hashes it with bcrypt before it ever touches the
            // database. The app never stores or logs the plain-text password.
            val response = api.register(RegisterRequest(fullName, email, phone, password))
            handleAuthResponse(response)
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun login(email: String, password: String): ApiResult<AuthResponse> {
        return try {
            val response = api.login(LoginRequest(email, password))
            handleAuthResponse(response)
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    private fun handleAuthResponse(response: Response<AuthResponse>): ApiResult<AuthResponse> {
        return if (response.isSuccessful && response.body() != null) {
            ApiResult.Success(response.body()!!)
        } else {
            val errorMessage = try {
                val errorBody = response.errorBody()?.string()
                gson.fromJson(errorBody, ApiError::class.java)?.message ?: "Request failed (${response.code()})"
            } catch (_: Exception) {
                "Request failed (${response.code()})"
            }
            ApiResult.Failure(errorMessage)
        }
    }
}