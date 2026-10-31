package cl.logisticaverde.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.util.Locale

/**
 * Integrante de una jornada (PRD §4). Los participantes no se conocen de antemano:
 * el líder escribe sus nombres en la Pantalla 2 y solo existen dentro de esa jornada.
 */
@Entity(
    tableName = "participante_jornada",
    foreignKeys = [
        ForeignKey(
            entity = JornadaEntity::class,
            parentColumns = ["id_registro"],
            childColumns = ["id_registro"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["id_registro", "nombre_normalizado"], unique = true)
    ]
)
data class ParticipanteJornadaEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "id_registro")
    @SerializedName("id_registro")
    val idRegistro: String,

    @ColumnInfo(name = "nombre")
    @SerializedName("nombre")
    val nombre: String,

    // Clave de unicidad: evita "Ana" y " ana " duplicados en la misma jornada (BR-02)
    @ColumnInfo(name = "nombre_normalizado")
    val nombreNormalizado: String = normalizarNombre(nombre),

    @ColumnInfo(name = "rol")
    @SerializedName("rol")
    val rol: String
) {
    companion object {
        const val ROL_LIDER = "LIDER"
        const val ROL_VOLUNTARIO = "VOLUNTARIO"

        /** Recorta, colapsa espacios internos y pasa a minúsculas. */
        fun normalizarNombre(nombre: String): String =
            nombre.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    }
}
