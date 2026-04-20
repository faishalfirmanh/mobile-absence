package com.example.absen_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.absen_android.navigation.AppNavigation
import com.example.absen_android.ui.theme.AbsenandroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AbsenandroidTheme {
                AppNavigation()
            }
        }
    }
}