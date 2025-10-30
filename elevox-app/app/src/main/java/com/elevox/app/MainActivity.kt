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
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

	private lateinit var prefs: SharedPreferences

	// Launcher para solicitar permissões na primeira abertura
	private val permissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestMultiplePermissions()
	) { permissions ->
		android.util.Log.d("MainActivity", "=== RESULTADO DAS PERMISSÕES ===")
		permissions.forEach { (permission, granted) ->
			android.util.Log.d("MainActivity", "$permission: ${if (granted) "✓ CONCEDIDA" else "✗ NEGADA"}")
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		prefs = getSharedPreferences("elevox_settings", Context.MODE_PRIVATE)

		setContent {
			ElevoxApp {
				ElevoxNavigation()
			}
		}

		// Solicita permissões na primeira abertura se auto-detecção estiver ativada
		// Chamado APÓS setContent para evitar problemas de timing
		requestPermissionsIfNeeded()

		// Registra token FCM para receber comandos da Alexa via push notifications
		registerFCMToken()
	}

	/**
	 * Solicita permissões necessárias na primeira abertura do app
	 */
	private fun requestPermissionsIfNeeded() {
		val autoDetectionEnabled = prefs.getBoolean("auto_detection_enabled", true)
		val hasAllPermissions = BluetoothPermissionHelper.hasAllPermissions(this)
		val missingPermissions = BluetoothPermissionHelper.getMissingPermissions(this)

		android.util.Log.d("MainActivity", "=== VERIFICAÇÃO DE PERMISSÕES ===")
		android.util.Log.d("MainActivity", "Auto-detecção habilitada: $autoDetectionEnabled")
		android.util.Log.d("MainActivity", "Todas permissões concedidas: $hasAllPermissions")
		android.util.Log.d("MainActivity", "Permissões faltando: ${missingPermissions.joinToString()}")

		if (autoDetectionEnabled && !hasAllPermissions) {
			val permissionsToRequest = BluetoothPermissionHelper.getRequiredPermissions()
			android.util.Log.d("MainActivity", "Solicitando permissões: ${permissionsToRequest.joinToString()}")
			permissionLauncher.launch(permissionsToRequest)
		}
	}

	/**
	 * Registra token FCM para receber comandos da Alexa
	 */
	private fun registerFCMToken() {
		android.util.Log.d("MainActivity", "🔑 Registrando token FCM...")

		// Primeiro autentica anonimamente
		val auth = FirebaseAuth.getInstance()

		if (auth.currentUser != null) {
			android.util.Log.d("MainActivity", "👤 Usuário já autenticado: ${auth.currentUser?.uid}")
			obtainAndSaveFCMToken()
		} else {
			android.util.Log.d("MainActivity", "🔐 Autenticando anonimamente...")
			auth.signInAnonymously()
				.addOnSuccessListener {
					android.util.Log.d("MainActivity", "✅ Autenticação bem-sucedida: ${it.user?.uid}")
					obtainAndSaveFCMToken()
				}
				.addOnFailureListener { e ->
					android.util.Log.e("MainActivity", "❌ Erro na autenticação: ${e.message}")
				}
		}
	}

	/**
	 * Obtém e salva o token FCM no Firebase
	 */
	private fun obtainAndSaveFCMToken() {
		FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
			if (!task.isSuccessful) {
				android.util.Log.e("MainActivity", "❌ Erro ao obter token FCM: ${task.exception}")
				return@addOnCompleteListener
			}

			// Token FCM obtido
			val token = task.result
			android.util.Log.d("MainActivity", "✅ Token FCM obtido: $token")

			// Salva no Firebase Realtime Database para o Lambda usar
			val database = FirebaseDatabase.getInstance().reference
			database.child("fcm_tokens").child("default_user").setValue(token)
				.addOnSuccessListener {
					android.util.Log.d("MainActivity", "✅ Token FCM salvo no Firebase")
				}
				.addOnFailureListener { e ->
					android.util.Log.e("MainActivity", "❌ Erro ao salvar token: ${e.message}")
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
