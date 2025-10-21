package com.elevox.app.home

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
	private val repository: CommandRepository = CommandRepository()
) : ViewModel() {

	private val _state = MutableStateFlow(HomeUiState())
	val state: StateFlow<HomeUiState> = _state

	init {
		startFloorDetection()

		// PARA TESTAR: Descomente uma das linhas abaixo para simular diferentes cenários
		// Cenário 1: Pessoa no Térreo, Elevador no Térreo (mesma posição - card azul)
		// _state.value = _state.value.copy(currentFloorNumeric = 0, elevatorFloorNumeric = 0)

		// Cenário 2: Pessoa no 1° andar, Elevador no Térreo (posições diferentes - card escuro clicável)
		// _state.value = _state.value.copy(currentFloorNumeric = 1, elevatorFloorNumeric = 0)

		// Cenário 3: Pessoa no 2° andar, Elevador no 3° andar
		// _state.value = _state.value.copy(currentFloorNumeric = 2, elevatorFloorNumeric = 3)
	}

	/**
	 * Inicia a detecção contínua do andar atual em background
	 * Por enquanto, simula a detecção. Futuramente pode usar sensores/beacons.
	 */
	private fun startFloorDetection() {
		viewModelScope.launch {
			while (isActive) {
				// TODO: Implementar detecção real de andar usando sensores/beacons
				// Por enquanto, mantém o valor atual
				delay(5000) // Verifica a cada 5 segundos
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
}


