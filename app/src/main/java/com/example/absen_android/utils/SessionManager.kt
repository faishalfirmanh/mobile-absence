package com.example.absen_android.utils

import android.content.Context
import android.content.SharedPreferences

object SessionManager {
    private const val PREF_NAME = "absen_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_FULLNAME = "fullname"
    private const val KEY_ROLE = "role"
    private const val KEY_USER_ID = "user_id"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveSession(context: Context, token: String, fullname: String, role: String, userId: Int) {
        getPrefs(context).edit().apply {
            putString(KEY_TOKEN, token)
            putString(KEY_FULLNAME, fullname)
            putString(KEY_ROLE, role)
            putInt(KEY_USER_ID, userId)
            apply()
        }
    }

    fun getToken(context: Context): String? {
        return getPrefs(context).getString(KEY_TOKEN, null)
    }

    fun getFullname(context: Context): String {
        return getPrefs(context).getString(KEY_FULLNAME, "") ?: ""
    }

    fun getRole(context: Context): String {
        return getPrefs(context).getString(KEY_ROLE, "") ?: ""
    }

    fun clearSession(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}