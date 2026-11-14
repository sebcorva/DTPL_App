# Glosario del Proyecto: "Deja tu Playa Limpia" / Logística Verde (`GLOSSARY.md`)

> Documento **derivado** de `PRD.md` v4.0. Estandariza el vocabulario de negocio, ambiental, normativo y técnico. Ante cualquier diferencia prevalece el PRD.

---

## 1. Dominio Logístico y Ambiental

* **Logística Verde:** iniciativa sin fines de lucro que convierte el trabajo de las brigadas de limpieza costera en datos verificables. **"Deja tu Playa Limpia"** es el nombre de la app.
* **Micro-logística inversa:** recolección manual, clasificación en origen, acopio, pesaje y derivación de residuos en terrenos donde no entra maquinaria (la arena de las playas, como Playa Grande de Cartagena), devolviendo los reciclables a la cadena productiva.
* **Jornada:** una salida de limpieza de una brigada, registrada de punta a punta en las 10 pantallas. Se identifica con `id_registro`.
* **Transecto:** área estandarizada de muestreo. En el piloto: **8.000 m²** (200 m × 40 m), dividida en 4 sectores.
* **Playa:** lugar de la jornada, elegido en la Pantalla 2 desde `playa_catalogo`. Cada playa tiene sus sectores con m² definidos de antemano.
* **Sector:** subdivisión de una playa (`SECTOR-01`..`04` en Playa Grande, 2.000 m² cada uno) que el líder marca en la Pantalla 3. Varias brigadas pueden elegir el mismo sector.
* **Brigada de limpieza:** 1 líder de brigada (lleva el teléfono) + al menos 1 voluntario, máximo 60 integrantes. Cada playa tiene sus brigadas (catálogo `brigada_catalogo`; Playa Grande: A, B, C y D). *(Antes "cuadrilla".)*
* **Fecha de la jornada (`fecha_jornada`):** día en que se realiza la jornada (hora de Chile), registrado al crearla. Distinta de `fecha_sincronizacion`.
* **Participante:** cualquier integrante de una jornada (líder o voluntario). Se registra **solo su nombre**, escrito a mano en la Pantalla 2, y existe solo dentro de esa jornada. No hay padrón de usuarios.
* **Encargado de pesaje:** persona que opera la única pesa digital en el punto de acopio y dicta los kilos al líder (Pantalla 8). Puede ser un participante u otra persona. *(Reemplaza a la antigua "Cuadrilla E".)*
* **Punto de acopio:** lugar donde se reúnen y pesan los sacos.
* **Basura normal (no valorizable / rechazo):** residuos que no se reciclan localmente (colillas, plumavit fragmentado, plásticos degradados, mascarillas). Destino típico: camión municipal (DIMAO) → relleno sanitario.
* **Basura reciclable (valorizable):** material limpio separado en sacos exclusivos (PET, latas, vidrio intacto). Destino: Punto Limpio municipal o reciclador de base.
* **Guía de despacho:** documento del retiro municipal; su número se registra opcionalmente cuando la basura normal se entrega al camión DIMAO.
* **Kit de micro-logística:** inventario trazado por QR: 5 pinzas ("Pinza 1".."Pinza 5") y 4 rastrillos ("Rastrillo 1".."Rastrillo 4"). El código del QR (`INV-02-PINZA-01`…) es interno y no se muestra. La pesa digital, los sacos y los guantes no forman parte del inventario de la brigada. Una brigada puede declarar que **no lleva herramientas**.
* **Motivo de omisión:** texto obligatorio que explica por qué no se tomó una foto (P3 o P6).
* **Estado de herramienta:** `Operativo`, `Dañado` (se puede usar) o `Extraviado` (no se puede usar).
* **Resumen de impacto:** tarjeta vertical 1080×1920 generada en la Pantalla 10 para compartir en Instagram, con las cifras de la jornada y las fotos de antes y después.

---

## 2. Catálogo de Residuos (`tipo_basura_catalogo`)

La `categoria_origen` no se pide al usuario: viene definida por el catálogo.

| `id_tipo_basura` | Nombre visible | `categoria_origen` |
| :--- | :--- | :--- |
| `COLILLAS` | Colillas de cigarro | Recreativa |
| `TAPAS_PLASTICAS` | Tapas plásticas | Recreativa |
| `BOTELLAS_PET` | Botellas plásticas (PET) | Recreativa |
| `LATAS_ALUMINIO` | Latas de aluminio | Recreativa |
| `ENVOLTORIOS` | Envoltorios / bolsas plásticas | Recreativa |
| `VIDRIO` | Vidrio / botellas | Recreativa |
| `PLUMAVIT` | Plumavit (EPS) | Marítima |
| `CABOS_REDES` | Cabos, cuerdas y redes | Marítima |
| `BOYAS_PESCA` | Boyas / artículos de pesca | Marítima |
| `MASCARILLAS_HIGIENE` | Toallitas húmedas / higiene | Higiene |

---

## 3. Estándares, Instituciones y Normativa

* **Protocolo Ocean Conservancy (*International Coastal Cleanup*):** metodología de ciencia ciudadana para contar y tipificar residuos ítem por ítem; base de la botonera de la Pantalla 5.
* **Criterios DIRECTEMAR (Armada de Chile):** clasificación por actividad de origen: **Recreativa** (visitantes), **Marítima** (pesca, embarcaciones, acuicultura) e **Higiene** (residuos sanitarios).
* **ODS 14.1.1:** indicador de la ONU sobre desechos plásticos marinos; aquí se reporta como densidad en kg/m².
* **DIMAO:** Dirección de Medio Ambiente, Aseo y Ornato de la Municipalidad de Cartagena; recibe informes y retira la basura normal.
* **PMRU / Punto Limpio / recicladores de base:** destinatarios del material reciclable.
* **Fondo para el Reciclaje (MMA):** fondo concursable del Ministerio del Medio Ambiente (Ley REP N.° 20.920) al que se postula con la evidencia generada.
* **Ley N.° 20.949:** limita la carga manual a **25 kg** (20 kg para mujeres y menores). La app **bloquea** el pesaje si el promedio por saco supera **25 kg** (decisión v3).
* **Ley N.° 21.719 (Protección de Datos Personales):** reemplaza a la Ley 19.628. La app no fotografía rostros, guarda solo nombres, los nombres no son legibles con la clave pública y la tarjeta de Instagram muestra solo la cantidad de voluntarios.

---

## 4. KPIs

> El tablero definitivo depende de la planilla que reformulará la clienta (PRD §8). Estas son las definiciones de referencia.

1. **Kilos totales:** Kg normal + Kg reciclable.
2. **Horas-voluntario:** `total_participantes × (hora_fin − hora_inicio)` en horas.
3. **Productividad (kg/h-voluntario):** Kilos totales / Horas-voluntario.
4. **Densidad diaria (kg/m²):** kilos del día y de la playa / área con los **sectores sin repetir**. *(La vista se eliminó en la v4; se recalculará con la nueva planilla.)* La densidad por jornada es solo referencial.
5. **% Valorización:** Kg reciclable / Kilos totales × 100 (0 % si los kilos totales son 0).

---

## 5. Términos Técnicos

* **`id_registro`:** identificador de la jornada (`REG-AAAAMMDD-XXXXXX`) generado en el teléfono. Garantiza la idempotencia: la RPC nunca inserta dos veces la misma jornada.
* **`pantalla_actual`:** última pantalla alcanzada (1..10); permite reanudar tras un cierre o apagón. Existe solo en Room. La app ya no muestra el número (v4).
* **Topes de la jornada:** participantes ≤ 60, conteo por residuo ≤ 5.000, sacos ≤ 100, kg ≤ 2.500, km ≤ 30, duración ≤ 12 h (`TopesJornada.kt` y la RPC).
* **`estado_sync`:** `EN_CURSO` (flujo abierto) → `PENDIENTE_SYNC` (guardada en P9) → `SINCRONIZADO` (confirmada por la RPC), o `ERROR_SYNC` (rechazada por la RPC o falta una foto; motivo en `motivo_error_sync`). Existe solo en Room.
* **Jornada rechazada (`ERROR_SYNC`):** no se edita nunca; solo "Reintentar" en Reportes, que reenvía los mismos datos. En Supabase queda una copia en `jornada_rechazada` para el administrador.
* **Servidor en mantención:** Supabase responde PGRST202/205 (SQL sin ejecutar o caché sin recargar); la app no intenta sincronizar.
* **`participante_jornada`:** tabla hija con `nombre` y `rol` (`LIDER` | `VOLUNTARIO`) de cada integrante.
* **`total_participantes`:** 1 líder + N voluntarios; base de las horas-voluntario.
* **`sectores_ids_csv` / `sectores_csv`:** IDs (para reanudar y deduplicar) y nombres (para mostrar) de los sectores elegidos.
* **`km_recorridos`:** distancia acumulada por el *Foreground Service* de GPS entre *Comenzar* y *Finalizar Limpieza*, con filtro anti-rebote.
* **Foreground Service:** servicio Android con notificación visible que mantiene el GPS activo con la pantalla apagada.
* **Botón Sincronizar tricolor:** `GRIS_SIN_DATOS` (0 pendientes) · `AMARILLO_SIN_INTERNET` (pendientes, sin red) · `VERDE_LISTO_PARA_SYNC` (pendientes, con red) · `SINCRONIZANDO`.
* **RLS (Row Level Security):** mecanismo de Postgres que restringe qué filas o tablas puede leer o escribir cada rol.
* **RPC `sincronizar_jornada`:** función Postgres `SECURITY DEFINER` que inserta una jornada completa en una sola transacción; única vía de escritura para la app.
* **Clave pública (`anon` / `sb_publishable_…`):** va dentro del APK; solo lee catálogos, ejecuta la RPC y sube fotos nuevas.
* **Clave secreta (`sb_secret_…` / `service_role`):** acceso total; solo en las *Script Properties* de Google Sheets y en la administración.
* **Bucket `evidencias-fotos`:** almacenamiento de las fotos; lectura pública, solo inserción para la app.
* **Keep-alive:** consulta diaria desde Apps Script que evita que el proyecto gratuito de Supabase se pause por inactividad.
* **`META_APP_ID`:** identificador opcional de una app de Meta que habilita abrir directamente el editor de Historias de Instagram.
