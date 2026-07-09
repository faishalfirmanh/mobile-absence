package com.example.absen_android.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.absen_android.network.LoginResponse
import com.google.gson.Gson

object SessionManager {

    private const val PREF_NAME  = "AbsenSessionPrefs"
    private const val KEY_SESSION = "session_data"
    private val gson = Gson()

    // ── SharedPreferences (MODE_PRIVATE = persists across app restart) ────────
    private fun getPreferences(context: Context): SharedPreferences =
        context.applicationContext                        // ← pakai applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ── Simpan sesi setelah login ─────────────────────────────────────────────
    fun saveSession(context: Context, response: LoginResponse) {
        try {
            val json = gson.toJson(response)
            getPreferences(context).edit().putString(KEY_SESSION, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── Baca sesi ─────────────────────────────────────────────────────────────
    fun getSession(context: Context): LoginResponse? {
        return try {
            val json = getPreferences(context).getString(KEY_SESSION, null)
                ?: return null
            gson.fromJson(json, LoginResponse::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // ── Cek apakah sesi tersimpan ─────────────────────────────────────────────
    fun hasSession(context: Context): Boolean =
        getPreferences(context).contains(KEY_SESSION)

    // ── Getter spesifik ───────────────────────────────────────────────────────
    fun getToken(context: Context): String? =
        getSession(context)?.token

    fun getFullname(context: Context): String =
        getSession(context)?.user?.fullname ?: ""

    fun getRole(context: Context): String =
        getSession(context)?.user?.role ?: ""

    fun getUserId(context: Context): Int =
        getSession(context)?.user?.id ?: 0

    fun isHrd(context: Context): Boolean =
        getRole(context).equals("HRD", ignoreCase = true)

    // ── Hapus sesi (hanya saat logout atau 401 dari server) ───────────────────
    fun clearSession(context: Context) {
        try {
            getPreferences(context).edit().remove(KEY_SESSION).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}