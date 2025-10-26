package com.elevox.app.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.elevox.app.MainActivity
import com.elevox.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serviço em foreground para detecção contínua do andar via Bluetooth
 * Executa scan BLE periodicamente e atualiza o andar detectado no SharedPreferences
 */
class FloorLocationService : Service() {

	private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
	private lateinit var bluetoothScanner: BluetoothScanner
	private lateinit var floorDetector: FloorDetector
	private lateinit var prefs: SharedPreferences

	private var scanJob: Job? = null

	companion object {
		private const val TAG = "FloorLocationService"
		private const val NOTIFICATION_ID = 1001
		private const val CHANNEL_ID = "floor_detection_channel"
		const val DETECTED_FLOOR_KEY = "detected_floor"
		const val LAST_DETECTION_TIME_KEY = "last_detection_time"

		/**
		 * Inicia o serviço de detecção de andar
		 */
		fun start(context: Context) {
			val intent = Intent(context, FloorLocationService::class.java)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				context.startForegroundService(intent)
			} else {
				context.startService(intent)
			}
		}

		/**
		 * Para o serviço de detecção de andar
		 */
		fun stop(context: Context) {
			val intent = Intent(context, FloorLocationService::class.java)
			context.stopService(intent)
		}
	}

	override fun onCreate() {
		super.onCreate()
		Log.d(TAG, "Service onCreate")

		bluetoothScanner = BluetoothScanner(this)
		floorDetector = FloorDetector()
		prefs = getSharedPreferences("elevox_settings", Context.MODE_PRIVATE)

		createNotificationChannel()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		Log.d(TAG, "Service onStartCommand")

		// Inicia o serviço em foreground com notificação
		startForeground(NOTIFICATION_ID, createNotification())

		// Inicia o scan contínuo
		startContinuousScanning()

		return START_STICKY
	}

	override fun onDestroy() {
		Log.d(TAG, "Service onDestroy")
		scanJob?.cancel()
		serviceScope.cancel()
		super.onDestroy()
	}

	override fun onBind(intent: Intent?): IBinder? = null

	/**
	 * Inicia o scan contínuo de dispositivos Bluetooth
	 * Executa scan periodicamente e atualiza o andar detectado
	 */
	private fun startContinuousScanning() {
		scanJob?.cancel()
		scanJob = serviceScope.launch {
			while (isActive) {
				performScan()
				delay(FloorBeaconConfig.SCAN_INTERVAL_MS)
			}
		}
	}

	/**
	 * Realiza um scan de dispositivos Bluetooth
	 */
	private suspend fun performScan() {
		if (!bluetoothScanner.isBluetoothEnabled()) {
			Log.w(TAG, "Bluetooth desativado")
			updateNotification("Bluetooth desativado")
			return
		}

		if (!bluetoothScanner.isBleSupported()) {
			Log.w(TAG, "BLE não suportado neste dispositivo")
			stopSelf()
			return
		}

		Log.d(TAG, "Iniciando scan...")
		updateNotification("Detectando andar...")

		var detectedFloor: Int? = null
		val scanDuration = FloorBeaconConfig.SCAN_DURATION_MS

		// Coleta resultados do scan por X segundos
		val collectJob = serviceScope.launch {
			bluetoothScanner.startScanning()
				.catch { e ->
					Log.e(TAG, "Erro no scan", e)
				}
				.collect { devices ->
					Log.d(TAG, "Dispositivos detectados: ${devices.size}")

					// Atualiza a detecção com os dispositivos encontrados
					val floor = floorDetector.detectFloor(devices)
					if (floor != null) {
						detectedFloor = floor
					}

					// Log de debug
					if (devices.isNotEmpty()) {
						Log.d(TAG, floorDetector.getDebugInfo(devices))
					}
				}
		}

		// Aguarda a duração do scan
		delay(scanDuration)
		collectJob.cancel()

		// Atualiza o SharedPreferences com o resultado
		if (detectedFloor != null) {
			saveDetectedFloor(detectedFloor!!)
			val floorName = formatFloorName(detectedFloor!!)
			updateNotification("Andar detectado: $floorName")
			Log.d(TAG, "Andar detectado: $floorName")
		} else {
			Log.d(TAG, "Nenhum andar detectado neste scan")
			updateNotification("Procurando beacons...")
		}
	}

	/**
	 * Salva o andar detectado no SharedPreferences
	 */
	private fun saveDetectedFloor(floor: Int) {
		prefs.edit()
			.putInt(DETECTED_FLOOR_KEY, floor)
			.putLong(LAST_DETECTION_TIME_KEY, System.currentTimeMillis())
			.apply()
	}

	/**
	 * Formata o nome do andar para exibição
	 */
	private fun formatFloorName(floor: Int): String {
		return when (floor) {
			0 -> "Térreo"
			else -> "${floor}° andar"
		}
	}

	/**
	 * Cria o canal de notificação (necessário para Android 8+)
	 */
	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				CHANNEL_ID,
				"Detecção de Andar",
				NotificationManager.IMPORTANCE_LOW
			).apply {
				description = "Notificação para detecção contínua de andar via Bluetooth"
				setShowBadge(false)
			}

			val notificationManager = getSystemService(NotificationManager::class.java)
			notificationManager.createNotificationChannel(channel)
		}
	}

	/**
	 * Cria a notificação para o foreground service
	 */
	private fun createNotification(contentText: String = "Detectando andar..."): Notification {
		val intent = Intent(this, MainActivity::class.java)
		val pendingIntent = PendingIntent.getActivity(
			this,
			0,
			intent,
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
		)

		return NotificationCompat.Builder(this, CHANNEL_ID)
			.setContentTitle("Elevox - Auto-detecção ativa")
			.setContentText(contentText)
			.setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: trocar por ícone personalizado
			.setContentIntent(pendingIntent)
			.setOngoing(true)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.setCategory(NotificationCompat.CATEGORY_SERVICE)
			.build()
	}

	/**
	 * Atualiza o texto da notificação
	 */
	private fun updateNotification(contentText: String) {
		val notification = createNotification(contentText)
		val notificationManager = getSystemService(NotificationManager::class.java)
		notificationManager.notify(NOTIFICATION_ID, notification)
	}
}
