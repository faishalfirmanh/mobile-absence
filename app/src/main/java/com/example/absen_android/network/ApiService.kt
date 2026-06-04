package com.example.absen_android.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

// ── Auth ──────────────────────────────────────────────────────────────────────
data class UserLokasi(
    val location_id: Int,
    val location_name: String,
    val latitude: String,
    val longitude: String,
    val radius_meters: String,
    val address: String,
    val is_active: Boolean
)

data class UserData(
    val id: Int,
    val fullname: String,
    val role: String,
    val username_machine: String,
    val lokasi: UserLokasi? = null
)

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val user: UserData? = null,
    val token: String? = null,
    val token_type: String? = null
)

// ── Get User (validate token) ─────────────────────────────────────────────────
data class GetUserData(
    val id: Int,
    val username: String? = null,
    val fullname: String,
    val role: String,
    val username_machine: String,
    val is_login_device: Int? = null,
    val location: Int? = null,
    val location_name: String? = null,
    val get_location: UserLokasi? = null
)

data class GetUserResponse(
    val status: Boolean,
    val message: String,
    val data: GetUserData? = null
)

// ── Validate Token ────────────────────────────────────────────────────────────
data class ValidateTokenResponse(
    val success: Boolean,
    val message: String? = null
)

// ── Attendance ────────────────────────────────────────────────────────────────
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

// ── Izin ──────────────────────────────────────────────────────────────────────
data class IzinData(
    val id: Int,
    val user_id: Int,
    val nama_custom: String,
    val jenis: String,
    val tgl_mulai: String,
    val tgl_selesai: String,
    val alasan: String,
    val bukti_sakit: String?,
    val status: String,
    val divisi_custom: String?,
    val jabatan_custom: String?,
    val kota_surat: String?,
    val created_at: String,
    val updated_at: String
)

data class IzinSubmitData(
    val id: Int,
    val jenis: String,
    val tgl_mulai: String,
    val tgl_selesai: String,
    val alasan: String,
    val bukti_sakit: String?,
    val user_id: Int,
    val nama_custom: String,
    val status: String? = null,
    val created_at: String,
    val updated_at: String
)

data class IzinSubmitResponse(
    val success: Boolean,
    val message: String,
    val data: IzinSubmitData? = null
)

data class IzinListData(
    val current_page: Int,
    val data: List<IzinData>,
    val last_page: Int,
    val total: Int,
    val next_page_url: String?,
    val prev_page_url: String?
)

data class IzinListResponse(
    val status: Boolean,
    val message: String,
    val data: IzinListData? = null
)

data class IzinDetailResponse(
    val status: Boolean,
    val message: String,
    val data: IzinData? = null
)

data class UpdateIzinResponse(
    val status: Boolean,
    val message: String,
    val data: IzinData? = null
)

data class SavePasswordResponse(
    val success: Boolean,
    val message: String
)

// ── Reports ──────────────────────────────────────────────────────────────────
data class ReportItem(
    val id_user: Any?, // Can be String or Int from API
    val nama_karyawan: String,
    val hadir_mesin: Int,
    val total_jam_masuk: Any?, // Can be Int or String
    val tidak_hadir_mesin: Int?,
    val sakit: Int,
    val izin: Int,
    val cuti: Int,
    val total_masuk_kurangi_izin: Int,
    val total_tidak_masuk: Int,
    val hari_efektif: Int
)

data class MonthlyReportResponse(
    val success: Boolean,
    val message: String,
    val periode: String?,
    val data: List<ReportItem>?
)

data class YearlyReportResponse(
    val success: Boolean,
    val message: String,
    val periode: String?,
    val data: List<ReportItem>?
)

// ── API Interface ─────────────────────────────────────────────────────────────
interface ApiService {

    @FormUrlEncoded
    @POST("login")
    suspend fun login(
        @Field("username_machine") username_machine: String,
        @Field("password_machine") password_machine: String
    ): Response<LoginResponse>

    // Validate token & get fresh user data
    @GET("get_user")
    suspend fun getUser(
        @Header("Authorization") token: String
    ): Response<GetUserResponse>

    @GET("user")
    suspend fun validateToken(
        @Header("Authorization") token: String
    ): Response<ValidateTokenResponse>

    @Multipart
    @POST("attendance")
    suspend fun submitAttendance(
        @Header("Authorization") token: String,
        @Part("location_id") locationId: RequestBody,
        @Part("attendance_type") attendanceType: RequestBody,
        @Part("submitted_latitude") latitude: RequestBody,
        @Part("submitted_longitude") longitude: RequestBody,
        @Part("device_id") deviceId: RequestBody,
        @Part("device_model") deviceModel: RequestBody,
        @Part("device_brand") deviceBrand: RequestBody,
        @Part("android_version") androidVersion: RequestBody,
        @Part("app_version") appVersion: RequestBody,
        @Part("gps_accuracy") gpsAccuracy: RequestBody
    ): Response<AttendanceResponse>

    @Multipart
    @POST("izin-absen")
    suspend fun submitIzin(
        @Header("Authorization") token: String,
        @Part("jenis") jenis: RequestBody,
        @Part("tgl_mulai") tglMulai: RequestBody,
        @Part("tgl_selesai") tglSelesai: RequestBody,
        @Part("alasan") alasan: RequestBody,
        @Part buktiSakit: MultipartBody.Part?
    ): Response<IzinSubmitResponse>

    @GET("list_izin")
    suspend fun getListIzin(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("keyword") keyword: String = "",
        @Query("kolom_name") kolomName: String = "nama_custom",
        @Query("limit") limit: Int = 10
    ): Response<IzinListResponse>

    @GET("findizin")
    suspend fun findIzin(
        @Header("Authorization") token: String,
        @Query("id") id: Int
    ): Response<IzinDetailResponse>

    @FormUrlEncoded
    @POST("update-izin")
    suspend fun updateIzin(
        @Header("Authorization") token: String,
        @Field("id") id: Int,
        @Field("status") status: String
    ): Response<UpdateIzinResponse>

    @FormUrlEncoded
    @POST("save_new_password")
    suspend fun saveNewPassword(
        @Header("Authorization") token: String,
        @Field("old_password") oldPass: String,
        @Field("new_password") newPass: String
    ): Response<SavePasswordResponse>

    @GET("report_bulan")
    suspend fun getMonthlyReport(
        @Header("Authorization") token: String,
        @Query("month") month: String,
        @Query("year") year: String
    ): Response<MonthlyReportResponse>

    @GET("report_tahun")
    suspend fun getYearlyReport(
        @Header("Authorization") token: String,
        @Query("year") year: String
    ): Response<YearlyReportResponse>
}
