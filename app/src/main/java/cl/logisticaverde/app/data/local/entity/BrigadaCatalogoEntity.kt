package cl.logisticaverde.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Catálogo de brigadas (ISSUE-62): cada playa tiene sus brigadas. Lo administra el desarrollador
 * en Supabase; en la P2 se elige primero la playa y luego una de sus brigadas.
 */
@Entity(
    tableName = "brigada_catalogo",
    // Sin FK en Room: los catálogos llegan de Supabase en descargas independientes
    indices = [Index(value = ["id_playa"])]
)
data class BrigadaCatalogoEntity(
    @PrimaryKey
    @ColumnInfo(name = "id_brigada")
    @SerializedName("id_brigada")
    val idBrigada: String,

    @ColumnInfo(name = "id_playa")
    @SerializedName("id_playa")
    val idPlaya: String,

    @ColumnInfo(name = "nombre_brigada")
    @SerializedName("nombre_brigada")
    val nombreBrigada: String,
    /**
     * ISSUE-43 / ISSUE-63: `is_activo = false` la oculta en jornadas nuevas; los datos se quedan.
     * Nullable a propósito: si el servidor no envía la columna, Gson deja null y se trata como activa.
     */
    @ColumnInfo(name = "is_activo")
    @SerializedName("is_activo")
    val activo: Boolean? = true
) {
    /** Activa salvo que el servidor diga explícitamente lo contrario (null = activa). */
    val estaActiva: Boolean get() = activo != false
}
