package com.elevox.app.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
	viewModel: HomeViewModel = viewModel(),
	onSettingsClick: () -> Unit = {}
) {
	val state by viewModel.state.collectAsState()
	val snackbarHostState = remember { SnackbarHostState() }
	val context = LocalContext.current

	// Verifica se auto-detecção está ativa
	val prefs = context.getSharedPreferences("elevox_settings", Context.MODE_PRIVATE)
	val isAutoDetectionEnabled = prefs.getBoolean("auto_detection_enabled", true)

	// Limpa a mensagem quando a tela é recomposta (volta das configurações)
	LaunchedEffect(Unit) {
		viewModel.clearLastMessage()
	}

	// Mostra Snackbar quando há nova mensagem
	LaunchedEffect(state.lastMessage) {
		state.lastMessage?.let { snackbarHostState.showSnackbar(it) }
	}

	Scaffold(
		snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
	) { padding ->
		Box(
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
		) {
			// Botão de configurações no topo direito
			IconButton(
				onClick = onSettingsClick,
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(16.dp)
					.semantics { contentDescription = "Configurações" }
			) {
				Icon(
					imageVector = Icons.Default.Settings,
					contentDescription = "Configurações",
					tint = Color.White,
					modifier = Modifier.size(28.dp)
				)
			}

			// Conteúdo principal - SEM card envolvente
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.align(Alignment.Center)
					.padding(horizontal = 24.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(24.dp)
			) {
				// Andar atual
				Card(
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(28.dp),
					colors = CardDefaults.cardColors(
						containerColor = if (isAutoDetectionEnabled) {
							Color(0xFF0D1D35) // Cor normal para auto-detecção
						} else {
							Color(0xFF1A2A40) // Cor diferente para modo manual
						}
					),
					elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
				) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 28.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(12.dp)
					) {
						// Cabeçalho com título e badge
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.Center
						) {
							Text(
								text = "ANDAR ATUAL",
								color = Color(0xFFD0DDEC),
								fontSize = 16.sp,
								fontWeight = FontWeight.Bold,
								letterSpacing = 1.5.sp
							)

							// Badge AUTO quando auto-detecção está ativa
							if (isAutoDetectionEnabled) {
								Spacer(modifier = Modifier.width(8.dp))
								Surface(
									shape = RoundedCornerShape(8.dp),
									color = Color(0xFF0066FF).copy(alpha = 0.3f),
									modifier = Modifier.border(
										width = 1.dp,
										color = Color(0xFF0066FF),
										shape = RoundedCornerShape(8.dp)
									)
								) {
									Row(
										modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(4.dp)
									) {
										// Ícone Bluetooth
										Text(
											text = "⚡",
											fontSize = 12.sp,
											color = Color(0xFF0066FF)
										)
										Text(
											text = "AUTO",
											fontSize = 11.sp,
											fontWeight = FontWeight.Bold,
											color = Color(0xFF0066FF),
											letterSpacing = 0.5.sp
										)
									}
								}
							}
						}

						// Exibe apenas o número/nome do andar
						Text(
							text = state.currentFloorNumber,
							color = Color.White,
							fontSize = 56.sp,
							fontWeight = FontWeight.Bold
						)

						// Texto indicativo do modo
						if (!isAutoDetectionEnabled) {
							Text(
								text = "Modo manual",
								color = Color(0xFF8B9BB3),
								fontSize = 13.sp,
								fontWeight = FontWeight.Normal
							)
						}
					}
				}

				Spacer(modifier = Modifier.height(4.dp))

				// Título "CHAMAR ELEVADOR" com linha embaixo
				Column(
					horizontalAlignment = Alignment.CenterHorizontally,
					modifier = Modifier.fillMaxWidth()
				) {
					Text(
						text = "CHAMAR ELEVADOR",
						color = Color(0xFFD0DDEC),
						fontSize = 20.sp,
						fontWeight = FontWeight.Bold,
						letterSpacing = 1.5.sp
					)
					Spacer(modifier = Modifier.height(6.dp))
					HorizontalDivider(
						modifier = Modifier.width(170.dp),
						thickness = 2.dp,
						color = Color(0xFF6B7A99)
					)
				}

				Spacer(modifier = Modifier.height(12.dp))

				// Botões de andar - TODOS sempre aparecem
				Column(
					verticalArrangement = Arrangement.spacedBy(16.dp),
					modifier = Modifier.fillMaxWidth()
				) {
					FloorButton(
						floor = "3°",
						isElevatorHere = state.elevatorFloorNumeric == 3,
						enabled = !state.isSending,
						onClick = { viewModel.onFloorSelected(3) }
					)
					FloorButton(
						floor = "2°",
						isElevatorHere = state.elevatorFloorNumeric == 2,
						enabled = !state.isSending,
						onClick = { viewModel.onFloorSelected(2) }
					)
					FloorButton(
						floor = "1°",
						isElevatorHere = state.elevatorFloorNumeric == 1,
						enabled = !state.isSending,
						onClick = { viewModel.onFloorSelected(1) }
					)
					FloorButton(
						floor = "Térreo",
						isElevatorHere = state.elevatorFloorNumeric == 0,
						enabled = !state.isSending,
						onClick = { viewModel.onFloorSelected(0) }
					)
				}
			}
		}
	}
}

@Composable
private fun FloorButton(
	floor: String,
	isElevatorHere: Boolean,
	enabled: Boolean,
	onClick: () -> Unit
) {
	Box(
		modifier = Modifier.fillMaxWidth(),
		contentAlignment = Alignment.Center
	) {
		// Camada de glow forte (apenas nos botões azuis)
		if (!isElevatorHere) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 8.dp)
					.height(86.dp)
					.background(
						brush = Brush.radialGradient(
							colors = listOf(
								Color(0xFF0066FF).copy(alpha = 0.6f),
								Color(0xFF0066FF).copy(alpha = 0.3f),
								Color(0xFF0066FF).copy(alpha = 0.1f),
								Color.Transparent
							),
							radius = 600f
						),
						shape = RoundedCornerShape(28.dp)
					)
			)
		}

		// Botão principal - SEM border, altura aumentada
		Button(
			onClick = onClick,
			enabled = enabled,
			modifier = Modifier
				.fillMaxWidth()
				.height(if (isElevatorHere) 82.dp else 78.dp),
			shape = RoundedCornerShape(28.dp),
			colors = ButtonDefaults.buttonColors(
				containerColor = if (isElevatorHere) {
					Color(0xFF0D1D35) // Escuro quando elevador está aqui
				} else {
					Color(0xFF0066FF) // Azul normal
				},
				disabledContainerColor = if (isElevatorHere) {
					Color(0xFF0A1220)
				} else {
					Color(0xFF003580)
				}
			),
			elevation = ButtonDefaults.buttonElevation(
				defaultElevation = 0.dp,
				pressedElevation = 2.dp,
				disabledElevation = 0.dp
			),
			border = null // Remove qualquer borda
		) {
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center,
				modifier = Modifier.padding(vertical = 8.dp)
			) {
				val buttonText = if (floor == "Térreo") {
					"Térreo"
				} else {
					"$floor andar"
				}

				Text(
					text = buttonText,
					fontSize = 24.sp,
					fontWeight = FontWeight.Bold,
					color = Color.White
				)

				// Mostra "elevador aqui" quando o elevador está neste andar
				if (isElevatorHere) {
					Spacer(modifier = Modifier.height(4.dp))
					Text(
						text = "elevador aqui",
						fontSize = 13.sp,
						fontWeight = FontWeight.Normal,
						color = Color(0xFF8B9BB3)
					)
				}
			}
		}
	}
}
