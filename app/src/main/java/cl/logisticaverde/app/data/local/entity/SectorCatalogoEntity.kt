package cl.logisticaverde.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "sector_catalogo",
    // Sin FK en Room: los catálogos llegan de Supabase en descargas independientes
    indices = [Index(value = ["id_playa"])]
)
data class SectorCatalogoEntity(
    @PrimaryKey
    @ColumnInfo(name = "id_sector")
    @SerializedName("id_sector")
    val idSector: String,

    @ColumnInfo(name = "id_playa")
    @SerializedName("id_playa")
    val idPlaya: String,

    @ColumnInfo(name = "nombre_sector")
    @SerializedName("nombre_sector")
    val nombreSector: String,

    @ColumnInfo(name = "area_m2")
    @SerializedName("area_m2")
    val areaM2: Int,
    /**
     * ISSUE-43: en vez de borrar en Supabase, el administrador marca `is_activo = false`.
     * La app lo oculta al ingresar datos de jornadas nuevas; las jornadas ya hechas lo conservan.
     * Nullable a propósito: si el servidor aún no tiene la columna, Gson deja null y se trata como activo.
     */
    @ColumnInfo(name = "is_activo")
    @SerializedName("is_activo")
    val activo: Boolean? = true
) {
    /** Activo salvo que el servidor diga explícitamente lo contrario (null = activo). */
    val estaActivo: Boolean get() = activo != false
}
