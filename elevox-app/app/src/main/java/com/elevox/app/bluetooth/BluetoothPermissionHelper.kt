package com.elevox.app.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Helper para gerenciar permissões Bluetooth necessárias para detecção de andar
 */
object BluetoothPermissionHelper {

	/**
	 * Retorna a lista de permissões necessárias baseado na versão do Android
	 */
	fun getRequiredPermissions(): Array<String> {
		val permissions = mutableListOf<String>()

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			// Android 12+ (API 31+)
			permissions.add(Manifest.permission.BLUETOOTH_SCAN)
			permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
			permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
		} else {
			// Android < 12 (API < 31)
			permissions.add(Manifest.permission.BLUETOOTH)
			permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
			permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
		}

		// Android 13+ precisa de permissão para notificações
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			permissions.add(Manifest.permission.POST_NOTIFICATIONS)
		}

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
