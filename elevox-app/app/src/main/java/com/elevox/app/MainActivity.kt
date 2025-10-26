package com.elevox.app

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elevox.app.bluetooth.BluetoothPermissionHelper
import com.elevox.app.bluetooth.FloorLocationService
import com.elevox.app.home.HomeScreen
import com.elevox.app.home.HomeViewModel
import com.elevox.app.settings.SettingsScreen
import com.elevox.app.settings.SettingsViewModel

class MainActivity : ComponentActivity() {

	private lateinit var prefs: SharedPreferences
	private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
		if (key == "auto_detection_enabled") {
			updateServiceState()
		}
	}

	// Launcher para solicitar permissões na primeira abertura
	private val permissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestMultiplePermissions()
	) { permissions ->
		// Atualiza o estado do serviço após conceder/negar permissões
		updateServiceState()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		prefs = getSharedPreferences("elevox_settings", Context.MODE_PRIVATE)
		prefs.registerOnSharedPreferenceChangeListener(prefsListener)

		// Solicita permissões na primeira abertura se auto-detecção estiver ativada
		requestPermissionsIfNeeded()

		// Verifica e inicia o serviço se auto-detecção estiver ativada
		updateServiceState()

		setContent {
			ElevoxApp {
				ElevoxNavigation()
			}
		}
	}

	/**
	 * Solicita permissões necessárias na primeira abertura do app
	 */
	private fun requestPermissionsIfNeeded() {
		val autoDetectionEnabled = prefs.getBoolean("auto_detection_enabled", true)

		if (autoDetectionEnabled && !BluetoothPermissionHelper.hasAllPermissions(this)) {
			// Solicita as permissões na primeira abertura
			permissionLauncher.launch(BluetoothPermissionHelper.getRequiredPermissions())
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
	}

	/**
	 * Inicia ou para o FloorLocationService baseado nas configurações
	 */
	private fun updateServiceState() {
		val autoDetectionEnabled = prefs.getBoolean("auto_detection_enabled", true)
		val hasPermissions = BluetoothPermissionHelper.hasAllPermissions(this)

		if (autoDetectionEnabled && hasPermissions) {
			// Inicia o serviço
			FloorLocationService.start(this)
		} else {
			// Para o serviço
			FloorLocationService.stop(this)
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
