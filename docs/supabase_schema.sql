-- ============================================================================
-- ESQUEMA SQL MAESTRO PARA SUPABASE POSTGRESQL: "DEJA TU PLAYA LIMPIA" (v4)
-- Iniciativa Logística Verde
-- Fuente: PRD.md §4 y §5 · ISSUE-20 y ISSUE-31
--
-- Instalación desde cero (31-10-2026): ejecutar primero `supabase_reset_v4.sql`
-- (borra todo) y luego este archivo. Después, este archivo se puede volver a
-- ejecutar sin perder datos (CREATE IF NOT EXISTS / CREATE OR REPLACE).
--
-- Novedades v4: catálogo de playas (cada sector pertenece a una playa), brigada
-- en vez de cuadrilla, fecha_jornada, jornadas sin herramientas, motivo al omitir
-- una foto, topes razonables y sin vistas de reportería (se rediseñan con la planilla).
--
-- Modelo de seguridad (PRD §5.3):
--   * anon (clave pública del APK): SELECT en los 4 catálogos, EXECUTE en
--     sincronizar_jornada() y solo INSERT de fotos nuevas en el bucket.
--   * service_role (clave secreta, Google Sheets / admin): acceso total.
--   * Las tablas de jornada NO son accesibles con la clave pública.
--
-- Topes (deben coincidir con TopesJornada.kt de la app):
--   participantes ≤ 60 · cantidad por residuo ≤ 5.000 · sacos ≤ 100
--   kg totales ≤ 2.500 · km ≤ 30 · duración ≤ 12 h · motivo de omisión ≤ 120 caracteres
-- ============================================================================


-- ============================================================================
-- 1. CATÁLOGOS MAESTROS (lectura pública)
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.playa_catalogo (
    id_playa TEXT PRIMARY KEY,
    nombre_playa TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS public.sector_catalogo (
    id_sector TEXT PRIMARY KEY,
    id_playa TEXT NOT NULL REFERENCES public.playa_catalogo(id_playa),
    nombre_sector TEXT NOT NULL,
    area_m2 INTEGER NOT NULL CHECK (area_m2 > 0)
);
CREATE INDEX IF NOT EXISTS sector_catalogo_id_playa ON public.sector_catalogo (id_playa);

-- La app muestra solo nombre_herramienta ("Pinza 1"); qr_codigo es el código interno del QR
CREATE TABLE IF NOT EXISTS public.herramienta_catalogo (
    qr_codigo TEXT PRIMARY KEY,
    nombre_herramienta TEXT NOT NULL,
    estado TEXT NOT NULL DEFAULT 'Operativo' CHECK (estado IN ('Operativo', 'Dañado', 'Extraviado'))
);

CREATE TABLE IF NOT EXISTS public.tipo_basura_catalogo (
    id_tipo_basura TEXT PRIMARY KEY,
    nombre_visible TEXT NOT NULL,
    categoria_origen TEXT NOT NULL CHECK (categoria_origen IN ('Recreativa', 'Marítima', 'Higiene'))
);


-- ============================================================================
-- 2. TABLAS OPERATIVAS (solo se escriben mediante sincronizar_jornada)
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.jornada (
    id_registro TEXT PRIMARY KEY,
    -- Día en que se realizó la jornada (hora de Chile). Distinto de fecha_sincronizacion.
    fecha_jornada DATE NOT NULL,
    brigada TEXT NOT NULL CHECK (brigada IN ('Brigada A', 'Brigada B', 'Brigada C', 'Brigada D')),
    id_playa TEXT NOT NULL REFERENCES public.playa_catalogo(id_playa),
    nombre_playa TEXT NOT NULL,
    nombre_lider TEXT NOT NULL,
    voluntarios_csv TEXT NOT NULL,
    total_participantes INTEGER NOT NULL CHECK (total_participantes BETWEEN 2 AND 60),
    sectores_ids TEXT[] NOT NULL DEFAULT '{}',
    sectores_csv TEXT NOT NULL,
    area_total_m2 INTEGER NOT NULL,
    sin_herramientas BOOLEAN NOT NULL DEFAULT FALSE,
    hora_inicio_ms BIGINT NOT NULL,
    hora_fin_ms BIGINT NOT NULL,
    latitud_inicial DOUBLE PRECISION DEFAULT 0.0,
    longitud_inicial DOUBLE PRECISION DEFAULT 0.0,
    km_recorridos REAL NOT NULL DEFAULT 0.0 CHECK (km_recorridos BETWEEN 0 AND 30),
    url_foto_inicio TEXT,
    url_foto_limpio TEXT,
    url_foto_sacos TEXT,
    -- Motivo obligatorio cuando la foto se omitió (url null); null si hay foto
    motivo_omision_inicio TEXT,
    motivo_omision_limpio TEXT,
    motivo_omision_sacos TEXT,
    cantidad_sacos INTEGER NOT NULL CHECK (cantidad_sacos BETWEEN 1 AND 100),
    kg_basura_normal REAL NOT NULL DEFAULT 0.0,
    kg_basura_reciclable REAL NOT NULL DEFAULT 0.0,
    kg_totales REAL NOT NULL DEFAULT 0.0 CHECK (kg_totales > 0 AND kg_totales <= 2500),
    encargado_pesaje TEXT NOT NULL,
    destino_basura_normal TEXT NOT NULL,
    nro_guia_despacho TEXT,
    destino_basura_reciclable TEXT NOT NULL,
    fecha_sincronizacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((url_foto_inicio IS NULL) = (motivo_omision_inicio IS NOT NULL)),
    CHECK ((url_foto_limpio IS NULL) = (motivo_omision_limpio IS NOT NULL)),
    CHECK ((url_foto_sacos IS NULL) = (motivo_omision_sacos IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS jornada_fecha_playa ON public.jornada (fecha_jornada, id_playa);

CREATE TABLE IF NOT EXISTS public.participante_jornada (
    id BIGSERIAL PRIMARY KEY,
    id_registro TEXT NOT NULL REFERENCES public.jornada(id_registro) ON DELETE CASCADE,
    nombre TEXT NOT NULL CHECK (length(trim(nombre)) > 0),
    rol TEXT NOT NULL CHECK (rol IN ('LIDER', 'VOLUNTARIO'))
);

-- Mismo criterio que la app: sin mayúsculas ni espacios repetidos (BR-01)
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

CREATE TABLE IF NOT EXISTS public.basura_recolectada (
    id_registro TEXT NOT NULL REFERENCES public.jornada(id_registro) ON DELETE CASCADE,
    id_tipo_basura TEXT NOT NULL REFERENCES public.tipo_basura_catalogo(id_tipo_basura),
    nombre_visible TEXT NOT NULL,
    categoria_origen TEXT NOT NULL,
    cantidad INTEGER NOT NULL DEFAULT 0 CHECK (cantidad BETWEEN 0 AND 5000),
    PRIMARY KEY (id_registro, id_tipo_basura)
);

-- ISSUE-42: copia de solo lectura de cada jornada que la RPC rechazó, para que el
-- administrador vea qué falló sin pedir el teléfono. No cuenta en ningún reporte.
-- Un mismo motivo para la misma jornada se acumula en "intentos" (no duplica filas).
CREATE TABLE IF NOT EXISTS public.jornada_rechazada (
    id BIGSERIAL PRIMARY KEY,
    id_registro TEXT NOT NULL,              -- '' si el payload no traía uno válido
    motivo TEXT NOT NULL,
    payload JSONB,                          -- NULL si el payload superaba 100 kB
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
ALTER TABLE public.herramienta_catalogo ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tipo_basura_catalogo ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.jornada ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.participante_jornada ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.herramienta_jornada ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.basura_recolectada ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.jornada_rechazada ENABLE ROW LEVEL SECURITY;

-- Doble barrera: además de RLS, se quitan los privilegios que Supabase otorga por defecto
REVOKE ALL ON
    public.playa_catalogo, public.sector_catalogo, public.herramienta_catalogo, public.tipo_basura_catalogo,
    public.jornada, public.participante_jornada, public.herramienta_jornada, public.basura_recolectada,
    public.jornada_rechazada
FROM anon, authenticated;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;

-- Catálogos: solo lectura
GRANT SELECT ON public.playa_catalogo, public.sector_catalogo, public.herramienta_catalogo, public.tipo_basura_catalogo
TO anon, authenticated;

DROP POLICY IF EXISTS "Lectura publica de playas" ON public.playa_catalogo;
CREATE POLICY "Lectura publica de playas"
ON public.playa_catalogo FOR SELECT TO anon, authenticated USING (true);

DROP POLICY IF EXISTS "Lectura publica de sectores" ON public.sector_catalogo;
CREATE POLICY "Lectura publica de sectores"
ON public.sector_catalogo FOR SELECT TO anon, authenticated USING (true);

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
--         "id_registro": "REG-20261116-A1B2C3", "fecha_jornada": "2026-11-16",
--         "brigada": "Brigada A", "id_playa": "PLAYA-01",
--         "sectores_ids": ["SECTOR-01", "SECTOR-02"], "sin_herramientas": false,
--         "hora_inicio_ms": 0, "hora_fin_ms": 0,
--         "latitud_inicial": -33.5, "longitud_inicial": -71.6, "km_recorridos": 1.2,
--         "url_foto_inicio": "https://…/storage/v1/object/public/evidencias-fotos/…jpg" | null,
--         "url_foto_limpio": … | null, "url_foto_sacos": … | null,
--         "motivo_omision_inicio": null | "Sin batería", "motivo_omision_limpio": …, "motivo_omision_sacos": …,
--         "cantidad_sacos": 2, "kg_basura_normal": 20.5, "kg_basura_reciclable": 9.5,
--         "encargado_pesaje": "Ana",
--         "destino_basura_normal": "Entregado a camión DIMAO", "nro_guia_despacho": "123" | null,
--         "destino_basura_reciclable": "Entregado a Punto Limpio Municipal"
--       },
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
-- Campos que el servidor calcula y NO toma del payload (evita inconsistencias):
--   nombre_lider, voluntarios_csv, total_participantes (desde participantes),
--   nombre_playa (desde playa_catalogo), sectores_csv, area_total_m2 (desde
--   sector_catalogo, sin repetir sectores), kg_totales (normal + reciclable),
--   nombre_herramienta, nombre_visible y categoria_origen (desde los catálogos).
CREATE OR REPLACE FUNCTION public.sincronizar_jornada_interna(p jsonb)
RETURNS text
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
    j jsonb := p -> 'jornada';
    v_participantes jsonb := COALESCE(p -> 'participantes', '[]'::jsonb);
    v_herramientas jsonb := COALESCE(p -> 'herramientas', '[]'::jsonb);
    v_basura jsonb := COALESCE(p -> 'basura', '[]'::jsonb);

    v_id text;
    v_fecha date;
    v_hoy_chile date := (now() AT TIME ZONE 'America/Santiago')::date;
    v_fecha_inicio date;
    v_brigada text;
    v_id_playa text;
    v_nombre_playa text;
    v_sectores text[];
    v_sin_herramientas boolean;
    v_inicio bigint;
    v_fin bigint;
    v_lat double precision;
    v_lng double precision;
    v_km real;
    v_sacos integer;
    v_kg_normal real;
    v_kg_reciclable real;
    v_encargado text;
    v_destino_normal text;
    v_guia text;
    v_destino_reciclable text;
    v_url text;
    v_motivos text[] := ARRAY[NULL, NULL, NULL]::text[];
    v_i integer;
    v_tipos_foto constant text[] := ARRAY['inicio', 'limpio', 'sacos'];

    v_lider text;
    v_voluntarios_csv text;
    v_n_lideres integer;
    v_n_voluntarios integer;
    v_area integer;
    v_sectores_csv text;
    v_faltante text;
    v_insertadas integer;

    c_prefijo_foto constant text := '/storage/v1/object/public/evidencias-fotos/';
BEGIN
    -- ---------- Estructura ----------
    IF j IS NULL OR jsonb_typeof(j) <> 'object' THEN
        RAISE EXCEPTION 'Falta el objeto "jornada"' USING ERRCODE = '22023';
    END IF;
    IF jsonb_typeof(v_participantes) <> 'array'
       OR jsonb_typeof(v_herramientas) <> 'array'
       OR jsonb_typeof(v_basura) <> 'array' THEN
        RAISE EXCEPTION '"participantes", "herramientas" y "basura" deben ser listas' USING ERRCODE = '22023';
    END IF;

    v_id := j ->> 'id_registro';
    IF v_id IS NULL OR v_id !~ '^REG-[0-9]{8}-[A-Z0-9]{4,12}$' THEN
        RAISE EXCEPTION 'id_registro inválido: %', v_id USING ERRCODE = '22023';
    END IF;

    -- ---------- Idempotencia: una jornada ya sincronizada no se modifica ----------
    IF EXISTS (SELECT 1 FROM jornada WHERE id_registro = v_id) THEN
        RETURN 'YA_EXISTIA';
    END IF;

    -- ---------- Cabecera: brigada, fecha y playa ----------
    v_brigada := j ->> 'brigada';
    IF v_brigada IS NULL OR v_brigada NOT IN ('Brigada A', 'Brigada B', 'Brigada C', 'Brigada D') THEN
        RAISE EXCEPTION 'brigada inválida: %', v_brigada USING ERRCODE = '22023';
    END IF;

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
    SELECT nombre_playa INTO v_nombre_playa FROM playa_catalogo WHERE id_playa = v_id_playa;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Playa desconocida: %', v_id_playa USING ERRCODE = '22023';
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
    SELECT SUM(area_m2), string_agg(nombre_sector, ', ' ORDER BY id_sector)
      INTO v_area, v_sectores_csv
      FROM sector_catalogo WHERE id_sector = ANY (v_sectores);

    -- ---------- Horas, GPS y topes ----------
    v_inicio := (j ->> 'hora_inicio_ms')::bigint;
    v_fin := (j ->> 'hora_fin_ms')::bigint;
    IF v_inicio IS NULL OR v_fin IS NULL OR v_inicio <= 0 OR v_fin < v_inicio
       OR v_fin - v_inicio > 12 * 3600 * 1000 THEN
        RAISE EXCEPTION 'Horas de inicio/fin inválidas (máximo 12 h de limpieza)' USING ERRCODE = '22023';
    END IF;
    IF to_timestamp(v_inicio / 1000.0) > now() + interval '1 day' THEN
        RAISE EXCEPTION 'La hora de inicio está en el futuro (revisa la hora del teléfono)' USING ERRCODE = '22023';
    END IF;
    -- La limpieza empieza el mismo día en que se creó la jornada (o el siguiente, si cruzó medianoche)
    v_fecha_inicio := (to_timestamp(v_inicio / 1000.0) AT TIME ZONE 'America/Santiago')::date;
    IF v_fecha_inicio NOT BETWEEN v_fecha AND v_fecha + 1 THEN
        RAISE EXCEPTION 'La hora de inicio (%) no corresponde a fecha_jornada (%)', v_fecha_inicio, v_fecha
            USING ERRCODE = '22023';
    END IF;

    v_lat := COALESCE((j ->> 'latitud_inicial')::double precision, 0);
    v_lng := COALESCE((j ->> 'longitud_inicial')::double precision, 0);
    IF v_lat NOT BETWEEN -90 AND 90 OR v_lng NOT BETWEEN -180 AND 180 THEN
        RAISE EXCEPTION 'Coordenadas inválidas' USING ERRCODE = '22023';
    END IF;

    v_km := COALESCE((j ->> 'km_recorridos')::real, 0);
    IF v_km < 0 OR v_km > 30 THEN
        RAISE EXCEPTION 'km_recorridos fuera de rango (0 a 30): %', v_km USING ERRCODE = '22023';
    END IF;

    -- ---------- Pesaje (BR-06, Ley 20.949: promedio <= 25 kg por saco) ----------
    v_sacos := (j ->> 'cantidad_sacos')::integer;
    v_kg_normal := COALESCE((j ->> 'kg_basura_normal')::real, 0);
    v_kg_reciclable := COALESCE((j ->> 'kg_basura_reciclable')::real, 0);
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

    -- ---------- Fotos: URL de este bucket y de esta jornada, u omitida con motivo ----------
    FOR v_i IN 1..3 LOOP
        v_url := j ->> ('url_foto_' || v_tipos_foto[v_i]);
        IF v_url IS NOT NULL THEN
            IF position(c_prefijo_foto IN v_url) = 0 OR v_url !~ ('/' || v_id || '_[123]_[a-z]+\.jpg$') THEN
                RAISE EXCEPTION 'URL de foto inválida: %', v_url USING ERRCODE = '22023';
            END IF;
            -- Con foto no se guarda motivo
        ELSE
            v_motivos[v_i] := regexp_replace(trim(COALESCE(j ->> ('motivo_omision_' || v_tipos_foto[v_i]), '')), '\s+', ' ', 'g');
            IF v_motivos[v_i] = '' OR length(v_motivos[v_i]) > 120 THEN
                RAISE EXCEPTION 'Falta el motivo para omitir la foto de %', v_tipos_foto[v_i] USING ERRCODE = '22023';
            END IF;
        END IF;
    END LOOP;

    -- ---------- Participantes (BR-01/BR-02) ----------
    IF EXISTS (
        SELECT 1 FROM jsonb_array_elements(v_participantes) e
        WHERE jsonb_typeof(e) <> 'object'
           OR trim(COALESCE(e ->> 'nombre', '')) = ''
           OR length(trim(e ->> 'nombre')) > 80
           OR COALESCE(e ->> 'rol', '') NOT IN ('LIDER', 'VOLUNTARIO')
    ) THEN
        RAISE EXCEPTION 'Participante inválido (nombre obligatorio, rol LIDER o VOLUNTARIO)' USING ERRCODE = '22023';
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
    -- Se valida aquí (HTTP 400) en vez de dejar que el índice único responda 409,
    -- que la app podría confundir con "ya existe"
    IF EXISTS (
        SELECT 1 FROM jsonb_array_elements(v_participantes) e
        GROUP BY lower(regexp_replace(trim(e ->> 'nombre'), '\s+', ' ', 'g'))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Hay nombres de participantes repetidos' USING ERRCODE = '22023';
    END IF;
    SELECT regexp_replace(trim(e ->> 'nombre'), '\s+', ' ', 'g') INTO v_lider
      FROM jsonb_array_elements(v_participantes) e WHERE e ->> 'rol' = 'LIDER';
    SELECT string_agg(regexp_replace(trim(e ->> 'nombre'), '\s+', ' ', 'g'), ', ' ORDER BY ord)
      INTO v_voluntarios_csv
      FROM jsonb_array_elements(v_participantes) WITH ORDINALITY AS t(e, ord)
     WHERE e ->> 'rol' = 'VOLUNTARIO';

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
        id_registro, fecha_jornada, brigada, id_playa, nombre_playa,
        nombre_lider, voluntarios_csv, total_participantes,
        sectores_ids, sectores_csv, area_total_m2, sin_herramientas, hora_inicio_ms, hora_fin_ms,
        latitud_inicial, longitud_inicial, km_recorridos,
        url_foto_inicio, url_foto_limpio, url_foto_sacos,
        motivo_omision_inicio, motivo_omision_limpio, motivo_omision_sacos,
        cantidad_sacos, kg_basura_normal, kg_basura_reciclable, kg_totales,
        encargado_pesaje, destino_basura_normal, nro_guia_despacho, destino_basura_reciclable
    ) VALUES (
        v_id, v_fecha, v_brigada, v_id_playa, v_nombre_playa,
        v_lider, v_voluntarios_csv, v_n_lideres + v_n_voluntarios,
        v_sectores, v_sectores_csv, v_area, v_sin_herramientas, v_inicio, v_fin,
        v_lat, v_lng, v_km,
        j ->> 'url_foto_inicio', j ->> 'url_foto_limpio', j ->> 'url_foto_sacos',
        v_motivos[1], v_motivos[2], v_motivos[3],
        v_sacos, v_kg_normal, v_kg_reciclable, v_kg_normal + v_kg_reciclable,
        v_encargado, v_destino_normal, v_guia, v_destino_reciclable
    )
    ON CONFLICT (id_registro) DO NOTHING;
    GET DIAGNOSTICS v_insertadas = ROW_COUNT;
    IF v_insertadas = 0 THEN
        RETURN 'YA_EXISTIA';
    END IF;

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
              AND j2.hora_fin_ms > v_fin
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
    c_max_payload constant integer := 100000;   -- bytes guardados por rechazo
    c_max_pendientes constant integer := 5000;  -- tope de filas sin resolver (anti-spam con la clave pública)
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
-- Eliminadas en la v4 (31-10-2026). Se rediseñan junto con la planilla de la
-- clienta (ISSUE-30). Mientras tanto, consultar las tablas con la clave secreta.
DROP VIEW IF EXISTS public.vista_reporte_jornadas;
DROP VIEW IF EXISTS public.vista_densidad_diaria;


-- ============================================================================
-- 6. POBLADO INICIAL (SEED) DE CATÁLOGOS MAESTROS
-- ============================================================================
-- Para agregar una playa: insertar la playa y luego sus sectores con sus m².
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
    -- Mismo formato que JornadaSyncRepository: REG-AAAAMMDD-XXXXXX_{1|2|3}_{tipo}.jpg
    AND name ~ '^REG-[0-9]{8}-[A-Z0-9]{4,12}_[123]_(inicio|limpio|sacos)\.jpg$'
);
-- No se crea política DELETE: con la clave pública no se pueden borrar fotos.


-- ============================================================================
-- 8. VERIFICACIÓN MANUAL (ISSUE-20 / ISSUE-31) — con la clave PÚBLICA, desde la terminal
-- ============================================================================
-- export SUPABASE_URL=https://<proyecto>.supabase.co
-- export KEY=<clave publicable sb_publishable_…>
--
-- 1) Catálogos visibles (200 con datos), incluida la playa:
--    curl "$SUPABASE_URL/rest/v1/playa_catalogo?select=*" -H "apikey: $KEY"
--    curl "$SUPABASE_URL/rest/v1/sector_catalogo?select=*" -H "apikey: $KEY"
-- 2) Jornadas NO visibles (401/403 "permission denied"):
--    curl "$SUPABASE_URL/rest/v1/jornada?select=*" -H "apikey: $KEY"
-- 3) No se puede cambiar el inventario (401/403):
--    curl -X PATCH "$SUPABASE_URL/rest/v1/herramienta_catalogo?qr_codigo=eq.INV-02-PINZA-01" \
--      -H "apikey: $KEY" -H "Content-Type: application/json" -d '{"estado":"Extraviado"}'
-- 4) RPC válida → "INSERTADA"; repetirla → "YA_EXISTIA".
--    ⚠️ fecha_jornada debe estar entre hoy−60 días y hoy+1: si el ejemplo quedó
--    viejo, cambia fecha_jornada, hora_inicio_ms y hora_fin_ms a una fecha reciente.
--    curl -X POST "$SUPABASE_URL/rest/v1/rpc/sincronizar_jornada" \
--      -H "apikey: $KEY" -H "Content-Type: application/json" -d @docs/rpc_ejemplo.json
-- 5) Subir dos veces la misma foto: la 1ª 200, la 2ª 400/409 "already exists":
--    curl -X POST "$SUPABASE_URL/storage/v1/object/evidencias-fotos/REG-20261030-TEST01_1_inicio.jpg" \
--      -H "apikey: $KEY" -H "Authorization: Bearer $KEY" -H "Content-Type: image/jpeg" --data-binary @foto.jpg
--    Con -H "x-upsert: true" también debe fallar.
-- 6) Jornada inválida → HTTP 200 "RECHAZADA: …" y queda en jornada_rechazada (ISSUE-42).
--    Ej.: rpc_ejemplo.json con "brigada": "Brigada Z" → "RECHAZADA: brigada inválida: Brigada Z".
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
