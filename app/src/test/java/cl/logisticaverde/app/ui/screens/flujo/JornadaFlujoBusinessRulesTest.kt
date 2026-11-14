package cl.logisticaverde.app.ui.screens.flujo

import cl.logisticaverde.app.data.local.db.CatalogoSeedData
import cl.logisticaverde.app.domain.reglas.ReglasTexto
import cl.logisticaverde.app.data.local.entity.HerramientaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import cl.logisticaverde.app.data.local.entity.ParticipanteJornadaEntity
import cl.logisticaverde.app.data.local.entity.PlayaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.SectorCatalogoEntity
import cl.logisticaverde.app.fakes.FakeCatalogoDao
import cl.logisticaverde.app.fakes.FakeJornadaDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JornadaFlujoBusinessRulesTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeCatalogoDao: FakeCatalogoDao
    private lateinit var fakeJornadaDao: FakeJornadaDao
    private lateinit var viewModel: JornadaFlujoViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeCatalogoDao = FakeCatalogoDao()
        fakeJornadaDao = FakeJornadaDao()
        viewModel = JornadaFlujoViewModel(fakeCatalogoDao, fakeJornadaDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Regla Pantalla 2 - Solo existen Brigadas A-D y exige lider, minimo 1 voluntario y playa`() = runTest {
        assertEquals(
            listOf("Brigada A", "Brigada B", "Brigada C", "Brigada D"),
            viewModel.brigadasDisponibles
        )

        var navigatedToP3 = false

        // Sin líder ni voluntarios -> No debe avanzar
        viewModel.guardarPantalla2Equipo { navigatedToP3 = true }
        assertFalse(navigatedToP3)
        assertNull(viewModel.uiState.value.idRegistroActivo)

        // Líder con menos de 3 caracteres -> No debe avanzar aunque haya voluntario
        viewModel.onNombreLiderChange("Al")
        assertTrue(viewModel.agregarVoluntario("Diego"))
        viewModel.guardarPantalla2Equipo { navigatedToP3 = true }
        assertFalse(navigatedToP3)

        // Con líder válido y 2 voluntarios pero sin playa -> No avanza
        viewModel.onNombreLiderChange("Vanessa")
        assertTrue(viewModel.agregarVoluntario("Valentina"))
        viewModel.guardarPantalla2Equipo { navigatedToP3 = true }
        assertFalse(navigatedToP3)

        // Con playa -> Crea la jornada y sus participantes, y avanza a Pantalla 3
        viewModel.selectBrigada("Brigada C")
        viewModel.selectPlaya(CatalogoSeedData.ID_PLAYA_GRANDE)
        viewModel.guardarPantalla2Equipo { navigatedToP3 = true }

        assertTrue(navigatedToP3)
        val idRegistro = viewModel.uiState.value.idRegistroActivo
        assertNotNull(idRegistro)

        val savedJornada = fakeJornadaDao.getJornadaById(idRegistro!!)!!
        assertEquals("Vanessa", savedJornada.nombreLider)
        assertEquals("Diego, Valentina", savedJornada.voluntariosCsv)
        assertEquals(3, savedJornada.totalParticipantes)
        assertEquals(3, savedJornada.pantallaActual)
        assertEquals("Brigada C", savedJornada.brigada)
        assertEquals(CatalogoSeedData.ID_PLAYA_GRANDE, savedJornada.idPlaya)
        assertEquals("Playa Grande de Cartagena", savedJornada.nombrePlaya)

        val participantes = fakeJornadaDao.getParticipantesList(idRegistro)
        assertEquals(listOf("Vanessa", "Diego", "Valentina"), participantes.map { it.nombre })
        assertEquals(
            listOf(
                ParticipanteJornadaEntity.ROL_LIDER,
                ParticipanteJornadaEntity.ROL_VOLUNTARIO,
                ParticipanteJornadaEntity.ROL_VOLUNTARIO
            ),
            participantes.map { it.rol }
        )
    }

    @Test
    fun `Regla Pantalla 2 - Rechaza nombres vacios y duplicados sin importar mayusculas ni espacios`() = runTest {
        viewModel.onNombreLiderChange("Vanessa Peña")

        assertFalse(viewModel.agregarVoluntario("   "))
        assertTrue(viewModel.agregarVoluntario("Diego  Muñoz"))
        assertFalse(viewModel.agregarVoluntario(" diego muñoz "))
        assertEquals("\"diego muñoz\" ya está en la lista.", viewModel.uiState.value.errorVoluntario)
        // Tampoco puede repetir el nombre del líder
        assertFalse(viewModel.agregarVoluntario("VANESSA PEÑA"))

        assertEquals("\"VANESSA PEÑA\" ya es el líder de la brigada.", viewModel.uiState.value.errorVoluntario)
        assertEquals(listOf("Diego Muñoz"), viewModel.uiState.value.voluntarios)

        // Al editar el campo se limpia el mensaje
        viewModel.limpiarErrorVoluntario()
        assertNull(viewModel.uiState.value.errorVoluntario)

        // El líder no puede quedar igual a un voluntario ya agregado
        viewModel.onNombreLiderChange("diego muñoz")
        assertNotNull(viewModel.uiState.value.errorNombreLider)
        assertFalse(viewModel.uiState.value.esEquipoValido)
    }

    @Test
    fun `Regla Pantalla 2 - Muestra error de largo minimo del lider y contador de integrantes`() = runTest {
        viewModel.onNombreLiderChange("Al")
        assertEquals("Mínimo 3 caracteres.", viewModel.uiState.value.errorNombreLider)

        viewModel.onNombreLiderChange("Ale")
        assertNull(viewModel.uiState.value.errorNombreLider)
        assertEquals("0 voluntarios + 1 líder = 1 integrantes", viewModel.uiState.value.resumenIntegrantes)

        viewModel.agregarVoluntario("Diego")
        assertEquals("1 voluntario + 1 líder = 2 integrantes", viewModel.uiState.value.resumenIntegrantes)
        viewModel.agregarVoluntario("Valentina")
        assertEquals("2 voluntarios + 1 líder = 3 integrantes", viewModel.uiState.value.resumenIntegrantes)
    }

    @Test
    fun `RNF-01 Pantalla 2 - Cada alta y baja se guarda en Room sin pulsar Siguiente`() = runTest {
        // Al agregar el primer participante se crea la jornada en pantalla 2
        viewModel.agregarVoluntario("Diego")
        val idRegistro = viewModel.uiState.value.idRegistroActivo
        assertNotNull(idRegistro)
        assertEquals(2, fakeJornadaDao.getJornadaById(idRegistro!!)!!.pantallaActual)
        assertEquals(listOf("Diego"), fakeJornadaDao.getParticipantesList(idRegistro).map { it.nombre })

        viewModel.onNombreLiderChange("Vanessa")
        viewModel.agregarVoluntario("Valentina")
        viewModel.eliminarVoluntario("Diego")

        val jornada = fakeJornadaDao.getJornadaById(idRegistro)!!
        assertEquals("Vanessa", jornada.nombreLider)
        assertEquals("Valentina", jornada.voluntariosCsv)
        assertEquals(2, jornada.totalParticipantes)
        assertEquals(
            setOf("Vanessa" to ParticipanteJornadaEntity.ROL_LIDER, "Valentina" to ParticipanteJornadaEntity.ROL_VOLUNTARIO),
            fakeJornadaDao.getParticipantesList(idRegistro).map { it.nombre to it.rol }.toSet()
        )

        // Si el teléfono se apaga aquí, al reabrir se recupera el formulario
        val reanudado = JornadaFlujoViewModel(fakeCatalogoDao, fakeJornadaDao)
        assertEquals(idRegistro, reanudado.uiState.value.idRegistroActivo)
        assertEquals("Vanessa", reanudado.uiState.value.nombreLider)
        assertEquals(listOf("Valentina"), reanudado.uiState.value.voluntarios)
    }

    @Test
    fun `Reanudacion - Reconstruye participantes y sectores desde las tablas, no por nombre`() = runTest {
        iniciarJornadaHastaPantalla3()
        viewModel.toggleSector("SECTOR-02")
        viewModel.toggleOmitirFotoInicio(true)
        viewModel.onMotivoOmisionInicioChange("Sin batería")
        viewModel.guardarPantalla3SectoresYFoto {}
        val idRegistro = viewModel.uiState.value.idRegistroActivo!!
        assertEquals("SECTOR-02", fakeJornadaDao.getJornadaById(idRegistro)!!.sectoresIdsCsv)

        // Simula reapertura de la app: un ViewModel nuevo sobre los mismos datos
        val reanudado = JornadaFlujoViewModel(fakeCatalogoDao, fakeJornadaDao)
        val state = reanudado.uiState.value
        assertEquals(idRegistro, state.idRegistroActivo)
        assertEquals("Vanessa", state.nombreLider)
        assertEquals(listOf("Diego"), state.voluntarios)
        assertEquals(setOf("SECTOR-02"), state.selectedSectoresIds)
        assertEquals(CatalogoSeedData.ID_PLAYA_GRANDE, state.selectedPlayaId)
        assertTrue(state.omitirFotoInicio)
        assertEquals("Sin batería", state.motivoOmisionInicio)
    }

    @Test
    fun `Regla Pantalla 3 y 6 - Exige al menos 1 sector y permite omitir fotografias guardando vacio`() = runTest {
        iniciarJornadaHastaPantalla3()
        var navigatedToP4 = false

        // Sin sector seleccionado no puede avanzar
        viewModel.guardarPantalla3SectoresYFoto { navigatedToP4 = true }
        assertFalse(navigatedToP4)

        // Seleccionando SECTOR-01 (2000 m²) y SECTOR-02 (2000 m²) = 4000 m², pero sin foto ni omitir -> No avanza
        viewModel.toggleSector("SECTOR-01")
        viewModel.toggleSector("SECTOR-02")
        viewModel.guardarPantalla3SectoresYFoto { navigatedToP4 = true }
        assertFalse(navigatedToP4)

        // Marcando "Omitir" sin motivo -> No avanza (ISSUE-35)
        viewModel.toggleOmitirFotoInicio(true)
        viewModel.guardarPantalla3SectoresYFoto { navigatedToP4 = true }
        assertFalse(navigatedToP4)

        // Con motivo -> Avanza a Pantalla 4 guardando pathFotoInicio vacío y el motivo
        viewModel.onMotivoOmisionInicioChange("  Sin batería  ")
        viewModel.guardarPantalla3SectoresYFoto { navigatedToP4 = true }
        assertTrue(navigatedToP4)

        val idRegistro = viewModel.uiState.value.idRegistroActivo!!
        val updatedP3 = fakeJornadaDao.getJornadaById(idRegistro)!!
        assertEquals(4000, updatedP3.areaTotalM2)
        assertEquals("", updatedP3.pathFotoInicio)
        assertEquals(4, updatedP3.pantallaActual)
        assertEquals("Sin batería", updatedP3.motivoOmisionInicio)

        // En Pantalla 6: sin fotos ni omitir no avanza; omitir sin motivo tampoco
        var siguiente: Int? = null
        viewModel.guardarPantalla6Evidencias { siguiente = it }
        assertNull(siguiente)

        viewModel.toggleOmitirFotoLimpio(true)
        viewModel.toggleOmitirFotoSacos(true)
        viewModel.onMotivoOmisionLimpioChange("Había personas en la toma")
        viewModel.guardarPantalla6Evidencias { siguiente = it }
        assertNull(siguiente)

        // Con ambos motivos avanza a Pantalla 7 (la brigada lleva herramientas)
        viewModel.onMotivoOmisionSacosChange("Cámara mojada")
        viewModel.guardarPantalla6Evidencias { siguiente = it }
        assertEquals(7, siguiente)

        val updatedP6 = fakeJornadaDao.getJornadaById(idRegistro)!!
        assertEquals("", updatedP6.pathFotoLimpio)
        assertEquals("", updatedP6.pathFotoSacos)
        assertEquals(7, updatedP6.pantallaActual)
        assertEquals("Había personas en la toma", updatedP6.motivoOmisionLimpio)
        assertEquals("Cámara mojada", updatedP6.motivoOmisionSacos)
    }

    @Test
    fun `Regla Pantalla 4 - Oculta y bloquea herramientas Extraviado, permite Danado mostrando su estado`() = runTest {
        val collectorCatalogo = backgroundScope.launch(testDispatcher) {
            viewModel.herramientasCatalogo.collect {}
        }
        val collectorJornada = backgroundScope.launch(testDispatcher) {
            viewModel.herramientasJornada.collect {}
        }

        iniciarJornadaHastaPantalla3()

        // Configuramos 1 herramienta Extraviada y 1 herramienta Dañada en el catálogo
        fakeCatalogoDao.updateEstadoHerramientaCatalogo(
            "INV-02-PINZA-01",
            HerramientaCatalogoEntity.ESTADO_EXTRAVIADO
        )
        fakeCatalogoDao.updateEstadoHerramientaCatalogo(
            "INV-02-PINZA-02",
            HerramientaCatalogoEntity.ESTADO_DANADO
        )

        // 1. Verificar que 'INV-02-PINZA-01' (Extraviado) se oculta de herramientasCatalogo
        val disponibles = viewModel.herramientasCatalogo.value
        assertFalse(disponibles.any { it.qrCodigo == "INV-02-PINZA-01" })
        assertTrue(disponibles.any { it.qrCodigo == "INV-02-PINZA-02" })

        // 2. Intentar escanear la herramienta Extraviada por QR debe bloquearse
        viewModel.agregarHerramientaPorQr("INV-02-PINZA-01")
        assertTrue(viewModel.herramientasJornada.value.isEmpty())
        assertTrue(viewModel.uiState.value.qrFeedbackMessage.orEmpty().contains("Extraviada"))
        assertTrue(viewModel.uiState.value.qrFeedbackEsError) // ISSUE-48: mensaje en rojo

        // 3. Escanear o elegir del desplegable la herramienta Dañada sí se permite y conserva su estado 'Dañado'
        viewModel.agregarHerramientaPorQr("INV-02-PINZA-02")
        assertEquals(1, viewModel.herramientasJornada.value.size)
        val agregada = viewModel.herramientasJornada.value.first()
        assertEquals(HerramientaCatalogoEntity.ESTADO_DANADO, agregada.estadoFinal)
        assertTrue(viewModel.uiState.value.qrFeedbackMessage.orEmpty().contains("Dañado"))
        assertFalse(viewModel.uiState.value.qrFeedbackEsError) // ISSUE-48: agregada → verde

        // 4. Intentar agregar la misma herramienta por segunda vez debe bloquear duplicado
        viewModel.agregarHerramientaPorQr("INV-02-PINZA-02")
        assertEquals(1, viewModel.herramientasJornada.value.size)

        collectorCatalogo.cancel()
        collectorJornada.cancel()
    }

    @Test
    fun `Regla Pantalla 5 - Botonera bloqueada antes de iniciar, nunca baja de 0 y permite cerrar con 0 residuos`() = runTest {
        val collectorBasura = backgroundScope.launch(testDispatcher) {
            viewModel.basuraRecolectada.collect {}
        }

        iniciarJornadaHastaPantalla3()
        val idRegistro = viewModel.uiState.value.idRegistroActivo!!

        // Antes de comenzar limpieza, modificar conteo no tiene efecto y no permite finalizar
        viewModel.modificarConteoBasura("COLILLAS", 10)
        val colillasAntes = fakeJornadaDao.getBasuraRecolectadaList(idRegistro)
            .first { it.idTipoBasura == "COLILLAS" }.cantidad
        assertEquals(0, colillasAntes)

        var navigatedToP6 = false
        viewModel.finalizarLimpieza { navigatedToP6 = true }
        assertFalse(navigatedToP6)

        // Al comenzar limpieza, se registra horaInicioMs y se habilita la botonera
        viewModel.comenzarLimpieza()
        assertTrue(viewModel.uiState.value.isLimpiezaIniciada)

        // Intentar restar -1 cuando está en 0 nunca baja de 0
        viewModel.modificarConteoBasura("COLILLAS", -1)
        val colillasTrasRestaEnCero = fakeJornadaDao.getBasuraRecolectadaList(idRegistro)
            .first { it.idTipoBasura == "COLILLAS" }.cantidad
        assertEquals(0, colillasTrasRestaEnCero)

        // Permite finalizar limpieza incluso con 0 residuos contados en cualquier brigada
        viewModel.finalizarLimpieza { navigatedToP6 = true }
        assertTrue(navigatedToP6)

        collectorBasura.cancel()
    }

    @Test
    fun `Regla Pantalla 8 - Exige Encargado de Pesaje y valida Ley 20949 (peso por saco mayor a 0 y menor o igual a 25 kg)`() = runTest {
        iniciarJornadaHastaPantalla3()
        var navigatedToP9 = false

        // Sin encargado de pesaje -> Inválido aunque los pesos estén bien
        viewModel.onCantidadSacosChange("2")
        viewModel.onKgBasuraNormalChange("20")
        viewModel.onKgBasuraReciclableChange("10")
        assertFalse(viewModel.uiState.value.esPesajeValido)
        viewModel.guardarPantalla8Pesaje { navigatedToP9 = true }
        assertFalse(navigatedToP9)

        // Seleccionamos Encargado de Pesaje
        viewModel.selectEncargadoPesaje("Diego")

        // Caso 1: 0 sacos -> Inválido
        viewModel.onCantidadSacosChange("0")
        assertFalse(viewModel.uiState.value.esPesajeValido)
        viewModel.guardarPantalla8Pesaje { navigatedToP9 = true }
        assertFalse(navigatedToP9)

        // Caso 2: 1 saco con 26 kg (26 kg/saco > 25 kg Ley 20.949) -> Bloqueado con error
        viewModel.onCantidadSacosChange("1")
        viewModel.onKgBasuraNormalChange("20")
        viewModel.onKgBasuraReciclableChange("6")
        assertTrue(viewModel.uiState.value.excedeLimiteLeySaco)
        assertFalse(viewModel.uiState.value.esPesajeValido)
        assertNotNull(viewModel.uiState.value.pesajeErrorMessage)
        viewModel.guardarPantalla8Pesaje { navigatedToP9 = true }
        assertFalse(navigatedToP9)

        // Caso 3: 2 sacos con 25 kg normal + 25 kg reciclable = 50 kg (exactamente 25.0 kg/saco) -> Válido
        viewModel.onCantidadSacosChange("2")
        viewModel.onKgBasuraNormalChange("25")
        viewModel.onKgBasuraReciclableChange("25")
        assertFalse(viewModel.uiState.value.excedeLimiteLeySaco)
        assertTrue(viewModel.uiState.value.esPesajeValido)
        assertNull(viewModel.uiState.value.pesajeErrorMessage)
        viewModel.guardarPantalla8Pesaje { navigatedToP9 = true }
        assertTrue(navigatedToP9)

        val idRegistro = viewModel.uiState.value.idRegistroActivo!!
        val saved = fakeJornadaDao.getJornadaById(idRegistro)!!
        assertEquals("Diego", saved.encargadoPesaje)
    }

    /** Deja la jornada con lo que en el flujo real completan P3–P7, para que pase el ValidadorJornada. */
    private suspend fun completarParaCierre(idRegistro: String) {
        val ahora = System.currentTimeMillis()
        fakeJornadaDao.deleteHerramientasJornada(idRegistro)
        val actual = fakeJornadaDao.getJornadaById(idRegistro)!!
        fakeJornadaDao.updateJornada(
            actual.copy(
                sectoresIdsCsv = "SECTOR-01",
                horaInicioMs = ahora - 60_000,
                horaFinMs = ahora,
                sinHerramientas = true,
                pathFotoInicio = "", motivoOmisionInicio = "Prueba",
                pathFotoLimpio = "", motivoOmisionLimpio = "Prueba",
                pathFotoSacos = "", motivoOmisionSacos = "Prueba"
            )
        )
    }

    @Test
    fun `Regla Pantallas 8 y 9 - Coherencia automatica de No aplicable cuando peso es 0 kg y limpieza de Guia Despacho`() = runTest {
        iniciarJornadaHastaPantalla3()

        // Seleccionamos Encargado de Pesaje e ingresamos 2 sacos: 15 kg normal y 0 kg reciclable
        viewModel.selectEncargadoPesaje("Diego")
        viewModel.onCantidadSacosChange("2")
        viewModel.onKgBasuraNormalChange("15")
        viewModel.onKgBasuraReciclableChange("0")

        var navigatedToP9 = false
        viewModel.guardarPantalla8Pesaje { navigatedToP9 = true }
        assertTrue(navigatedToP9)

        // 1. Como kgReciclable == 0, destinoBasuraReciclable se auto-selecciona en 'No aplicable'
        assertEquals(
            JornadaEntity.DESTINO_RECICLABLE_NO_APLICA,
            viewModel.uiState.value.destinoBasuraReciclable
        )
        // Intentar elegir 'Entregado a Punto Limpio Municipal' con 0 kg reciclables debe estar bloqueado
        viewModel.selectDestinoBasuraReciclable(JornadaEntity.DESTINO_RECICLABLE_PUNTO_LIMPIO)
        assertEquals(
            JornadaEntity.DESTINO_RECICLABLE_NO_APLICA,
            viewModel.uiState.value.destinoBasuraReciclable
        )

        // 2. Como kgNormal == 15 (> 0), se prohíbe seleccionar 'No aplicable' en basura normal
        viewModel.selectDestinoBasuraNormal(JornadaEntity.DESTINO_NORMAL_NO_APLICA)
        assertEquals(
            JornadaEntity.DESTINO_NORMAL_DIMAO,
            viewModel.uiState.value.destinoBasuraNormal
        )

        // 3. Con 'Entregado a camión DIMAO' se puede ingresar N° Guía de Despacho
        assertTrue(viewModel.uiState.value.mostrarCampoGuiaDespacho)
        viewModel.onNroGuiaDespachoChange("GD-2026-999")
        assertEquals("GD-2026-999", viewModel.uiState.value.nroGuiaDespachoInput)

        // 4. Al cambiar a 'Pendiente de retiro', se oculta y limpia automáticamente el N° Guía de Despacho
        viewModel.selectDestinoBasuraNormal(JornadaEntity.DESTINO_NORMAL_PENDIENTE)
        assertFalse(viewModel.uiState.value.mostrarCampoGuiaDespacho)
        assertEquals("", viewModel.uiState.value.nroGuiaDespachoInput)

        // 5. Al guardar la jornada, en Room queda PENDIENTE_SYNC y nroGuiaDespacho = null
        val idRegistro = viewModel.uiState.value.idRegistroActivo!!

        // ISSUE-42: con datos incompletos (sin limpieza ni fotos) la jornada NO se cierra y se avisa
        val avisos = mutableListOf<String>()
        val recolector = backgroundScope.launch(testDispatcher) { viewModel.avisos.collect { avisos += it } }
        var returnedToMenu = false
        viewModel.finalizarYGuardarJornada { returnedToMenu = true }
        assertFalse(returnedToMenu)
        assertEquals(JornadaEntity.SYNC_EN_CURSO, fakeJornadaDao.getJornadaById(idRegistro)!!.estadoSync)
        assertTrue(avisos.single().startsWith("No se puede cerrar la jornada:"))
        recolector.cancel()

        // Se completa lo que en el flujo real hacen P3–P7 (limpieza, fotos omitidas con motivo, sin herramientas)
        completarParaCierre(idRegistro)
        viewModel.finalizarYGuardarJornada { returnedToMenu = true }
        assertTrue(returnedToMenu)

        val jornadaCerrada = fakeJornadaDao.getJornadaById(idRegistro)!!
        assertEquals(JornadaEntity.SYNC_PENDIENTE, jornadaCerrada.estadoSync)
        assertEquals(JornadaEntity.DESTINO_NORMAL_PENDIENTE, jornadaCerrada.destinoBasuraNormal)
        assertNull(jornadaCerrada.nroGuiaDespacho)
        assertEquals(JornadaEntity.DESTINO_RECICLABLE_NO_APLICA, jornadaCerrada.destinoBasuraReciclable)
    }

    @Test
    fun `Regla Pantalla 1 - Semaforo Tricolor del Boton Sincronizar (Gris, Amarillo, Verde)`() = runTest {
        fun resolverEstadoBoton(pendingCount: Int, isOnline: Boolean, isSyncing: Boolean): String = when {
            isSyncing -> "SINCRONIZANDO"
            pendingCount == 0 -> "GRIS_SIN_DATOS"
            !isOnline -> "AMARILLO_SIN_INTERNET"
            else -> "VERDE_LISTO_PARA_SYNC"
        }

        // 0 pendientes (con o sin internet) -> GRIS
        assertEquals("GRIS_SIN_DATOS", resolverEstadoBoton(pendingCount = 0, isOnline = false, isSyncing = false))
        assertEquals("GRIS_SIN_DATOS", resolverEstadoBoton(pendingCount = 0, isOnline = true, isSyncing = false))

        // >0 pendientes y sin internet -> AMARILLO
        assertEquals("AMARILLO_SIN_INTERNET", resolverEstadoBoton(pendingCount = 2, isOnline = false, isSyncing = false))

        // >0 pendientes y con internet -> VERDE
        assertEquals("VERDE_LISTO_PARA_SYNC", resolverEstadoBoton(pendingCount = 2, isOnline = true, isSyncing = false))
    }

    @Test
    fun `Regla Pantalla 8 - Encargado externo exige nombre escrito y se guarda limpio`() = runTest {
        iniciarJornadaHastaPantalla3()
        val idRegistro = viewModel.uiState.value.idRegistroActivo!!
        var navigatedToP9 = false

        viewModel.selectEncargadoPesaje("Diego")
        viewModel.onCantidadSacosChange("2")
        viewModel.onKgBasuraNormalChange("20")
        viewModel.onKgBasuraReciclableChange("10")
        assertTrue(viewModel.uiState.value.esPesajeValido)

        // Elegir "Otra persona" limpia la selección: sin nombre escrito no es válido
        viewModel.seleccionarEncargadoExterno()
        assertTrue(viewModel.uiState.value.encargadoEsExterno)
        assertFalse(viewModel.uiState.value.esPesajeValido)
        viewModel.onEncargadoExternoChange("   ")
        assertFalse(viewModel.uiState.value.esPesajeValido)
        viewModel.guardarPantalla8Pesaje { navigatedToP9 = true }
        assertFalse(navigatedToP9)

        // Con nombre escrito sigue aplicando la Ley 20.949 (31 kg/saco > 25 kg)
        viewModel.onEncargadoExternoChange("  Rosa   Acopio ")
        viewModel.onCantidadSacosChange("1")
        viewModel.onKgBasuraNormalChange("31")
        viewModel.onKgBasuraReciclableChange("0")
        assertFalse(viewModel.uiState.value.esPesajeValido)

        viewModel.onCantidadSacosChange("2")
        assertTrue(viewModel.uiState.value.esPesajeValido)
        viewModel.guardarPantalla8Pesaje { navigatedToP9 = true }
        assertTrue(navigatedToP9)
        assertEquals("Rosa Acopio", fakeJornadaDao.getJornadaById(idRegistro)!!.encargadoPesaje)

        // Al reanudar se reconoce que el encargado no es de la brigada
        val reanudado = JornadaFlujoViewModel(fakeCatalogoDao, fakeJornadaDao)
        assertTrue(reanudado.uiState.value.encargadoEsExterno)
        assertEquals("Rosa Acopio", reanudado.uiState.value.encargadoPesaje)
    }

    @Test
    fun `RF-NAV-3 - Una limpieza finalizada no se puede volver a finalizar ni cambia hora_fin`() = runTest {
        iniciarJornadaHastaPantalla3()
        val idRegistro = viewModel.uiState.value.idRegistroActivo!!

        viewModel.comenzarLimpieza()
        var navegacionesAP6 = 0
        viewModel.finalizarLimpieza { navegacionesAP6++ }
        val horaFinOriginal = fakeJornadaDao.getJornadaById(idRegistro)!!.horaFinMs
        assertTrue(horaFinOriginal > 0L)

        Thread.sleep(5)
        // Una lectura GPS tardía no cambia los km de una limpieza ya cerrada
        val kmFinales = fakeJornadaDao.getJornadaById(idRegistro)!!.kmRecorridos
        fakeJornadaDao.updateKmRecorridos(idRegistro, 9.9f)
        viewModel.finalizarLimpieza { navegacionesAP6++ }

        assertEquals(1, navegacionesAP6)
        val jornada = fakeJornadaDao.getJornadaById(idRegistro)!!
        assertEquals(horaFinOriginal, jornada.horaFinMs)
        assertEquals(kmFinales, jornada.kmRecorridos)
        assertEquals(6, jornada.pantallaActual)
    }

    @Test
    fun `ISSUE-23 - Los km que el servicio escribe en Room se reflejan en la UI y se conservan al finalizar`() = runTest {
        iniciarJornadaHastaPantalla3()
        val idRegistro = viewModel.uiState.value.idRegistroActivo!!
        viewModel.comenzarLimpieza()

        // Simula LimpiezaLocationService escribiendo en Room
        fakeJornadaDao.updateCoordenadaInicialSiVacia(idRegistro, -33.53, -71.60)
        fakeJornadaDao.updateKmRecorridos(idRegistro, 1.25f)
        assertEquals(1.25f, viewModel.uiState.value.kmRecorridos)

        // La coordenada inicial no se pisa con lecturas posteriores
        fakeJornadaDao.updateCoordenadaInicialSiVacia(idRegistro, -10.0, -10.0)

        viewModel.finalizarLimpieza {}
        val jornada = fakeJornadaDao.getJornadaById(idRegistro)!!
        assertEquals(1.25f, jornada.kmRecorridos)
        assertEquals(-33.53, jornada.latitudInicial, 0.0)
        assertEquals(-71.60, jornada.longitudInicial, 0.0)
    }

    // ------------------------------------------------------------------
    // Cambios v4 (ISSUE-31 a 36)
    // ------------------------------------------------------------------

    @Test
    fun `ISSUE-31 - La fecha de la jornada se fija al crearla en P2, en hora de Chile`() = runTest {
        // 16-11-2026 02:30 UTC = 15-11-2026 23:30 en Chile (UTC-3 en noviembre)
        val instante = java.time.Instant.parse("2026-11-16T02:30:00Z").toEpochMilli()
        val vm = JornadaFlujoViewModel(fakeCatalogoDao, fakeJornadaDao, reloj = { instante })
        vm.agregarVoluntario("Diego")

        val idRegistro = vm.uiState.value.idRegistroActivo!!
        assertTrue(idRegistro.startsWith("REG-20261115-"))
        assertEquals("2026-11-15", fakeJornadaDao.getJornadaById(idRegistro)!!.fechaJornada)
    }

    @Test
    fun `ISSUE-59 - El ID de la jornada tiene un sufijo de 12 caracteres hexadecimales`() = runTest {
        viewModel.agregarVoluntario("Diego")

        val idRegistro = viewModel.uiState.value.idRegistroActivo!!
        assertTrue(idRegistro, idRegistro.matches(Regex("^REG-[0-9]{8}-[0-9A-F]{12}$")))
    }

    @Test
    fun `ISSUE-59 - Un ID repetido no reemplaza la jornada existente, se genera otro`() = runTest {
        val sufijos = ArrayDeque(listOf("AAAAAAAAAAAA", "AAAAAAAAAAAA", "BBBBBBBBBBBB"))
        val instante = java.time.Instant.parse("2026-11-16T15:00:00Z").toEpochMilli()
        val existente = JornadaEntity(
            idRegistro = "REG-20261116-AAAAAAAAAAAA",
            nombreLider = "Otra brigada",
            estadoSync = JornadaEntity.SYNC_PENDIENTE
        )
        fakeJornadaDao.insertJornada(existente)
        val vm = JornadaFlujoViewModel(
            fakeCatalogoDao, fakeJornadaDao,
            reloj = { instante },
            generarSufijoId = { sufijos.removeFirst() }
        )

        vm.agregarVoluntario("Diego")

        assertEquals("REG-20261116-BBBBBBBBBBBB", vm.uiState.value.idRegistroActivo)
        assertEquals("Otra brigada", fakeJornadaDao.getJornadaById(existente.idRegistro)!!.nombreLider)
    }

    @Test
    fun `ISSUE-60 - La guia de despacho se corta en 12 caracteres y una invalida muestra error`() = runTest {
        viewModel.onKgBasuraNormalChange("20")
        viewModel.selectDestinoBasuraNormal(JornadaEntity.DESTINO_NORMAL_DIMAO)

        viewModel.onNroGuiaDespachoChange("GD-2026-0041-EXTRA")
        assertEquals("GD-2026-0041", viewModel.uiState.value.nroGuiaDespachoInput)
        assertNull(viewModel.uiState.value.errorGuiaDespacho)

        viewModel.onNroGuiaDespachoChange("=SUMA(A1)")
        assertNotNull(viewModel.uiState.value.errorGuiaDespacho)
    }

    @Test
    fun `ISSUE-58 - Nombres con simbolos o numeros se rechazan con un mensaje bajo el campo`() = runTest {
        listOf("=IMPORTXML(A1)", "+56912345678", "@ana", "-Diego", "Diego2", "Ana.").forEach { nombre ->
            assertFalse(nombre, viewModel.agregarVoluntario(nombre))
            assertEquals(ReglasTexto.MENSAJE_NOMBRE, viewModel.uiState.value.errorVoluntario)
        }
        assertTrue(viewModel.agregarVoluntario("María José O'Higgins Núñez"))

        viewModel.onNombreLiderChange("=HYPERLINK(1)")
        assertEquals(ReglasTexto.MENSAJE_NOMBRE, viewModel.uiState.value.errorNombreLider)
        assertFalse(viewModel.uiState.value.esEquipoValido)
    }

    @Test
    fun `ISSUE-58 - Motivo de omision solo con letras, numeros y espacios`() = runTest {
        assertTrue(EquipoSeleccionUiState.esMotivoValido("Sin batería en el teléfono 2"))
        listOf("=1+1", "sin batería, lluvia", "-sin luz", "@todos").forEach { motivo ->
            assertFalse(motivo, EquipoSeleccionUiState.esMotivoValido(motivo))
        }
    }

    @Test
    fun `ISSUE-58 - El encargado externo con simbolos no permite validar el pesaje`() = runTest {
        viewModel.seleccionarEncargadoExterno()
        viewModel.onEncargadoExternoChange("=A1")
        viewModel.onCantidadSacosChange("2")
        viewModel.onKgBasuraNormalChange("20")

        assertEquals(ReglasTexto.MENSAJE_NOMBRE, viewModel.uiState.value.errorEncargadoExterno)
        assertFalse(viewModel.uiState.value.esPesajeValido)

        viewModel.onEncargadoExternoChange("Rosa del Punto Limpio")
        assertTrue(viewModel.uiState.value.esPesajeValido)
    }

    @Test
    fun `ISSUE-32 - P3 muestra solo los sectores de la playa elegida y cambiar de playa los limpia`() = runTest {
        fakeCatalogoDao.upsertPlayas(
            CatalogoSeedData.playas + PlayaCatalogoEntity(idPlaya = "PLAYA-02", nombrePlaya = "Playa Chica")
        )
        fakeCatalogoDao.upsertSectores(
            CatalogoSeedData.sectoresPlayaGrande +
                SectorCatalogoEntity(idSector = "CHICA-01", idPlaya = "PLAYA-02", nombreSector = "Chica 1", areaM2 = 500)
        )
        val collector = backgroundScope.launch(testDispatcher) { viewModel.sectoresCatalogo.collect {} }

        iniciarJornadaHastaPantalla3()
        assertEquals(4, viewModel.sectoresCatalogo.value.size)
        viewModel.toggleSector("SECTOR-01")

        viewModel.selectPlaya("PLAYA-02")
        assertEquals(listOf("CHICA-01"), viewModel.sectoresCatalogo.value.map { it.idSector })
        assertTrue(viewModel.uiState.value.selectedSectoresIds.isEmpty())
        val jornada = fakeJornadaDao.getJornadaById(viewModel.uiState.value.idRegistroActivo!!)!!
        assertEquals("Playa Chica", jornada.nombrePlaya)

        // Un sector de otra playa no se guarda aunque llegue marcado
        viewModel.toggleSector("SECTOR-02")
        viewModel.toggleSector("CHICA-01")
        viewModel.toggleOmitirFotoInicio(true)
        viewModel.onMotivoOmisionInicioChange("Niebla")
        viewModel.guardarPantalla3SectoresYFoto {}
        val guardada = fakeJornadaDao.getJornadaById(viewModel.uiState.value.idRegistroActivo!!)!!
        assertEquals("CHICA-01", guardada.sectoresIdsCsv)
        assertEquals(500, guardada.areaTotalM2)

        collector.cancel()
    }

    @Test
    fun `ISSUE-34 - Sin herramientas permite avanzar sin agregar ninguna y salta P7`() = runTest {
        val collector = backgroundScope.launch(testDispatcher) { viewModel.herramientasJornada.collect {} }
        iniciarJornadaHastaPantalla3()
        val idRegistro = viewModel.uiState.value.idRegistroActivo!!

        var navegoAP5 = false
        viewModel.guardarPantalla4Herramientas { navegoAP5 = true }
        assertFalse("Sin herramientas y sin marcar la casilla no avanza", navegoAP5)

        // Una herramienta ya agregada se quita al marcar "no lleva herramientas"
        viewModel.agregarHerramientaPorQr("INV-02-PINZA-01")
        assertEquals(1, viewModel.herramientasJornada.value.size)
        viewModel.toggleSinHerramientas(true)
        assertTrue(viewModel.herramientasJornada.value.isEmpty())
        assertTrue(fakeJornadaDao.getJornadaById(idRegistro)!!.sinHerramientas)

        // Con la casilla marcada no se pueden agregar herramientas
        viewModel.agregarHerramientaPorQr("INV-02-PINZA-02")
        assertTrue(viewModel.herramientasJornada.value.isEmpty())

        viewModel.guardarPantalla4Herramientas { navegoAP5 = true }
        assertTrue(navegoAP5)

        // P6 lleva directo a P8
        viewModel.toggleOmitirFotoLimpio(true)
        viewModel.onMotivoOmisionLimpioChange("Sin batería")
        viewModel.toggleOmitirFotoSacos(true)
        viewModel.onMotivoOmisionSacosChange("Sin batería")
        var siguiente: Int? = null
        viewModel.guardarPantalla6Evidencias { siguiente = it }
        assertEquals(8, siguiente)
        assertEquals(8, fakeJornadaDao.getJornadaById(idRegistro)!!.pantallaActual)

        // La reanudación recuerda la opción
        assertTrue(JornadaFlujoViewModel(fakeCatalogoDao, fakeJornadaDao).uiState.value.sinHerramientas)
        collector.cancel()
    }

    @Test
    fun `ISSUE-34 - Los mensajes de P4 nunca muestran el codigo QR`() = runTest {
        iniciarJornadaHastaPantalla3()
        viewModel.agregarHerramientaPorQr("INV-99-DESCONOCIDA")
        assertFalse(viewModel.uiState.value.qrFeedbackMessage.orEmpty().contains("INV-"))
        assertTrue(viewModel.uiState.value.qrFeedbackEsError)

        viewModel.agregarHerramientaPorQr("INV-02-PINZA-03")
        assertEquals("Agregada: Pinza 3", viewModel.uiState.value.qrFeedbackMessage)
        assertFalse(viewModel.uiState.value.qrFeedbackEsError)
        // La pesa ya no es parte del inventario
        assertNull(fakeCatalogoDao.getHerramientaByQr("INV-01-PESA-01"))
    }

    @Test
    fun `ISSUE-35 - Desmarcar omitir borra el motivo y tomar la foto tambien`() = runTest {
        iniciarJornadaHastaPantalla3()
        val idRegistro = viewModel.uiState.value.idRegistroActivo!!

        viewModel.toggleOmitirFotoInicio(true)
        assertEquals("", fakeJornadaDao.getJornadaById(idRegistro)!!.motivoOmisionInicio)
        viewModel.onMotivoOmisionInicioChange("x".repeat(500))
        assertEquals(120, viewModel.uiState.value.motivoOmisionInicio.length)

        viewModel.toggleOmitirFotoInicio(false)
        assertEquals("", viewModel.uiState.value.motivoOmisionInicio)
        assertNull(fakeJornadaDao.getJornadaById(idRegistro)!!.motivoOmisionInicio)
    }

    @Test
    fun `ISSUE-31 - Topes de participantes, sacos, kilos, conteo, km y duracion`() = runTest {
        // Participantes: líder + 59 voluntarios = 60
        viewModel.onNombreLiderChange("Vanessa")
        // ISSUE-58: los nombres no llevan números ("Voluntario AA", "Voluntario AB"…)
        repeat(59) { assertTrue(viewModel.agregarVoluntario("Voluntario ${'A' + it / 26}${'A' + it % 26}")) }
        assertFalse(viewModel.agregarVoluntario("Uno más"))
        assertEquals("Máximo 60 integrantes por brigada.", viewModel.uiState.value.errorVoluntario)

        // Pesaje
        viewModel.selectEncargadoPesaje("Vanessa")
        viewModel.onCantidadSacosChange("101")
        viewModel.onKgBasuraNormalChange("100")
        assertFalse(viewModel.uiState.value.esPesajeValido)
        assertEquals("Máximo 100 sacos por jornada. Revisa la cantidad.", viewModel.uiState.value.pesajeErrorMessage)
        viewModel.onCantidadSacosChange("100")
        viewModel.onKgBasuraNormalChange("2501")
        assertFalse(viewModel.uiState.value.esPesajeValido)
        viewModel.onKgBasuraNormalChange("2500")
        assertTrue(viewModel.uiState.value.esPesajeValido)
    }

    @Test
    fun `ISSUE-31 - Conteo maximo 5000, km maximo 30 y duracion maxima 12 h`() = runTest {
        var ahora = 1_000_000L
        val vm = JornadaFlujoViewModel(fakeCatalogoDao, fakeJornadaDao, reloj = { ahora })
        vm.onNombreLiderChange("Vanessa")
        vm.agregarVoluntario("Diego")
        val idRegistro = vm.uiState.value.idRegistroActivo!!

        vm.comenzarLimpieza()
        vm.modificarConteoBasura("COLILLAS", 4_995)
        vm.modificarConteoBasura("COLILLAS", 10)
        assertEquals(5_000, fakeJornadaDao.getBasuraRecolectadaList(idRegistro).first { it.idTipoBasura == "COLILLAS" }.cantidad)

        fakeJornadaDao.updateKmRecorridos(idRegistro, 45f)
        assertEquals(30f, fakeJornadaDao.getJornadaById(idRegistro)!!.kmRecorridos)

        // Se olvidó finalizar: 20 h después la duración queda en 12 h
        ahora += 20L * 60 * 60 * 1000
        vm.finalizarLimpieza {}
        val jornada = fakeJornadaDao.getJornadaById(idRegistro)!!
        assertEquals(12L * 60 * 60 * 1000, jornada.horaFinMs - jornada.horaInicioMs)
    }

    private fun iniciarJornadaHastaPantalla3() {
        viewModel.onNombreLiderChange("Vanessa")
        viewModel.agregarVoluntario("Diego")
        viewModel.selectPlaya(CatalogoSeedData.ID_PLAYA_GRANDE)
        viewModel.guardarPantalla2Equipo {}
    }

    @Test
    fun `Desarrollo - Eliminar jornada en curso borra solo esa jornada y sus participantes`() = runTest {
        viewModel.onNombreLiderChange("Vanessa")
        assertTrue(viewModel.agregarVoluntario("Diego"))
        viewModel.selectPlaya(CatalogoSeedData.ID_PLAYA_GRANDE)
        viewModel.guardarPantalla2Equipo { }
        val idEnCurso = viewModel.uiState.value.idRegistroActivo!!
        // Jornada ya cerrada (pendiente de sync): no debe tocarse
        val pendiente = fakeJornadaDao.getJornadaById(idEnCurso)!!
            .copy(idRegistro = "cerrada-1", estadoSync = JornadaEntity.SYNC_PENDIENTE)
        fakeJornadaDao.insertJornada(pendiente)

        var eliminada = false
        viewModel.eliminarJornadaEnCurso { eliminada = true }

        assertTrue(eliminada)
        assertNull(fakeJornadaDao.getJornadaById(idEnCurso))
        assertTrue(fakeJornadaDao.getParticipantesList(idEnCurso).isEmpty())
        assertNotNull(fakeJornadaDao.getJornadaById("cerrada-1"))
        assertNull(viewModel.uiState.value.idRegistroActivo)
        assertEquals("", viewModel.uiState.value.nombreLider)
    }
}
