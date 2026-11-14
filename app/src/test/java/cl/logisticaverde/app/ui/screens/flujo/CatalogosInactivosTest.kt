package cl.logisticaverde.app.ui.screens.flujo

import cl.logisticaverde.app.data.local.db.CatalogoSeedData
import cl.logisticaverde.app.data.local.entity.PlayaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.SectorCatalogoEntity
import cl.logisticaverde.app.fakes.FakeCatalogoDao
import cl.logisticaverde.app.fakes.FakeJornadaDao
import com.google.gson.Gson
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ISSUE-43: los catálogos no se borran, se desactivan con `is_activo = false`.
 * La app los oculta al ingresar datos de jornadas nuevas; lo ya elegido en la jornada en curso se mantiene.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogosInactivosTest {

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

    private fun iniciarJornadaHastaPantalla3() {
        viewModel.onNombreLiderChange("Vanessa")
        viewModel.agregarVoluntario("Diego")
        viewModel.selectPlaya(CatalogoSeedData.ID_PLAYA_GRANDE)
        viewModel.selectBrigada(CatalogoSeedData.brigadas.first())
        viewModel.guardarPantalla2Equipo {}
    }

    @Test
    fun `Sin la columna en el servidor (null) se considera activo`() {
        val gson = Gson()
        val sinColumna = gson.fromJson("""{"id_playa":"P-1","nombre_playa":"Playa"}""", PlayaCatalogoEntity::class.java)
        val inactiva = gson.fromJson(
            """{"id_playa":"P-2","nombre_playa":"Playa","is_activo":false}""",
            PlayaCatalogoEntity::class.java
        )
        assertNull(sinColumna.activo)
        assertTrue(sinColumna.estaActivo)
        assertFalse(inactiva.estaActivo)
    }

    @Test
    fun `P2 oculta playas inactivas, pero mantiene la ya elegida`() = runTest {
        fakeCatalogoDao.upsertPlayas(
            CatalogoSeedData.playas + PlayaCatalogoEntity(idPlaya = "PLAYA-02", nombrePlaya = "Playa Chica", activo = false)
        )
        backgroundScope.launch(testDispatcher) { viewModel.playasCatalogo.collect {} }

        assertEquals(listOf(CatalogoSeedData.ID_PLAYA_GRANDE), viewModel.playasCatalogo.value.map { it.idPlaya })

        // La playa elegida se desactiva a mitad de la jornada: sigue visible para esa jornada
        viewModel.selectPlaya(CatalogoSeedData.ID_PLAYA_GRANDE)
        fakeCatalogoDao.upsertPlayas(CatalogoSeedData.playas.map { it.copy(activo = false) })
        assertEquals(listOf(CatalogoSeedData.ID_PLAYA_GRANDE), viewModel.playasCatalogo.value.map { it.idPlaya })
    }

    @Test
    fun `P3 oculta sectores inactivos, pero mantiene los ya elegidos`() = runTest {
        fakeCatalogoDao.upsertSectores(
            CatalogoSeedData.sectoresPlayaGrande.map { if (it.idSector == "SECTOR-04") it.copy(activo = false) else it }
        )
        backgroundScope.launch(testDispatcher) { viewModel.sectoresCatalogo.collect {} }
        iniciarJornadaHastaPantalla3()

        assertEquals(
            listOf("SECTOR-01", "SECTOR-02", "SECTOR-03"),
            viewModel.sectoresCatalogo.value.map { it.idSector }
        )

        viewModel.toggleSector("SECTOR-01")
        fakeCatalogoDao.upsertSectores(
            CatalogoSeedData.sectoresPlayaGrande.map {
                if (it.idSector in setOf("SECTOR-01", "SECTOR-04")) it.copy(activo = false) else it
            }
        )
        assertEquals(
            listOf("SECTOR-01", "SECTOR-02", "SECTOR-03"),
            viewModel.sectoresCatalogo.value.map { it.idSector }
        )
    }

    @Test
    fun `P4 no ofrece herramientas dadas de baja y el QR las rechaza`() = runTest {
        fakeCatalogoDao.upsertHerramientas(
            CatalogoSeedData.herramientasQr.map {
                if (it.qrCodigo == "INV-02-PINZA-02") it.copy(activo = false) else it
            }
        )
        backgroundScope.launch(testDispatcher) { viewModel.herramientasCatalogo.collect {} }
        iniciarJornadaHastaPantalla3()

        assertFalse(viewModel.herramientasCatalogo.value.any { it.qrCodigo == "INV-02-PINZA-02" })
        assertTrue(viewModel.herramientasCatalogo.value.any { it.qrCodigo == "INV-02-PINZA-03" })

        viewModel.agregarHerramientaPorQr("INV-02-PINZA-02")
        assertTrue(viewModel.uiState.value.qrFeedbackEsError)
        assertTrue(viewModel.uiState.value.qrFeedbackMessage!!.contains("dada de baja"))
        val idRegistro = viewModel.uiState.value.idRegistroActivo!!
        assertTrue(fakeJornadaDao.getHerramientasJornadaList(idRegistro).isEmpty())
    }

    @Test
    fun `Una jornada nueva no trae los tipos de residuo inactivos`() = runTest {
        fakeCatalogoDao.upsertTiposBasura(
            CatalogoSeedData.tiposBasura.map { if (it.idTipoBasura == "COLILLAS") it.copy(activo = false) else it }
        )
        iniciarJornadaHastaPantalla3()

        val idRegistro = viewModel.uiState.value.idRegistroActivo!!
        val filas = fakeJornadaDao.getBasuraRecolectadaList(idRegistro)
        assertEquals(CatalogoSeedData.tiposBasura.size - 1, filas.size)
        assertFalse(filas.any { it.idTipoBasura == "COLILLAS" })
    }

    @Test
    fun `Desactivar un tipo de residuo no quita su fila a la jornada en curso`() = runTest {
        iniciarJornadaHastaPantalla3()
        val idRegistro = viewModel.uiState.value.idRegistroActivo!!

        fakeCatalogoDao.upsertTiposBasura(CatalogoSeedData.tiposBasura.map { it.copy(activo = false) })

        val filas = fakeJornadaDao.getBasuraRecolectadaList(idRegistro)
        assertEquals(CatalogoSeedData.tiposBasura.size, filas.size)
    }

    @Test
    fun `Un sector elegido que se desactiva se guarda igual en la P3`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.sectoresCatalogo.collect {} }
        iniciarJornadaHastaPantalla3()
        viewModel.toggleSector("SECTOR-02")
        fakeCatalogoDao.upsertSectores(
            CatalogoSeedData.sectoresPlayaGrande.map { it.copy(activo = false) } +
                SectorCatalogoEntity(idSector = "SECTOR-09", idPlaya = CatalogoSeedData.ID_PLAYA_GRANDE, nombreSector = "Nuevo", areaM2 = 100)
        )
        assertEquals(setOf("SECTOR-02", "SECTOR-09"), viewModel.sectoresCatalogo.value.map { it.idSector }.toSet())
    }
}
