package com.elevox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elevox.app.home.HomeScreen
import com.elevox.app.home.HomeViewModel
import com.elevox.app.settings.SettingsScreen
import com.elevox.app.settings.SettingsViewModel

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			ElevoxApp {
				ElevoxNavigation()
			}
		}
	}
}

@Composable
fun ElevoxNavigation() {
	var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
	val context = androidx.compose.ui.platform.LocalContext.current
	val homeViewModel: HomeViewModel = viewModel {
		HomeViewModel(context)
	}
	val settingsViewModel: SettingsViewModel = viewModel {
		SettingsViewModel(context)
	}

	when (currentScreen) {
		Screen.Home -> {
			HomeScreen(
				viewModel = homeViewModel,
				onSettingsClick = { currentScreen = Screen.Settings }
			)
		}
		Screen.Settings -> {
			SettingsScreen(
				viewModel = settingsViewModel,
				onBackClick = { currentScreen = Screen.Home }
			)
		}
	}
}

sealed class Screen {
	object Home : Screen()
	object Settings : Screen()
}
