package com.elevox.app.alexa

/**
 * Representa um comando recebido da Alexa via Firebase
 */
data class AlexaCommand(
	val commandId: String = "",
	val type: CommandType = CommandType.CALL_ELEVATOR,
	val currentFloor: Int = 0,
	val targetFloor: Int? = null,
	val timestamp: Long = System.currentTimeMillis(),
	val processed: Boolean = false
)

/**
 * Tipos de comandos que a Alexa pode enviar
 */
enum class CommandType {
	/**
	 * Apenas chamar o elevador para o andar atual
	 */
	CALL_ELEVATOR,

	/**
	 * Chamar o elevador e ir para um andar específico
	 */
	GO_TO_FLOOR
}
