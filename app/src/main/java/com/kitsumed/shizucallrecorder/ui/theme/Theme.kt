package com.kitsumed.shizucallrecorder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import dev.n2k02.theme.N2K02Theme

/** Shared Material 3 Expressive design system used by all N2K02 apps. */
@Composable
fun ShizuCallRecorderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    N2K02Theme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        content = content,
    )
}
