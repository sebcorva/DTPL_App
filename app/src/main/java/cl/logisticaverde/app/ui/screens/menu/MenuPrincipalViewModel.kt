package cl.logisticaverde.app.ui.screens.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.logisticaverde.app.data.local.db.JornadaDao
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import cl.logisticaverde.app.data.repository.JornadaSyncRepository
import cl.logisticaverde.app.domain.util.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    SINCRONIZANDO
}

data class MenuPrincipalUiState(
    val jornadaEnCurso: JornadaEntity? = null,
    val pendingSyncCount: Int = 0,
    val isOnline: Boolean = false,
    val syncButtonState: SyncButtonState = SyncButtonState.GRIS_SIN_DATOS,
    val syncFeedbackMessage: String? = null
)

class MenuPrincipalViewModel(
    private val jornadaDao: JornadaDao,
    private val syncRepository: JornadaSyncRepository,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    private val _feedbackMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MenuPrincipalUiState> = combine(
        jornadaDao.observeJornadaEnCurso(),
        jornadaDao.observePendingSyncCount(),
        networkMonitor.isOnline,
        _isSyncing,
        _feedbackMessage
    ) { jornadaEnCurso, pendingCount, isOnline, isSyncing, feedback ->
        val buttonState = when {
            isSyncing -> SyncButtonState.SINCRONIZANDO
            pendingCount == 0 -> SyncButtonState.GRIS_SIN_DATOS
            !isOnline -> SyncButtonState.AMARILLO_SIN_INTERNET
            else -> SyncButtonState.VERDE_LISTO_PARA_SYNC
        }
        MenuPrincipalUiState(
            jornadaEnCurso = jornadaEnCurso,
            pendingSyncCount = pendingCount,
            isOnline = isOnline,
            syncButtonState = buttonState,
            syncFeedbackMessage = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MenuPrincipalUiState()
    )

    /** "Ver últimos resúmenes": hasta 5 jornadas cerradas, sincronizadas o pendientes (ISSUE-25). */
    val ultimasJornadasCerradas: StateFlow<List<JornadaEntity>> = jornadaDao
        .observeUltimasJornadasCerradas(MAX_RESUMENES)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Si hay internet al abrir el menú, refresca silenciosamente los catálogos maestros desde Supabase
        viewModelScope.launch {
            syncRepository.sincronizarCatalogosMaestros()
        }
    }

    fun onSyncClicked() {
        val currentState = uiState.value
        if (currentState.syncButtonState != SyncButtonState.VERDE_LISTO_PARA_SYNC) return

        viewModelScope.launch {
            _isSyncing.value = true
            _feedbackMessage.value = "Sincronizando ${currentState.pendingSyncCount} jornada(s) con Supabase..."

            val result = syncRepository.sincronizarJornadasPendientes()

            _isSyncing.value = false
            _feedbackMessage.value = result.message
        }
    }

    fun clearFeedbackMessage() {
        _feedbackMessage.value = null
    }

    companion object {
        const val MAX_RESUMENES = 5
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
