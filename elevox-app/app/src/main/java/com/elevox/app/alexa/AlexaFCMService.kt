package com.elevox.app.alexa

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.database.FirebaseDatabase

/**
 * Serviço FCM para receber comandos da Alexa via Push Notifications
 * Não precisa de notificação permanente - o Android acorda o app automaticamente
 */
class AlexaFCMService : FirebaseMessagingService() {

	companion object {
		private const val TAG = "AlexaFCMService"
		// Cache de comandos processados (evita duplicatas)
		private val processedCommands = mutableSetOf<String>()
		private const val MAX_CACHE_SIZE = 50
	}

	/**
	 * Chamado quando um novo token FCM é gerado
	 */
	override fun onNewToken(token: String) {
		super.onNewToken(token)
		Log.d(TAG, "🔑 Novo token FCM gerado: $token")

		// Salva o token no Firebase Realtime Database para o Lambda usar
		saveTokenToFirebase(token)
	}

	/**
	 * Chamado quando uma mensagem FCM é recebida (comando da Alexa)
	 */
	override fun onMessageReceived(remoteMessage: RemoteMessage) {
		super.onMessageReceived(remoteMessage)

		Log.i(TAG, "📨 Push notification recebida da Alexa")

		// Extrai dados do comando
		val data = remoteMessage.data

		if (data.isEmpty()) {
			Log.w(TAG, "⚠️ Mensagem sem dados")
			return
		}

		try {
			// Parse do comando
			val commandId = data["commandId"] ?: return
			val type = parseCommandType(data["type"])
			val currentFloor = data["currentFloor"]?.toIntOrNull() ?: 0
			val targetFloor = data["targetFloor"]?.toIntOrNull()
			val timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()

			val command = AlexaCommand(
				commandId = commandId,
				type = type,
				currentFloor = currentFloor,
				targetFloor = targetFloor,
				timestamp = timestamp,
				processed = false
			)

			Log.i(TAG, "📨 Comando Alexa recebido via FCM:")
			Log.i(TAG, "  ID: $commandId")
			Log.i(TAG, "  Tipo: ${command.type}")
			Log.i(TAG, "  Andar atual: ${command.currentFloor}")
			Log.i(TAG, "  Andar destino: ${command.targetFloor ?: "N/A"}")

			// Processa o comando
			val processor = AlexaCommandProcessor(this)
			processor.processCommand(command)

			// Marca como processado no Firebase (opcional)
			markAsProcessed(commandId)

		} catch (e: Exception) {
			Log.e(TAG, "❌ Erro ao processar comando FCM: ${e.message}", e)
		}
	}

	/**
	 * Parse do tipo de comando
	 */
	private fun parseCommandType(typeStr: String?): CommandType {
		return when (typeStr?.uppercase()) {
			"CALL_ELEVATOR" -> CommandType.CALL_ELEVATOR
			"GO_TO_FLOOR" -> CommandType.GO_TO_FLOOR
			else -> CommandType.CALL_ELEVATOR
		}
	}

	/**
	 * Salva token FCM no Firebase para o Lambda usar
	 */
	private fun saveTokenToFirebase(token: String) {
		try {
			val database = FirebaseDatabase.getInstance().reference
			database.child("fcm_tokens").child("default_user").setValue(token)
				.addOnSuccessListener {
					Log.d(TAG, "✅ Token FCM salvo no Firebase")
				}
				.addOnFailureListener { e ->
					Log.e(TAG, "❌ Erro ao salvar token FCM: ${e.message}")
				}
		} catch (e: Exception) {
			Log.e(TAG, "❌ Erro ao salvar token: ${e.message}", e)
		}
	}

	/**
	 * Marca comando como processado no Firebase (opcional)
	 */
	private fun markAsProcessed(commandId: String) {
		try {
			val database = FirebaseDatabase.getInstance().reference
			database.child("commands").child(commandId).child("processed").setValue(true)
				.addOnSuccessListener {
					Log.d(TAG, "✅ Comando $commandId marcado como processado")
				}
				.addOnFailureListener { e ->
					Log.e(TAG, "❌ Erro ao marcar comando: ${e.message}")
				}
		} catch (e: Exception) {
			Log.e(TAG, "❌ Erro ao marcar como processado: ${e.message}", e)
		}
	}
}
