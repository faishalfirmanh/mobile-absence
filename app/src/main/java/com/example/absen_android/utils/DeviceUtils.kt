package com.example.absen_android.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings

object DeviceUtils {

    // Get unique device ID
    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }

    // Get device brand + model name (e.g. "Samsung Galaxy A51")
    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    // Get device model only (e.g. "SM-A515F")
    fun getDeviceModel(): String = Build.MODEL

    // Get device brand (e.g. "Samsung")
    fun getDeviceBrand(): String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    // Get Android OS version (e.g. "14")
    fun getAndroidVersion(): String = Build.VERSION.RELEASE

    // Get SDK int (e.g. 34)
    fun getSdkVersion(): Int = Build.VERSION.SDK_INT
}
