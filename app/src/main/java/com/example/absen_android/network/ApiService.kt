package com.example.absen_android.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

data class LoginRequest(
    val username_machine: String,
    val password_machine: String
)

data class UserData(
    val id: Int,
    val fullname: String,
    val role: String,
    val username_machine: String
)

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val user: UserData? = null,
    val token: String? = null,
    val token_type: String? = null
)

data class AttendanceRequest(
    val location_id: Int = 1,
    val attendance_type: String = "check_in",
    val submitted_latitude: Double,
    val submitted_longitude: Double,
    val device_id: String,
    val device_model: String,
    val gps_accuracy: Float
)

data class AttendanceData(
    val attendance_id: Int,
    val employee_id: Int,
    val location_id: Int,
    val attendance_type: String,
    val attendance_date: String,
    val submitted_latitude: String,
    val submitted_longitude: String,
    val distance_meters: String,
    val device_id: String,
    val device_model: String,
    val status: String
)

data class AttendanceResponse(
    val success: Boolean,
    val message: String,
    val data: AttendanceData? = null,
    val distance_meters: Double? = null
)

data class ValidateTokenResponse(
    val success: Boolean,
    val message: String? = null
)

interface ApiService {
    @FormUrlEncoded
    @POST("login")
    suspend fun login(
        @Field("username_machine") username_machine: String,
        @Field("password_machine") password_machine: String
    ): Response<LoginResponse>

    @POST("attendance")
    suspend fun submitAttendance(
        @Header("Authorization") token: String,
        @Body request: AttendanceRequest
    ): Response<AttendanceResponse>

    @GET("user")
    suspend fun validateToken(
        @Header("Authorization") token: String
    ): Response<ValidateTokenResponse>
}