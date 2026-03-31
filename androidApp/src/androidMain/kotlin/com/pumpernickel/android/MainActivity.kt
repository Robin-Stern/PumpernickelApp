package com.pumpernickel.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pumpernickel.android.ui.navigation.MainScreen
import com.pumpernickel.android.ui.theme.PumpernickelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PumpernickelTheme {
                MainScreen()
            }
        }
    }
}
