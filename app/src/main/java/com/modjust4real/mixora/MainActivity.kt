package com.modjust4real.mixora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.modjust4real.mixora.ui.MixoraApp
import com.modjust4real.mixora.ui.MixoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MixoraTheme {
                MixoraApp()
            }
        }
    }
}
