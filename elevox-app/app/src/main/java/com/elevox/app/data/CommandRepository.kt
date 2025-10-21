package com.elevox.app.data

import com.elevox.app.net.ApiClient
import com.elevox.app.net.DadosRequest

class CommandRepository {
	/**
	 * Envia requisição com andar atual e andar de destino
	 * São enviados como duas requisições separadas conforme especificado
	 */
	suspend fun sendFloorRequest(
		currentFloor: Int,
		targetFloor: Int
	): Result<Unit> = runCatching {
		// Primeira requisição: envia andar atual
		val currentFloorRequest = DadosRequest(valor = currentFloor)
		val currentResp = ApiClient.dadosApi.send(currentFloorRequest)
		if (!currentResp.isSuccessful) {
			error("Falha ao enviar andar atual. HTTP ${currentResp.code()}")
		}

		// Segunda requisição: envia andar de destino
		val targetFloorRequest = DadosRequest(valor = targetFloor)
		val targetResp = ApiClient.dadosApi.send(targetFloorRequest)
		if (!targetResp.isSuccessful) {
			error("Falha ao enviar andar de destino. HTTP ${targetResp.code()}")
		}
	}

	/**
	 * Método legado de teste - mantido para compatibilidade
	 */
	suspend fun ping(): Result<Unit> = runCatching {
		val body = DadosRequest(valor = 123)
		val resp = ApiClient.dadosApi.send(body)
		if (!resp.isSuccessful) error("HTTP ${resp.code()}")
	}
}

