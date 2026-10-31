package cl.logisticaverde.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "herramienta_jornada",
    foreignKeys = [
        ForeignKey(
            entity = JornadaEntity::class,
            parentColumns = ["id_registro"],
            childColumns = ["id_registro"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = HerramientaCatalogoEntity::class,
            parentColumns = ["qr_codigo"],
            childColumns = ["qr_codigo"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["id_registro", "qr_codigo"], unique = true),
        Index(value = ["qr_codigo"])
    ]
)
data class HerramientaJornadaEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "id_registro")
    @SerializedName("id_registro")
    val idRegistro: String,

    @ColumnInfo(name = "qr_codigo")
    @SerializedName("qr_codigo")
    val qrCodigo: String,

    @ColumnInfo(name = "nombre_herramienta")
    @SerializedName("nombre_herramienta")
    val nombreHerramienta: String,

    @ColumnInfo(name = "estado_final")
    @SerializedName("estado_final")
    val estadoFinal: String = HerramientaCatalogoEntity.ESTADO_OPERATIVO
)
