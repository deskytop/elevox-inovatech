package com.elevox.app.data

import com.elevox.app.net.ApiClient
import com.elevox.app.net.DadosRequest
import com.elevox.app.net.ElevatorStatus

class CommandRepository {
	/**
	 * Envia requisição com andar atual e andar de destino
	 * Envia uma única requisição com ambos os andares no formato JSON esperado pelo ESP32
	 */
	suspend fun sendFloorRequest(
		currentFloor: Int,
		targetFloor: Int
	): Result<Unit> = runCatching {
		// Envia uma única requisição com currentFloor e targetFloor
		val request = DadosRequest(
			currentFloor = currentFloor,
			targetFloor = targetFloor
		)
		val response = ApiClient.dadosApi.send(request)
		if (!response.isSuccessful) {
			error("Falha ao enviar comando. HTTP ${response.code()}")
		}
	}

	/**
	 * Consulta a posição atual do elevador no ESP32
	 * Retorna a posição REAL do elevador (onde está fisicamente)
	 */
	suspend fun getElevatorStatus(): Result<ElevatorStatus> = runCatching {
		val response = ApiClient.statusApi.getStatus()
		if (response.isSuccessful) {
			response.body() ?: error("Resposta vazia do servidor")
		} else {
			error("Falha ao consultar status. HTTP ${response.code()}")
		}
	}
}

