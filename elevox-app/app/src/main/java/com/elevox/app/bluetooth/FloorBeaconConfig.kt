package com.elevox.app.bluetooth

/**
 * Configuração dos beacons Bluetooth para cada andar
 *
 * Mapeia o nome do dispositivo Bluetooth para o número do andar correspondente.
 * Cada andar deve ter um dispositivo Bluetooth configurado com o nome específico.
 */
object FloorBeaconConfig {
	/**
	 * Mapa de nomes de dispositivos → número do andar
	 *
	 * Configuração:
	 * - "terreo" → Andar 0 (Térreo)
	 * - "1" → Andar 1 (1° andar)
	 * - "2" → Andar 2 (2° andar)
	 * - "3" → Andar 3 (3° andar)
	 */
	val FLOOR_BEACONS = mapOf(
		"terreo" to 0,
		"1" to 1,
		"2" to 2,
		"3" to 3
	)

	/**
	 * Threshold mínimo de RSSI para considerar um beacon válido
	 * Valores abaixo deste threshold serão ignorados para evitar falsos positivos
	 * -90 dBm é um sinal muito fraco, mas ainda detectável
	 */
	const val RSSI_THRESHOLD = -90

	/**
	 * Hysteresis em dBm para evitar mudanças rápidas de andar
	 * O novo andar deve ter pelo menos 5 dBm a mais que o andar atual
	 */
	const val RSSI_HYSTERESIS = 5

	/**
	 * Intervalo de scan em milissegundos
	 * 5000ms = 5 segundos (balance entre bateria e responsividade)
	 */
	const val SCAN_INTERVAL_MS = 5000L

	/**
	 * Duração de cada scan em milissegundos
	 * 2000ms = 2 segundos de scan ativo
	 */
	const val SCAN_DURATION_MS = 2000L

	/**
	 * Verifica se um nome de dispositivo corresponde a um beacon configurado
	 */
	fun isValidBeacon(deviceName: String?): Boolean {
		return deviceName != null && FLOOR_BEACONS.containsKey(deviceName)
	}

	/**
	 * Retorna o número do andar correspondente ao nome do beacon
	 */
	fun getFloorNumber(deviceName: String): Int? {
		return FLOOR_BEACONS[deviceName]
	}
}
