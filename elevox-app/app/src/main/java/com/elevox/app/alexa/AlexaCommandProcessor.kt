package com.elevox.app.alexa

import android.content.Context
import android.util.Log
import com.elevox.app.data.CommandRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Processa comandos recebidos da Alexa e executa as ações correspondentes
 */
class AlexaCommandProcessor(private val context: Context) {

	private val commandRepository = CommandRepository()
	private val scope = CoroutineScope(Dispatchers.IO)

	companion object {
		private const val TAG = "AlexaCommandProcessor"
	}

	/**
	 * Processa um comando da Alexa
	 */
	fun processCommand(command: AlexaCommand) {
		Log.i(TAG, "=== PROCESSANDO COMANDO ALEXA ===")
		Log.i(TAG, "Tipo: ${command.type}")
		Log.i(TAG, "Andar atual: ${command.currentFloor}")
		Log.i(TAG, "Andar destino: ${command.targetFloor}")
		Log.i(TAG, "Timestamp: ${command.timestamp}")

		when (command.type) {
			CommandType.CALL_ELEVATOR -> processCallElevator(command)
			CommandType.GO_TO_FLOOR -> processGoToFloor(command)
		}
	}

	/**
	 * Processa comando para chamar o elevador
	 *
	 * A Alexa já envia:
	 * - currentFloor: andar onde você ESTÁ (detectado pelo app ou manual)
	 * - targetFloor: andar para onde você QUER IR
	 */
	private fun processCallElevator(command: AlexaCommand) {
		val currentFloor = command.currentFloor
		val targetFloor = command.targetFloor ?: run {
			Log.e(TAG, "❌ Comando CALL_ELEVATOR sem targetFloor especificado")
			return
		}

		Log.i(TAG, "📞 Chamando elevador: andar $currentFloor → andar $targetFloor")

		scope.launch {
			try {
				val result = commandRepository.sendFloorRequest(
					currentFloor = currentFloor,
					targetFloor = targetFloor
				)

				if (result.isSuccess) {
					Log.i(TAG, "✅ Comando enviado com sucesso: $currentFloor → $targetFloor")
				} else {
					Log.e(TAG, "❌ Erro ao chamar elevador: ${result.exceptionOrNull()?.message}")
				}
			} catch (e: Exception) {
				Log.e(TAG, "❌ Exceção ao processar comando: ${e.message}", e)
			}
		}
	}

	/**
	 * Processa comando para ir de um andar para outro
	 *
	 * A Alexa já envia os andares corretos, basta usar diretamente
	 */
	private fun processGoToFloor(command: AlexaCommand) {
		val currentFloor = command.currentFloor
		val targetFloor = command.targetFloor ?: run {
			Log.e(TAG, "❌ Comando GO_TO_FLOOR sem targetFloor especificado")
			return
		}

		Log.i(TAG, "🎯 Indo do andar $currentFloor para o andar $targetFloor")

		scope.launch {
			try {
				val result = commandRepository.sendFloorRequest(
					currentFloor = currentFloor,
					targetFloor = targetFloor
				)

				if (result.isSuccess) {
					Log.i(TAG, "✅ Comando enviado com sucesso: $currentFloor → $targetFloor")
				} else {
					Log.e(TAG, "❌ Erro ao enviar comando: ${result.exceptionOrNull()?.message}")
				}
			} catch (e: Exception) {
				Log.e(TAG, "❌ Exceção ao processar comando: ${e.message}", e)
			}
		}
	}
}
