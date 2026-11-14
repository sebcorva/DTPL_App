-- ============================================================================
-- ESQUEMA SQL MAESTRO PARA SUPABASE POSTGRESQL: "DEJA TU PLAYA LIMPIA" (v5)
-- Iniciativa Logística Verde
-- Fuente: PRD.md §4 y §5 · ISSUE-20, ISSUE-31 e ISSUE-62
--
-- Instalación desde cero: ejecutar primero `supabase_reset.sql` (borra todo) y
-- luego este archivo. Después, este archivo se puede volver a ejecutar sin perder
-- datos (CREATE IF NOT EXISTS / CREATE OR REPLACE).
--
-- Novedades v5 (ISSUE-62, 13-11-2026): todo dato que se servirá en la planilla,
-- una web o informes vive en tablas con PK y FK; lo derivado se entrega en vistas.
--   * brigada_catalogo: cada playa tiene sus brigadas (antes, un CHECK fijo).
--   * jornada_sector: sectores de cada jornada (antes, un arreglo TEXT[] sin FK).
--   * foto_jornada: las 3 fotos, con la RUTA dentro del bucket (no la URL) o el motivo.
--   * jornada sin copias derivadas (nombre de playa, líder, voluntarios, sectores,
--     área, total de participantes): las entrega vista_jornada.
--   * kg y km en NUMERIC (sin errores de redondeo); horas en TIMESTAMPTZ; la
--     coordenada inicial es NULL cuando no hubo lectura de GPS (antes 0, 0).
--   * Índices en las FK y en la consulta del inventario de herramientas.
--
-- Modelo de seguridad (PRD §5.3):
--   * anon (clave pública del APK): SELECT en los 5 catálogos, EXECUTE en
--     sincronizar_jornada() y solo INSERT de fotos nuevas en el bucket.
--   * service_role (clave secreta, Google Sheets / admin): acceso total.
--   * Las tablas de jornada y vista_jornada NO son accesibles con la clave pública.
--
-- Topes (deben coincidir con TopesJornada.kt y ReglasTexto.kt de la app):
--   participantes ≤ 60 · cantidad por residuo ≤ 5.000 · sacos ≤ 100
--   kg totales ≤ 2.500 · km ≤ 30 · duración ≤ 12 h · motivo de omisión ≤ 120 caracteres
--   nombres ≤ 80 (letras, espacios y apóstrofo) · motivo (letras, números y espacios)
--   N° de guía 1 a 12 (letras, números y guion)
-- ============================================================================


-- ============================================================================
-- 1. CATÁLOGOS MAESTROS (lectura pública)
-- ============================================================================
-- ISSUE-43 / ISSUE-63: los catálogos no se borran; se desactivan con is_activo = false.
-- is_activo solo decide qué ofrece la app en jornadas nuevas; los datos se quedan en la base.
-- La RPC sigue aceptando jornadas que usen un registro inactivo (solo exige que exista),
-- así las pendientes de antes no se rechazan. Ejemplo (SQL Editor, como admin):
--   UPDATE public.sector_catalogo SET is_activo = false WHERE id_sector = 'SECTOR-04';
CREATE TABLE IF NOT EXISTS public.playa_catalogo (
    id_playa TEXT PRIMARY KEY,
    nombre_playa TEXT NOT NULL UNIQUE,
    is_activo BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS public.sector_catalogo (
    id_sector TEXT PRIMARY KEY,
    id_playa TEXT NOT NULL REFERENCES public.playa_catalogo(id_playa),
    nombre_sector TEXT NOT NULL,
    area_m2 INTEGER NOT NULL CHECK (area_m2 > 0),
    is_activo BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX IF NOT EXISTS sector_catalogo_id_playa ON public.sector_catalogo (id_playa);

-- ISSUE-62: cada playa tiene sus brigadas. El índice único (id_playa, nombre_brigada)
-- también sirve para buscar por playa.
CREATE TABLE IF NOT EXISTS public.brigada_catalogo (
    id_brigada TEXT PRIMARY KEY,
    id_playa TEXT NOT NULL REFERENCES public.playa_catalogo(id_playa),
    nombre_brigada TEXT NOT NULL,
    is_activo BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (id_playa, nombre_brigada)
);

-- La app muestra solo nombre_herramienta ("Pinza 1"); qr_codigo es el código interno del QR
CREATE TABLE IF NOT EXISTS public.herramienta_catalogo (
    qr_codigo TEXT PRIMARY KEY,
    nombre_herramienta TEXT NOT NULL,
    estado TEXT NOT NULL DEFAULT 'Operativo' CHECK (estado IN ('Operativo', 'Dañado', 'Extraviado')),
    is_activo BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS public.tipo_basura_catalogo (
    id_tipo_basura TEXT PRIMARY KEY,
    nombre_visible TEXT NOT NULL,
    categoria_origen TEXT NOT NULL CHECK (categoria_origen IN ('Recreativa', 'Marítima', 'Higiene')),
    is_activo BOOLEAN NOT NULL DEFAULT true
);


-- ============================================================================
-- 2. TABLAS OPERATIVAS (solo se escriben mediante sincronizar_jornada)
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.jornada (
    id_registro TEXT PRIMARY KEY,
    -- Día en que se realizó la jornada (hora de Chile). Distinto de fecha_sincronizacion.
    fecha_jornada DATE NOT NULL,
    id_playa TEXT NOT NULL REFERENCES public.playa_catalogo(id_playa),
    id_brigada TEXT NOT NULL REFERENCES public.brigada_catalogo(id_brigada),
    sin_herramientas BOOLEAN NOT NULL DEFAULT FALSE,
    hora_inicio TIMESTAMPTZ NOT NULL,
    hora_fin TIMESTAMPTZ NOT NULL,
    -- NULL = no hubo lectura de GPS (ISSUE-62; antes se guardaba 0, 0)
    latitud_inicial DOUBLE PRECISION CHECK (latitud_inicial BETWEEN -90 AND 90),
    longitud_inicial DOUBLE PRECISION CHECK (longitud_inicial BETWEEN -180 AND 180),
    km_recorridos NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (km_recorridos BETWEEN 0 AND 30),
    cantidad_sacos INTEGER NOT NULL CHECK (cantidad_sacos BETWEEN 1 AND 100),
    kg_basura_normal NUMERIC(7, 2) NOT NULL DEFAULT 0 CHECK (kg_basura_normal >= 0),
    kg_basura_reciclable NUMERIC(7, 2) NOT NULL DEFAULT 0 CHECK (kg_basura_reciclable >= 0),
    kg_totales NUMERIC(7, 2) GENERATED ALWAYS AS (kg_basura_normal + kg_basura_reciclable) STORED,
    encargado_pesaje TEXT NOT NULL,
    destino_basura_normal TEXT NOT NULL,
    nro_guia_despacho TEXT,
    destino_basura_reciclable TEXT NOT NULL,
    fecha_sincronizacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (hora_fin >= hora_inicio AND hora_fin - hora_inicio <= interval '12 hours'),
    CHECK ((latitud_inicial IS NULL) = (longitud_inicial IS NULL)),
    CHECK (kg_basura_normal + kg_basura_reciclable > 0 AND kg_basura_normal + kg_basura_reciclable <= 2500)
);
CREATE INDEX IF NOT EXISTS jornada_fecha_playa ON public.jornada (fecha_jornada, id_playa);
CREATE INDEX IF NOT EXISTS jornada_id_playa ON public.jornada (id_playa);
CREATE INDEX IF NOT EXISTS jornada_id_brigada ON public.jornada (id_brigada);

-- ISSUE-62: sectores limpiados en cada jornada. area_m2 es la del catálogo en ese momento
-- (si después cambia la superficie de un sector, las jornadas pasadas no se alteran).
CREATE TABLE IF NOT EXISTS public.jornada_sector (
    id_registro TEXT NOT NULL REFERENCES public.jornada(id_registro) ON DELETE CASCADE,
    id_sector TEXT NOT NULL REFERENCES public.sector_catalogo(id_sector),
    area_m2 INTEGER NOT NULL CHECK (area_m2 > 0),
    PRIMARY KEY (id_registro, id_sector)
);
CREATE INDEX IF NOT EXISTS jornada_sector_id_sector ON public.jornada_sector (id_sector);

-- ISSUE-62: las 3 fotos de la jornada. Se guarda la RUTA dentro del bucket
-- (REG-…_1_inicio.jpg), no la URL: con el bucket público la URL se arma con la ruta
-- (=IMAGE() en Sheets, una página); con bucket privado (ISSUE-40), sirve para URLs firmadas.
CREATE TABLE IF NOT EXISTS public.foto_jornada (
    id_registro TEXT NOT NULL REFERENCES public.jornada(id_registro) ON DELETE CASCADE,
    tipo TEXT NOT NULL CHECK (tipo IN ('inicio', 'limpio', 'sacos')),
    ruta TEXT,
    -- Motivo obligatorio cuando la foto se omitió (ruta null)
    motivo_omision TEXT,
    PRIMARY KEY (id_registro, tipo),
    CHECK ((ruta IS NULL) <> (motivo_omision IS NULL))
);

CREATE TABLE IF NOT EXISTS public.participante_jornada (
    id BIGSERIAL PRIMARY KEY,
    id_registro TEXT NOT NULL REFERENCES public.jornada(id_registro) ON DELETE CASCADE,
    nombre TEXT NOT NULL CHECK (length(trim(nombre)) > 0),
    rol TEXT NOT NULL CHECK (rol IN ('LIDER', 'VOLUNTARIO'))
);

-- Mismo criterio que la app: sin mayúsculas ni espacios repetidos (BR-01).
-- Empieza por id_registro, así que también sirve para buscar los participantes de una jornada.
CREATE UNIQUE INDEX IF NOT EXISTS participante_jornada_nombre_unico
    ON public.participante_jornada (id_registro, lower(regexp_replace(trim(nombre), '\s+', ' ', 'g')));

CREATE TABLE IF NOT EXISTS public.herramienta_jornada (
    id BIGSERIAL PRIMARY KEY,
    id_registro TEXT NOT NULL REFERENCES public.jornada(id_registro) ON DELETE CASCADE,
    qr_codigo TEXT NOT NULL REFERENCES public.herramienta_catalogo(qr_codigo),
    nombre_herramienta TEXT NOT NULL,
    estado_final TEXT NOT NULL CHECK (estado_final IN ('Operativo', 'Dañado', 'Extraviado')),
    UNIQUE (id_registro, qr_codigo)
);
-- ISSUE-62: la RPC busca el historial de cada herramienta para actualizar el inventario;
-- sin este índice recorría toda la tabla en cada sincronización.
CREATE INDEX IF NOT EXISTS herramienta_jornada_qr_codigo ON public.herramienta_jornada (qr_codigo);

CREATE TABLE IF NOT EXISTS public.basura_recolectada (
    id_registro TEXT NOT NULL REFERENCES public.jornada(id_registro) ON DELETE CASCADE,
    id_tipo_basura TEXT NOT NULL REFERENCES public.tipo_basura_catalogo(id_tipo_basura),
    nombre_visible TEXT NOT NULL,
    categoria_origen TEXT NOT NULL,
    cantidad INTEGER NOT NULL DEFAULT 0 CHECK (cantidad BETWEEN 0 AND 5000),
    PRIMARY KEY (id_registro, id_tipo_basura)
);
CREATE INDEX IF NOT EXISTS basura_recolectada_id_tipo_basura ON public.basura_recolectada (id_tipo_basura);

-- ISSUE-42: copia de solo lectura de cada jornada que la RPC rechazó, para que el
-- administrador vea qué falló sin pedir el teléfono. No cuenta en ningún reporte.
-- Un mismo motivo para la misma jornada se acumula en "intentos" (no duplica filas).
CREATE TABLE IF NOT EXISTS public.jornada_rechazada (
    id BIGSERIAL PRIMARY KEY,
    id_registro TEXT NOT NULL,              -- '' si el payload no traía uno válido
    motivo TEXT NOT NULL,
    payload JSONB,                          -- NULL si el payload superaba 20 kB (ISSUE-60)
    intentos INTEGER NOT NULL DEFAULT 1,
    primer_intento TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultimo_intento TIMESTAMPTZ NOT NULL DEFAULT now(),
    resuelta BOOLEAN NOT NULL DEFAULT FALSE, -- TRUE cuando la jornada entra después (reintento)
    UNIQUE (id_registro, motivo)
);
CREATE INDEX IF NOT EXISTS idx_jornada_rechazada_pendientes
    ON public.jornada_rechazada (ultimo_intento DESC) WHERE NOT resuelta;


-- ============================================================================
-- 3. ROW LEVEL SECURITY Y PERMISOS
-- ============================================================================
ALTER TABLE public.playa_catalogo ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sector_catalogo ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.brigada_catalogo ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.herramienta_catalogo ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tipo_basura_catalogo ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.jornada ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.jornada_sector ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.foto_jornada ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.participante_jornada ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.herramienta_jornada ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.basura_recolectada ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.jornada_rechazada ENABLE ROW LEVEL SECURITY;

-- Doble barrera: además de RLS, se quitan los privilegios que Supabase otorga por defecto
REVOKE ALL ON
    public.playa_catalogo, public.sector_catalogo, public.brigada_catalogo,
    public.herramienta_catalogo, public.tipo_basura_catalogo,
    public.jornada, public.jornada_sector, public.foto_jornada,
    public.participante_jornada, public.herramienta_jornada, public.basura_recolectada,
    public.jornada_rechazada
FROM anon, authenticated;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;

-- Catálogos: solo lectura
GRANT SELECT ON public.playa_catalogo, public.sector_catalogo, public.brigada_catalogo,
    public.herramienta_catalogo, public.tipo_basura_catalogo
TO anon, authenticated;

DROP POLICY IF EXISTS "Lectura publica de playas" ON public.playa_catalogo;
CREATE POLICY "Lectura publica de playas"
ON public.playa_catalogo FOR SELECT TO anon, authenticated USING (true);

DROP POLICY IF EXISTS "Lectura publica de sectores" ON public.sector_catalogo;
CREATE POLICY "Lectura publica de sectores"
ON public.sector_catalogo FOR SELECT TO anon, authenticated USING (true);

DROP POLICY IF EXISTS "Lectura publica de brigadas" ON public.brigada_catalogo;
CREATE POLICY "Lectura publica de brigadas"
ON public.brigada_catalogo FOR SELECT TO anon, authenticated USING (true);

DROP POLICY IF EXISTS "Lectura publica de herramientas" ON public.herramienta_catalogo;
CREATE POLICY "Lectura publica de herramientas"
ON public.herramienta_catalogo FOR SELECT TO anon, authenticated USING (true);

DROP POLICY IF EXISTS "Lectura publica de tipos de basura" ON public.tipo_basura_catalogo;
CREATE POLICY "Lectura publica de tipos de basura"
ON public.tipo_basura_catalogo FOR SELECT TO anon, authenticated USING (true);

-- Tablas de jornada: sin políticas para anon/authenticated = sin acceso directo.
-- service_role (clave secreta) se salta RLS y conserva sus privilegios.


-- ============================================================================
-- 4. RPC sincronizar_jornada(p jsonb): única vía de escritura de la app
-- ============================================================================
-- Llamada desde el APK (ejemplo completo en docs/rpc_ejemplo.json):
--   POST /rest/v1/rpc/sincronizar_jornada
--   { "p": {
--       "jornada": {
--         "id_registro": "REG-20261116-A1B2C3D4E5F6", "fecha_jornada": "2026-11-16",
--         "id_playa": "PLAYA-01", "id_brigada": "BRIGADA-01",
--         "sectores_ids": ["SECTOR-01", "SECTOR-02"], "sin_herramientas": false,
--         "hora_inicio_ms": 0, "hora_fin_ms": 0,
--         "latitud_inicial": -33.5 | null, "longitud_inicial": -71.6 | null, "km_recorridos": 1.2,
--         "cantidad_sacos": 2, "kg_basura_normal": 20.5, "kg_basura_reciclable": 9.5,
--         "encargado_pesaje": "Ana",
--         "destino_basura_normal": "Entregado a camión DIMAO", "nro_guia_despacho": "GD-123" | null,
--         "destino_basura_reciclable": "Entregado a Punto Limpio Municipal"
--       },
--       "fotos": [ {"tipo": "inicio", "ruta": "REG-…_1_inicio.jpg", "motivo_omision": null},
--                  {"tipo": "limpio", "ruta": null, "motivo_omision": "Sin batería"},
--                  {"tipo": "sacos",  "ruta": "REG-…_3_sacos.jpg",  "motivo_omision": null} ],
--       "participantes": [ {"nombre": "Ana", "rol": "LIDER"}, {"nombre": "Luis", "rol": "VOLUNTARIO"} ],
--       "herramientas":  [ {"qr_codigo": "INV-02-PINZA-01", "estado_final": "Dañado"} ],
--       "basura":        [ {"id_tipo_basura": "COLILLAS", "cantidad": 412} ]
--   } }
--
-- Respuesta (HTTP 200): "INSERTADA", "YA_EXISTIA" o "RECHAZADA: <motivo>" (ISSUE-42).
-- Un dato inválido no guarda nada de la jornada (todo o nada); el rechazo queda
-- registrado en jornada_rechazada. Errores no previstos siguen respondiendo HTTP 4xx/5xx.
--
-- Estructura: sincronizar_jornada_interna valida e inserta (lanza error si algo es
-- inválido); sincronizar_jornada (pública) la envuelve y convierte los errores de
-- validación en "RECHAZADA". La interna no es ejecutable por anon.
--
-- El servidor NO toma del payload lo que puede obtener de los catálogos: el área de
-- cada sector, nombre_herramienta, nombre_visible y categoria_origen.
CREATE OR REPLACE FUNCTION public.sincronizar_jornada_interna(p jsonb)
RETURNS text
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
    j jsonb := p -> 'jornada';
    v_fotos jsonb := COALESCE(p -> 'fotos', '[]'::jsonb);
    v_participantes jsonb := COALESCE(p -> 'participantes', '[]'::jsonb);
    v_herramientas jsonb := COALESCE(p -> 'herramientas', '[]'::jsonb);
    v_basura jsonb := COALESCE(p -> 'basura', '[]'::jsonb);

    v_id text;
    v_fecha date;
    v_hoy_chile date := (now() AT TIME ZONE 'America/Santiago')::date;
    v_fecha_inicio date;
    v_id_playa text;
    v_id_brigada text;
    v_sectores text[];
    v_sin_herramientas boolean;
    v_inicio_ms bigint;
    v_fin_ms bigint;
    v_inicio timestamptz;
    v_fin timestamptz;
    v_lat double precision;
    v_lng double precision;
    v_km numeric;
    v_sacos integer;
    v_kg_normal numeric;
    v_kg_reciclable numeric;
    v_encargado text;
    v_destino_normal text;
    v_guia text;
    v_destino_reciclable text;
    v_foto jsonb;
    v_rutas text[] := ARRAY[NULL, NULL, NULL]::text[];
    v_motivos text[] := ARRAY[NULL, NULL, NULL]::text[];
    v_i integer;
    v_tipos_foto constant text[] := ARRAY['inicio', 'limpio', 'sacos'];

    v_n_lideres integer;
    v_n_voluntarios integer;
    v_faltante text;
    v_insertadas integer;

    -- ISSUE-58: lista blanca de caracteres por campo, igual que ReglasTexto.kt. Evita que un texto
    -- se ejecute como fórmula al copiarlo a Google Sheets (=, +, -, @ al inicio).
    c_letras constant text := 'A-Za-zÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÄËÏÖÜÑÇáéíóúàèìòùâêîôûäëïöüñç';
    c_re_nombre constant text := '^[' || c_letras || '][' || c_letras || ' ''’]*$';   -- letras, espacios y apóstrofo
    c_re_motivo constant text := '^[' || c_letras || '0-9][' || c_letras || '0-9 ]*$'; -- letras, números y espacios
BEGIN
    -- ---------- Estructura ----------
    IF j IS NULL OR jsonb_typeof(j) <> 'object' THEN
        RAISE EXCEPTION 'Falta el objeto "jornada"' USING ERRCODE = '22023';
    END IF;
    IF jsonb_typeof(v_fotos) <> 'array'
       OR jsonb_typeof(v_participantes) <> 'array'
       OR jsonb_typeof(v_herramientas) <> 'array'
       OR jsonb_typeof(v_basura) <> 'array' THEN
        RAISE EXCEPTION '"fotos", "participantes", "herramientas" y "basura" deben ser listas' USING ERRCODE = '22023';
    END IF;

    v_id := j ->> 'id_registro';
    IF v_id IS NULL OR v_id !~ '^REG-[0-9]{8}-[A-Z0-9]{4,12}$' THEN
        RAISE EXCEPTION 'id_registro inválido: %', v_id USING ERRCODE = '22023';
    END IF;

    -- ---------- Idempotencia: una jornada ya sincronizada no se modifica ----------
    IF EXISTS (SELECT 1 FROM jornada WHERE id_registro = v_id) THEN
        RETURN 'YA_EXISTIA';
    END IF;

    -- ---------- Cabecera: fecha, playa y brigada ----------
    -- Reloj del teléfono (ISSUE-36): ni fechas futuras ni más de 60 días atrás
    BEGIN
        v_fecha := (j ->> 'fecha_jornada')::date;
    EXCEPTION WHEN others THEN
        RAISE EXCEPTION 'fecha_jornada inválida: %', j ->> 'fecha_jornada' USING ERRCODE = '22023';
    END;
    IF v_fecha IS NULL OR v_fecha > v_hoy_chile + 1 OR v_fecha < v_hoy_chile - 60 THEN
        RAISE EXCEPTION 'fecha_jornada fuera de rango (revisa la hora del teléfono): %', v_fecha USING ERRCODE = '22023';
    END IF;

    v_id_playa := j ->> 'id_playa';
    IF NOT EXISTS (SELECT 1 FROM playa_catalogo WHERE id_playa = v_id_playa) THEN
        RAISE EXCEPTION 'Playa desconocida: %', v_id_playa USING ERRCODE = '22023';
    END IF;

    -- ISSUE-62: la brigada debe existir y ser de la playa elegida
    v_id_brigada := j ->> 'id_brigada';
    IF NOT EXISTS (SELECT 1 FROM brigada_catalogo WHERE id_brigada = v_id_brigada AND id_playa = v_id_playa) THEN
        RAISE EXCEPTION 'Brigada desconocida o de otra playa: %', v_id_brigada USING ERRCODE = '22023';
    END IF;

    IF jsonb_typeof(j -> 'sectores_ids') IS DISTINCT FROM 'array' THEN
        RAISE EXCEPTION 'sectores_ids debe ser una lista' USING ERRCODE = '22023';
    END IF;
    SELECT COALESCE(array_agg(DISTINCT s ORDER BY s), '{}')
      INTO v_sectores
      FROM jsonb_array_elements_text(j -> 'sectores_ids') AS s;
    IF cardinality(v_sectores) = 0 THEN
        RAISE EXCEPTION 'La jornada debe tener al menos 1 sector' USING ERRCODE = '22023';
    END IF;
    -- Cada sector debe existir y ser de la playa elegida
    SELECT s INTO v_faltante FROM unnest(v_sectores) AS s
     WHERE NOT EXISTS (SELECT 1 FROM sector_catalogo sc WHERE sc.id_sector = s AND sc.id_playa = v_id_playa) LIMIT 1;
    IF FOUND THEN
        RAISE EXCEPTION 'Sector desconocido o de otra playa: %', v_faltante USING ERRCODE = '22023';
    END IF;

    -- ---------- Horas, GPS y topes ----------
    -- La app envía milisegundos; se guardan como TIMESTAMPTZ (ISSUE-62)
    v_inicio_ms := (j ->> 'hora_inicio_ms')::bigint;
    v_fin_ms := (j ->> 'hora_fin_ms')::bigint;
    IF v_inicio_ms IS NULL OR v_fin_ms IS NULL OR v_inicio_ms <= 0 OR v_fin_ms < v_inicio_ms
       OR v_fin_ms - v_inicio_ms > 12 * 3600 * 1000 THEN
        RAISE EXCEPTION 'Horas de inicio/fin inválidas (máximo 12 h de limpieza)' USING ERRCODE = '22023';
    END IF;
    v_inicio := to_timestamp(v_inicio_ms / 1000.0);
    v_fin := to_timestamp(v_fin_ms / 1000.0);
    IF v_inicio > now() + interval '1 day' THEN
        RAISE EXCEPTION 'La hora de inicio está en el futuro (revisa la hora del teléfono)' USING ERRCODE = '22023';
    END IF;
    -- La limpieza empieza el mismo día en que se creó la jornada (o el siguiente, si cruzó medianoche)
    v_fecha_inicio := (v_inicio AT TIME ZONE 'America/Santiago')::date;
    IF v_fecha_inicio NOT BETWEEN v_fecha AND v_fecha + 1 THEN
        RAISE EXCEPTION 'La hora de inicio (%) no corresponde a fecha_jornada (%)', v_fecha_inicio, v_fecha
            USING ERRCODE = '22023';
    END IF;

    -- Sin lectura de GPS: ambas NULL (ISSUE-62)
    v_lat := (j ->> 'latitud_inicial')::double precision;
    v_lng := (j ->> 'longitud_inicial')::double precision;
    IF (v_lat IS NULL) <> (v_lng IS NULL)
       OR v_lat NOT BETWEEN -90 AND 90 OR v_lng NOT BETWEEN -180 AND 180 THEN
        RAISE EXCEPTION 'Coordenadas inválidas' USING ERRCODE = '22023';
    END IF;

    v_km := round(COALESCE((j ->> 'km_recorridos')::numeric, 0), 2);
    IF v_km < 0 OR v_km > 30 THEN
        RAISE EXCEPTION 'km_recorridos fuera de rango (0 a 30): %', v_km USING ERRCODE = '22023';
    END IF;

    -- ---------- Pesaje (BR-06, Ley 20.949: promedio <= 25 kg por saco) ----------
    v_sacos := (j ->> 'cantidad_sacos')::integer;
    v_kg_normal := round(COALESCE((j ->> 'kg_basura_normal')::numeric, 0), 2);
    v_kg_reciclable := round(COALESCE((j ->> 'kg_basura_reciclable')::numeric, 0), 2);
    IF v_sacos IS NULL OR v_sacos <= 0 OR v_sacos > 100 THEN
        RAISE EXCEPTION 'cantidad_sacos debe estar entre 1 y 100' USING ERRCODE = '22023';
    END IF;
    IF v_kg_normal < 0 OR v_kg_reciclable < 0 OR v_kg_normal + v_kg_reciclable <= 0 THEN
        RAISE EXCEPTION 'Los kilos deben ser >= 0 y sumar más de 0' USING ERRCODE = '22023';
    END IF;
    IF v_kg_normal + v_kg_reciclable > 2500 THEN
        RAISE EXCEPTION 'Máximo 2.500 kg por jornada' USING ERRCODE = '22023';
    END IF;
    IF (v_kg_normal + v_kg_reciclable) / v_sacos > 25 THEN
        RAISE EXCEPTION 'Promedio por saco supera 25 kg (Ley 20.949)' USING ERRCODE = '22023';
    END IF;

    v_encargado := regexp_replace(trim(COALESCE(j ->> 'encargado_pesaje', '')), '\s+', ' ', 'g');
    IF v_encargado = '' OR length(v_encargado) > 80 THEN
        RAISE EXCEPTION 'encargado_pesaje es obligatorio (máx. 80 caracteres)' USING ERRCODE = '22023';
    END IF;
    IF v_encargado !~ c_re_nombre THEN
        RAISE EXCEPTION 'encargado_pesaje: solo letras, espacios y apóstrofo' USING ERRCODE = '22023';
    END IF;

    -- ---------- Destinos (P9: "No aplicable" si y solo si hay 0 kg) ----------
    v_destino_normal := j ->> 'destino_basura_normal';
    v_destino_reciclable := j ->> 'destino_basura_reciclable';
    IF v_destino_normal IS NULL OR v_destino_normal NOT IN
       ('Entregado a camión DIMAO', 'Pendiente de retiro', 'No aplicable') THEN
        RAISE EXCEPTION 'destino_basura_normal inválido: %', v_destino_normal USING ERRCODE = '22023';
    END IF;
    IF v_destino_reciclable IS NULL OR v_destino_reciclable NOT IN
       ('Entregado a Punto Limpio Municipal', 'Entregado a Reciclador de Base', 'Pendiente de entrega', 'No aplicable') THEN
        RAISE EXCEPTION 'destino_basura_reciclable inválido: %', v_destino_reciclable USING ERRCODE = '22023';
    END IF;
    IF (v_kg_normal = 0) <> (v_destino_normal = 'No aplicable')
       OR (v_kg_reciclable = 0) <> (v_destino_reciclable = 'No aplicable') THEN
        RAISE EXCEPTION '"No aplicable" solo corresponde cuando hay 0 kg' USING ERRCODE = '22023';
    END IF;
    v_guia := NULLIF(trim(COALESCE(j ->> 'nro_guia_despacho', '')), '');
    IF v_destino_normal <> 'Entregado a camión DIMAO' THEN
        v_guia := NULL;
    END IF;
    -- ISSUE-60: era el único texto libre sin tope. Mismo formato que ReglasTexto.kt
    IF v_guia IS NOT NULL AND v_guia !~ '^[A-Za-z0-9][A-Za-z0-9-]{0,11}$' THEN
        RAISE EXCEPTION 'nro_guia_despacho inválido (1 a 12 letras, números o guion)' USING ERRCODE = '22023';
    END IF;

    -- ---------- Fotos: las 3, cada una con su ruta exacta o con motivo (ISSUE-62) ----------
    IF jsonb_array_length(v_fotos) <> 3
       OR (SELECT COUNT(DISTINCT e ->> 'tipo') FROM jsonb_array_elements(v_fotos) e
            WHERE jsonb_typeof(e) = 'object' AND e ->> 'tipo' = ANY (v_tipos_foto)) <> 3 THEN
        RAISE EXCEPTION 'Se esperan las 3 fotos (inicio, limpio y sacos), tomadas u omitidas' USING ERRCODE = '22023';
    END IF;
    FOR v_i IN 1..3 LOOP
        SELECT e INTO v_foto FROM jsonb_array_elements(v_fotos) e WHERE e ->> 'tipo' = v_tipos_foto[v_i];
        v_rutas[v_i] := NULLIF(v_foto ->> 'ruta', '');
        IF v_rutas[v_i] IS NOT NULL THEN
            -- La ruta es exactamente la que sube la app para esta jornada y este tipo
            IF v_rutas[v_i] <> v_id || '_' || v_i || '_' || v_tipos_foto[v_i] || '.jpg' THEN
                RAISE EXCEPTION 'Ruta de foto inválida: %', v_rutas[v_i] USING ERRCODE = '22023';
            END IF;
            -- Con foto no se guarda motivo
        ELSE
            v_motivos[v_i] := regexp_replace(trim(COALESCE(v_foto ->> 'motivo_omision', '')), '\s+', ' ', 'g');
            IF v_motivos[v_i] = '' OR length(v_motivos[v_i]) > 120 THEN
                RAISE EXCEPTION 'Falta el motivo para omitir la foto de %', v_tipos_foto[v_i] USING ERRCODE = '22023';
            END IF;
            IF v_motivos[v_i] !~ c_re_motivo THEN
                RAISE EXCEPTION 'Motivo de la foto de %: solo letras, números y espacios', v_tipos_foto[v_i] USING ERRCODE = '22023';
            END IF;
        END IF;
    END LOOP;

    -- ---------- Participantes (BR-01/BR-02) ----------
    IF EXISTS (
        SELECT 1 FROM jsonb_array_elements(v_participantes) e
        WHERE jsonb_typeof(e) <> 'object'
           OR trim(COALESCE(e ->> 'nombre', '')) = ''
           OR length(trim(e ->> 'nombre')) > 80
           OR regexp_replace(trim(e ->> 'nombre'), '\s+', ' ', 'g') !~ c_re_nombre
           OR COALESCE(e ->> 'rol', '') NOT IN ('LIDER', 'VOLUNTARIO')
    ) THEN
        RAISE EXCEPTION 'Participante inválido (nombre con solo letras, espacios y apóstrofo, máx. 80; rol LIDER o VOLUNTARIO)' USING ERRCODE = '22023';
    END IF;
    SELECT COUNT(*) FILTER (WHERE e ->> 'rol' = 'LIDER'),
           COUNT(*) FILTER (WHERE e ->> 'rol' = 'VOLUNTARIO')
      INTO v_n_lideres, v_n_voluntarios
      FROM jsonb_array_elements(v_participantes) e;
    IF v_n_lideres <> 1 OR v_n_voluntarios < 1 THEN
        RAISE EXCEPTION 'Se requiere exactamente 1 líder y al menos 1 voluntario' USING ERRCODE = '22023';
    END IF;
    IF v_n_lideres + v_n_voluntarios > 60 THEN
        RAISE EXCEPTION 'Máximo 60 integrantes por brigada' USING ERRCODE = '22023';
    END IF;
    -- Se valida aquí (HTTP 200 "RECHAZADA") en vez de dejar que el índice único responda 409,
    -- que la app podría confundir con "ya existe"
    IF EXISTS (
        SELECT 1 FROM jsonb_array_elements(v_participantes) e
        GROUP BY lower(regexp_replace(trim(e ->> 'nombre'), '\s+', ' ', 'g'))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Hay nombres de participantes repetidos' USING ERRCODE = '22023';
    END IF;

    -- ---------- Herramientas (ISSUE-34: sin herramientas ⇒ lista vacía; si no, al menos 1) ----------
    v_sin_herramientas := COALESCE((j ->> 'sin_herramientas')::boolean, FALSE);
    IF v_sin_herramientas AND jsonb_array_length(v_herramientas) > 0 THEN
        RAISE EXCEPTION 'Una jornada sin herramientas no puede declarar herramientas' USING ERRCODE = '22023';
    END IF;
    IF NOT v_sin_herramientas AND jsonb_array_length(v_herramientas) = 0 THEN
        RAISE EXCEPTION 'Falta al menos 1 herramienta (o marcar sin_herramientas)' USING ERRCODE = '22023';
    END IF;
    SELECT e ->> 'qr_codigo' INTO v_faltante
      FROM jsonb_array_elements(v_herramientas) e
     WHERE NOT EXISTS (SELECT 1 FROM herramienta_catalogo hc WHERE hc.qr_codigo = e ->> 'qr_codigo')
     LIMIT 1;
    IF FOUND THEN
        RAISE EXCEPTION 'Herramienta desconocida: %', v_faltante USING ERRCODE = '22023';
    END IF;
    IF EXISTS (SELECT 1 FROM jsonb_array_elements(v_herramientas) e
               WHERE COALESCE(e ->> 'estado_final', '') NOT IN ('Operativo', 'Dañado', 'Extraviado')) THEN
        RAISE EXCEPTION 'estado_final de herramienta inválido' USING ERRCODE = '22023';
    END IF;
    IF EXISTS (SELECT 1 FROM jsonb_array_elements(v_herramientas) e
               GROUP BY e ->> 'qr_codigo' HAVING COUNT(*) > 1) THEN
        RAISE EXCEPTION 'Herramienta repetida en la jornada' USING ERRCODE = '22023';
    END IF;

    -- ---------- Basura ----------
    SELECT e ->> 'id_tipo_basura' INTO v_faltante
      FROM jsonb_array_elements(v_basura) e
     WHERE NOT EXISTS (SELECT 1 FROM tipo_basura_catalogo tb WHERE tb.id_tipo_basura = e ->> 'id_tipo_basura')
     LIMIT 1;
    IF FOUND THEN
        RAISE EXCEPTION 'Tipo de basura desconocido: %', v_faltante USING ERRCODE = '22023';
    END IF;
    IF EXISTS (SELECT 1 FROM jsonb_array_elements(v_basura) e
               WHERE (e ->> 'cantidad') IS NULL
                  OR (e ->> 'cantidad')::integer < 0
                  OR (e ->> 'cantidad')::integer > 5000) THEN
        RAISE EXCEPTION 'Las cantidades de basura deben estar entre 0 y 5.000' USING ERRCODE = '22023';
    END IF;
    IF EXISTS (SELECT 1 FROM jsonb_array_elements(v_basura) e
               GROUP BY e ->> 'id_tipo_basura' HAVING COUNT(*) > 1) THEN
        RAISE EXCEPTION 'Tipo de basura repetido en la jornada' USING ERRCODE = '22023';
    END IF;

    -- ---------- Inserción (todo o nada) ----------
    -- ON CONFLICT cubre dos envíos simultáneos de la misma jornada
    INSERT INTO jornada (
        id_registro, fecha_jornada, id_playa, id_brigada, sin_herramientas,
        hora_inicio, hora_fin, latitud_inicial, longitud_inicial, km_recorridos,
        cantidad_sacos, kg_basura_normal, kg_basura_reciclable,
        encargado_pesaje, destino_basura_normal, nro_guia_despacho, destino_basura_reciclable
    ) VALUES (
        v_id, v_fecha, v_id_playa, v_id_brigada, v_sin_herramientas,
        v_inicio, v_fin, v_lat, v_lng, v_km,
        v_sacos, v_kg_normal, v_kg_reciclable,
        v_encargado, v_destino_normal, v_guia, v_destino_reciclable
    )
    ON CONFLICT (id_registro) DO NOTHING;
    GET DIAGNOSTICS v_insertadas = ROW_COUNT;
    IF v_insertadas = 0 THEN
        RETURN 'YA_EXISTIA';
    END IF;

    INSERT INTO jornada_sector (id_registro, id_sector, area_m2)
    SELECT v_id, sc.id_sector, sc.area_m2
      FROM sector_catalogo sc
     WHERE sc.id_sector = ANY (v_sectores);

    INSERT INTO foto_jornada (id_registro, tipo, ruta, motivo_omision)
    SELECT v_id, v_tipos_foto[i], v_rutas[i], v_motivos[i]
      FROM generate_series(1, 3) AS i;

    INSERT INTO participante_jornada (id_registro, nombre, rol)
    SELECT v_id, regexp_replace(trim(e ->> 'nombre'), '\s+', ' ', 'g'), e ->> 'rol'
      FROM jsonb_array_elements(v_participantes) WITH ORDINALITY AS t(e, ord)
     ORDER BY ord;

    INSERT INTO herramienta_jornada (id_registro, qr_codigo, nombre_herramienta, estado_final)
    SELECT v_id, hc.qr_codigo, hc.nombre_herramienta, e ->> 'estado_final'
      FROM jsonb_array_elements(v_herramientas) e
      JOIN herramienta_catalogo hc ON hc.qr_codigo = e ->> 'qr_codigo';

    INSERT INTO basura_recolectada (id_registro, id_tipo_basura, nombre_visible, categoria_origen, cantidad)
    SELECT v_id, tb.id_tipo_basura, tb.nombre_visible, tb.categoria_origen, (e ->> 'cantidad')::integer
      FROM jsonb_array_elements(v_basura) e
      JOIN tipo_basura_catalogo tb ON tb.id_tipo_basura = e ->> 'id_tipo_basura';

    -- Estado del inventario: solo si no hay otra jornada sincronizada más reciente con esa
    -- herramienta (una jornada antigua que se sincroniza tarde no pisa el estado actual).
    -- Usa el índice herramienta_jornada_qr_codigo (ISSUE-62).
    -- ⚠️ ISSUE-39: cualquiera con la clave pública puede llegar aquí; pendiente de decidir protección.
    UPDATE herramienta_catalogo hc
       SET estado = hj.estado_final
      FROM herramienta_jornada hj
     WHERE hj.id_registro = v_id
       AND hc.qr_codigo = hj.qr_codigo
       AND NOT EXISTS (
           SELECT 1
             FROM herramienta_jornada hj2
             JOIN jornada j2 ON j2.id_registro = hj2.id_registro
            WHERE hj2.qr_codigo = hj.qr_codigo
              AND j2.id_registro <> v_id
              AND j2.hora_fin > v_fin
       );

    RETURN 'INSERTADA';
END;
$$;

REVOKE ALL ON FUNCTION public.sincronizar_jornada_interna(jsonb) FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.sincronizar_jornada(p jsonb)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_resultado text;
    v_motivo text;
    v_id text;
    -- ISSUE-60: una jornada real pesa 1–5 kB. Peor caso con la clave pública: 1.000 × 20 kB ≈ 20 MB
    c_max_payload constant integer := 20000;    -- bytes guardados por rechazo (si se supera, se guarda sin payload)
    c_max_pendientes constant integer := 1000;  -- tope de filas sin resolver (anti-spam con la clave pública)
BEGIN
    v_id := left(COALESCE(p -> 'jornada' ->> 'id_registro', ''), 40);

    BEGIN
        v_resultado := sincronizar_jornada_interna(p);
    EXCEPTION
        -- Clase 22 (datos inválidos: validaciones, casts) y 23 (CHECK/FK/únicos)
        WHEN data_exception OR integrity_constraint_violation THEN
            GET STACKED DIAGNOSTICS v_motivo = MESSAGE_TEXT;
            v_motivo := left(v_motivo, 300);
            IF (SELECT COUNT(*) FROM jornada_rechazada WHERE NOT resuelta) < c_max_pendientes THEN
                INSERT INTO jornada_rechazada (id_registro, motivo, payload)
                VALUES (v_id, v_motivo, CASE WHEN pg_column_size(p) <= c_max_payload THEN p END)
                ON CONFLICT (id_registro, motivo) DO UPDATE
                   SET intentos = jornada_rechazada.intentos + 1,
                       ultimo_intento = now(),
                       resuelta = FALSE;
            END IF;
            RETURN 'RECHAZADA: ' || v_motivo;
    END;

    IF v_resultado IN ('INSERTADA', 'YA_EXISTIA') AND v_id <> '' THEN
        UPDATE jornada_rechazada SET resuelta = TRUE WHERE id_registro = v_id AND NOT resuelta;
    END IF;
    RETURN v_resultado;
END;
$$;

REVOKE ALL ON FUNCTION public.sincronizar_jornada(jsonb) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.sincronizar_jornada(jsonb) TO anon, service_role;


-- ============================================================================
-- 5. VISTAS DE REPORTERÍA
-- ============================================================================
-- Vistas de la v3 eliminadas en la v4 (31-10-2026).
DROP VIEW IF EXISTS public.vista_reporte_jornadas;
DROP VIEW IF EXISTS public.vista_densidad_diaria;

-- ISSUE-62: una fila por jornada con todo lo derivado (para la planilla, una web o informes).
-- ISSUE-30: alimenta la pestaña Jornadas de la planilla; duracion_h y voluntario_horas van al
-- final porque CREATE OR REPLACE VIEW solo permite agregar columnas al final.
-- security_invoker: la vista se consulta con los permisos de quien la llama (solo service_role).
CREATE OR REPLACE VIEW public.vista_jornada WITH (security_invoker = true) AS
SELECT
    j.id_registro,
    j.fecha_jornada,
    j.id_playa,
    pl.nombre_playa,
    j.id_brigada,
    br.nombre_brigada,
    pa.nombre_lider,
    pa.voluntarios,
    pa.total_participantes,
    se.sectores,
    se.area_total_m2,
    j.sin_herramientas,
    j.hora_inicio,
    j.hora_fin,
    j.hora_fin - j.hora_inicio AS duracion,
    j.latitud_inicial,
    j.longitud_inicial,
    j.km_recorridos,
    fo.ruta_foto_inicio,
    fo.motivo_omision_inicio,
    fo.ruta_foto_limpio,
    fo.motivo_omision_limpio,
    fo.ruta_foto_sacos,
    fo.motivo_omision_sacos,
    j.cantidad_sacos,
    j.kg_basura_normal,
    j.kg_basura_reciclable,
    j.kg_totales,
    j.encargado_pesaje,
    j.destino_basura_normal,
    j.nro_guia_despacho,
    j.destino_basura_reciclable,
    j.fecha_sincronizacion,
    -- ISSUE-30: horas decimales (1 h 30 min = 1.50) y voluntario-horas = participantes
    -- (líder + voluntarios) × duración. Se calculan sin redondear y se redondea al final.
    round(extract(epoch FROM j.hora_fin - j.hora_inicio) / 3600, 2) AS duracion_h,
    round(COALESCE(pa.total_participantes, 0) * extract(epoch FROM j.hora_fin - j.hora_inicio) / 3600, 2) AS voluntario_horas
FROM public.jornada j
JOIN public.playa_catalogo pl ON pl.id_playa = j.id_playa
JOIN public.brigada_catalogo br ON br.id_brigada = j.id_brigada
LEFT JOIN (
    SELECT id_registro,
           max(nombre) FILTER (WHERE rol = 'LIDER') AS nombre_lider,
           string_agg(nombre, ', ' ORDER BY id) FILTER (WHERE rol = 'VOLUNTARIO') AS voluntarios,
           count(*)::integer AS total_participantes
      FROM public.participante_jornada
     GROUP BY id_registro
) pa ON pa.id_registro = j.id_registro
LEFT JOIN (
    SELECT js.id_registro,
           string_agg(sc.nombre_sector, ', ' ORDER BY js.id_sector) AS sectores,
           sum(js.area_m2)::integer AS area_total_m2
      FROM public.jornada_sector js
      JOIN public.sector_catalogo sc ON sc.id_sector = js.id_sector
     GROUP BY js.id_registro
) se ON se.id_registro = j.id_registro
LEFT JOIN (
    SELECT id_registro,
           max(ruta) FILTER (WHERE tipo = 'inicio') AS ruta_foto_inicio,
           max(motivo_omision) FILTER (WHERE tipo = 'inicio') AS motivo_omision_inicio,
           max(ruta) FILTER (WHERE tipo = 'limpio') AS ruta_foto_limpio,
           max(motivo_omision) FILTER (WHERE tipo = 'limpio') AS motivo_omision_limpio,
           max(ruta) FILTER (WHERE tipo = 'sacos') AS ruta_foto_sacos,
           max(motivo_omision) FILTER (WHERE tipo = 'sacos') AS motivo_omision_sacos
      FROM public.foto_jornada
     GROUP BY id_registro
) fo ON fo.id_registro = j.id_registro;

REVOKE ALL ON public.vista_jornada FROM anon, authenticated;

-- ISSUE-30: pestaña Residuos. Una fila por jornada y tipo de residuo; los tipos con
-- cantidad 0 no se muestran (mismo criterio que la P10, BR-12). El conteo es por jornada,
-- no por sector, y la app no pesa por tipo: el peso está en vista_jornada.
CREATE OR REPLACE VIEW public.vista_residuos WITH (security_invoker = true) AS
SELECT
    b.id_registro,
    j.fecha_jornada,
    j.id_playa,
    pl.nombre_playa,
    j.id_brigada,
    br.nombre_brigada,
    b.id_tipo_basura,
    b.nombre_visible,
    b.categoria_origen,
    b.cantidad
FROM public.basura_recolectada b
JOIN public.jornada j ON j.id_registro = b.id_registro
JOIN public.playa_catalogo pl ON pl.id_playa = j.id_playa
JOIN public.brigada_catalogo br ON br.id_brigada = j.id_brigada
WHERE b.cantidad > 0;

REVOKE ALL ON public.vista_residuos FROM anon, authenticated;

-- ISSUE-30: pestaña Herramientas. Una fila por herramienta y jornada (cada fila es una
-- unidad física). qr_codigo es el ID interno de la herramienta (Herramienta_ID en la planilla).
-- Las jornadas con sin_herramientas = true no tienen filas aquí.
CREATE OR REPLACE VIEW public.vista_herramientas WITH (security_invoker = true) AS
SELECT
    h.id_registro,
    j.fecha_jornada,
    j.id_playa,
    pl.nombre_playa,
    j.id_brigada,
    br.nombre_brigada,
    h.qr_codigo,
    h.nombre_herramienta,
    h.estado_final
FROM public.herramienta_jornada h
JOIN public.jornada j ON j.id_registro = h.id_registro
JOIN public.playa_catalogo pl ON pl.id_playa = j.id_playa
JOIN public.brigada_catalogo br ON br.id_brigada = j.id_brigada;

REVOKE ALL ON public.vista_herramientas FROM anon, authenticated;

-- ISSUE-30: área limpiada por playa y día contando cada sector UNA sola vez. Si dos
-- brigadas eligen el mismo sector el mismo día, sumar vista_jornada.area_total_m2 lo
-- contaría dos veces; el KPI de área total y kg/m² total usa esta vista.
-- area_m2 es la guardada en la jornada (la del catálogo en ese momento); si el mismo
-- sector quedó con dos superficies distintas ese día, se toma la mayor.
CREATE OR REPLACE VIEW public.vista_area_diaria WITH (security_invoker = true) AS
SELECT
    s.fecha_jornada,
    s.id_playa,
    pl.nombre_playa,
    count(*)::integer AS sectores_limpiados,
    sum(s.area_m2)::integer AS area_m2
FROM (
    SELECT j.fecha_jornada, j.id_playa, js.id_sector, max(js.area_m2) AS area_m2
      FROM public.jornada_sector js
      JOIN public.jornada j ON j.id_registro = js.id_registro
     GROUP BY j.fecha_jornada, j.id_playa, js.id_sector
) s
JOIN public.playa_catalogo pl ON pl.id_playa = s.id_playa
GROUP BY s.fecha_jornada, s.id_playa, pl.nombre_playa;

REVOKE ALL ON public.vista_area_diaria FROM anon, authenticated;


-- ============================================================================
-- 6. POBLADO INICIAL (SEED) DE CATÁLOGOS MAESTROS
-- ============================================================================
-- Para agregar una playa: insertar la playa y luego sus sectores (con sus m²) y sus brigadas.
INSERT INTO public.playa_catalogo (id_playa, nombre_playa) VALUES
    ('PLAYA-01', 'Playa Grande de Cartagena')
ON CONFLICT (id_playa) DO UPDATE SET nombre_playa = EXCLUDED.nombre_playa;

INSERT INTO public.sector_catalogo (id_sector, id_playa, nombre_sector, area_m2) VALUES
    ('SECTOR-01', 'PLAYA-01', 'Sector 1 - Norte (2.000 m²)', 2000),
    ('SECTOR-02', 'PLAYA-01', 'Sector 2 - Centro Norte (2.000 m²)', 2000),
    ('SECTOR-03', 'PLAYA-01', 'Sector 3 - Centro Sur (2.000 m²)', 2000),
    ('SECTOR-04', 'PLAYA-01', 'Sector 4 - Sur (2.000 m²)', 2000)
ON CONFLICT (id_sector) DO UPDATE SET
    id_playa = EXCLUDED.id_playa,
    nombre_sector = EXCLUDED.nombre_sector,
    area_m2 = EXCLUDED.area_m2;

INSERT INTO public.brigada_catalogo (id_brigada, id_playa, nombre_brigada) VALUES
    ('BRIGADA-01', 'PLAYA-01', 'Brigada A'),
    ('BRIGADA-02', 'PLAYA-01', 'Brigada B'),
    ('BRIGADA-03', 'PLAYA-01', 'Brigada C'),
    ('BRIGADA-04', 'PLAYA-01', 'Brigada D')
ON CONFLICT (id_brigada) DO UPDATE SET
    id_playa = EXCLUDED.id_playa,
    nombre_brigada = EXCLUDED.nombre_brigada;

-- Nombre y número visibles; el código queda solo en el QR. La pesa no es herramienta de brigada.
-- Se actualiza el nombre pero NO el estado real de las herramientas ya registradas.
INSERT INTO public.herramienta_catalogo (qr_codigo, nombre_herramienta, estado) VALUES
    ('INV-02-PINZA-01', 'Pinza 1', 'Operativo'),
    ('INV-02-PINZA-02', 'Pinza 2', 'Operativo'),
    ('INV-02-PINZA-03', 'Pinza 3', 'Operativo'),
    ('INV-02-PINZA-04', 'Pinza 4', 'Operativo'),
    ('INV-02-PINZA-05', 'Pinza 5', 'Operativo'),
    ('INV-03-RASTRILLO-01', 'Rastrillo 1', 'Operativo'),
    ('INV-03-RASTRILLO-02', 'Rastrillo 2', 'Operativo'),
    ('INV-03-RASTRILLO-03', 'Rastrillo 3', 'Operativo'),
    ('INV-03-RASTRILLO-04', 'Rastrillo 4', 'Operativo')
ON CONFLICT (qr_codigo) DO UPDATE SET nombre_herramienta = EXCLUDED.nombre_herramienta;

INSERT INTO public.tipo_basura_catalogo (id_tipo_basura, nombre_visible, categoria_origen) VALUES
    ('COLILLAS', 'Colillas de cigarro', 'Recreativa'),
    ('TAPAS_PLASTICAS', 'Tapas plásticas', 'Recreativa'),
    ('BOTELLAS_PET', 'Botellas plásticas (PET)', 'Recreativa'),
    ('LATAS_ALUMINIO', 'Latas de aluminio', 'Recreativa'),
    ('ENVOLTORIOS', 'Envoltorios / bolsas plásticas', 'Recreativa'),
    ('VIDRIO', 'Vidrio / botellas', 'Recreativa'),
    ('PLUMAVIT', 'Plumavit (EPS)', 'Marítima'),
    ('CABOS_REDES', 'Cabos, cuerdas y redes', 'Marítima'),
    ('BOYAS_PESCA', 'Boyas / artículos de pesca', 'Marítima'),
    ('MASCARILLAS_HIGIENE', 'Toallitas húmedas / higiene', 'Higiene')
ON CONFLICT (id_tipo_basura) DO UPDATE SET
    nombre_visible = EXCLUDED.nombre_visible,
    categoria_origen = EXCLUDED.categoria_origen;


-- ============================================================================
-- 7. STORAGE: BUCKET DE EVIDENCIAS (lectura pública, la app solo agrega fotos nuevas)
-- ============================================================================
-- Bucket público: las fotos se ven por su URL pública sin necesitar política SELECT.
-- Solo JPEG y máximo 5 MB (la app sube 1280x720 comprimido). Ver ISSUE-40.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('evidencias-fotos', 'evidencias-fotos', TRUE, 5242880, ARRAY['image/jpeg'])
ON CONFLICT (id) DO UPDATE SET
    public = TRUE,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

-- Sin SELECT para anon: con la clave pública no se puede listar el contenido del bucket
DROP POLICY IF EXISTS "Lectura publica de evidencias" ON storage.objects;
-- Sin UPDATE: con la clave pública no se puede reemplazar una foto ya subida (x-upsert)
DROP POLICY IF EXISTS "Actualizacion publica de evidencias desde APK" ON storage.objects;

DROP POLICY IF EXISTS "Subida publica de evidencias desde APK" ON storage.objects;
CREATE POLICY "Subida publica de evidencias desde APK"
ON storage.objects FOR INSERT TO anon
WITH CHECK (
    bucket_id = 'evidencias-fotos'
    -- Mismo formato que JornadaSyncRepository: REG-AAAAMMDD-XXXXXXXXXXXX_{1|2|3}_{tipo}.jpg (sufijo de 12 desde ISSUE-59; se aceptan de 4 a 12)
    AND name ~ '^REG-[0-9]{8}-[A-Z0-9]{4,12}_[123]_(inicio|limpio|sacos)\.jpg$'
);
-- No se crea política DELETE: con la clave pública no se pueden borrar fotos.


-- ============================================================================
-- 8. VERIFICACIÓN MANUAL (ISSUE-20 / ISSUE-31 / ISSUE-62) — con la clave PÚBLICA, desde la terminal
-- ============================================================================
-- export SUPABASE_URL=https://<proyecto>.supabase.co
-- export KEY=<clave publicable sb_publishable_…>
--
-- 1) Catálogos visibles (200 con datos), incluidas playas y brigadas:
--    curl "$SUPABASE_URL/rest/v1/playa_catalogo?select=*" -H "apikey: $KEY"
--    curl "$SUPABASE_URL/rest/v1/brigada_catalogo?select=*" -H "apikey: $KEY"
--    curl "$SUPABASE_URL/rest/v1/sector_catalogo?select=*" -H "apikey: $KEY"
-- 2) Jornadas y vista NO visibles (401/403 "permission denied"):
--    curl "$SUPABASE_URL/rest/v1/jornada?select=*" -H "apikey: $KEY"
--    curl "$SUPABASE_URL/rest/v1/foto_jornada?select=*" -H "apikey: $KEY"
--    curl "$SUPABASE_URL/rest/v1/vista_jornada?select=*" -H "apikey: $KEY"
--    (lo mismo con vista_residuos, vista_herramientas y vista_area_diaria — ISSUE-30)
-- 3) No se puede cambiar el inventario (401/403):
--    curl -X PATCH "$SUPABASE_URL/rest/v1/herramienta_catalogo?qr_codigo=eq.INV-02-PINZA-01" \
--      -H "apikey: $KEY" -H "Content-Type: application/json" -d '{"estado":"Extraviado"}'
-- 4) RPC válida → "INSERTADA"; repetirla → "YA_EXISTIA".
--    ⚠️ fecha_jornada debe estar entre hoy−60 días y hoy+1: si el ejemplo quedó
--    viejo, cambia fecha_jornada, hora_inicio_ms y hora_fin_ms a una fecha reciente.
--    curl -X POST "$SUPABASE_URL/rest/v1/rpc/sincronizar_jornada" \
--      -H "apikey: $KEY" -H "Content-Type: application/json" -d @docs/rpc_ejemplo.json
--    Revisar (SQL Editor): SELECT * FROM public.vista_jornada;
-- 5) Subir dos veces la misma foto: la 1ª 200, la 2ª 400/409 "already exists":
--    curl -X POST "$SUPABASE_URL/storage/v1/object/evidencias-fotos/REG-20261030-TEST01_1_inicio.jpg" \
--      -H "apikey: $KEY" -H "Authorization: Bearer $KEY" -H "Content-Type: image/jpeg" --data-binary @foto.jpg
--    Con -H "x-upsert: true" también debe fallar.
-- 6) Jornada inválida → HTTP 200 "RECHAZADA: …" y queda en jornada_rechazada (ISSUE-42).
--    Ej.: rpc_ejemplo.json con "id_brigada": "BRIGADA-99" → "RECHAZADA: Brigada desconocida o de otra playa: BRIGADA-99".
--    Ej.: un participante "=IMPORTXML(A1)" → "RECHAZADA: Participante inválido …" (ISSUE-58).
--    Ver los rechazos (SQL Editor, como admin):
--    SELECT id_registro, motivo, intentos, ultimo_intento, resuelta
--      FROM public.jornada_rechazada ORDER BY ultimo_intento DESC;
-- 7) La función interna NO es ejecutable con la clave pública (404/401):
--    curl -X POST "$SUPABASE_URL/rest/v1/rpc/sincronizar_jornada_interna" \
--      -H "apikey: $KEY" -H "Content-Type: application/json" -d @docs/rpc_ejemplo.json
-- 8) jornada_rechazada NO es visible con la clave pública (401/403):
--    curl "$SUPABASE_URL/rest/v1/jornada_rechazada?select=*" -H "apikey: $KEY"
--
-- Para borrar las jornadas de prueba (SQL Editor, como admin):
--    DELETE FROM public.jornada WHERE id_registro LIKE 'REG-%-TEST%';
--    DELETE FROM public.jornada_rechazada WHERE id_registro LIKE 'REG-%-TEST%';

-- Supabase (PostgREST) debe recargar su caché para ver tablas y funciones nuevas
NOTIFY pgrst, 'reload schema';
