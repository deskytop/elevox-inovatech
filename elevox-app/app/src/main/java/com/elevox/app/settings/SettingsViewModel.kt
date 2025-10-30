package com.elevox.app.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
	val autoDetectionEnabled: Boolean = true,
	val manualFloor: Int = 0, // 0 = Térreo, 1 = 1°, 2 = 2°, 3 = 3°
	val isSaving: Boolean = false
)

class SettingsViewModel(
	context: Context
) : ViewModel() {

	private val _state = MutableStateFlow(SettingsUiState())
	val state: StateFlow<SettingsUiState> = _state

	private val prefs: SharedPreferences = context.getSharedPreferences("elevox_settings", Context.MODE_PRIVATE)

	companion object {
		private const val AUTO_DETECTION_KEY = "auto_detection_enabled"
		private const val MANUAL_FLOOR_KEY = "manual_floor"
	}

	init {
		loadSettings()
		syncCurrentFloorToFirebase()
	}

	/**
	 * Carrega as configurações salvas do SharedPreferences
	 */
	private fun loadSettings() {
		_state.value = SettingsUiState(
			autoDetectionEnabled = prefs.getBoolean(AUTO_DETECTION_KEY, true),
			manualFloor = prefs.getInt(MANUAL_FLOOR_KEY, 0)
		)
	}

	/**
	 * Sincroniza o andar atual com o Firebase ao iniciar o app
	 */
	private fun syncCurrentFloorToFirebase() {
		viewModelScope.launch {
			try {
				val currentFloor = _state.value.manualFloor
				val database = FirebaseDatabase.getInstance().reference
				database.child("user_status").child("default_user").child("current_floor").setValue(currentFloor)
					.addOnSuccessListener {
						android.util.Log.d("SettingsViewModel", "Andar inicial sincronizado com Firebase: $currentFloor")
					}
					.addOnFailureListener { error ->
						android.util.Log.e("SettingsViewModel", "Erro ao sincronizar andar inicial com Firebase", error)
					}
			} catch (e: Exception) {
				android.util.Log.e("SettingsViewModel", "Exceção ao sincronizar andar inicial com Firebase", e)
			}
		}
	}

	/**
	 * Alterna entre detecção automática e manual
	 */
	fun toggleAutoDetection(enabled: Boolean) {
		_state.value = _state.value.copy(
			autoDetectionEnabled = enabled,
			isSaving = true
		)

		viewModelScope.launch {
			prefs.edit().putBoolean(AUTO_DETECTION_KEY, enabled).apply()
			_state.value = _state.value.copy(isSaving = false)
		}
	}

	/**
	 * Define o andar manual quando detecção automática está desativada
	 */
	fun setManualFloor(floor: Int) {
		_state.value = _state.value.copy(
			manualFloor = floor,
			isSaving = true
		)

		viewModelScope.launch {
			// Salva localmente
			prefs.edit().putInt(MANUAL_FLOOR_KEY, floor).apply()

			// Sincroniza com Firebase para a Alexa poder consultar
			try {
				val database = FirebaseDatabase.getInstance().reference
				database.child("user_status").child("default_user").child("current_floor").setValue(floor)
					.addOnSuccessListener {
						android.util.Log.d("SettingsViewModel", "Andar manual sincronizado com Firebase: $floor")
					}
					.addOnFailureListener { error ->
						android.util.Log.e("SettingsViewModel", "Erro ao sincronizar andar com Firebase", error)
					}
			} catch (e: Exception) {
				android.util.Log.e("SettingsViewModel", "Exceção ao sincronizar andar com Firebase", e)
			}

			_state.value = _state.value.copy(isSaving = false)
		}
	}

	/**
	 * Retorna o andar atual baseado na configuração
	 * (manual ou automático)
	 */
	fun getCurrentFloor(): Int {
		return if (_state.value.autoDetectionEnabled) {
			// TODO: Implementar detecção Bluetooth aqui no futuro
			// Por enquanto retorna o manual
			_state.value.manualFloor
		} else {
			_state.value.manualFloor
		}
	}
}
