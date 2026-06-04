package com.example.absen_android.utils

import android.content.Context
import com.example.absen_android.network.LoginResponse
import com.google.gson.Gson
import java.io.File

object SessionManager {

    private const val SESSION_FILE = "session.json"
    private val gson = Gson()

    // ── File path ─────────────────────────────────────────────────────────────
    private fun sessionFile(context: Context): File =
        File(context.filesDir, SESSION_FILE)

    // ── Save full login response to file ──────────────────────────────────────
    fun saveSession(context: Context, response: LoginResponse) {
        try {
            val json = gson.toJson(response)
            sessionFile(context).writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── Read session from file ────────────────────────────────────────────────
    fun getSession(context: Context): LoginResponse? {
        return try {
            val file = sessionFile(context)
            if (!file.exists()) return null
            val json = file.readText()
            gson.fromJson(json, LoginResponse::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // ── Check if session file exists ──────────────────────────────────────────
    fun hasSession(context: Context): Boolean =
        sessionFile(context).exists()

    // ── Get token from saved session ──────────────────────────────────────────
    fun getToken(context: Context): String? =
        getSession(context)?.token

    // ── Get user data ─────────────────────────────────────────────────────────
    fun getFullname(context: Context): String =
        getSession(context)?.user?.fullname ?: ""

    fun getRole(context: Context): String =
        getSession(context)?.user?.role ?: ""

    fun getUserId(context: Context): Int =
        getSession(context)?.user?.id ?: 0

    fun isHrd(context: Context): Boolean =
        getRole(context).equals("HRD", ignoreCase = true)

    // ── Delete session file on logout ─────────────────────────────────────────
    fun clearSession(context: Context) {
        try {
            val file = sessionFile(context)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
