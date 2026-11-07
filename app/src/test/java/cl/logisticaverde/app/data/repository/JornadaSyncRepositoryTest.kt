package cl.logisticaverde.app.data.repository

import cl.logisticaverde.app.data.local.db.CatalogoSeedData
import cl.logisticaverde.app.data.local.entity.BasuraRecolectadaEntity
import cl.logisticaverde.app.data.local.entity.HerramientaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.HerramientaJornadaEntity
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import cl.logisticaverde.app.data.local.entity.ParticipanteJornadaEntity
import cl.logisticaverde.app.data.local.entity.PlayaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.SectorCatalogoEntity
import cl.logisticaverde.app.data.local.entity.TipoBasuraCatalogoEntity
import cl.logisticaverde.app.data.remote.SincronizarJornadaRequest
import cl.logisticaverde.app.data.remote.SupabaseApi
import cl.logisticaverde.app.domain.sync.ErrorSync
import cl.logisticaverde.app.fakes.FakeCatalogoDao
import cl.logisticaverde.app.fakes.FakeJornadaDao
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response

class JornadaSyncRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakeCatalogoDao: FakeCatalogoDao
    private lateinit var fakeJornadaDao: FakeJornadaDao
    private lateinit var fakeApi: FakeSupabaseApi

    private val idRegistro = "REG-20261116-ABC123"

    @Before
    fun setUp() {
        fakeCatalogoDao = FakeCatalogoDao()
        fakeJornadaDao = FakeJornadaDao()
        fakeApi = FakeSupabaseApi()
    }

    private fun repository(configurado: Boolean = true) = JornadaSyncRepository(
        catalogoDao = fakeCatalogoDao,
        jornadaDao = fakeJornadaDao,
        supabaseApi = fakeApi,
        isCloudConfigured = { configurado },
        publicPhotoUrlBuilder = { "https://test.supabase.co/storage/v1/object/public/evidencias-fotos/$it" }
    )

    /** Jornada PENDIENTE_SYNC con 2 fotos (la de sacos se omitió), participantes, una herramienta y conteo. */
    private suspend fun insertarJornadaPendiente(idRegistro: String = this.idRegistro) {
        val foto1 = tempFolder.newFile("${idRegistro}_inicio.jpg").apply { writeBytes(byteArrayOf(10, 20, 30)) }
        val foto2 = tempFolder.newFile("${idRegistro}_limpio.jpg").apply { writeBytes(byteArrayOf(40, 50, 60)) }
        fakeJornadaDao.insertJornada(
            JornadaEntity(
                idRegistro = idRegistro,
                brigada = "Brigada B",
                fechaJornada = "2026-11-16",
                idPlaya = CatalogoSeedData.ID_PLAYA_GRANDE,
                nombrePlaya = "Playa Grande de Cartagena",
                nombreLider = "Vanessa",
                voluntariosCsv = "Diego",
                totalParticipantes = 2,
                sectoresIdsCsv = "SECTOR-01,SECTOR-02",
                sectoresCsv = "Sector 1, Sector 2",
                areaTotalM2 = 4000,
                horaInicioMs = 1000L,
                horaFinMs = 9000L,
                pathFotoInicio = foto1.absolutePath,
                pathFotoLimpio = foto2.absolutePath,
                pathFotoSacos = "",
                motivoOmisionSacos = "Sin batería",
                // Motivo residual de una foto que sí se tomó: no debe enviarse
                motivoOmisionInicio = "texto viejo",
                cantidadSacos = 2,
                kgBasuraNormal = 15f,
                kgBasuraReciclable = 10f,
                kgTotales = 25f,
                encargadoPesaje = "Rosa Acopio",
                estadoSync = JornadaEntity.SYNC_PENDIENTE
            )
        )
        fakeJornadaDao.insertParticipante(
            ParticipanteJornadaEntity(idRegistro = idRegistro, nombre = "Vanessa", rol = ParticipanteJornadaEntity.ROL_LIDER)
        )
        fakeJornadaDao.insertParticipante(
            ParticipanteJornadaEntity(idRegistro = idRegistro, nombre = "Diego", rol = ParticipanteJornadaEntity.ROL_VOLUNTARIO)
        )
        fakeJornadaDao.insertHerramientaJornada(
            HerramientaJornadaEntity(
                idRegistro = idRegistro,
                qrCodigo = "INV-02-PINZA-03",
                nombreHerramienta = "Pinza 3",
                estadoFinal = HerramientaCatalogoEntity.ESTADO_DANADO
            )
        )
        fakeJornadaDao.upsertBasuraRecolectada(
            CatalogoSeedData.tiposBasura.map {
                BasuraRecolectadaEntity(
                    idRegistro = idRegistro,
                    idTipoBasura = it.idTipoBasura,
                    nombreVisible = it.nombreVisible,
                    categoriaOrigen = it.categoriaOrigen,
                    cantidad = if (it.idTipoBasura == "COLILLAS") 412 else 0
                )
            }
        )
    }

    private suspend fun estadoEnRoom() = fakeJornadaDao.getJornadaById(idRegistro)!!.estadoSync

    @Test
    fun `Sin clave configurada - no marca nada como sincronizado ni llama a la red`() = runTest {
        insertarJornadaPendiente()

        val result = repository(configurado = false).sincronizarJornadasPendientes()

        assertEquals(0, result.successCount)
        assertEquals(1, result.failedCount)
        assertEquals(ErrorSync.SinConfiguracion, result.error)
        assertEquals(JornadaEntity.SYNC_PENDIENTE, estadoEnRoom())
        assertEquals(0, fakeApi.fotosSubidas.size)
        assertEquals(0, fakeApi.rpcRecibidas.size)
    }

    @Test
    fun `Sync exitoso - envia una sola RPC con toda la jornada y pasa a SINCRONIZADO`() = runTest {
        insertarJornadaPendiente()

        val result = repository().sincronizarJornadasPendientes()

        assertEquals(1, result.successCount)
        assertEquals(JornadaEntity.SYNC_SINCRONIZADO, estadoEnRoom())

        // Solo se suben las fotos que existen (la de sacos se omitió)
        assertEquals(listOf("${idRegistro}_1_inicio.jpg", "${idRegistro}_2_limpio.jpg"), fakeApi.fotosSubidas)

        val payload = fakeApi.rpcRecibidas.single().payload
        assertEquals(idRegistro, payload.jornada.idRegistro)
        assertEquals(listOf("SECTOR-01", "SECTOR-02"), payload.jornada.sectoresIds)
        assertNull(payload.jornada.urlFotoSacos)
        assertEquals("Sin batería", payload.jornada.motivoOmisionSacos)
        assertNull("La foto de inicio se subió: su motivo no viaja", payload.jornada.motivoOmisionInicio)
        assertEquals(CatalogoSeedData.ID_PLAYA_GRANDE, payload.jornada.idPlaya)
        assertEquals("2026-11-16", payload.jornada.fechaJornada)
        assertTrue(payload.jornada.urlFotoInicio!!.endsWith("/evidencias-fotos/${idRegistro}_1_inicio.jpg"))
        assertEquals(listOf("Vanessa" to "LIDER", "Diego" to "VOLUNTARIO"), payload.participantes.map { it.nombre to it.rol })
        assertEquals("Dañado", payload.herramientas.single().estadoFinal)
        assertEquals(412, payload.basura.first { it.idTipoBasura == "COLILLAS" }.cantidad)

        // El inventario local refleja el estado confirmado
        assertEquals(HerramientaCatalogoEntity.ESTADO_DANADO, fakeCatalogoDao.getHerramientaByQr("INV-02-PINZA-03")!!.estado)
    }

    @Test
    fun `Contrato RPC - el JSON usa la clave p y los nombres de columna del SQL`() = runTest {
        insertarJornadaPendiente()
        repository().sincronizarJornadasPendientes()

        val json = com.google.gson.Gson().toJson(fakeApi.rpcRecibidas.single())
        assertTrue(json.startsWith("{\"p\":{\"jornada\":{\"id_registro\":\"$idRegistro\""))
        listOf("\"sectores_ids\":[\"SECTOR-01\",\"SECTOR-02\"]", "\"participantes\":", "\"herramientas\":",
            "\"basura\":", "\"encargado_pesaje\":\"Rosa Acopio\"", "\"qr_codigo\":\"INV-02-PINZA-03\"",
            "\"id_tipo_basura\":\"COLILLAS\"", "\"estado_final\":\"Dañado\"",
            "\"brigada\":\"Brigada B\"", "\"fecha_jornada\":\"2026-11-16\"", "\"id_playa\":\"PLAYA-01\"",
            "\"sin_herramientas\":false", "\"motivo_omision_sacos\":\"Sin batería\"").forEach {
            assertTrue("Falta $it en $json", json.contains(it))
        }
    }

    @Test
    fun `Falla una foto - la jornada sigue PENDIENTE_SYNC y no se llama a la RPC`() = runTest {
        insertarJornadaPendiente()
        fakeApi.respuestaFoto = { nombre ->
            if (nombre.contains("_2_")) Response.error(500, "Storage timeout".toResponseBody("text/plain".toMediaType()))
            else Response.success(Unit)
        }

        val result = repository().sincronizarJornadasPendientes()

        assertEquals(1, result.failedCount)
        assertEquals(JornadaEntity.SYNC_PENDIENTE, estadoEnRoom())
        assertEquals(0, fakeApi.rpcRecibidas.size)
    }

    @Test
    fun `Foto ya existente (409 o 400 Duplicate) cuenta como exito`() = runTest {
        insertarJornadaPendiente()
        fakeApi.respuestaFoto = { nombre ->
            if (nombre.contains("_1_")) {
                Response.error(409, "{}".toResponseBody("application/json".toMediaType()))
            } else {
                Response.error(
                    400,
                    """{"statusCode":"409","error":"Duplicate","message":"The resource already exists"}"""
                        .toResponseBody("application/json".toMediaType())
                )
            }
        }

        val result = repository().sincronizarJornadasPendientes()

        assertEquals(1, result.successCount)
        assertEquals(JornadaEntity.SYNC_SINCRONIZADO, estadoEnRoom())
        assertNotNull(fakeApi.rpcRecibidas.single().payload.jornada.urlFotoLimpio)
    }

    @Test
    fun `Servidor antiguo responde 400 de validacion - ERROR_SYNC con motivo y el reintento no resube fotos`() = runTest {
        insertarJornadaPendiente()
        fakeApi.respuestaRpc = {
            Response.error(
                400,
                """{"code":"22023","message":"Promedio por saco supera 25 kg (Ley 20.949)"}"""
                    .toResponseBody("application/json".toMediaType())
            )
        }

        val result = repository().sincronizarJornadasPendientes()

        assertEquals(1, result.failedCount)
        assertEquals(1, result.rechazadasCount)
        assertEquals(ErrorSync.Rechazada("Promedio por saco supera 25 kg (Ley 20.949)"), result.error)
        val enRoom = fakeJornadaDao.getJornadaById(idRegistro)!!
        assertEquals(JornadaEntity.SYNC_ERROR, enRoom.estadoSync)
        assertEquals("Promedio por saco supera 25 kg (Ley 20.949)", enRoom.motivoErrorSync)
        assertNotNull(enRoom.urlFotoInicio)

        // Una rechazada no vuelve a intentarse sola en la sincronización normal
        assertEquals(0, repository().sincronizarJornadasPendientes().failedCount)

        // "Reintentar" (servidor corregido): mismos datos, sin volver a subir fotos
        fakeApi.respuestaRpc = { Response.success(JornadaSyncRepository.RPC_INSERTADA) }
        val fotosAntes = fakeApi.fotosSubidas.size
        val reintento = repository().sincronizarJornada(idRegistro)
        assertEquals(1, reintento.successCount)
        assertEquals(fotosAntes, fakeApi.fotosSubidas.size)
        assertEquals(JornadaEntity.SYNC_SINCRONIZADO, estadoEnRoom())
        assertNull(fakeJornadaDao.getJornadaById(idRegistro)!!.motivoErrorSync)
    }

    @Test
    fun `RPC responde RECHAZADA - pasa a ERROR_SYNC con el motivo del servidor`() = runTest {
        insertarJornadaPendiente()
        fakeApi.respuestaRpc = { Response.success("RECHAZADA: Sector desconocido o de otra playa: SECTOR-07") }

        val result = repository().sincronizarJornadasPendientes()

        assertEquals(1, result.rechazadasCount)
        assertEquals("Jornada rechazada. Revisa Reportes.", result.message)
        val enRoom = fakeJornadaDao.getJornadaById(idRegistro)!!
        assertEquals(JornadaEntity.SYNC_ERROR, enRoom.estadoSync)
        assertEquals("Sector desconocido o de otra playa: SECTOR-07", enRoom.motivoErrorSync)
    }

    @Test
    fun `Servidor sin preparar (PGRST205 en catalogos) - mantencion, no llama a la RPC y sigue PENDIENTE`() = runTest {
        insertarJornadaPendiente()
        fakeApi.errorCatalogo = 404 to """{"code":"PGRST205","message":"Could not find the table"}"""

        assertEquals(EstadoServidor.NO_PREPARADO, repository().sincronizarCatalogosMaestros())
        val result = repository().sincronizarJornadasPendientes()

        assertEquals(ErrorSync.ServidorNoPreparado, result.error)
        assertEquals("El servidor está en mantención. Avisa al administrador.", result.message)
        assertEquals(0, fakeApi.rpcRecibidas.size)
        assertEquals(JornadaEntity.SYNC_PENDIENTE, estadoEnRoom())
    }

    @Test
    fun `RPC sin preparar (PGRST202) - ServidorNoPreparado y sigue PENDIENTE`() = runTest {
        insertarJornadaPendiente()
        fakeApi.respuestaRpc = {
            Response.error(404, """{"code":"PGRST202","message":"Could not find the function"}""".toResponseBody("application/json".toMediaType()))
        }

        val result = repository().sincronizarJornadasPendientes()

        assertEquals(ErrorSync.ServidorNoPreparado, result.error)
        assertEquals(JornadaEntity.SYNC_PENDIENTE, estadoEnRoom())
    }

    @Test
    fun `401 - NoAutorizado y sigue PENDIENTE`() = runTest {
        insertarJornadaPendiente()
        fakeApi.respuestaRpc = {
            Response.error(401, """{"message":"Invalid API key"}""".toResponseBody("application/json".toMediaType()))
        }

        val result = repository().sincronizarJornadasPendientes()

        assertEquals(ErrorSync.NoAutorizado, result.error)
        assertEquals(JornadaEntity.SYNC_PENDIENTE, estadoEnRoom())
    }

    @Test
    fun `Sin internet a mitad del lote - SinConexion, se detiene y ambas siguen PENDIENTE`() = runTest {
        insertarJornadaPendiente()
        insertarJornadaPendiente("REG-20261116-XYZ789")
        fakeApi.excepcionRpc = java.net.UnknownHostException("sin red")

        val result = repository().sincronizarJornadasPendientes()

        assertEquals(ErrorSync.SinConexion, result.error)
        assertEquals(2, result.failedCount)
        assertEquals("Se detiene tras el primer corte", 1, fakeApi.rpcRecibidas.size)
        assertEquals(JornadaEntity.SYNC_PENDIENTE, estadoEnRoom())
        assertEquals(JornadaEntity.SYNC_PENDIENTE, fakeJornadaDao.getJornadaById("REG-20261116-XYZ789")!!.estadoSync)
    }

    @Test
    fun `Error 500 - ErrorServidor transitorio y sigue PENDIENTE`() = runTest {
        insertarJornadaPendiente()
        fakeApi.respuestaRpc = { Response.error(503, "".toResponseBody("text/plain".toMediaType())) }

        val result = repository().sincronizarJornadasPendientes()

        assertEquals(ErrorSync.ErrorServidor, result.error)
        assertEquals(JornadaEntity.SYNC_PENDIENTE, estadoEnRoom())
    }

    @Test
    fun `Foto borrada del telefono - ERROR_SYNC con motivo y sin llamar a la RPC`() = runTest {
        insertarJornadaPendiente()
        val actual = fakeJornadaDao.getJornadaById(idRegistro)!!
        fakeJornadaDao.updateJornada(actual.copy(pathFotoLimpio = "/no/existe/limpio.jpg"))

        val result = repository().sincronizarJornadasPendientes()

        assertEquals(ErrorSync.FotoFaltante, result.error)
        assertEquals(0, fakeApi.rpcRecibidas.size)
        val enRoom = fakeJornadaDao.getJornadaById(idRegistro)!!
        assertEquals(JornadaEntity.SYNC_ERROR, enRoom.estadoSync)
        assertEquals("Falta una foto en el teléfono", enRoom.motivoErrorSync)
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun `Una cancelacion no se cuenta como error - se propaga`() = runTest {
        insertarJornadaPendiente()
        fakeApi.excepcionRpc = kotlinx.coroutines.CancellationException("cancelada")
        repository().sincronizarJornadasPendientes()
    }

    @Test
    fun `Reintentar ignora jornadas ya sincronizadas`() = runTest {
        insertarJornadaPendiente()
        fakeJornadaDao.updateEstadoSync(idRegistro, JornadaEntity.SYNC_SINCRONIZADO)

        val result = repository().sincronizarJornada(idRegistro)

        assertEquals(0, result.successCount + result.failedCount)
        assertEquals(0, fakeApi.rpcRecibidas.size)
    }

    @Test
    fun `RPC responde YA_EXISTIA - se marca SINCRONIZADO`() = runTest {
        insertarJornadaPendiente()
        fakeApi.respuestaRpc = { Response.success(JornadaSyncRepository.RPC_YA_EXISTIA) }

        val result = repository().sincronizarJornadasPendientes()

        assertEquals(1, result.successCount)
        assertEquals(JornadaEntity.SYNC_SINCRONIZADO, estadoEnRoom())
    }

    @Test
    fun `RPC con respuesta desconocida - no se marca SINCRONIZADO`() = runTest {
        insertarJornadaPendiente()
        fakeApi.respuestaRpc = { Response.success("OTRA_COSA") }

        repository().sincronizarJornadasPendientes()

        assertEquals(JornadaEntity.SYNC_PENDIENTE, estadoEnRoom())
    }

    @Test
    fun `BR-10 Catalogo remoto no pisa el estado de herramientas de jornadas no sincronizadas`() = runTest {
        insertarJornadaPendiente()
        // Estado local tras P7 de la jornada pendiente
        fakeCatalogoDao.updateEstadoHerramientaCatalogo("INV-02-PINZA-03", HerramientaCatalogoEntity.ESTADO_DANADO)
        // El servidor aún dice Operativo para la pinza 3, y Extraviado para la pinza 1 (no usada localmente)
        fakeApi.herramientasRemotas = CatalogoSeedData.herramientasQr.map {
            when (it.qrCodigo) {
                "INV-02-PINZA-01" -> it.copy(estado = HerramientaCatalogoEntity.ESTADO_EXTRAVIADO)
                else -> it.copy(estado = HerramientaCatalogoEntity.ESTADO_OPERATIVO)
            }
        }

        assertEquals(EstadoServidor.OK, repository().sincronizarCatalogosMaestros())

        assertEquals(HerramientaCatalogoEntity.ESTADO_DANADO, fakeCatalogoDao.getHerramientaByQr("INV-02-PINZA-03")!!.estado)
        assertEquals(HerramientaCatalogoEntity.ESTADO_EXTRAVIADO, fakeCatalogoDao.getHerramientaByQr("INV-02-PINZA-01")!!.estado)
    }

    @Test
    fun `ISSUE-43 Sectores borrados en Supabase desaparecen del telefono`() = runTest {
        // Copia local con un sector que el servidor ya no tiene (SECTOR-TEST de la prueba de ISSUE-42)
        val sectorPrueba = SectorCatalogoEntity(
            idSector = "SECTOR-TEST",
            idPlaya = CatalogoSeedData.ID_PLAYA_GRANDE,
            nombreSector = "Sector PRUEBA",
            areaM2 = 100
        )
        fakeCatalogoDao.upsertSectores(listOf(sectorPrueba))
        assertTrue(fakeCatalogoDao.getSectoresList().any { it.idSector == "SECTOR-TEST" })

        fakeApi.sectoresRemotos = CatalogoSeedData.sectoresPlayaGrande
        assertEquals(EstadoServidor.OK, repository().sincronizarCatalogosMaestros())

        assertEquals(
            CatalogoSeedData.sectoresPlayaGrande.map { it.idSector },
            fakeCatalogoDao.getSectoresList().map { it.idSector }
        )
    }

    @Test
    fun `ISSUE-43 Un catalogo de sectores vacio no borra los sectores locales`() = runTest {
        fakeApi.sectoresRemotos = emptyList()
        assertEquals(EstadoServidor.OK, repository().sincronizarCatalogosMaestros())
        assertEquals(CatalogoSeedData.sectoresPlayaGrande.size, fakeCatalogoDao.getSectoresList().size)
    }

    private class FakeSupabaseApi : SupabaseApi {
        val fotosSubidas = mutableListOf<String>()
        val rpcRecibidas = mutableListOf<SincronizarJornadaRequest>()
        var herramientasRemotas: List<HerramientaCatalogoEntity> = emptyList()
        var sectoresRemotos: List<SectorCatalogoEntity> = emptyList()
        var respuestaFoto: (String) -> Response<Unit> = { Response.success(Unit) }
        var respuestaRpc: (SincronizarJornadaRequest) -> Response<String> =
            { Response.success(JornadaSyncRepository.RPC_INSERTADA) }
        var excepcionRpc: Exception? = null
        /** (código HTTP, cuerpo) que responden los 4 catálogos; null = OK. */
        var errorCatalogo: Pair<Int, String>? = null

        private fun <T> catalogo(ok: T): Response<T> = errorCatalogo
            ?.let { (code, cuerpo) -> Response.error(code, cuerpo.toResponseBody("application/json".toMediaType())) }
            ?: Response.success(ok)

        override suspend fun getHerramientasCatalogo(select: String): Response<List<HerramientaCatalogoEntity>> =
            catalogo(herramientasRemotas)

        override suspend fun getTiposBasuraCatalogo(select: String): Response<List<TipoBasuraCatalogoEntity>> =
            catalogo(emptyList())

        override suspend fun getPlayasCatalogo(select: String): Response<List<PlayaCatalogoEntity>> =
            catalogo(emptyList())

        override suspend fun getSectoresCatalogo(select: String): Response<List<SectorCatalogoEntity>> =
            catalogo(sectoresRemotos)

        override suspend fun uploadEvidencePhoto(
            bucket: String,
            fileName: String,
            contentType: String,
            imageBytes: RequestBody
        ): Response<Unit> {
            fotosSubidas += fileName
            return respuestaFoto(fileName)
        }

        override suspend fun sincronizarJornada(request: SincronizarJornadaRequest): Response<String> {
            rpcRecibidas += request
            excepcionRpc?.let { throw it }
            return respuestaRpc(request)
        }
    }
}
