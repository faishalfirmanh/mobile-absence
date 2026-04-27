package com.example.absen_android.utils

import android.content.Context
import android.content.SharedPreferences

object SessionManager {
    private const val PREF_NAME  = "absen_session"
    private const val KEY_TOKEN    = "token"
    private const val KEY_FULLNAME = "fullname"
    private const val KEY_ROLE     = "role"
    private const val KEY_USER_ID  = "user_id"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveSession(context: Context, token: String, fullname: String, role: String, userId: Int) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_FULLNAME, fullname)
            .putString(KEY_ROLE, role)
            .putInt(KEY_USER_ID, userId)
            .apply()
    }

    fun getToken(context: Context): String?    = prefs(context).getString(KEY_TOKEN, null)
    fun getFullname(context: Context): String  = prefs(context).getString(KEY_FULLNAME, "") ?: ""
    fun getRole(context: Context): String      = prefs(context).getString(KEY_ROLE, "") ?: ""
    fun getUserId(context: Context): Int       = prefs(context).getInt(KEY_USER_ID, 0)
    fun isHrd(context: Context): Boolean       = getRole(context).equals("HRD", ignoreCase = true)

    fun clearSession(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
