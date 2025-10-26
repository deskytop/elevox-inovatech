package com.elevox.app.bluetooth

import android.util.Log

/**
 * Detector de andar baseado em análise de RSSI (força do sinal)
 * Determina em qual andar o usuário está com base no beacon com sinal mais forte
 */
class FloorDetector {

	companion object {
		private const val TAG = "FloorDetector"
		private const val DETECTED_FLOOR_KEY = "detected_floor"
	}

	// Armazena o último andar detectado para aplicar hysteresis
	private var lastDetectedFloor: Int? = null

	/**
	 * Determina o andar atual baseado na lista de dispositivos detectados
	 *
	 * Algoritmo:
	 * 1. Filtra dispositivos com RSSI acima do threshold mínimo
	 * 2. Encontra o dispositivo com maior RSSI (sinal mais forte)
	 * 3. Aplica hysteresis para evitar mudanças rápidas
	 * 4. Retorna o número do andar correspondente
	 *
	 * @param devices Lista de dispositivos Bluetooth detectados
	 * @return Número do andar detectado (0-3) ou null se nenhum beacon válido
	 */
	fun detectFloor(devices: List<BluetoothDevice>): Int? {
		if (devices.isEmpty()) {
			Log.d(TAG, "Nenhum dispositivo detectado")
			return null
		}

		// Filtra dispositivos com sinal forte o suficiente
		val validDevices = devices.filter { device ->
			device.rssi >= FloorBeaconConfig.RSSI_THRESHOLD
		}

		if (validDevices.isEmpty()) {
			Log.d(TAG, "Nenhum dispositivo com sinal forte o suficiente (threshold: ${FloorBeaconConfig.RSSI_THRESHOLD} dBm)")
			return null
		}

		// Encontra o dispositivo com maior RSSI (sinal mais forte)
		val strongestDevice = validDevices.maxByOrNull { it.rssi }

		if (strongestDevice == null) {
			Log.d(TAG, "Erro ao encontrar dispositivo mais forte")
			return null
		}

		// Obtém o número do andar correspondente ao beacon
		val detectedFloor = FloorBeaconConfig.getFloorNumber(strongestDevice.name)

		if (detectedFloor == null) {
			Log.w(TAG, "Beacon desconhecido: ${strongestDevice.name}")
			return null
		}

		Log.d(TAG, "Dispositivo mais forte: ${strongestDevice.name} (RSSI: ${strongestDevice.rssi} dBm) → Andar $detectedFloor")

		// Aplica hysteresis se já houver um andar detectado anteriormente
		val shouldUpdateFloor = shouldUpdateFloor(
			currentFloor = detectedFloor,
			currentRssi = strongestDevice.rssi,
			devices = validDevices
		)

		return if (shouldUpdateFloor) {
			lastDetectedFloor = detectedFloor
			detectedFloor
		} else {
			lastDetectedFloor
		}
	}

	/**
	 * Decide se deve atualizar o andar detectado baseado em hysteresis
	 * Evita mudanças rápidas quando o usuário está entre andares
	 */
	private fun shouldUpdateFloor(
		currentFloor: Int,
		currentRssi: Int,
		devices: List<BluetoothDevice>
	): Boolean {
		// Se é a primeira detecção, aceita imediatamente
		if (lastDetectedFloor == null) {
			Log.d(TAG, "Primeira detecção: Andar $currentFloor")
			return true
		}

		// Se o andar não mudou, aceita
		if (currentFloor == lastDetectedFloor) {
			return true
		}

		// Encontra o RSSI do beacon do último andar detectado
		val lastFloorBeaconName = FloorBeaconConfig.FLOOR_BEACONS.entries
			.find { it.value == lastDetectedFloor }?.key

		val lastFloorRssi = devices.find { it.name == lastFloorBeaconName }?.rssi

		// Se o beacon do último andar não está mais visível, aceita a mudança
		if (lastFloorRssi == null) {
			Log.d(TAG, "Beacon do andar anterior não detectado, mudando para andar $currentFloor")
			return true
		}

		// Aplica hysteresis: o novo andar deve ter sinal significativamente mais forte
		val rssiDifference = currentRssi - lastFloorRssi
		val shouldChange = rssiDifference >= FloorBeaconConfig.RSSI_HYSTERESIS

		if (shouldChange) {
			Log.d(TAG, "Mudança de andar: $lastDetectedFloor → $currentFloor (diferença RSSI: $rssiDifference dBm)")
		} else {
			Log.d(TAG, "Mantendo andar $lastDetectedFloor (diferença RSSI insuficiente: $rssiDifference dBm < ${FloorBeaconConfig.RSSI_HYSTERESIS} dBm)")
		}

		return shouldChange
	}

	/**
	 * Reseta o estado do detector
	 * Útil para forçar uma nova detecção sem hysteresis
	 */
	fun reset() {
		lastDetectedFloor = null
		Log.d(TAG, "Detector resetado")
	}

	/**
	 * Retorna informações de debug sobre os dispositivos detectados
	 */
	fun getDebugInfo(devices: List<BluetoothDevice>): String {
		return buildString {
			appendLine("=== Floor Detector Debug ===")
			appendLine("Último andar detectado: ${lastDetectedFloor ?: "N/A"}")
			appendLine("Dispositivos detectados: ${devices.size}")
			appendLine()
			devices.sortedByDescending { it.rssi }.forEach { device ->
				val floor = FloorBeaconConfig.getFloorNumber(device.name)
				val isStrong = device.rssi >= FloorBeaconConfig.RSSI_THRESHOLD
				appendLine("  ${device.name} → Andar $floor")
				appendLine("    RSSI: ${device.rssi} dBm ${if (isStrong) "✓" else "✗ (fraco)"}")
				appendLine("    MAC: ${device.address}")
			}
		}
	}
}
