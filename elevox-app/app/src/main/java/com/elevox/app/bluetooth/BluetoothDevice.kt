package com.elevox.app.bluetooth

/**
 * Representa um dispositivo Bluetooth detectado durante o scan
 *
 * @property name Nome do dispositivo Bluetooth (ex: "terreo", "1", "2", "3")
 * @property address Endereço MAC do dispositivo (ex: "00:11:22:33:44:55")
 * @property rssi Força do sinal em dBm (Received Signal Strength Indicator)
 *               Valores típicos: -30 (muito próximo) a -90 (muito distante)
 */
data class BluetoothDevice(
	val name: String,
	val address: String,
	val rssi: Int
)
