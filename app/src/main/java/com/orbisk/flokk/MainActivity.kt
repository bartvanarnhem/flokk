package com.orbisk.flokk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.orbisk.flokk.ui.LocalFlokk
import com.orbisk.flokk.ui.nav.FlokkNavHost
import com.orbisk.flokk.ui.theme.FlokkTuiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalFlokk provides (application as FlokkApplication)) {
                FlokkTuiTheme {
                    FlokkNavHost()
                }
            }
        }
    }
}
