package cl.logisticaverde.app.ui.screens.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.logisticaverde.app.data.local.db.JornadaDao
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import cl.logisticaverde.app.data.repository.EstadoServidor
import cl.logisticaverde.app.data.repository.JornadaSyncRepository
import cl.logisticaverde.app.domain.sync.ErrorSync
import cl.logisticaverde.app.domain.util.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estados del botón Sincronizar en la Pantalla 1 según especificación:
 * - GRIS_SIN_DATOS: cuando no hay nada que sincronizar (0 pendientes).
 * - AMARILLO_SIN_INTERNET: cuando hay datos que sincronizar (>0) pero no hay conexión a internet.
 * - VERDE_LISTO_PARA_SYNC: cuando hay datos a sincronizar (>0) y conexión a internet activa.
 */
enum class SyncButtonState {
    GRIS_SIN_DATOS,
    AMARILLO_SIN_INTERNET,
    VERDE_LISTO_PARA_SYNC,
    SINCRONIZANDO,
    // ISSUE-42: el servidor responde PGRST202/205 (SQL sin ejecutar): no se intenta sincronizar
    MANTENCION
}

/** Resultado de la última sincronización: éxito = animación + sonido; error = ícono rojo + texto ~4 s. */
sealed interface ResultadoSync {
    data object Exito : ResultadoSync
    data class Error(val mensaje: String) : ResultadoSync
}

data class MenuPrincipalUiState(
    val jornadaEnCurso: JornadaEntity? = null,
    val pendingSyncCount: Int = 0,
    val isOnline: Boolean = false,
    val syncButtonState: SyncButtonState = SyncButtonState.GRIS_SIN_DATOS,
    val resultadoSync: ResultadoSync? = null,
    // ISSUE-42
    val rechazadasCount: Int = 0,
    val servidorEnMantencion: Boolean = false
)

class MenuPrincipalViewModel(
    jornadaDao: JornadaDao,
    private val syncRepository: JornadaSyncRepository,
    networkMonitor: NetworkMonitor,
    private val reloj: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    private val _resultadoSync = MutableStateFlow<ResultadoSync?>(null)
    private val _mantencion = MutableStateFlow(false)
    private var ultimaRevisionMs = 0L

    private val conteos = combine(
        jornadaDao.observePendingSyncCount(),
        jornadaDao.observeRechazadasCount(),
        _mantencion
    ) { pendientes, rechazadas, mantencion -> Triple(pendientes, rechazadas, mantencion) }

    val uiState: StateFlow<MenuPrincipalUiState> = combine(
        jornadaDao.observeJornadaEnCurso(),
        conteos,
        networkMonitor.isOnline,
        _isSyncing,
        _resultadoSync
    ) { jornadaEnCurso, (pendingCount, rechazadas, mantencion), isOnline, isSyncing, resultado ->
        val buttonState = when {
            isSyncing -> SyncButtonState.SINCRONIZANDO
            pendingCount == 0 -> SyncButtonState.GRIS_SIN_DATOS
            !isOnline -> SyncButtonState.AMARILLO_SIN_INTERNET
            mantencion -> SyncButtonState.MANTENCION
            else -> SyncButtonState.VERDE_LISTO_PARA_SYNC
        }
        MenuPrincipalUiState(
            jornadaEnCurso = jornadaEnCurso,
            pendingSyncCount = pendingCount,
            isOnline = isOnline,
            syncButtonState = buttonState,
            resultadoSync = resultado,
            rechazadasCount = rechazadas,
            servidorEnMantencion = mantencion
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MenuPrincipalUiState()
    )

    init {
        // Al abrir el menú y cada vez que vuelve internet: refresca catálogos y revisa si el servidor está listo
        viewModelScope.launch {
            networkMonitor.isOnline.distinctUntilChanged().filter { it }.collect { revisarServidor(forzar = true) }
        }
    }

    /**
     * Descarga catálogos y actualiza "Servidor en mantención" (PGRST202/205).
     * La P1 lo llama al volver a primer plano; como máximo una vez cada 30 s.
     */
    fun revisarServidor(forzar: Boolean = false) {
        val ahora = reloj()
        if (!forzar && ahora - ultimaRevisionMs < INTERVALO_REVISION_MS) return
        ultimaRevisionMs = ahora
        viewModelScope.launch {
            when (syncRepository.sincronizarCatalogosMaestros()) {
                EstadoServidor.NO_PREPARADO -> _mantencion.value = true
                EstadoServidor.OK -> _mantencion.value = false
                else -> Unit // sin internet u otro error: no se sabe, se mantiene lo último conocido
            }
        }
    }

    fun onSyncClicked() {
        val currentState = uiState.value
        if (currentState.syncButtonState != SyncButtonState.VERDE_LISTO_PARA_SYNC) return

        viewModelScope.launch {
            _resultadoSync.value = null
            _isSyncing.value = true

            val result = try {
                syncRepository.sincronizarJornadasPendientes()
            } finally {
                _isSyncing.value = false
            }

            if (result.error == ErrorSync.ServidorNoPreparado) _mantencion.value = true
            if (result.successCount > 0) _mantencion.value = false
            _resultadoSync.value = when {
                result.error == null -> ResultadoSync.Exito
                else -> ResultadoSync.Error(result.message)
            }
        }
    }

    /** La P1 lo llama cuando termina la animación del resultado. */
    fun limpiarResultadoSync() {
        _resultadoSync.value = null
    }

    private companion object {
        const val INTERVALO_REVISION_MS = 30_000L
    }

    class Factory(
        private val jornadaDao: JornadaDao,
        private val syncRepository: JornadaSyncRepository,
        private val networkMonitor: NetworkMonitor
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MenuPrincipalViewModel(jornadaDao, syncRepository, networkMonitor) as T
        }
    }
}
