-- ============================================================================
-- ⚠️  REINICIO DESTRUCTIVO A LA v4 — "DEJA TU PLAYA LIMPIA"
-- ============================================================================
-- Decisión del 31-10-2026 (ISSUE-31): los datos actuales son de prueba, así que
-- en vez de migrar se borra todo y se vuelve a crear con `supabase_schema.sql`.
--
-- BORRA TODAS LAS JORNADAS, PARTICIPANTES, HERRAMIENTAS, CONTEOS Y CATÁLOGOS.
-- No borra las fotos del bucket (Storage no se vacía con SQL): si se quieren
-- eliminar, hacerlo desde el panel Storage → evidencias-fotos.
--
-- Uso (SQL Editor de Supabase, una sola vez):
--   1. Ejecutar este archivo.
--   2. Ejecutar docs/supabase_schema.sql.
--   3. Correr la verificación de la sección 8 de supabase_schema.sql.
--
-- Una vez que el piloto tenga datos reales, NO volver a ejecutar este archivo.
-- ============================================================================

-- Vistas de reportería: se eliminan por ahora (se rediseñan con la planilla, ISSUE-30)
DROP VIEW IF EXISTS public.vista_reporte_jornadas;
DROP VIEW IF EXISTS public.vista_densidad_diaria;

DROP FUNCTION IF EXISTS public.sincronizar_jornada(jsonb);
DROP FUNCTION IF EXISTS public.sincronizar_jornada_interna(jsonb);

DROP TABLE IF EXISTS public.jornada_rechazada CASCADE;
DROP TABLE IF EXISTS public.basura_recolectada CASCADE;
DROP TABLE IF EXISTS public.herramienta_jornada CASCADE;
DROP TABLE IF EXISTS public.participante_jornada CASCADE;
DROP TABLE IF EXISTS public.jornada CASCADE;
DROP TABLE IF EXISTS public.usuario CASCADE;
DROP TABLE IF EXISTS public.sector_catalogo CASCADE;
DROP TABLE IF EXISTS public.herramienta_catalogo CASCADE;
DROP TABLE IF EXISTS public.tipo_basura_catalogo CASCADE;
DROP TABLE IF EXISTS public.playa_catalogo CASCADE;
