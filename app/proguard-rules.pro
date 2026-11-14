# Reglas de R8 para el APK release (solo aplican si isMinifyEnabled = true en app/build.gradle.kts).
#
# Gson arma y lee el JSON por reflexión con los nombres de @SerializedName. Si R8 renombra o
# elimina esos campos, la sincronización envía JSON vacío o los catálogos llegan en null.
# Por eso se conservan, con sus campos, las clases que viajan por la red:
#   - data.remote: payload de la RPC sincronizar_jornada (JornadaSyncPayload, *RpcDto).
#   - data.local.entity: los catálogos se descargan de Supabase directo a las entidades de Room.
-keep class cl.logisticaverde.app.data.remote.** { *; }
-keep class cl.logisticaverde.app.data.local.entity.** { *; }

# Mantiene las anotaciones y los tipos genéricos (List<...>) que Gson y Retrofit leen en ejecución.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
