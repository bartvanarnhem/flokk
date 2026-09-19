package com.flokk.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.flokk.app.FlokkApplication

val LocalFlokk = staticCompositionLocalOf<FlokkApplication> {
    error("LocalFlokk not provided")
}
