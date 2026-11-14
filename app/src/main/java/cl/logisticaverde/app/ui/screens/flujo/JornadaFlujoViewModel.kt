package cl.logisticaverde.app.ui.screens.flujo

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.logisticaverde.app.data.local.db.CatalogoDao
import cl.logisticaverde.app.data.local.db.JornadaDao
import cl.logisticaverde.app.data.local.entity.BasuraRecolectadaEntity
import cl.logisticaverde.app.data.local.entity.BrigadaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.HerramientaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.HerramientaJornadaEntity
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import cl.logisticaverde.app.data.local.entity.ParticipanteJornadaEntity
import cl.logisticaverde.app.data.local.entity.PlayaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.SectorCatalogoEntity
import cl.logisticaverde.app.domain.reglas.FechaJornada
import cl.logisticaverde.app.domain.reglas.ReglasTexto
import cl.logisticaverde.app.domain.reglas.TopesJornada
import cl.logisticaverde.app.domain.util.ImageCompressor
import cl.logisticaverde.app.domain.reglas.ValidadorJornada
import cl.logisticaverde.app.domain.sync.RegistroErrores
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

data class EquipoSeleccionUiState(
    val idRegistroActivo: String? = null,
    // ISSUE-62: brigada del catálogo de la playa elegida (se elige después de la playa)
    val selectedBrigadaId: String? = null,
    val nombreBrigada: String = "",
    // Pantalla 2: nombres escritos a mano (sin padrón de usuarios)
    val nombreLider: String = "",
    val voluntarios: List<String> = emptyList(),
    val errorVoluntario: String? = null,
    // Pantalla 2: playa elegida del catálogo (ISSUE-32)
    val selectedPlayaId: String? = null,
    val selectedSectoresIds: Set<String> = emptySet(),
    // Fotografías y opción de omitir fotografía con motivo (Pantallas 3 y 6, ISSUE-35)
    val pathFotoInicio: String = "",
    val omitirFotoInicio: Boolean = false,
    val motivoOmisionInicio: String = "",
    val pathFotoLimpio: String = "",
    val omitirFotoLimpio: Boolean = false,
    val motivoOmisionLimpio: String = "",
    val pathFotoSacos: String = "",
    val omitirFotoSacos: Boolean = false,
    val motivoOmisionSacos: String = "",
    val isSavingPhoto: Boolean = false,
    val qrFeedbackMessage: String? = null,
    // true si el último mensaje del escaneo es un error (rojo); false si se agregó (verde) — ISSUE-48
    val qrFeedbackEsError: Boolean = false,
    // Sube con cada mensaje nuevo: la P4 reinicia con él los 2 s del aviso "Agregada", aunque el texto se repita
    val qrFeedbackSeq: Int = 0,
    // Pantalla 4: la brigada no lleva herramientas (se salta P7)
    val sinHerramientas: Boolean = false,
    val isLimpiezaIniciada: Boolean = false,
    val horaInicioMs: Long = 0L,
    val horaFinMs: Long = 0L,
    val kmRecorridos: Float = 0f,
    // Pantalla 8: Pesaje, Encargado de Pesaje y validación Ley N° 20.949 ("Ley del Saco" <= 25 kg/saco)
    val encargadoPesaje: String = "",
    // true cuando pesa alguien que no está en la brigada ("Otra persona", nombre escrito a mano)
    val encargadoEsExterno: Boolean = false,
    val cantidadSacosInput: String = "",
    val kgBasuraNormalInput: String = "",
    val kgBasuraReciclableInput: String = "",
    val pesajeErrorMessage: String? = null,
    // Pantalla 9: Entrega de Sacos y Trazabilidad de Destino
    val destinoBasuraNormal: String = JornadaEntity.DESTINO_NORMAL_DIMAO,
    val nroGuiaDespachoInput: String = "",
    val destinoBasuraReciclable: String = JornadaEntity.DESTINO_RECICLABLE_PUNTO_LIMPIO
) {
    companion object {
        const val MAX_KG_POR_SACO_LEY_20949 = 25.0f
        const val MIN_CARACTERES_NOMBRE_LIDER = 3

        /** Motivo de una foto omitida: obligatorio y solo con caracteres permitidos (ISSUE-58). */
        fun esMotivoValido(motivo: String): Boolean = motivo.isNotBlank() && ReglasTexto.esMotivoValido(motivo)
    }

    /**
     * Pantalla 2: líder con nombre de al menos 3 caracteres, al menos 1 voluntario, playa y brigada elegidas.
     */
    val esEquipoValido: Boolean
        get() = errorNombreLider == null &&
            nombreLider.isNotBlank() &&
            voluntarios.isNotEmpty() &&
            voluntarios.size <= TopesJornada.MAX_VOLUNTARIOS &&
            !selectedPlayaId.isNullOrBlank() &&
            !selectedBrigadaId.isNullOrBlank()

    /** P3: foto tomada, u omitida con motivo. */
    val fotoInicioResuelta: Boolean
        get() = pathFotoInicio.isNotBlank() || (omitirFotoInicio && esMotivoValido(motivoOmisionInicio))

    /** P6: cada foto tomada, u omitida con motivo. */
    val fotosFinalesResueltas: Boolean
        get() = (pathFotoLimpio.isNotBlank() || (omitirFotoLimpio && esMotivoValido(motivoOmisionLimpio))) &&
            (pathFotoSacos.isNotBlank() || (omitirFotoSacos && esMotivoValido(motivoOmisionSacos)))

    /** Mensaje bajo el campo del líder (null si está vacío o es válido). */
    val errorNombreLider: String?
        get() {
            val limpio = nombreLider.trim()
            if (limpio.isEmpty()) return null
            ReglasTexto.errorNombre(limpio)?.let { return it }
            if (limpio.length < MIN_CARACTERES_NOMBRE_LIDER) {
                return "Mínimo $MIN_CARACTERES_NOMBRE_LIDER caracteres."
            }
            val normalizado = ParticipanteJornadaEntity.normalizarNombre(limpio)
            if (voluntarios.any { ParticipanteJornadaEntity.normalizarNombre(it) == normalizado }) {
                return "El líder no puede estar también en la lista de voluntarios."
            }
            return null
        }

    /** "N voluntarios + 1 líder = N+1 integrantes" */
    val resumenIntegrantes: String
        get() {
            val n = voluntarios.size
            val etiquetaVoluntarios = if (n == 1) "1 voluntario" else "$n voluntarios"
            return "$etiquetaVoluntarios + 1 líder = ${n + 1} integrantes"
        }

    val cantidadSacosVal: Int
        get() = cantidadSacosInput.toIntOrNull() ?: 0

    val kgBasuraNormalVal: Float
        get() = kgBasuraNormalInput.toFloatOrNull() ?: 0f

    val kgBasuraReciclableVal: Float
        get() = kgBasuraReciclableInput.toFloatOrNull() ?: 0f

    val kgTotalesVal: Float
        get() = kgBasuraNormalVal + kgBasuraReciclableVal

    val kgPromedioPorSaco: Float
        get() = if (cantidadSacosVal > 0) kgTotalesVal / cantidadSacosVal else 0f

    /**
     * Regla Ley N° 20.949: El peso promedio por saco debe ser > 0 kg y <= 25 kg.
     */
    val excedeLimiteLeySaco: Boolean
        get() = cantidadSacosVal > 0 && kgTotalesVal > 0f && kgPromedioPorSaco > MAX_KG_POR_SACO_LEY_20949

    /** Topes de ISSUE-31: más de 100 sacos o 2.500 kg no es una jornada real. */
    val excedeTopesPesaje: Boolean
        get() = cantidadSacosVal > TopesJornada.MAX_SACOS || kgTotalesVal > TopesJornada.MAX_KG_TOTALES

    /**
     * Pantalla 8: Requiere Encargado de Pesaje seleccionado, sacos > 0, kgTotales > 0,
     * cumplir Ley 20.949 y los topes de la jornada.
     */
    val esPesajeValido: Boolean
        get() = encargadoPesaje.isNotBlank() &&
            ReglasTexto.esNombreValido(encargadoPesaje) &&
            cantidadSacosVal > 0 &&
            kgTotalesVal > 0f &&
            !excedeLimiteLeySaco &&
            !excedeTopesPesaje

    /**
     * Opciones permitidas en Pantalla 9 para Basura Normal según si kgBasuraNormalVal > 0 o == 0.
     */
    val opcionesPermitidasBasuraNormal: List<String>
        get() = if (kgBasuraNormalVal <= 0f) {
            listOf(JornadaEntity.DESTINO_NORMAL_NO_APLICA)
        } else {
            JornadaEntity.OPCIONES_DESTINO_NORMAL.filter { it != JornadaEntity.DESTINO_NORMAL_NO_APLICA }
        }

    /**
     * Opciones permitidas en Pantalla 9 para Basura Reciclable según si kgBasuraReciclableVal > 0 o == 0.
     */
    val opcionesPermitidasBasuraReciclable: List<String>
        get() = if (kgBasuraReciclableVal <= 0f) {
            listOf(JornadaEntity.DESTINO_RECICLABLE_NO_APLICA)
        } else {
            JornadaEntity.OPCIONES_DESTINO_RECICLABLE.filter { it != JornadaEntity.DESTINO_RECICLABLE_NO_APLICA }
        }

    /**
     * El campo N° Guía de Despacho solo aplica cuando Basura Normal se entrega a camión DIMAO.
     */
    val mostrarCampoGuiaDespacho: Boolean
        get() = destinoBasuraNormal == JornadaEntity.DESTINO_NORMAL_DIMAO && kgBasuraNormalVal > 0f

    /** ISSUE-58: mensaje bajo el nombre de quien pesa, si no es de la brigada. */
    val errorEncargadoExterno: String?
        get() = if (encargadoEsExterno) ReglasTexto.errorNombre(encargadoPesaje) else null

    /** ISSUE-60: mensaje bajo el campo de la guía (null si está vacía o es válida). */
    val errorGuiaDespacho: String?
        get() = if (mostrarCampoGuiaDespacho) ReglasTexto.errorGuia(nroGuiaDespachoInput) else null
}

@OptIn(ExperimentalCoroutinesApi::class)
class JornadaFlujoViewModel(
    private val catalogoDao: CatalogoDao,
    private val jornadaDao: JornadaDao,
    // Inyectable para probar la fecha de la jornada y el tope de duración
    private val reloj: () -> Long = { System.currentTimeMillis() },
    // Inyectable para probar un ID repetido (ISSUE-59)
    private val generarSufijoId: () -> String = ::sufijoIdAleatorio
) : ViewModel() {

    private val _uiState = MutableStateFlow(EquipoSeleccionUiState())
    val uiState: StateFlow<EquipoSeleccionUiState> = _uiState.asStateFlow()

    /**
     * ISSUE-42: avisos cortos para el líder (se muestran abajo de la pantalla). Un error al guardar
     * en Room, comprimir una foto, etc. no cierra la app: se registra, se avisa y el líder se queda
     * en la misma pantalla (la navegación de esa acción no ocurre).
     */
    private val _avisos = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val avisos: SharedFlow<String> = _avisos.asSharedFlow()

    private val manejadorErrores = CoroutineExceptionHandler { _, error ->
        RegistroErrores.advertencia(TAG, "Error en el flujo de jornada", error)
        _uiState.update { it.copy(isSavingPhoto = false) }
        _avisos.tryEmit(AVISO_ERROR_GUARDADO)
    }

    /** P2: playas activas (ISSUE-43); una ya elegida en la jornada en curso se mantiene aunque se desactive. */
    val playasCatalogo: StateFlow<List<PlayaCatalogoEntity>> = combine(
        catalogoDao.observePlayas(),
        _uiState.map { it.selectedPlayaId }.distinctUntilChanged()
    ) { playas, idElegida ->
        playas.filter { it.estaActivo || it.idPlaya == idElegida }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * P2 (ISSUE-62): brigadas activas de la playa elegida; la ya elegida en la jornada en curso
     * se mantiene aunque se desactive. Sin playa elegida no hay brigadas.
     */
    val brigadasCatalogo: StateFlow<List<BrigadaCatalogoEntity>> = combine(
        catalogoDao.observeBrigadas(),
        _uiState.map { it.selectedPlayaId to it.selectedBrigadaId }.distinctUntilChanged()
    ) { brigadas, (idPlaya, idElegida) ->
        brigadas.filter { it.idPlaya == idPlaya && (it.estaActiva || it.idBrigada == idElegida) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * P3: solo los sectores activos de la playa elegida en P2 (ISSUE-43); los ya elegidos en la
     * jornada en curso se mantienen aunque se desactiven.
     */
    val sectoresCatalogo: StateFlow<List<SectorCatalogoEntity>> = combine(
        catalogoDao.observeSectores(),
        _uiState.map { it.selectedPlayaId to it.selectedSectoresIds }.distinctUntilChanged()
    ) { sectores, (idPlaya, elegidos) ->
        sectores.filter { it.idPlaya == idPlaya && (it.estaActivo || it.idSector in elegidos) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Regla de Inventario (Pantalla 4):
     * Las herramientas con estado 'Extraviado' se ocultan del selector disponible;
     * se muestran las 'Operativo' y 'Dañado' (visualizando su estado).
     * ISSUE-43: tampoco se ofrecen las dadas de baja (`is_activo = false`).
     */
    val herramientasCatalogo: StateFlow<List<HerramientaCatalogoEntity>> =
        catalogoDao.observeHerramientasCatalogo()
            .map { lista ->
                lista.filter {
                    it.estaActivo && !it.estado.equals(HerramientaCatalogoEntity.ESTADO_EXTRAVIADO, ignoreCase = true)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Participantes guardados de la jornada activa, para seleccionar al Encargado de Pesaje en Pantalla 8.
     */
    val participantesJornada: StateFlow<List<ParticipanteJornadaEntity>> = _uiState
        .map { it.idRegistroActivo }
        .flatMapLatest { idRegistro ->
            if (idRegistro.isNullOrBlank()) flowOf(emptyList())
            else jornadaDao.observeParticipantes(idRegistro)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val herramientasJornada: StateFlow<List<HerramientaJornadaEntity>> = _uiState
        .map { it.idRegistroActivo }
        .flatMapLatest { idRegistro ->
            if (idRegistro.isNullOrBlank()) flowOf(emptyList())
            else jornadaDao.observeHerramientasJornada(idRegistro)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val basuraRecolectada: StateFlow<List<BasuraRecolectadaEntity>> = _uiState
        .map { it.idRegistroActivo }
        .flatMapLatest { idRegistro ->
            if (idRegistro.isNullOrBlank()) flowOf(emptyList())
            else jornadaDao.observeBasuraRecolectada(idRegistro)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        cargarJornadaEnCursoSiExiste()
        observarKmDesdeRoom()
    }

    /** ISSUE-23: los km los escribe LimpiezaLocationService en Room; la UI solo los refleja. */
    private fun observarKmDesdeRoom() {
        viewModelScope.launch(manejadorErrores) {
            _uiState
                .map { it.idRegistroActivo }
                .distinctUntilChanged()
                .flatMapLatest { idRegistro ->
                    if (idRegistro.isNullOrBlank()) flowOf(null) else jornadaDao.observeJornadaById(idRegistro)
                }
                .collect { jornada ->
                    if (jornada != null) _uiState.update { it.copy(kmRecorridos = jornada.kmRecorridos) }
                }
        }
    }

    fun cargarJornadaEnCursoSiExiste() {
        viewModelScope.launch(manejadorErrores) {
            val enCurso = jornadaDao.observeJornadaEnCurso().firstOrNull()
            if (enCurso != null) {
                // Se reconstruye desde las tablas, no comparando nombres en los CSV
                val participantes = jornadaDao.getParticipantesList(enCurso.idRegistro)
                val voluntarios = participantes
                    .filter { it.rol == ParticipanteJornadaEntity.ROL_VOLUNTARIO }
                    .map { it.nombre }
                val sectoresIds = enCurso.sectoresIdsCsv
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()

                _uiState.update {
                    it.copy(
                        idRegistroActivo = enCurso.idRegistro,
                        selectedBrigadaId = enCurso.idBrigada.ifBlank { null },
                        nombreBrigada = enCurso.brigada,
                        nombreLider = enCurso.nombreLider,
                        voluntarios = voluntarios,
                        selectedPlayaId = enCurso.idPlaya.ifBlank { null },
                        selectedSectoresIds = sectoresIds,
                        pathFotoInicio = enCurso.pathFotoInicio,
                        // Un motivo guardado indica que la foto se omitió (también a mitad de pantalla)
                        omitirFotoInicio = enCurso.motivoOmisionInicio != null,
                        motivoOmisionInicio = enCurso.motivoOmisionInicio.orEmpty(),
                        pathFotoLimpio = enCurso.pathFotoLimpio,
                        omitirFotoLimpio = enCurso.motivoOmisionLimpio != null,
                        motivoOmisionLimpio = enCurso.motivoOmisionLimpio.orEmpty(),
                        pathFotoSacos = enCurso.pathFotoSacos,
                        omitirFotoSacos = enCurso.motivoOmisionSacos != null,
                        motivoOmisionSacos = enCurso.motivoOmisionSacos.orEmpty(),
                        sinHerramientas = enCurso.sinHerramientas,
                        isLimpiezaIniciada = enCurso.horaInicioMs > 0L,
                        horaInicioMs = enCurso.horaInicioMs,
                        horaFinMs = enCurso.horaFinMs,
                        kmRecorridos = enCurso.kmRecorridos,
                        encargadoPesaje = enCurso.encargadoPesaje,
                        encargadoEsExterno = enCurso.encargadoPesaje.isNotBlank() &&
                            participantes.none { it.nombre == enCurso.encargadoPesaje },
                        cantidadSacosInput = if (enCurso.cantidadSacos > 0) enCurso.cantidadSacos.toString() else "",
                        kgBasuraNormalInput = if (enCurso.kgBasuraNormal > 0f) enCurso.kgBasuraNormal.toString() else "",
                        kgBasuraReciclableInput = if (enCurso.kgBasuraReciclable > 0f) enCurso.kgBasuraReciclable.toString() else "",
                        destinoBasuraNormal = enCurso.destinoBasuraNormal,
                        nroGuiaDespachoInput = enCurso.nroGuiaDespacho.orEmpty(),
                        destinoBasuraReciclable = enCurso.destinoBasuraReciclable
                    )
                }
            } else {
                _uiState.value = EquipoSeleccionUiState()
            }
        }
    }

    // --- PANTALLA 2: Líder y Voluntarios escritos a mano ---
    // Serializa las escrituras de P2: varias altas seguidas no deben crear dos jornadas
    private val equipoMutex = Mutex()

    fun selectBrigada(brigada: BrigadaCatalogoEntity) {
        _uiState.update { it.copy(selectedBrigadaId = brigada.idBrigada, nombreBrigada = brigada.nombreBrigada) }
        persistirEquipoEnSegundoPlano()
    }

    /**
     * ISSUE-32 / ISSUE-62: cambiar de playa descarta los sectores marcados y la brigada elegida
     * (pertenecen a la playa anterior).
     */
    fun selectPlaya(idPlaya: String) {
        _uiState.update { current ->
            if (current.selectedPlayaId == idPlaya) current
            else current.copy(
                selectedPlayaId = idPlaya,
                selectedSectoresIds = emptySet(),
                selectedBrigadaId = null,
                nombreBrigada = ""
            )
        }
        persistirEquipoEnSegundoPlano()
    }

    fun onNombreLiderChange(nombre: String) {
        _uiState.update { it.copy(nombreLider = nombre) }
        persistirEquipoEnSegundoPlano()
    }

    fun limpiarErrorVoluntario() {
        _uiState.update { it.copy(errorVoluntario = null) }
    }

    /**
     * Agrega un voluntario si el nombre no está vacío ni repetido (incluido el líder),
     * comparando sin mayúsculas ni espacios extra. Devuelve false si se rechazó.
     */
    fun agregarVoluntario(nombre: String): Boolean {
        val limpio = limpiarNombre(nombre)
        if (limpio.isEmpty()) {
            _uiState.update { it.copy(errorVoluntario = "Escribe el nombre del voluntario.") }
            return false
        }
        ReglasTexto.errorNombre(limpio)?.let { mensaje ->
            _uiState.update { it.copy(errorVoluntario = mensaje) }
            return false
        }
        val state = _uiState.value
        val normalizado = ParticipanteJornadaEntity.normalizarNombre(limpio)
        if (state.voluntarios.any { ParticipanteJornadaEntity.normalizarNombre(it) == normalizado }) {
            _uiState.update { it.copy(errorVoluntario = "\"$limpio\" ya está en la lista.") }
            return false
        }
        if (ParticipanteJornadaEntity.normalizarNombre(state.nombreLider) == normalizado) {
            _uiState.update { it.copy(errorVoluntario = "\"$limpio\" ya es el líder de la brigada.") }
            return false
        }
        if (state.voluntarios.size >= TopesJornada.MAX_VOLUNTARIOS) {
            _uiState.update {
                it.copy(errorVoluntario = "Máximo ${TopesJornada.MAX_PARTICIPANTES} integrantes por brigada.")
            }
            return false
        }
        _uiState.update { it.copy(voluntarios = it.voluntarios + limpio, errorVoluntario = null) }
        persistirEquipoEnSegundoPlano()
        return true
    }

    fun eliminarVoluntario(nombre: String) {
        _uiState.update { it.copy(voluntarios = it.voluntarios - nombre) }
        persistirEquipoEnSegundoPlano()
    }

    fun guardarPantalla2Equipo(onSuccessNavigateToP3: () -> Unit) {
        if (!_uiState.value.esEquipoValido) return

        viewModelScope.launch(manejadorErrores) {
            val idRegistro = equipoMutex.withLock { persistirEquipo() } ?: return@launch
            val actual = jornadaDao.getJornadaById(idRegistro) ?: return@launch
            // Si se volvió atrás desde una pantalla posterior, no retroceder pantalla_actual
            if (actual.pantallaActual < 3) jornadaDao.updatePantallaActual(idRegistro, 3)
            onSuccessNavigateToP3()
        }
    }

    /** RNF-01: cada alta, baja o cambio de P2 queda en Room de inmediato. */
    private fun persistirEquipoEnSegundoPlano() {
        viewModelScope.launch(manejadorErrores) {
            equipoMutex.withLock { persistirEquipo() }
        }
    }

    /**
     * Refleja el formulario de P2 en `jornada` y `participante_jornada`. Crea la jornada
     * (en pantalla 2) cuando aparece el primer participante. Devuelve el id o null si aún no hay nadie.
     */
    private suspend fun persistirEquipo(): String? {
        val state = _uiState.value
        val nombreLider = limpiarNombre(state.nombreLider)
        val hayParticipantes = nombreLider.isNotEmpty() || state.voluntarios.isNotEmpty()

        val idRegistro = state.idRegistroActivo
            ?: if (hayParticipantes) crearJornada() else return null

        val voluntariosCsv = state.voluntarios.joinToString(", ")
        val actual = jornadaDao.getJornadaById(idRegistro) ?: return null
        val playa = state.selectedPlayaId?.let { id ->
            catalogoDao.observePlayas().firstOrNull().orEmpty().find { it.idPlaya == id }
        }
        val cambioDePlaya = (playa?.idPlaya ?: "") != actual.idPlaya
        jornadaDao.updateJornada(
            actual.copy(
                nombreLider = nombreLider,
                idBrigada = state.selectedBrigadaId.orEmpty(),
                brigada = state.nombreBrigada,
                voluntariosCsv = voluntariosCsv,
                totalParticipantes = (if (nombreLider.isNotEmpty()) 1 else 0) + state.voluntarios.size,
                idPlaya = playa?.idPlaya ?: "",
                nombrePlaya = playa?.nombrePlaya ?: "",
                // Los sectores guardados son de la playa anterior
                sectoresIdsCsv = if (cambioDePlaya) "" else actual.sectoresIdsCsv,
                sectoresCsv = if (cambioDePlaya) "" else actual.sectoresCsv,
                areaTotalM2 = if (cambioDePlaya) 0 else actual.areaTotalM2
            )
        )

        // participante_jornada refleja exactamente el formulario
        jornadaDao.getParticipantesList(idRegistro).forEach { jornadaDao.deleteParticipante(it.id) }
        val liderRepetido = state.voluntarios.any {
            ParticipanteJornadaEntity.normalizarNombre(it) == ParticipanteJornadaEntity.normalizarNombre(nombreLider)
        }
        if (nombreLider.isNotEmpty() && !liderRepetido) {
            jornadaDao.insertParticipante(
                ParticipanteJornadaEntity(
                    idRegistro = idRegistro,
                    nombre = nombreLider,
                    rol = ParticipanteJornadaEntity.ROL_LIDER
                )
            )
        }
        state.voluntarios.forEach { nombre ->
            jornadaDao.insertParticipante(
                ParticipanteJornadaEntity(
                    idRegistro = idRegistro,
                    nombre = nombre,
                    rol = ParticipanteJornadaEntity.ROL_VOLUNTARIO
                )
            )
        }
        return idRegistro
    }

    private suspend fun crearJornada(): String {
        // Fecha de la jornada (ISSUE-31): el día en que se crea en P2, en hora de Chile
        val fechaHoy = FechaJornada.hoy(reloj())
        // ISSUE-59: si el ID ya existe en el teléfono se genera otro; insertJornada usa ABORT,
        // así que una jornada existente nunca se sobrescribe (ni se borran sus datos en cascada)
        var nuevoIdRegistro = nuevoIdRegistro(fechaHoy)
        var intentos = 1
        while (jornadaDao.getJornadaById(nuevoIdRegistro) != null && intentos < MAX_INTENTOS_ID) {
            nuevoIdRegistro = nuevoIdRegistro(fechaHoy)
            intentos++
        }

        jornadaDao.insertJornada(
            JornadaEntity(
                idRegistro = nuevoIdRegistro,
                pantallaActual = 2,
                fechaJornada = FechaJornada.iso(fechaHoy),
                estadoSync = JornadaEntity.SYNC_EN_CURSO
            )
        )

        // ISSUE-43: la grilla de una jornada nueva solo trae los tipos activos
        val tiposBasura = catalogoDao.getTiposBasuraList().filter { it.estaActivo }
        val filasIniciales = tiposBasura.map { tipo ->
            BasuraRecolectadaEntity(
                idRegistro = nuevoIdRegistro,
                idTipoBasura = tipo.idTipoBasura,
                nombreVisible = tipo.nombreVisible,
                categoriaOrigen = tipo.categoriaOrigen,
                cantidad = 0
            )
        }
        jornadaDao.upsertBasuraRecolectada(filasIniciales)

        _uiState.update { it.copy(idRegistroActivo = nuevoIdRegistro) }
        return nuevoIdRegistro
    }

    private fun nuevoIdRegistro(fecha: java.time.LocalDate) = "REG-${FechaJornada.compacta(fecha)}-${generarSufijoId()}"

    private fun limpiarNombre(nombre: String): String = nombre.trim().replace(Regex("\\s+"), " ")

    // --- PANTALLA 3: Selección de Sectores (m²) y Foto de Sector Inicio (con opción de omitir) ---
    fun toggleSector(idSector: String) {
        _uiState.update { current ->
            val updated = current.selectedSectoresIds.toMutableSet()
            if (!updated.add(idSector)) {
                updated.remove(idSector)
            }
            current.copy(selectedSectoresIds = updated)
        }
    }

    /**
     * ISSUE-35: al omitir se borra la foto y se exige un motivo; al desmarcar se borra el motivo.
     * En Room, motivo != null significa "foto omitida" (así la reanudación lo reconoce).
     */
    fun toggleOmitirFotoInicio(omitir: Boolean) {
        _uiState.update {
            it.copy(
                omitirFotoInicio = omitir,
                pathFotoInicio = if (omitir) "" else it.pathFotoInicio,
                motivoOmisionInicio = if (omitir) it.motivoOmisionInicio else ""
            )
        }
        actualizarJornadaEnSegundoPlano { actual ->
            if (omitir) actual.copy(pathFotoInicio = "", motivoOmisionInicio = actual.motivoOmisionInicio ?: "")
            else actual.copy(motivoOmisionInicio = null)
        }
    }

    fun onMotivoOmisionInicioChange(motivo: String) {
        val recortado = motivo.take(TopesJornada.MAX_CARACTERES_MOTIVO_OMISION)
        _uiState.update { it.copy(motivoOmisionInicio = recortado) }
        actualizarJornadaEnSegundoPlano { it.copy(motivoOmisionInicio = recortado) }
    }

    /** Lee la jornada activa de Room, aplica el cambio y la guarda (RNF-01). */
    private fun actualizarJornadaEnSegundoPlano(cambio: (JornadaEntity) -> JornadaEntity) {
        val idRegistro = _uiState.value.idRegistroActivo ?: return
        viewModelScope.launch(manejadorErrores) {
            val actual = jornadaDao.getJornadaById(idRegistro) ?: return@launch
            jornadaDao.updateJornada(cambio(actual))
        }
    }

    fun procesarFotoInicioCapturada(context: Context, tempFile: File) {
        val idRegistro = _uiState.value.idRegistroActivo ?: "TEMP"
        viewModelScope.launch(manejadorErrores) {
            _uiState.update { it.copy(isSavingPhoto = true) }
            val compressedPath = ImageCompressor.compressAndSaveEvidencePhoto(
                context = context,
                sourceFile = tempFile,
                idRegistro = idRegistro,
                tipoEvidencia = "1_SECTOR_INICIO"
            )
            _uiState.update {
                it.copy(
                    pathFotoInicio = compressedPath,
                    omitirFotoInicio = false,
                    motivoOmisionInicio = "",
                    isSavingPhoto = false
                )
            }
            val actual = jornadaDao.getJornadaById(idRegistro)
            if (actual != null) {
                jornadaDao.updateJornada(actual.copy(pathFotoInicio = compressedPath, motivoOmisionInicio = null))
            }
        }
    }

    fun guardarPantalla3SectoresYFoto(onSuccessNavigateToP4: () -> Unit) {
        val state = _uiState.value
        val idRegistro = state.idRegistroActivo ?: return

        // Exigir al menos 1 sector y o bien foto tomada o bien omitida con motivo
        if (state.selectedSectoresIds.isEmpty()) return
        if (!state.fotoInicioResuelta) return

        viewModelScope.launch(manejadorErrores) {
            // Solo sectores de la playa elegida en P2
            val todosSectores = catalogoDao.observeSectores().firstOrNull().orEmpty()
            val sectoresElegidos = todosSectores
                .filter { it.idSector in state.selectedSectoresIds && it.idPlaya == state.selectedPlayaId }
            if (sectoresElegidos.isEmpty()) return@launch

            val areaTotalM2 = sectoresElegidos.sumOf { it.areaM2 }
            val nombresSectoresCsv = sectoresElegidos.joinToString(", ") { it.nombreSector }
            val idsSectoresCsv = sectoresElegidos.joinToString(",") { it.idSector }
            val omitida = state.pathFotoInicio.isBlank()

            val actual = jornadaDao.getJornadaById(idRegistro)
            if (actual != null) {
                jornadaDao.updateJornada(
                    actual.copy(
                        sectoresCsv = nombresSectoresCsv,
                        sectoresIdsCsv = idsSectoresCsv,
                        areaTotalM2 = areaTotalM2,
                        pathFotoInicio = state.pathFotoInicio,
                        motivoOmisionInicio = if (omitida) state.motivoOmisionInicio.trim() else null,
                        pantallaActual = maxOf(actual.pantallaActual, 4)
                    )
                )
            }
            onSuccessNavigateToP4()
        }
    }

    // --- PANTALLA 4: Ingreso de Herramientas por Código QR o Dropdown ---
    // El QR se sigue usando para identificar la herramienta, pero los mensajes nunca muestran el código (ISSUE-34)
    fun agregarHerramientaPorQr(rawQrCode: String) {
        val idRegistro = _uiState.value.idRegistroActivo ?: return
        if (_uiState.value.sinHerramientas) return
        val codigoLimpio = rawQrCode.trim().uppercase()
        if (codigoLimpio.isEmpty()) return

        viewModelScope.launch(manejadorErrores) {
            val catalogoItem = catalogoDao.getHerramientaByQr(codigoLimpio)
            if (catalogoItem == null) {
                _uiState.update {
                    it.copy(qrFeedbackMessage = "Este código QR no corresponde a ninguna herramienta del inventario.", qrFeedbackEsError = true, qrFeedbackSeq = it.qrFeedbackSeq + 1)
                }
                return@launch
            }

            // ISSUE-43: herramienta dada de baja por el administrador
            if (!catalogoItem.estaActivo) {
                _uiState.update {
                    it.copy(qrFeedbackMessage = "La herramienta '${catalogoItem.nombreHerramienta}' está dada de baja y no puede utilizarse.", qrFeedbackEsError = true, qrFeedbackSeq = it.qrFeedbackSeq + 1)
                }
                return@launch
            }

            // Regla 1: Bloquear herramientas extraviadas; permitir 'Operativo' y 'Dañado' mostrando su estado
            if (catalogoItem.estado.equals(HerramientaCatalogoEntity.ESTADO_EXTRAVIADO, ignoreCase = true)) {
                _uiState.update {
                    it.copy(qrFeedbackMessage = "La herramienta '${catalogoItem.nombreHerramienta}' figura como Extraviada y no puede utilizarse.", qrFeedbackEsError = true, qrFeedbackSeq = it.qrFeedbackSeq + 1)
                }
                return@launch
            }

            val insertedRowId = jornadaDao.insertHerramientaJornada(
                HerramientaJornadaEntity(
                    idRegistro = idRegistro,
                    qrCodigo = catalogoItem.qrCodigo,
                    nombreHerramienta = catalogoItem.nombreHerramienta,
                    estadoFinal = catalogoItem.estado
                )
            )
            if (insertedRowId == -1L) {
                _uiState.update {
                    it.copy(qrFeedbackMessage = "La herramienta '${catalogoItem.nombreHerramienta}' ya fue agregada.", qrFeedbackEsError = true, qrFeedbackSeq = it.qrFeedbackSeq + 1)
                }
            } else {
                val avisoEstado = if (catalogoItem.estado.equals(HerramientaCatalogoEntity.ESTADO_DANADO, ignoreCase = true)) {
                    " (Estado actual: Dañado)"
                } else {
                    ""
                }
                _uiState.update {
                    it.copy(qrFeedbackMessage = "Agregada: ${catalogoItem.nombreHerramienta}$avisoEstado", qrFeedbackEsError = false, qrFeedbackSeq = it.qrFeedbackSeq + 1)
                }
            }
        }
    }

    fun eliminarHerramientaAgregada(id: Int) {
        viewModelScope.launch(manejadorErrores) {
            jornadaDao.deleteHerramientaJornadaById(id)
        }
    }

    /**
     * ISSUE-34: "Esta brigada no lleva herramientas". Al marcarla se quitan las herramientas
     * ya agregadas (una jornada no puede tener ambas cosas) y luego se salta P7.
     */
    fun toggleSinHerramientas(sinHerramientas: Boolean) {
        val idRegistro = _uiState.value.idRegistroActivo ?: return
        _uiState.update { it.copy(sinHerramientas = sinHerramientas, qrFeedbackMessage = null) }
        viewModelScope.launch(manejadorErrores) {
            if (sinHerramientas) jornadaDao.deleteHerramientasJornada(idRegistro)
            val actual = jornadaDao.getJornadaById(idRegistro) ?: return@launch
            jornadaDao.updateJornada(actual.copy(sinHerramientas = sinHerramientas))
        }
    }

    fun guardarPantalla4Herramientas(onSuccessNavigateToP5: () -> Unit) {
        val idRegistro = _uiState.value.idRegistroActivo ?: return
        val sinHerramientas = _uiState.value.sinHerramientas
        viewModelScope.launch(manejadorErrores) {
            val actuales = jornadaDao.getHerramientasJornadaList(idRegistro)
            if (actuales.isEmpty() && !sinHerramientas) return@launch
            jornadaDao.updatePantallaActual(idRegistro, 5)
            onSuccessNavigateToP5()
        }
    }

    // --- PANTALLA 5: Comenzar/Finalizar Limpieza, GPS (km_recorridos) y Botonera (-1 / +1 / +10) ---
    fun comenzarLimpieza() {
        val idRegistro = _uiState.value.idRegistroActivo ?: return
        if (_uiState.value.isLimpiezaIniciada) return

        val ahoraMs = reloj()
        _uiState.update {
            it.copy(
                isLimpiezaIniciada = true,
                horaInicioMs = ahoraMs
            )
        }

        viewModelScope.launch(manejadorErrores) {
            val actual = jornadaDao.getJornadaById(idRegistro)
            if (actual != null && actual.horaInicioMs == 0L) {
                jornadaDao.updateJornada(actual.copy(horaInicioMs = ahoraMs))
            }
        }
    }

    fun modificarConteoBasura(idTipoBasura: String, delta: Int) {
        if (!_uiState.value.isLimpiezaIniciada) return
        val idRegistro = _uiState.value.idRegistroActivo ?: return
        viewModelScope.launch(manejadorErrores) {
            jornadaDao.modificarCantidadBasura(
                idRegistro = idRegistro,
                idTipoBasura = idTipoBasura,
                delta = delta
            )
        }
    }

    fun finalizarLimpieza(onSuccessNavigateToP6: () -> Unit) {
        val state = _uiState.value
        if (!state.isLimpiezaIniciada) return
        // Una limpieza ya finalizada no se vuelve a cerrar (no se pisa hora_fin ni km)
        if (state.horaFinMs > 0L) return
        val idRegistro = state.idRegistroActivo ?: return
        // Tope de 12 h (ISSUE-31): si se olvidó finalizar, la duración queda en el máximo
        // en vez de dejar una jornada que el servidor rechazaría para siempre
        val finMs = minOf(reloj(), state.horaInicioMs + TopesJornada.MAX_DURACION_MS)
        _uiState.update { it.copy(horaFinMs = finMs) }

        viewModelScope.launch(manejadorErrores) {
            val actual = jornadaDao.getJornadaById(idRegistro)
            if (actual != null) {
                jornadaDao.updateJornada(
                    // km_recorridos queda tal como lo dejó el servicio de GPS
                    actual.copy(
                        horaFinMs = finMs,
                        pantallaActual = 6
                    )
                )
            }
            onSuccessNavigateToP6()
        }
    }

    // --- PANTALLA 6: Evidencias Finales (Foto Sector Limpio + Foto Sacos Llenos, con opción de omitir) ---
    fun toggleOmitirFotoLimpio(omitir: Boolean) {
        _uiState.update {
            it.copy(
                omitirFotoLimpio = omitir,
                pathFotoLimpio = if (omitir) "" else it.pathFotoLimpio,
                motivoOmisionLimpio = if (omitir) it.motivoOmisionLimpio else ""
            )
        }
        actualizarJornadaEnSegundoPlano { actual ->
            if (omitir) actual.copy(pathFotoLimpio = "", motivoOmisionLimpio = actual.motivoOmisionLimpio ?: "")
            else actual.copy(motivoOmisionLimpio = null)
        }
    }

    fun onMotivoOmisionLimpioChange(motivo: String) {
        val recortado = motivo.take(TopesJornada.MAX_CARACTERES_MOTIVO_OMISION)
        _uiState.update { it.copy(motivoOmisionLimpio = recortado) }
        actualizarJornadaEnSegundoPlano { it.copy(motivoOmisionLimpio = recortado) }
    }

    fun toggleOmitirFotoSacos(omitir: Boolean) {
        _uiState.update {
            it.copy(
                omitirFotoSacos = omitir,
                pathFotoSacos = if (omitir) "" else it.pathFotoSacos,
                motivoOmisionSacos = if (omitir) it.motivoOmisionSacos else ""
            )
        }
        actualizarJornadaEnSegundoPlano { actual ->
            if (omitir) actual.copy(pathFotoSacos = "", motivoOmisionSacos = actual.motivoOmisionSacos ?: "")
            else actual.copy(motivoOmisionSacos = null)
        }
    }

    fun onMotivoOmisionSacosChange(motivo: String) {
        val recortado = motivo.take(TopesJornada.MAX_CARACTERES_MOTIVO_OMISION)
        _uiState.update { it.copy(motivoOmisionSacos = recortado) }
        actualizarJornadaEnSegundoPlano { it.copy(motivoOmisionSacos = recortado) }
    }

    fun procesarFotoLimpioCapturada(context: Context, tempFile: File) {
        val idRegistro = _uiState.value.idRegistroActivo ?: "TEMP"
        viewModelScope.launch(manejadorErrores) {
            _uiState.update { it.copy(isSavingPhoto = true) }
            val compressedPath = ImageCompressor.compressAndSaveEvidencePhoto(
                context = context,
                sourceFile = tempFile,
                idRegistro = idRegistro,
                tipoEvidencia = "2_SECTOR_LIMPIO"
            )
            _uiState.update {
                it.copy(
                    pathFotoLimpio = compressedPath,
                    omitirFotoLimpio = false,
                    motivoOmisionLimpio = "",
                    isSavingPhoto = false
                )
            }
            val actual = jornadaDao.getJornadaById(idRegistro)
            if (actual != null) {
                jornadaDao.updateJornada(actual.copy(pathFotoLimpio = compressedPath, motivoOmisionLimpio = null))
            }
        }
    }

    fun procesarFotoSacosCapturada(context: Context, tempFile: File) {
        val idRegistro = _uiState.value.idRegistroActivo ?: "TEMP"
        viewModelScope.launch(manejadorErrores) {
            _uiState.update { it.copy(isSavingPhoto = true) }
            val compressedPath = ImageCompressor.compressAndSaveEvidencePhoto(
                context = context,
                sourceFile = tempFile,
                idRegistro = idRegistro,
                tipoEvidencia = "3_SACOS_LLENOS"
            )
            _uiState.update {
                it.copy(
                    pathFotoSacos = compressedPath,
                    omitirFotoSacos = false,
                    motivoOmisionSacos = "",
                    isSavingPhoto = false
                )
            }
            val actual = jornadaDao.getJornadaById(idRegistro)
            if (actual != null) {
                jornadaDao.updateJornada(actual.copy(pathFotoSacos = compressedPath, motivoOmisionSacos = null))
            }
        }
    }

    /**
     * Guarda P6 y avanza a P7, o directo a P8 si la brigada no lleva herramientas (ISSUE-34).
     * [onSuccessNavigate] recibe el número de la pantalla siguiente.
     */
    fun guardarPantalla6Evidencias(onSuccessNavigate: (siguientePantalla: Int) -> Unit) {
        val state = _uiState.value
        val idRegistro = state.idRegistroActivo ?: return
        if (!state.fotosFinalesResueltas) return

        val limpioOmitida = state.pathFotoLimpio.isBlank()
        val sacosOmitida = state.pathFotoSacos.isBlank()
        val siguiente = if (state.sinHerramientas) 8 else 7

        viewModelScope.launch(manejadorErrores) {
            val actual = jornadaDao.getJornadaById(idRegistro)
            if (actual != null) {
                jornadaDao.updateJornada(
                    actual.copy(
                        pathFotoLimpio = state.pathFotoLimpio,
                        motivoOmisionLimpio = if (limpioOmitida) state.motivoOmisionLimpio.trim() else null,
                        pathFotoSacos = state.pathFotoSacos,
                        motivoOmisionSacos = if (sacosOmitida) state.motivoOmisionSacos.trim() else null,
                        pantallaActual = siguiente
                    )
                )
            }
            onSuccessNavigate(siguiente)
        }
    }

    // --- PANTALLA 7: Estado de Herramientas Ingresadas ---
    fun actualizarEstadoHerramienta(herramienta: HerramientaJornadaEntity, nuevoEstado: String) {
        viewModelScope.launch(manejadorErrores) {
            jornadaDao.updateEstadoHerramientaJornada(herramienta.id, nuevoEstado)
            catalogoDao.updateEstadoHerramientaCatalogo(herramienta.qrCodigo, nuevoEstado)
        }
    }

    fun guardarPantalla7EstadoHerramientas(onSuccessNavigateToP8: () -> Unit) {
        val idRegistro = _uiState.value.idRegistroActivo ?: return
        viewModelScope.launch(manejadorErrores) {
            jornadaDao.updatePantallaActual(idRegistro, 8)
            onSuccessNavigateToP8()
        }
    }

    // --- PANTALLA 8: Resumen, Encargado de Pesaje e Ingreso de Pesos Recolectados (Ley N° 20.949) ---
    fun selectEncargadoPesaje(nombreEncargado: String) {
        _uiState.update { it.copy(encargadoPesaje = nombreEncargado, encargadoEsExterno = false) }
    }

    /** "Otra persona (escribir nombre)": habilita el campo de texto y limpia la selección previa. */
    fun seleccionarEncargadoExterno() {
        _uiState.update { it.copy(encargadoPesaje = "", encargadoEsExterno = true) }
    }

    fun onEncargadoExternoChange(nombre: String) {
        _uiState.update { it.copy(encargadoPesaje = nombre, encargadoEsExterno = true) }
    }

    fun onCantidadSacosChange(value: String) {
        val filtered = value.filter { it.isDigit() }
        _uiState.update { current ->
            val next = current.copy(cantidadSacosInput = filtered)
            next.copy(pesajeErrorMessage = calcularMensajeErrorPesaje(next))
        }
    }

    fun onKgBasuraNormalChange(value: String) {
        val normalized = value.replace(',', '.')
        if (normalized.count { it == '.' } <= 1 && normalized.all { it.isDigit() || it == '.' }) {
            _uiState.update { current ->
                val next = current.copy(kgBasuraNormalInput = normalized)
                next.copy(pesajeErrorMessage = calcularMensajeErrorPesaje(next))
            }
        }
    }

    fun onKgBasuraReciclableChange(value: String) {
        val normalized = value.replace(',', '.')
        if (normalized.count { it == '.' } <= 1 && normalized.all { it.isDigit() || it == '.' }) {
            _uiState.update { current ->
                val next = current.copy(kgBasuraReciclableInput = normalized)
                next.copy(pesajeErrorMessage = calcularMensajeErrorPesaje(next))
            }
        }
    }

    private fun calcularMensajeErrorPesaje(state: EquipoSeleccionUiState): String? {
        if (state.cantidadSacosVal > TopesJornada.MAX_SACOS) {
            return "Máximo ${TopesJornada.MAX_SACOS} sacos por jornada. Revisa la cantidad."
        }
        if (state.kgTotalesVal > TopesJornada.MAX_KG_TOTALES) {
            return "Máximo ${TopesJornada.MAX_KG_TOTALES.toInt()} kg por jornada. Revisa los kilos."
        }
        if (state.excedeLimiteLeySaco) {
            // ISSUE-52: texto corto; el detalle del promedio ya se ve en la barra de la P8
            return "El promedio por saco supera los 25 kg (Ley 20.949)."
        }
        return null
    }

    fun guardarPantalla8Pesaje(onSuccessNavigateToP9: () -> Unit) {
        val state = _uiState.value
        val idRegistro = state.idRegistroActivo ?: return

        // Regla 2 (Ley 20.949) + Encargado de Pesaje obligatorio
        if (!state.esPesajeValido) {
            _uiState.update { it.copy(pesajeErrorMessage = calcularMensajeErrorPesaje(it)) }
            return
        }

        val sacos = state.cantidadSacosVal
        val kgNormal = state.kgBasuraNormalVal
        val kgReciclable = state.kgBasuraReciclableVal
        val kgTotales = state.kgTotalesVal

        // Regla 3: Auto-seleccionar 'No aplicable' cuando el peso sea 0 kg, y prohibir 'No aplicable' si > 0 kg
        val destinoNormalCoherente = when {
            kgNormal <= 0f -> JornadaEntity.DESTINO_NORMAL_NO_APLICA
            state.destinoBasuraNormal == JornadaEntity.DESTINO_NORMAL_NO_APLICA -> JornadaEntity.DESTINO_NORMAL_DIMAO
            else -> state.destinoBasuraNormal
        }
        val guiaCoherente = if (destinoNormalCoherente == JornadaEntity.DESTINO_NORMAL_DIMAO) {
            state.nroGuiaDespachoInput
        } else {
            ""
        }

        val destinoReciclableCoherente = when {
            kgReciclable <= 0f -> JornadaEntity.DESTINO_RECICLABLE_NO_APLICA
            state.destinoBasuraReciclable == JornadaEntity.DESTINO_RECICLABLE_NO_APLICA -> JornadaEntity.DESTINO_RECICLABLE_PUNTO_LIMPIO
            else -> state.destinoBasuraReciclable
        }

        _uiState.update {
            it.copy(
                destinoBasuraNormal = destinoNormalCoherente,
                nroGuiaDespachoInput = guiaCoherente,
                destinoBasuraReciclable = destinoReciclableCoherente,
                pesajeErrorMessage = null
            )
        }

        viewModelScope.launch(manejadorErrores) {
            val actual = jornadaDao.getJornadaById(idRegistro)
            if (actual != null) {
                jornadaDao.updateJornada(
                    actual.copy(
                        encargadoPesaje = state.encargadoPesaje.trim().replace(Regex("\\s+"), " "),
                        cantidadSacos = sacos,
                        kgBasuraNormal = kgNormal,
                        kgBasuraReciclable = kgReciclable,
                        kgTotales = kgTotales,
                        destinoBasuraNormal = destinoNormalCoherente,
                        nroGuiaDespacho = guiaCoherente.trim().takeIf { it.isNotEmpty() },
                        destinoBasuraReciclable = destinoReciclableCoherente,
                        pantallaActual = 9
                    )
                )
            }
            onSuccessNavigateToP9()
        }
    }

    // --- PANTALLA 9: Entrega de Sacos (Destino Normal + Guía Despacho / Destino Reciclable) y Guardar ---
    fun selectDestinoBasuraNormal(destino: String) {
        _uiState.update { current ->
            // Regla 3: Prohibir elegir 'No aplicable' si kgNormal > 0, o cualquier otro si kgNormal == 0
            if (destino !in current.opcionesPermitidasBasuraNormal) return@update current

            // Regla 6: Ocultar y limpiar automáticamente N° Guía de Despacho si NO es 'Entregado a camión DIMAO'
            val guiaActualizada = if (destino == JornadaEntity.DESTINO_NORMAL_DIMAO) {
                current.nroGuiaDespachoInput
            } else {
                ""
            }
            current.copy(
                destinoBasuraNormal = destino,
                nroGuiaDespachoInput = guiaActualizada
            )
        }
    }

    fun onNroGuiaDespachoChange(value: String) {
        _uiState.update { current ->
            if (!current.mostrarCampoGuiaDespacho) return@update current
            // ISSUE-60: no se puede escribir más allá del máximo
            current.copy(nroGuiaDespachoInput = value.take(ReglasTexto.MAX_CARACTERES_GUIA))
        }
    }

    fun selectDestinoBasuraReciclable(destino: String) {
        _uiState.update { current ->
            // Regla 3: Prohibir elegir 'No aplicable' si kgReciclable > 0, o cualquier otro si kgReciclable == 0
            if (destino !in current.opcionesPermitidasBasuraReciclable) return@update current
            current.copy(destinoBasuraReciclable = destino)
        }
    }

    /** P9 [ Guardar ]: cierra la jornada y entrega su id para abrir la P10 (ISSUE-25). */
    fun finalizarYGuardarJornada(onJornadaCerrada: (idRegistro: String) -> Unit) {
        val state = _uiState.value
        val idRegistro = state.idRegistroActivo ?: return

        if (state.destinoBasuraNormal !in state.opcionesPermitidasBasuraNormal) return
        if (state.destinoBasuraReciclable !in state.opcionesPermitidasBasuraReciclable) return
        if (state.errorGuiaDespacho != null) {
            _avisos.tryEmit("Revisa el N° de guía de despacho.")
            return
        }

        val guiaFinal = if (state.mostrarCampoGuiaDespacho) {
            state.nroGuiaDespachoInput.trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }

        viewModelScope.launch(manejadorErrores) {
            val actual = jornadaDao.getJornadaById(idRegistro) ?: return@launch
            val cerrada = actual.copy(
                destinoBasuraNormal = state.destinoBasuraNormal,
                nroGuiaDespacho = guiaFinal,
                destinoBasuraReciclable = state.destinoBasuraReciclable
            )
            // ISSUE-42: mismas reglas que la RPC, para que el servidor no la rechace después
            val problemas = ValidadorJornada.validar(
                jornada = cerrada,
                participantes = jornadaDao.getParticipantesList(idRegistro),
                herramientas = jornadaDao.getHerramientasJornadaList(idRegistro),
                basura = jornadaDao.getBasuraRecolectadaList(idRegistro),
                sectoresCatalogo = catalogoDao.getSectoresList(),
                herramientasCatalogo = catalogoDao.observeHerramientasCatalogo().first(),
                brigadasCatalogo = catalogoDao.getBrigadasList(),
                ahoraMs = reloj()
            )
            if (problemas.isNotEmpty()) {
                RegistroErrores.advertencia(TAG, "$idRegistro no se cierra: ${problemas.joinToString("; ")}")
                _avisos.tryEmit("No se puede cerrar la jornada: ${problemas.first()}.")
                return@launch
            }
            jornadaDao.updateJornada(cerrada.copy(estadoSync = JornadaEntity.SYNC_PENDIENTE))
            _uiState.value = EquipoSeleccionUiState()
            onJornadaCerrada(idRegistro)
        }
    }

    companion object {
        private const val TAG = "JornadaFlujo"
        private const val MAX_INTENTOS_ID = 3

        /**
         * ISSUE-59: 12 caracteres hexadecimales del UUID (48 bits aleatorios, ~281 billones por día).
         * Con 6 (~16,7 millones) se podían adivinar las fotos públicas o adelantarse a una jornada real.
         */
        fun sufijoIdAleatorio(): String = UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
        const val AVISO_ERROR_GUARDADO = "No se pudo guardar. Intenta de nuevo."
    }

    class Factory(
        private val catalogoDao: CatalogoDao,
        private val jornadaDao: JornadaDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return JornadaFlujoViewModel(catalogoDao, jornadaDao) as T
        }
    }
}
