package com.elevox.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
	viewModel: SettingsViewModel,
	onBackClick: () -> Unit
) {
	val state by viewModel.state.collectAsState()

	Scaffold(
		topBar = {
			TopAppBar(
				title = {
					Text(
						text = "Configurações",
						color = Color.White,
						fontSize = 20.sp,
						fontWeight = FontWeight.Bold
					)
				},
				navigationIcon = {
					IconButton(onClick = onBackClick) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBack,
							contentDescription = "Voltar",
							tint = Color.White
						)
					}
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = Color.Transparent
				)
			)
		}
	) { padding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.background(
					Brush.verticalGradient(
						colors = listOf(
							Color(0xFF001133),
							Color(0xFF000816)
						)
					)
				)
				.padding(padding)
				.padding(horizontal = 24.dp, vertical = 16.dp),
			verticalArrangement = Arrangement.spacedBy(20.dp)
		) {
			// Card: Detecção Automática
			Card(
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(20.dp),
				colors = CardDefaults.cardColors(
					containerColor = Color(0xFF0D1D35)
				)
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(20.dp)
				) {
					Text(
						text = "Detecção de Andar",
						color = Color.White,
						fontSize = 18.sp,
						fontWeight = FontWeight.Bold
					)
					Spacer(modifier = Modifier.height(8.dp))
					Text(
						text = "Identifica automaticamente o andar em que você está via Bluetooth",
						color = Color(0xFF8B9BB3),
						fontSize = 14.sp,
						lineHeight = 20.sp
					)
					Spacer(modifier = Modifier.height(16.dp))

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Text(
							text = "Detecção Automática",
							color = Color.White,
							fontSize = 16.sp,
							fontWeight = FontWeight.Medium
						)
						Switch(
							checked = state.autoDetectionEnabled,
							onCheckedChange = { viewModel.toggleAutoDetection(it) },
							colors = SwitchDefaults.colors(
								checkedThumbColor = Color.White,
								checkedTrackColor = Color(0xFF0066FF),
								uncheckedThumbColor = Color(0xFF6B7A99),
								uncheckedTrackColor = Color(0xFF1A2A40)
							)
						)
					}
				}
			}

			// Card: Seleção Manual (só aparece se automática estiver desativada)
			if (!state.autoDetectionEnabled) {
				Card(
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(20.dp),
					colors = CardDefaults.cardColors(
						containerColor = Color(0xFF0D1D35)
					)
				) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(20.dp)
					) {
						Text(
							text = "Seleção Manual do Andar",
							color = Color.White,
							fontSize = 18.sp,
							fontWeight = FontWeight.Bold
						)
						Spacer(modifier = Modifier.height(8.dp))
						Text(
							text = "Selecione manualmente o andar em que você está",
							color = Color(0xFF8B9BB3),
							fontSize = 14.sp
						)
						Spacer(modifier = Modifier.height(16.dp))

						// Opções de andar
						FloorOption(
							label = "3° andar",
							selected = state.manualFloor == 3,
							onClick = { viewModel.setManualFloor(3) }
						)
						FloorOption(
							label = "2° andar",
							selected = state.manualFloor == 2,
							onClick = { viewModel.setManualFloor(2) }
						)
						FloorOption(
							label = "1° andar",
							selected = state.manualFloor == 1,
							onClick = { viewModel.setManualFloor(1) }
						)
						FloorOption(
							label = "Térreo",
							selected = state.manualFloor == 0,
							onClick = { viewModel.setManualFloor(0) }
						)
					}
				}
			}

			// Informação sobre modo atual
			Card(
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(20.dp),
				colors = CardDefaults.cardColors(
					containerColor = Color(0xFF0066FF).copy(alpha = 0.2f)
				)
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = "ℹ️",
						fontSize = 24.sp,
						modifier = Modifier.padding(end = 12.dp)
					)
					Column {
						Text(
							text = if (state.autoDetectionEnabled) {
								"Modo Automático Ativo"
							} else {
								"Modo Manual Ativo"
							},
							color = Color.White,
							fontSize = 16.sp,
							fontWeight = FontWeight.Bold
						)
						Text(
							text = if (state.autoDetectionEnabled) {
								"O app detectará automaticamente seu andar (requer Bluetooth ativo)"
							} else {
								"Você selecionou manualmente: ${formatFloor(state.manualFloor)}"
							},
							color = Color(0xFFD0DDEC),
							fontSize = 13.sp,
							lineHeight = 18.sp
						)
					}
				}
			}
		}
	}
}

@Composable
private fun FloorOption(
	label: String,
	selected: Boolean,
	onClick: () -> Unit
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		RadioButton(
			selected = selected,
			onClick = onClick,
			colors = RadioButtonDefaults.colors(
				selectedColor = Color(0xFF0066FF),
				unselectedColor = Color(0xFF6B7A99)
			)
		)
		Text(
			text = label,
			color = if (selected) Color.White else Color(0xFF8B9BB3),
			fontSize = 16.sp,
			fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
			modifier = Modifier.padding(start = 8.dp)
		)
	}
}

private fun formatFloor(floor: Int): String {
	return when (floor) {
		0 -> "Térreo"
		else -> "${floor}° andar"
	}
}
