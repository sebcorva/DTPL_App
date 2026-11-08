package cl.logisticaverde.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/** Catálogo de playas (ISSUE-32): lo administra el desarrollador en Supabase; cada playa tiene sus sectores. */
@Entity(tableName = "playa_catalogo")
data class PlayaCatalogoEntity(
    @PrimaryKey
    @ColumnInfo(name = "id_playa")
    @SerializedName("id_playa")
    val idPlaya: String,

    @ColumnInfo(name = "nombre_playa")
    @SerializedName("nombre_playa")
    val nombrePlaya: String,
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
