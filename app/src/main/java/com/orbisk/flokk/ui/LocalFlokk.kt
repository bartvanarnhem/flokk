package com.orbisk.flokk.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.orbisk.flokk.FlokkApplication

val LocalFlokk = staticCompositionLocalOf<FlokkApplication> {
    error("LocalFlokk not provided")
}
