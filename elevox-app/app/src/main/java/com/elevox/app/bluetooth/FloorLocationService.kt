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

		// Verifica se tem as permissões necessárias
		if (!BluetoothPermissionHelper.hasAllPermissions(this)) {
			Log.e(TAG, "Permissões necessárias não foram concedidas. Parando serviço.")
			stopSelf()
			return START_NOT_STICKY
		}

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
		Log.i(TAG, "=== INICIANDO NOVO SCAN ===")

		if (!bluetoothScanner.isBluetoothEnabled()) {
			Log.w(TAG, "⚠️ Bluetooth desativado - pulando scan")
			updateNotification("Bluetooth desativado")
			return
		}

		if (!bluetoothScanner.isBleSupported()) {
			Log.e(TAG, "❌ BLE não suportado neste dispositivo - parando serviço")
			stopSelf()
			return
		}

		// Verifica permissões novamente (podem ter sido revogadas)
		if (!BluetoothPermissionHelper.hasAllPermissions(this)) {
			Log.e(TAG, "❌ Permissões foram revogadas - parando serviço")
			stopSelf()
			return
		}

		Log.d(TAG, "✓ Bluetooth ativo, BLE suportado, permissões OK")
		updateNotification("Detectando andar...")

		var detectedFloor: Int? = null
		val scanDuration = FloorBeaconConfig.SCAN_DURATION_MS
		var deviceCount = 0

		// Coleta resultados do scan por X segundos
		val collectJob = serviceScope.launch {
			bluetoothScanner.startScanning()
				.catch { e ->
					Log.e(TAG, "❌ Erro no scan: ${e.message}", e)
				}
				.collect { devices ->
					deviceCount = devices.size
					Log.d(TAG, "📡 Beacons detectados neste scan: $deviceCount")

					// Atualiza a detecção com os dispositivos encontrados
					val floor = floorDetector.detectFloor(devices)
					if (floor != null) {
						detectedFloor = floor
						Log.i(TAG, "🎯 Andar identificado: ${formatFloorName(floor)}")
					}

					// Log de debug detalhado
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
			Log.i(TAG, "✅ RESULTADO: Andar $floorName salvo (total de $deviceCount beacons detectados)")
		} else {
			if (deviceCount > 0) {
				Log.w(TAG, "⚠️ RESULTADO: $deviceCount beacons detectados mas nenhum andar identificado (RSSI muito fraco?)")
			} else {
				Log.w(TAG, "⚠️ RESULTADO: Nenhum beacon detectado neste scan")
			}
			updateNotification("Procurando beacons...")
		}

		Log.i(TAG, "=== FIM DO SCAN ===\n")
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
