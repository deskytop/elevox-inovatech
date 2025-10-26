package com.elevox.app.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Helper para gerenciar permissões Bluetooth necessárias para detecção de andar
 */
object BluetoothPermissionHelper {

	private const val TAG = "BluetoothPermissions"

	/**
	 * Retorna a lista de permissões necessárias baseado na versão do Android
	 */
	@Suppress("InlinedApi")
	fun getRequiredPermissions(): Array<String> {
		val permissions = mutableListOf<String>()

		Log.d(TAG, "=== CONFIGURANDO PERMISSÕES ===")
		Log.d(TAG, "Android SDK: ${Build.VERSION.SDK_INT}")
		Log.d(TAG, "Android Version: ${Build.VERSION.RELEASE}")

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			// Android 12+ (API 31+)
			Log.d(TAG, "Usando permissões Android 12+ (API 31+)")
			permissions.add(Manifest.permission.BLUETOOTH_SCAN)
			permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
			permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
			Log.d(TAG, "  - BLUETOOTH_SCAN")
			Log.d(TAG, "  - BLUETOOTH_CONNECT")
			Log.d(TAG, "  - ACCESS_FINE_LOCATION")
		} else {
			// Android < 12 (API < 31)
			Log.d(TAG, "Usando permissões Android < 12 (API < 31)")
			permissions.add(Manifest.permission.BLUETOOTH)
			permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
			permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
			Log.d(TAG, "  - BLUETOOTH")
			Log.d(TAG, "  - BLUETOOTH_ADMIN")
			Log.d(TAG, "  - ACCESS_FINE_LOCATION")
		}

		// Android 13+ precisa de permissão para notificações
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			Log.d(TAG, "Adicionando permissão POST_NOTIFICATIONS (Android 13+)")
			permissions.add(Manifest.permission.POST_NOTIFICATIONS)
			Log.d(TAG, "  - POST_NOTIFICATIONS")
		}

		Log.d(TAG, "Total de permissões: ${permissions.size}")
		Log.d(TAG, "=== FIM CONFIGURAÇÃO ===")
		return permissions.toTypedArray()
	}

	/**
	 * Verifica se todas as permissões necessárias foram concedidas
	 */
	fun hasAllPermissions(context: Context): Boolean {
		return getRequiredPermissions().all { permission ->
			ContextCompat.checkSelfPermission(
				context,
				permission
			) == PackageManager.PERMISSION_GRANTED
		}
	}

	/**
	 * Retorna as permissões que ainda não foram concedidas
	 */
	fun getMissingPermissions(context: Context): List<String> {
		return getRequiredPermissions().filter { permission ->
			ContextCompat.checkSelfPermission(
				context,
				permission
			) != PackageManager.PERMISSION_GRANTED
		}
	}

	/**
	 * Retorna uma mensagem explicativa sobre as permissões necessárias
	 */
	fun getPermissionExplanation(): String {
		return buildString {
			appendLine("Para detectar automaticamente o andar em que você está, o app precisa de:")
			appendLine()
			appendLine("• Bluetooth: para escanear beacons próximos")
			appendLine("• Localização: requerida pelo Android para scan Bluetooth")
			appendLine()
			appendLine("Sua privacidade está protegida:")
			appendLine("• Apenas detectamos beacons configurados")
			appendLine("• Não rastreamos sua localização GPS")
			appendLine("• Não enviamos dados para servidores externos")
		}
	}
}
