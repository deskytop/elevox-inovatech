package com.elevox.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Scanner de dispositivos Bluetooth Low Energy (BLE)
 * Responsável por escanear e filtrar apenas os beacons configurados para detecção de andar
 */
class BluetoothScanner(private val context: Context) {

	private val bluetoothManager: BluetoothManager? =
		context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

	private val bluetoothAdapter: BluetoothAdapter? =
		bluetoothManager?.adapter

	private val bleScanner: BluetoothLeScanner? =
		bluetoothAdapter?.bluetoothLeScanner

	companion object {
		private const val TAG = "BluetoothScanner"
	}

	/**
	 * Inicia o scan de dispositivos Bluetooth e emite os resultados via Flow
	 * Filtra apenas dispositivos configurados no FloorBeaconConfig
	 *
	 * @return Flow com lista de dispositivos detectados
	 */
	@SuppressLint("MissingPermission")
	fun startScanning(): Flow<List<BluetoothDevice>> = callbackFlow {
		if (bleScanner == null) {
			Log.e(TAG, "BLE Scanner não disponível")
			close()
			return@callbackFlow
		}

		if (bluetoothAdapter?.isEnabled != true) {
			Log.e(TAG, "Bluetooth está desativado")
			close()
			return@callbackFlow
		}

		// Mapa para armazenar dispositivos detectados (key = MAC address)
		val detectedDevices = mutableMapOf<String, BluetoothDevice>()

		val scanCallback = object : ScanCallback() {
			override fun onScanResult(callbackType: Int, result: ScanResult?) {
				result?.let { scanResult ->
					val deviceName = scanResult.device?.name
					val deviceAddress = scanResult.device?.address ?: return
					val rssi = scanResult.rssi

					// Log de TODOS os dispositivos detectados (mesmo não-beacons)
					Log.d(TAG, "Dispositivo BLE detectado: nome='$deviceName', MAC=$deviceAddress, RSSI=$rssi dBm")

					// Verifica se é um beacon configurado
					if (FloorBeaconConfig.isValidBeacon(deviceName)) {
						Log.i(TAG, "✓ BEACON VÁLIDO: $deviceName (RSSI: $rssi dBm)")

						// Atualiza ou adiciona o dispositivo
						detectedDevices[deviceAddress] = BluetoothDevice(
							name = deviceName!!,
							address = deviceAddress,
							rssi = rssi
						)

						// Emite a lista atualizada
						trySend(detectedDevices.values.toList())
					} else {
						Log.d(TAG, "✗ Dispositivo ignorado (não é beacon configurado): $deviceName")
					}
				}
			}

			override fun onBatchScanResults(results: MutableList<ScanResult>?) {
				results?.forEach { result ->
					onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
				}
			}

			override fun onScanFailed(errorCode: Int) {
				Log.e(TAG, "Scan falhou com código: $errorCode")
			}
		}

		// Configurações de scan para economia de bateria
		val scanSettings = ScanSettings.Builder()
			.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
			.build()

		try {
			Log.d(TAG, "Iniciando scan BLE...")
			bleScanner.startScan(null, scanSettings, scanCallback)
		} catch (e: SecurityException) {
			Log.e(TAG, "Permissão de Bluetooth negada", e)
			close()
			return@callbackFlow
		}

		// Aguarda o fechamento do Flow
		awaitClose {
			try {
				Log.d(TAG, "Parando scan BLE...")
				bleScanner.stopScan(scanCallback)
			} catch (e: SecurityException) {
				Log.e(TAG, "Erro ao parar scan", e)
			}
		}
	}

	/**
	 * Verifica se o Bluetooth está habilitado
	 */
	fun isBluetoothEnabled(): Boolean {
		return bluetoothAdapter?.isEnabled == true
	}

	/**
	 * Verifica se o dispositivo suporta BLE
	 */
	fun isBleSupported(): Boolean {
		return bluetoothAdapter != null &&
			context.packageManager.hasSystemFeature("android.hardware.bluetooth_le")
	}
}
