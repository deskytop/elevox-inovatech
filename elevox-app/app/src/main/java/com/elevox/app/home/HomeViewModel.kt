package com.elevox.app.home

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elevox.app.bluetooth.BluetoothPermissionHelper
import com.elevox.app.bluetooth.BluetoothScanner
import com.elevox.app.bluetooth.FloorBeaconConfig
import com.elevox.app.bluetooth.FloorDetector
import com.elevox.app.data.CommandRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiState(
	val currentFloorNumeric: Int = 0, // 0 = Térreo, 1 = 1°, 2 = 2°, 3 = 3°
	val elevatorFloorNumeric: Int = 0, // Andar onde o elevador está
	val isSending: Boolean = false,
	val lastMessage: String? = null
) {
	// Formatação do andar atual para exibição
	val currentFloorNumber: String
		get() = when (currentFloorNumeric) {
			0 -> "Térreo"
			else -> "${currentFloorNumeric}º"
		}

	// Formatação do andar do elevador para exibição
	val elevatorFloorNumber: String
		get() = when (elevatorFloorNumeric) {
			0 -> "Térreo"
			else -> "${elevatorFloorNumeric}º"
		}

	// Verifica se o elevador está no mesmo andar que a pessoa
	val isElevatorAtCurrentFloor: Boolean
		get() = currentFloorNumeric == elevatorFloorNumeric
}

class HomeViewModel(
	private val context: Context,
	private val repository: CommandRepository = CommandRepository()
) : ViewModel() {

	private val _state = MutableStateFlow(HomeUiState())
	val state: StateFlow<HomeUiState> = _state

	private val prefs: SharedPreferences = context.getSharedPreferences("elevox_settings", Context.MODE_PRIVATE)
	private val bluetoothScanner = BluetoothScanner(context)
	private val floorDetector = FloorDetector()
	private var scanJob: Job? = null
	private var pollingJob: Job? = null

	companion object {
		private const val TAG = "HomeViewModel"
		private const val AUTO_DETECTION_KEY = "auto_detection_enabled"
		private const val MANUAL_FLOOR_KEY = "manual_floor"
		private const val DETECTED_FLOOR_KEY = "detected_floor"
		private const val LAST_DETECTION_TIME_KEY = "last_detection_time"
		private const val POLLING_INTERVAL_MS = 2000L // 2 segundos
	}

	init {
		// Carrega andar inicial das configurações
		loadCurrentFloorFromSettings()

		// Inicia detecção da posição da pessoa (Bluetooth)
		startFloorDetection()

		// Inicia polling da posição do elevador (ESP32)
		startElevatorPolling()
	}

	/**
	 * Carrega o andar atual das configurações do SharedPreferences
	 */
	private fun loadCurrentFloorFromSettings() {
		val manualFloor = prefs.getInt(MANUAL_FLOOR_KEY, 0)
		_state.value = _state.value.copy(currentFloorNumeric = manualFloor)
	}

	/**
	 * Inicia a detecção contínua do andar via Bluetooth
	 * Scan ocorre APENAS quando a tela está visível e auto-detecção está ativa
	 */
	private fun startFloorDetection() {
		scanJob?.cancel()
		scanJob = viewModelScope.launch {
			while (isActive) {
				val autoDetectionEnabled = prefs.getBoolean(AUTO_DETECTION_KEY, true)
				val manualFloor = prefs.getInt(MANUAL_FLOOR_KEY, 0)

				if (autoDetectionEnabled) {
					// Verifica permissões antes de escanear
					if (BluetoothPermissionHelper.hasAllPermissions(context)) {
						performBluetoothScan()
					} else {
						// Sem permissões, usa andar manual como fallback
						Log.w(TAG, "⚠️ Sem permissões Bluetooth, usando modo manual")
						_state.value = _state.value.copy(currentFloorNumeric = manualFloor)
					}
				} else {
					// Modo manual
					_state.value = _state.value.copy(currentFloorNumeric = manualFloor)
				}

				// Aguarda intervalo entre scans
				delay(FloorBeaconConfig.SCAN_INTERVAL_MS)
			}
		}
	}

	/**
	 * Inicia polling contínuo para consultar a posição do elevador no ESP32
	 * Consulta GET /status a cada 2 segundos
	 */
	private fun startElevatorPolling() {
		pollingJob?.cancel()
		pollingJob = viewModelScope.launch {
			while (isActive) {
				try {
					val result = repository.getElevatorStatus()
					if (result.isSuccess) {
						val status = result.getOrNull()
						if (status != null) {
							// Atualiza posição do elevador na UI
							_state.value = _state.value.copy(elevatorFloorNumeric = status.currentFloor)
							Log.d(TAG, "📍 Posição do elevador atualizada: Andar ${status.currentFloor} (${status.status})")
						}
					} else {
						Log.w(TAG, "⚠️ Erro ao consultar status do elevador: ${result.exceptionOrNull()?.message}")
					}
				} catch (e: Exception) {
					Log.e(TAG, "❌ Exceção no polling: ${e.message}", e)
				}

				// Aguarda intervalo entre consultas
				delay(POLLING_INTERVAL_MS)
			}
		}
	}

	/**
	 * Realiza um scan Bluetooth para detectar o andar atual
	 */
	private suspend fun performBluetoothScan() {
		Log.i(TAG, "🔍 Iniciando scan Bluetooth...")

		if (!bluetoothScanner.isBluetoothEnabled()) {
			Log.w(TAG, "⚠️ Bluetooth desativado")
			return
		}

		var detectedFloor: Int? = null
		val scanDuration = FloorBeaconConfig.SCAN_DURATION_MS

		// Coleta resultados do scan
		val collectJob = viewModelScope.launch {
			bluetoothScanner.startScanning()
				.catch { e ->
					Log.e(TAG, "❌ Erro no scan: ${e.message}", e)
				}
				.collect { devices ->
					Log.d(TAG, "📡 Beacons detectados: ${devices.size}")

					val floor = floorDetector.detectFloor(devices)
					if (floor != null) {
						detectedFloor = floor
						Log.i(TAG, "🎯 Andar detectado: ${formatFloorName(floor)}")
					}
				}
		}

		// Aguarda duração do scan
		delay(scanDuration)
		collectJob.cancel()

		// Atualiza estado com andar detectado
		if (detectedFloor != null) {
			_state.value = _state.value.copy(currentFloorNumeric = detectedFloor!!)
			
			// Salva detecção com timestamp para uso da Alexa
			saveDetectedFloor(detectedFloor!!)
			
			Log.i(TAG, "✅ Andar atualizado: ${formatFloorName(detectedFloor!!)}")
		} else {
			Log.w(TAG, "⚠️ Nenhum andar detectado neste scan")
		}
	}

	/**
	 * Salva o andar detectado com timestamp
	 */
	private fun saveDetectedFloor(floor: Int) {
		prefs.edit()
			.putInt(DETECTED_FLOOR_KEY, floor)
			.putLong(LAST_DETECTION_TIME_KEY, System.currentTimeMillis())
			.apply()
		
		Log.d(TAG, "💾 Andar $floor salvo com timestamp")
	}

	/**
	 * Formata nome do andar para exibição
	 */
	private fun formatFloorName(floor: Int): String {
		return when (floor) {
			0 -> "Térreo"
			else -> "${floor}º andar"
		}
	}

	/**
	 * Chamado quando o usuário seleciona um andar de destino
	 * Envia: andar atual + andar selecionado
	 */
	fun onFloorSelected(targetFloor: Int) {
		if (_state.value.isSending) return

		// Proteção: não permite chamar elevador para o andar atual
		if (_state.value.currentFloorNumeric == targetFloor) {
			Log.w(TAG, "⚠️ Tentativa de chamar elevador para o andar atual ($targetFloor) - bloqueado")
			_state.value = _state.value.copy(lastMessage = "Você já está neste andar")
			return
		}

		_state.value = _state.value.copy(isSending = true, lastMessage = null)

		viewModelScope.launch {
			// Envia os dois dados: andar atual e andar selecionado
			val result = repository.sendFloorRequest(
				currentFloor = _state.value.currentFloorNumeric,
				targetFloor = targetFloor
			)

			val floorName = when (targetFloor) {
				0 -> "Térreo"
				else -> "${targetFloor}º andar"
			}

			_state.value = if (result.isSuccess) {
				_state.value.copy(
					isSending = false,
					lastMessage = "Elevador chamado para o $floorName"
				)
			} else {
				_state.value.copy(
					isSending = false,
					lastMessage = result.exceptionOrNull()?.message ?: "Falha ao chamar elevador"
				)
			}
		}
	}

	/**
	 * Atualiza manualmente o andar atual (para testes)
	 */
	fun updateCurrentFloor(floor: Int) {
		_state.value = _state.value.copy(currentFloorNumeric = floor)
	}

	/**
	 * Atualiza a posição do elevador recebida do ESP32
	 */
	fun updateElevatorFloor(floor: Int) {
		_state.value = _state.value.copy(elevatorFloorNumeric = floor)
	}

	/**
	 * Limpa a última mensagem exibida (Snackbar)
	 */
	fun clearLastMessage() {
		_state.value = _state.value.copy(lastMessage = null)
	}

	/**
	 * Cancela scan e polling quando ViewModel é destruído
	 */
	override fun onCleared() {
		super.onCleared()
		scanJob?.cancel()
		pollingJob?.cancel()
		Log.d(TAG, "🛑 ViewModel destruído, scan Bluetooth e polling cancelados")
	}
}


