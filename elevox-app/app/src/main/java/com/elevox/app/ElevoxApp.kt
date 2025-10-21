package com.elevox.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
	primary = androidx.compose.ui.graphics.Color(0xFF0066FF), // bright blue
	onPrimary = androidx.compose.ui.graphics.Color.White,
	primaryContainer = androidx.compose.ui.graphics.Color(0xFFCCE0FF),
	onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF001B3F),
	secondary = androidx.compose.ui.graphics.Color(0xFF0053B3),
	background = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
	surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
	primary = androidx.compose.ui.graphics.Color(0xFF66A3FF),
	onPrimary = androidx.compose.ui.graphics.Color(0xFF001B3F),
	primaryContainer = androidx.compose.ui.graphics.Color(0xFF003A80),
	onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFCCE0FF),
	secondary = androidx.compose.ui.graphics.Color(0xFF99C2FF),
	background = androidx.compose.ui.graphics.Color(0xFF101317),
	surface = androidx.compose.ui.graphics.Color(0xFF101317)
)

@Composable
fun ElevoxApp(content: @Composable () -> Unit) {
	val dark = isSystemInDarkTheme()
	MaterialTheme(
		colorScheme = if (dark) DarkColors else LightColors,
		content = content
	)
}


