package com.flokk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.flokk.app.ui.LocalFlokk
import com.flokk.app.ui.nav.FlokkNavHost
import com.flokk.app.ui.theme.FlokkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalFlokk provides (application as FlokkApplication)) {
                FlokkTheme {
                    FlokkNavHost()
                }
            }
        }
    }
}
