package com.kungdeok.game.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cream = Color(0xFFF6EBDD)
val Floor = Color(0xFFE2C9A6)
val Wall = Color(0xFFF3E3CF)
val Choco = Color(0xFF5A3A28)
val Silver = Color(0xFF8E97A3)
val HeartPink = Color(0xFFE86A8A)

@Composable
fun KungdeokTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Choco,
            onPrimary = Color.White,
            secondary = Silver,
            background = Cream,
            surface = Cream,
            surfaceVariant = Color(0xFFEBDCC8),
        ),
        content = content,
    )
}
