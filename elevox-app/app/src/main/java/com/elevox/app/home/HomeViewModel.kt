package com.elevox.app.home

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elevox.app.data.CommandRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
			else -> "${currentFloorNumeric}°"
		}

	// Formatação do andar do elevador para exibição
	val elevatorFloorNumber: String
		get() = when (elevatorFloorNumeric) {
			0 -> "Térreo"
			else -> "${elevatorFloorNumeric}°"
		}

	// Verifica se o elevador está no mesmo andar que a pessoa
	val isElevatorAtCurrentFloor: Boolean
		get() = currentFloorNumeric == elevatorFloorNumeric
}

class HomeViewModel(
	context: Context,
	private val repository: CommandRepository = CommandRepository()
) : ViewModel() {

	private val _state = MutableStateFlow(HomeUiState())
	val state: StateFlow<HomeUiState> = _state

	private val prefs: SharedPreferences = context.getSharedPreferences("elevox_settings", Context.MODE_PRIVATE)

	companion object {
		private const val AUTO_DETECTION_KEY = "auto_detection_enabled"
		private const val MANUAL_FLOOR_KEY = "manual_floor"
	}

	init {
		// Carrega andar inicial das configurações
		loadCurrentFloorFromSettings()
		startFloorDetection()

		// PARA TESTAR: Descomente uma das linhas abaixo para simular diferentes cenários
		// Cenário 1: Pessoa no Térreo, Elevador no Térreo (mesma posição)
		// _state.value = _state.value.copy(currentFloorNumeric = 0, elevatorFloorNumeric = 0)

		// Cenário 2: Pessoa no 1° andar, Elevador no Térreo (posições diferentes)
		// _state.value = _state.value.copy(currentFloorNumeric = 1, elevatorFloorNumeric = 0)

		// Cenário 3: Pessoa no 2° andar, Elevador no 3° andar
		// _state.value = _state.value.copy(currentFloorNumeric = 2, elevatorFloorNumeric = 3)
	}

	/**
	 * Carrega o andar atual das configurações do SharedPreferences
	 */
	private fun loadCurrentFloorFromSettings() {
		val manualFloor = prefs.getInt(MANUAL_FLOOR_KEY, 0)
		_state.value = _state.value.copy(currentFloorNumeric = manualFloor)
	}

	/**
	 * Inicia a detecção contínua do andar atual em background
	 * Lê as configurações do SharedPreferences a cada 2 segundos
	 */
	private fun startFloorDetection() {
		viewModelScope.launch {
			while (isActive) {
				// Lê configurações do SharedPreferences
				val autoDetectionEnabled = prefs.getBoolean(AUTO_DETECTION_KEY, true)
				val manualFloor = prefs.getInt(MANUAL_FLOOR_KEY, 0)

				if (autoDetectionEnabled) {
					// TODO: Implementar detecção Bluetooth no futuro
					// Por enquanto usa o valor manual mesmo com auto-detection ativado
					_state.value = _state.value.copy(currentFloorNumeric = manualFloor)
				} else {
					// Modo manual: usa o andar configurado
					_state.value = _state.value.copy(currentFloorNumeric = manualFloor)
				}

				delay(2000) // Verifica a cada 2 segundos
			}
		}
	}

	/**
	 * Chamado quando o usuário seleciona um andar de destino
	 * Envia: andar atual + andar selecionado
	 */
	fun onFloorSelected(targetFloor: Int) {
		if (_state.value.isSending) return

		_state.value = _state.value.copy(isSending = true, lastMessage = null)

		viewModelScope.launch {
			// Envia os dois dados: andar atual e andar selecionado
			val result = repository.sendFloorRequest(
				currentFloor = _state.value.currentFloorNumeric,
				targetFloor = targetFloor
			)

			val floorName = when (targetFloor) {
				0 -> "Térreo"
				else -> "${targetFloor}° andar"
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
}


