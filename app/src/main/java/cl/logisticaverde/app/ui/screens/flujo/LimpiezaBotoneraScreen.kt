package cl.logisticaverde.app.ui.screens.flujo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cl.logisticaverde.app.data.local.entity.BasuraRecolectadaEntity
import cl.logisticaverde.app.service.LimpiezaLocationService
import java.util.Locale

/**
 * Pantalla 5:
 * - Arriba: Botón "Comenzar Limpieza" (al presionar cambia a "Finalizar Limpieza").
 * - Debajo: Botonera inhabilitada antes de comenzar.
 * - Costado izquierdo: [-1], Centro: Nombre + Contador, Costado derecho: [+1] [+10].
 */
@Composable
fun LimpiezaBotoneraScreen(
    uiState: EquipoSeleccionUiState,
    itemsBasura: List<BasuraRecolectadaEntity>,
    onComenzarLimpieza: () -> Unit,
    onModificarConteo: (idTipoBasura: String, delta: Int) -> Unit,
    onFinalizarLimpieza: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showConfirmFinalizarDialog by remember { mutableStateOf(false) }

    // Ubicación para los km (servicio en primer plano) y, en Android 13+, notificaciones para verlos
    val permisosLimpieza = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Sin permiso de ubicación la limpieza igual comienza (sin km); el servicio no se inicia
        onComenzarLimpieza()
        uiState.idRegistroActivo?.let { LimpiezaLocationService.iniciar(context, it) }
    }

    // Al volver a P5 (o reanudar la jornada) con la limpieza en curso, el GPS debe estar corriendo
    LaunchedEffect(uiState.isLimpiezaIniciada, uiState.horaFinMs, uiState.idRegistroActivo) {
        val id = uiState.idRegistroActivo
        if (uiState.isLimpiezaIniciada && uiState.horaFinMs == 0L && id != null) {
            LimpiezaLocationService.iniciar(context, id)
        }
    }

    val isStarted = uiState.isLimpiezaIniciada
    val totalUnidades = itemsBasura.sumOf { it.cantidad }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Limpieza y Conteo",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 1. Botón Superior Maestro: Comenzar Limpieza -> Finalizar Limpieza
        Button(
            onClick = {
                if (!isStarted) {
                    val faltanPermisos = permisosLimpieza.any {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (faltanPermisos) {
                        permissionLauncher.launch(permisosLimpieza)
                    } else {
                        onComenzarLimpieza()
                        uiState.idRegistroActivo?.let { LimpiezaLocationService.iniciar(context, it) }
                    }
                } else {
                    showConfirmFinalizarDialog = true
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isStarted) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text(
                text = if (isStarted) "Finalizar Limpieza" else "Comenzar Limpieza",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Barra de estado de recorrido (Km recorridos y total de ítems)
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = if (isStarted) "Estado: Recorrido en curso" else "Presiona 'Comenzar Limpieza' para habilitar",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isStarted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.getDefault(), "%.2f km | %d ítems", uiState.kmRecorridos, totalUnidades),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Botonera Interactiva de Residuos (Inhabilitada antes de comenzar)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isStarted) 1f else 0.45f)
        ) {
            items(itemsBasura, key = { it.idTipoBasura }) { item ->
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        // Costado Izquierdo: [-1]
                        OutlinedButton(
                            onClick = { onModificarConteo(item.idTipoBasura, -1) },
                            enabled = isStarted && item.cantidad > 0
                        ) {
                            Text(text = "-1", fontWeight = FontWeight.Bold)
                        }

                        // Centro: Nombre del residuo, categoría por defecto y contador actual
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = item.nombreVisible,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Origen: ${item.categoriaOrigen}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${item.cantidad}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Costado Derecho: [+1] y [+10]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = { onModificarConteo(item.idTipoBasura, 1) },
                                enabled = isStarted
                            ) {
                                Text(text = "+1", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = { onModificarConteo(item.idTipoBasura, 10) },
                                enabled = isStarted
                            ) {
                                Text(text = "+10", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmFinalizarDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmFinalizarDialog = false },
            title = { Text("¿Finalizar Limpieza?") },
            text = {
                Text("Se registrará la hora de término y pasarás a tomar las fotos de evidencia final (Total contado: $totalUnidades ítems).")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmFinalizarDialog = false
                        onFinalizarLimpieza()
                    }
                ) {
                    Text("Sí, Finalizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmFinalizarDialog = false }) {
                    Text("Seguir Limpiando")
                }
            }
        )
    }
}
