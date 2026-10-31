package cl.logisticaverde.app.ui.screens.resumen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.logisticaverde.app.data.local.db.JornadaDao
import cl.logisticaverde.app.domain.resumen.ResumenImpacto
import cl.logisticaverde.app.domain.resumen.ResumenImpactoCalculator
import cl.logisticaverde.app.domain.sync.RegistroErrores
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ResumenImpactoUiState {
    data object Cargando : ResumenImpactoUiState
    data object NoEncontrada : ResumenImpactoUiState
    data class Listo(val resumen: ResumenImpacto) : ResumenImpactoUiState
}

/** Pantalla 10: carga una jornada cerrada desde Room y calcula sus cifras (sin internet). */
class ResumenImpactoViewModel(
    private val jornadaDao: JornadaDao,
    private val idRegistro: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<ResumenImpactoUiState>(ResumenImpactoUiState.Cargando)
    val uiState: StateFlow<ResumenImpactoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = try {
                val jornada = jornadaDao.getJornadaById(idRegistro)
                if (jornada == null) {
                    ResumenImpactoUiState.NoEncontrada
                } else {
                    ResumenImpactoUiState.Listo(
                        ResumenImpactoCalculator.calcular(jornada, jornadaDao.getBasuraRecolectadaList(idRegistro))
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RegistroErrores.advertencia("ResumenImpacto", "No se pudo cargar $idRegistro", e)
                ResumenImpactoUiState.NoEncontrada
            }
        }
    }

    class Factory(
        private val jornadaDao: JornadaDao,
        private val idRegistro: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ResumenImpactoViewModel(jornadaDao, idRegistro) as T
    }
}
