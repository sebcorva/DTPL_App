package cl.logisticaverde.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import cl.logisticaverde.app.LogisticaVerdeApp
import cl.logisticaverde.app.domain.util.NetworkMonitor
import cl.logisticaverde.app.service.LimpiezaLocationService
import cl.logisticaverde.app.ui.screens.flujo.EntregaSacosScreen
import cl.logisticaverde.app.ui.screens.flujo.EquipoScreen
import cl.logisticaverde.app.ui.screens.flujo.EstadoHerramientasScreen
import cl.logisticaverde.app.ui.screens.flujo.EvidenciasFinalesScreen
import cl.logisticaverde.app.ui.screens.flujo.HerramientasQrScreen
import cl.logisticaverde.app.ui.screens.flujo.JornadaFlujoViewModel
import cl.logisticaverde.app.ui.screens.flujo.LimpiezaBotoneraScreen
import cl.logisticaverde.app.ui.screens.flujo.PesajeResumenScreen
import cl.logisticaverde.app.ui.screens.flujo.SectoresFotoInicioScreen
import cl.logisticaverde.app.ui.screens.menu.MenuPrincipalScreen
import cl.logisticaverde.app.ui.screens.menu.MenuPrincipalViewModel
import cl.logisticaverde.app.ui.screens.resumen.ResumenImpactoScreen
import cl.logisticaverde.app.ui.screens.resumen.ResumenImpactoViewModel
import cl.logisticaverde.app.ui.screens.reportes.ReportesScreen
import cl.logisticaverde.app.ui.screens.reportes.ReportesViewModel

object AppDestinations {
    const val PANTALLA_1_MENU = "pantalla_1_menu"
    const val PANTALLA_2_EQUIPO = "pantalla_2_equipo"
    const val PANTALLA_3_SECTORES = "pantalla_3_sectores"
    const val PANTALLA_4_QR = "pantalla_4_qr"
    const val PANTALLA_5_LIMPIEZA = "pantalla_5_limpieza"
    const val PANTALLA_6_EVIDENCIAS = "pantalla_6_evidencias"
    const val PANTALLA_7_ESTADO_HERRAMIENTAS = "pantalla_7_estado_herramientas"
    const val PANTALLA_8_PESAJE = "pantalla_8_pesaje"
    const val PANTALLA_9_ENTREGA_SACOS = "pantalla_9_entrega_sacos"
    const val REPORTES = "reportes"
    const val ARG_ID_REGISTRO = "idRegistro"
    const val PANTALLA_10_RESUMEN = "pantalla_10_resumen/{$ARG_ID_REGISTRO}"

    fun rutaResumen(idRegistro: String): String = "pantalla_10_resumen/${android.net.Uri.encode(idRegistro)}"

    fun routeForScreenNumber(screenNumber: Int): String = when (screenNumber) {
        2 -> PANTALLA_2_EQUIPO
        3 -> PANTALLA_3_SECTORES
        4 -> PANTALLA_4_QR
        5 -> PANTALLA_5_LIMPIEZA
        6 -> PANTALLA_6_EVIDENCIAS
        7 -> PANTALLA_7_ESTADO_HERRAMIENTAS
        8 -> PANTALLA_8_PESAJE
        9 -> PANTALLA_9_ENTREGA_SACOS
        else -> PANTALLA_2_EQUIPO
    }
}

@Composable
fun LogisticaVerdeNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val app = context.applicationContext as LogisticaVerdeApp

    // ViewModel compartido del flujo de jornada (Pantallas 2 a 9)
    val flujoViewModel: JornadaFlujoViewModel = viewModel(
        factory = JornadaFlujoViewModel.Factory(
            catalogoDao = app.database.catalogoDao(),
            jornadaDao = app.database.jornadaDao()
        )
    )
    val flujoUiState by flujoViewModel.uiState.collectAsStateWithLifecycle()
    val participantesJornada by flujoViewModel.participantesJornada.collectAsStateWithLifecycle()
    val playas by flujoViewModel.playasCatalogo.collectAsStateWithLifecycle()
    val sectores by flujoViewModel.sectoresCatalogo.collectAsStateWithLifecycle()
    val catalogoHerramientas by flujoViewModel.herramientasCatalogo.collectAsStateWithLifecycle()
    val herramientasJornada by flujoViewModel.herramientasJornada.collectAsStateWithLifecycle()
    val basuraRecolectada by flujoViewModel.basuraRecolectada.collectAsStateWithLifecycle()

    // RF-NAV-3/4: desde P5 iniciada se avanza dejando solo el menú debajo, así Atrás nunca reabre P5..P8
    val avanzarSinRetorno: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(AppDestinations.PANTALLA_1_MENU) { inclusive = false }
        }
    }
    val salirAlMenu: () -> Unit = {
        navController.popBackStack(route = AppDestinations.PANTALLA_1_MENU, inclusive = false)
    }

    // ISSUE-42: avisos cortos del flujo (P2–P9) abajo de la pantalla; la app no se cierra por un error
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(flujoViewModel) {
        flujoViewModel.avisos.collect { aviso ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(aviso)
        }
    }

    Box(modifier = modifier) {
        NavHost(
            navController = navController,
            startDestination = AppDestinations.PANTALLA_1_MENU,
            modifier = Modifier.fillMaxSize()
        ) {
            // PANTALLA 1: Menú Principal ("Deja tu Playa Limpia") + Botón Sincronizar Tricolor
            composable(AppDestinations.PANTALLA_1_MENU) {
                val menuViewModel: MenuPrincipalViewModel = viewModel(
                    factory = MenuPrincipalViewModel.Factory(
                        jornadaDao = app.database.jornadaDao(),
                        syncRepository = app.syncRepository,
                        networkMonitor = NetworkMonitor(app)
                    )
                )
                val menuUiState by menuViewModel.uiState.collectAsStateWithLifecycle()

                // ISSUE-42: al volver al menú se revisa si el servidor está listo ("Servidor en mantención")
                LifecycleResumeEffect(Unit) {
                    menuViewModel.revisarServidor()
                    onPauseOrDispose { }
                }

                MenuPrincipalScreen(
                    uiState = menuUiState,
                    onIniciarJornadaClick = {
                        flujoViewModel.cargarJornadaEnCursoSiExiste()
                        val targetRoute = menuUiState.jornadaEnCurso?.let {
                            AppDestinations.routeForScreenNumber(it.pantallaActual)
                        } ?: AppDestinations.PANTALLA_2_EQUIPO
                        navController.navigate(targetRoute)
                    },
                    onSyncClick = {
                        menuViewModel.onSyncClicked()
                    },
                    onReportesClick = {
                        navController.navigate(AppDestinations.REPORTES) { launchSingleTop = true }
                    },
                    onFeedbackMostrado = menuViewModel::limpiarResultadoSync
                )
            }

            // PANTALLA 2: Líder de Brigada, Brigada A-D y Playa (Card 1) y Voluntarios (Card 2)
            composable(AppDestinations.PANTALLA_2_EQUIPO) {
                EquipoScreen(
                    uiState = flujoUiState,
                    brigadas = flujoViewModel.brigadasDisponibles,
                    playas = playas,
                    onSelectBrigada = flujoViewModel::selectBrigada,
                    onSelectPlaya = flujoViewModel::selectPlaya,
                    onNombreLiderChange = flujoViewModel::onNombreLiderChange,
                    onAgregarVoluntario = flujoViewModel::agregarVoluntario,
                    onEliminarVoluntario = flujoViewModel::eliminarVoluntario,
                    onNuevoVoluntarioEditado = flujoViewModel::limpiarErrorVoluntario,
                    onSiguienteClick = {
                        flujoViewModel.guardarPantalla2Equipo {
                            navController.navigate(AppDestinations.PANTALLA_3_SECTORES)
                        }
                    }
                )
            }

            // PANTALLA 3: Selección de Sectores (m²) y Foto de Sector Inicio (con opción de omitir)
            composable(AppDestinations.PANTALLA_3_SECTORES) {
                SectoresFotoInicioScreen(
                    uiState = flujoUiState,
                    sectores = sectores,
                    onToggleSector = flujoViewModel::toggleSector,
                    onFotoCapturada = { tempFile ->
                        flujoViewModel.procesarFotoInicioCapturada(context, tempFile)
                    },
                    onToggleOmitirFoto = flujoViewModel::toggleOmitirFotoInicio,
                    onMotivoOmisionChange = flujoViewModel::onMotivoOmisionInicioChange,
                    onSiguienteClick = {
                        flujoViewModel.guardarPantalla3SectoresYFoto {
                            navController.navigate(AppDestinations.PANTALLA_4_QR)
                        }
                    }
                )
            }

            // PANTALLA 4: Escaneo QR + Dropdown Desplegable de Herramientas + Lista de Ítems Agregados
            composable(AppDestinations.PANTALLA_4_QR) {
                HerramientasQrScreen(
                    uiState = flujoUiState,
                    herramientasAgregadas = herramientasJornada,
                    catalogoHerramientas = catalogoHerramientas,
                    onQrDetected = flujoViewModel::agregarHerramientaPorQr,
                    onEliminarHerramienta = flujoViewModel::eliminarHerramientaAgregada,
                    onToggleSinHerramientas = flujoViewModel::toggleSinHerramientas,
                    onSiguienteClick = {
                        flujoViewModel.guardarPantalla4Herramientas {
                            navController.navigate(AppDestinations.PANTALLA_5_LIMPIEZA)
                        }
                    }
                )
            }

            // PANTALLA 5: Comenzar/Finalizar Limpieza, GPS (km_recorridos) y Botonera (-1 / +1 / +10)
            composable(AppDestinations.PANTALLA_5_LIMPIEZA) {
                // Antes de "Comenzar Limpieza" Atrás es libre (RF-NAV-2); después solo ofrece salir al menú
                SalirAlMenuBackHandler(
                    enabled = flujoUiState.isLimpiezaIniciada,
                    onSalirAlMenu = salirAlMenu
                )
                LimpiezaBotoneraScreen(
                    uiState = flujoUiState,
                    itemsBasura = basuraRecolectada,
                    onComenzarLimpieza = flujoViewModel::comenzarLimpieza,
                    onModificarConteo = flujoViewModel::modificarConteoBasura,
                    onFinalizarLimpieza = {
                        // Se detiene el GPS antes de cerrar: los km guardados en Room son los finales
                        LimpiezaLocationService.detener(context)
                        flujoViewModel.finalizarLimpieza {
                            avanzarSinRetorno(AppDestinations.PANTALLA_6_EVIDENCIAS)
                        }
                    }
                )
            }

            // PANTALLA 6: Evidencias Finales (Foto Sector Limpio y Foto Sacos Llenos, con opción de omitir)
            composable(AppDestinations.PANTALLA_6_EVIDENCIAS) {
                SalirAlMenuBackHandler(onSalirAlMenu = salirAlMenu)
                EvidenciasFinalesScreen(
                    uiState = flujoUiState,
                    onFotoLimpioCapturada = { tempFile ->
                        flujoViewModel.procesarFotoLimpioCapturada(context, tempFile)
                    },
                    onToggleOmitirFotoLimpio = flujoViewModel::toggleOmitirFotoLimpio,
                    onMotivoOmisionLimpioChange = flujoViewModel::onMotivoOmisionLimpioChange,
                    onFotoSacosCapturada = { tempFile ->
                        flujoViewModel.procesarFotoSacosCapturada(context, tempFile)
                    },
                    onToggleOmitirFotoSacos = flujoViewModel::toggleOmitirFotoSacos,
                    onMotivoOmisionSacosChange = flujoViewModel::onMotivoOmisionSacosChange,
                    onSiguienteClick = {
                        // Sin herramientas se salta P7 (ISSUE-34)
                        flujoViewModel.guardarPantalla6Evidencias { siguiente ->
                            avanzarSinRetorno(AppDestinations.routeForScreenNumber(siguiente))
                        }
                    }
                )
            }

            // PANTALLA 7: Lista de Herramientas Ingresadas y Selección de Estado
            composable(AppDestinations.PANTALLA_7_ESTADO_HERRAMIENTAS) {
                SalirAlMenuBackHandler(onSalirAlMenu = salirAlMenu)
                EstadoHerramientasScreen(
                    herramientas = herramientasJornada,
                    onCambiarEstado = flujoViewModel::actualizarEstadoHerramienta,
                    onSiguienteClick = {
                        flujoViewModel.guardarPantalla7EstadoHerramientas {
                            avanzarSinRetorno(AppDestinations.PANTALLA_8_PESAJE)
                        }
                    }
                )
            }

            // PANTALLA 8: Resumen (Km, Horas, Líder), Encargado de Pesaje e Ingreso de Pesos Recolectados
            composable(AppDestinations.PANTALLA_8_PESAJE) {
                SalirAlMenuBackHandler(onSalirAlMenu = salirAlMenu)
                PesajeResumenScreen(
                    uiState = flujoUiState,
                    participantes = participantesJornada,
                    onSelectEncargadoPesaje = flujoViewModel::selectEncargadoPesaje,
                    onSelectEncargadoExterno = flujoViewModel::seleccionarEncargadoExterno,
                    onEncargadoExternoChange = flujoViewModel::onEncargadoExternoChange,
                    onCantidadSacosChange = flujoViewModel::onCantidadSacosChange,
                    onKgNormalChange = flujoViewModel::onKgBasuraNormalChange,
                    onKgReciclableChange = flujoViewModel::onKgBasuraReciclableChange,
                    onSiguienteClick = {
                        flujoViewModel.guardarPantalla8Pesaje {
                            avanzarSinRetorno(AppDestinations.PANTALLA_9_ENTREGA_SACOS)
                        }
                    }
                )
            }

            // PANTALLA 9: Entrega de Sacos (DIMAO + Guía Despacho / Reciclaje) y Guardar -> Resumen (P10)
            composable(AppDestinations.PANTALLA_9_ENTREGA_SACOS) {
                SalirAlMenuBackHandler(onSalirAlMenu = salirAlMenu)
                EntregaSacosScreen(
                    uiState = flujoUiState,
                    onSelectDestinoNormal = flujoViewModel::selectDestinoBasuraNormal,
                    onNroGuiaDespachoChange = flujoViewModel::onNroGuiaDespachoChange,
                    onSelectDestinoReciclable = flujoViewModel::selectDestinoBasuraReciclable,
                    onGuardarJornadaClick = {
                        // Solo queda el menú debajo: Atrás desde la P10 vuelve a la P1, nunca a la P9 (ISSUE-25)
                        flujoViewModel.finalizarYGuardarJornada { idRegistro ->
                            avanzarSinRetorno(AppDestinations.rutaResumen(idRegistro))
                        }
                    }
                )
            }

            // REPORTES (barra inferior): últimas 5 jornadas cerradas → resumen P10
            composable(AppDestinations.REPORTES) {
                val reportesViewModel: ReportesViewModel = viewModel(
                    factory = ReportesViewModel.Factory(
                        jornadaDao = app.database.jornadaDao(),
                        syncRepository = app.syncRepository,
                        isOnline = NetworkMonitor(app).isOnline
                    )
                )
                val reportesUiState by reportesViewModel.uiState.collectAsStateWithLifecycle()
                ReportesScreen(
                    uiState = reportesUiState,
                    onVerResumen = { idRegistro -> navController.navigate(AppDestinations.rutaResumen(idRegistro)) },
                    onReintentar = reportesViewModel::reintentar,
                    onReintentoMostrado = reportesViewModel::limpiarReintento,
                    onInicio = salirAlMenu
                )
            }

            // PANTALLA 10: Resumen de Impacto (tarjeta 1080×1920 para Instagram), también desde "Ver últimos resúmenes"
            composable(
                route = AppDestinations.PANTALLA_10_RESUMEN,
                arguments = listOf(navArgument(AppDestinations.ARG_ID_REGISTRO) { type = NavType.StringType })
            ) { entry ->
                val idRegistro = entry.arguments?.getString(AppDestinations.ARG_ID_REGISTRO).orEmpty()
                val resumenViewModel: ResumenImpactoViewModel = viewModel(
                    key = "resumen_$idRegistro",
                    factory = ResumenImpactoViewModel.Factory(app.database.jornadaDao(), idRegistro)
                )
                val resumenUiState by resumenViewModel.uiState.collectAsStateWithLifecycle()
                ResumenImpactoScreen(
                    uiState = resumenUiState,
                    onVolverAlInicio = salirAlMenu
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}
