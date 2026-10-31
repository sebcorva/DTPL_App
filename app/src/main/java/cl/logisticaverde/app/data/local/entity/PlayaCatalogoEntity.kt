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
    val nombrePlaya: String
)
