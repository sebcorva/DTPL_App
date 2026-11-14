package cl.logisticaverde.app.data.local.db

import cl.logisticaverde.app.data.local.entity.BrigadaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.HerramientaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.PlayaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.SectorCatalogoEntity
import cl.logisticaverde.app.data.local.entity.TipoBasuraCatalogoEntity

/**
 * Datos semilla iniciales (Seed) para operar 100% offline desde la primera instalación.
 * Estos catálogos se sincronizan y actualizan desde Supabase PostgreSQL cuando hay internet.
 */
object CatalogoSeedData {

    const val ID_PLAYA_GRANDE = "PLAYA-01"

    val playas = listOf(
        PlayaCatalogoEntity(idPlaya = ID_PLAYA_GRANDE, nombrePlaya = "Playa Grande de Cartagena")
    )

    /** ISSUE-62: brigadas de cada playa (mismo seed que supabase_schema.sql). */
    val brigadas = listOf(
        BrigadaCatalogoEntity(idBrigada = "BRIGADA-01", idPlaya = ID_PLAYA_GRANDE, nombreBrigada = "Brigada A"),
        BrigadaCatalogoEntity(idBrigada = "BRIGADA-02", idPlaya = ID_PLAYA_GRANDE, nombreBrigada = "Brigada B"),
        BrigadaCatalogoEntity(idBrigada = "BRIGADA-03", idPlaya = ID_PLAYA_GRANDE, nombreBrigada = "Brigada C"),
        BrigadaCatalogoEntity(idBrigada = "BRIGADA-04", idPlaya = ID_PLAYA_GRANDE, nombreBrigada = "Brigada D")
    )

    val sectoresPlayaGrande = listOf(
        SectorCatalogoEntity(
            idSector = "SECTOR-01",
            idPlaya = ID_PLAYA_GRANDE,
            nombreSector = "Sector 1 - Norte (2.000 m²)",
            areaM2 = 2000
        ),
        SectorCatalogoEntity(
            idSector = "SECTOR-02",
            idPlaya = ID_PLAYA_GRANDE,
            nombreSector = "Sector 2 - Centro Norte (2.000 m²)",
            areaM2 = 2000
        ),
        SectorCatalogoEntity(
            idSector = "SECTOR-03",
            idPlaya = ID_PLAYA_GRANDE,
            nombreSector = "Sector 3 - Centro Sur (2.000 m²)",
            areaM2 = 2000
        ),
        SectorCatalogoEntity(
            idSector = "SECTOR-04",
            idPlaya = ID_PLAYA_GRANDE,
            nombreSector = "Sector 4 - Sur (2.000 m²)",
            areaM2 = 2000
        )
    )

    // El QR conserva el código interno; la app muestra solo nombre y número (ISSUE-34).
    // La pesa digital no es herramienta de brigada: no está en el catálogo.
    val herramientasQr = listOf(
        HerramientaCatalogoEntity(
            qrCodigo = "INV-02-PINZA-01",
            nombreHerramienta = "Pinza 1",
            estado = HerramientaCatalogoEntity.ESTADO_OPERATIVO
        ),
        HerramientaCatalogoEntity(
            qrCodigo = "INV-02-PINZA-02",
            nombreHerramienta = "Pinza 2",
            estado = HerramientaCatalogoEntity.ESTADO_OPERATIVO
        ),
        HerramientaCatalogoEntity(
            qrCodigo = "INV-02-PINZA-03",
            nombreHerramienta = "Pinza 3",
            estado = HerramientaCatalogoEntity.ESTADO_OPERATIVO
        ),
        HerramientaCatalogoEntity(
            qrCodigo = "INV-02-PINZA-04",
            nombreHerramienta = "Pinza 4",
            estado = HerramientaCatalogoEntity.ESTADO_OPERATIVO
        ),
        HerramientaCatalogoEntity(
            qrCodigo = "INV-02-PINZA-05",
            nombreHerramienta = "Pinza 5",
            estado = HerramientaCatalogoEntity.ESTADO_OPERATIVO
        ),
        HerramientaCatalogoEntity(
            qrCodigo = "INV-03-RASTRILLO-01",
            nombreHerramienta = "Rastrillo 1",
            estado = HerramientaCatalogoEntity.ESTADO_OPERATIVO
        ),
        HerramientaCatalogoEntity(
            qrCodigo = "INV-03-RASTRILLO-02",
            nombreHerramienta = "Rastrillo 2",
            estado = HerramientaCatalogoEntity.ESTADO_OPERATIVO
        ),
        HerramientaCatalogoEntity(
            qrCodigo = "INV-03-RASTRILLO-03",
            nombreHerramienta = "Rastrillo 3",
            estado = HerramientaCatalogoEntity.ESTADO_OPERATIVO
        ),
        HerramientaCatalogoEntity(
            qrCodigo = "INV-03-RASTRILLO-04",
            nombreHerramienta = "Rastrillo 4",
            estado = HerramientaCatalogoEntity.ESTADO_OPERATIVO
        )
    )

    val tiposBasura = listOf(
        TipoBasuraCatalogoEntity(
            idTipoBasura = "COLILLAS",
            nombreVisible = "Colillas de cigarro",
            categoriaOrigen = TipoBasuraCatalogoEntity.ORIGEN_RECREATIVA
        ),
        TipoBasuraCatalogoEntity(
            idTipoBasura = "TAPAS_PLASTICAS",
            nombreVisible = "Tapas plásticas",
            categoriaOrigen = TipoBasuraCatalogoEntity.ORIGEN_RECREATIVA
        ),
        TipoBasuraCatalogoEntity(
            idTipoBasura = "BOTELLAS_PET",
            nombreVisible = "Botellas plásticas (PET)",
            categoriaOrigen = TipoBasuraCatalogoEntity.ORIGEN_RECREATIVA
        ),
        TipoBasuraCatalogoEntity(
            idTipoBasura = "LATAS_ALUMINIO",
            nombreVisible = "Latas de aluminio",
            categoriaOrigen = TipoBasuraCatalogoEntity.ORIGEN_RECREATIVA
        ),
        TipoBasuraCatalogoEntity(
            idTipoBasura = "ENVOLTORIOS",
            nombreVisible = "Envoltorios / bolsas plásticas",
            categoriaOrigen = TipoBasuraCatalogoEntity.ORIGEN_RECREATIVA
        ),
        TipoBasuraCatalogoEntity(
            idTipoBasura = "VIDRIO",
            nombreVisible = "Vidrio / botellas",
            categoriaOrigen = TipoBasuraCatalogoEntity.ORIGEN_RECREATIVA
        ),
        TipoBasuraCatalogoEntity(
            idTipoBasura = "PLUMAVIT",
            nombreVisible = "Plumavit (EPS)",
            categoriaOrigen = TipoBasuraCatalogoEntity.ORIGEN_MARITIMA
        ),
        TipoBasuraCatalogoEntity(
            idTipoBasura = "CABOS_REDES",
            nombreVisible = "Cabos, cuerdas y redes",
            categoriaOrigen = TipoBasuraCatalogoEntity.ORIGEN_MARITIMA
        ),
        TipoBasuraCatalogoEntity(
            idTipoBasura = "BOYAS_PESCA",
            nombreVisible = "Boyas / artículos de pesca",
            categoriaOrigen = TipoBasuraCatalogoEntity.ORIGEN_MARITIMA
        ),
        TipoBasuraCatalogoEntity(
            idTipoBasura = "MASCARILLAS_HIGIENE",
            nombreVisible = "Toallitas húmedas / higiene",
            categoriaOrigen = TipoBasuraCatalogoEntity.ORIGEN_HIGIENE
        )
    )
}
