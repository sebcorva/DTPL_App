/**
 * ============================================================================
 * CONECTOR AUTOMÁTICO SUPABASE -> GOOGLE SHEETS ("DEJA TU PLAYA LIMPIA")
 * Iniciativa Logística Verde - Playa Grande de Cartagena
 * ============================================================================
 *
 * INSTRUCCIONES DE INSTALACIÓN EN GOOGLE SHEETS (SIN HARDCODEAR CREDENCIALES):
 * 1. Abre la planilla "Logistica_Verde_Piloto" en Google Sheets.
 * 2. Ve a "Extensiones" > "Apps Script".
 * 3. En el menú izquierdo de Apps Script, haz clic en el ícono de engranaje ("Configuración del proyecto")
 *    y baja hasta "Propiedades de secuencia de comandos" (Script Properties).
 * 4. Agrega estas 2 propiedades de entorno:
 *    - Propiedad: SUPABASE_URL             | Valor: https://<tu-proyecto>.supabase.co
 *    - Propiedad: SUPABASE_PUBLISHABLE_KEY | Valor: tu_clave_publica_de_supabase
 * 5. Ejecuta una vez la función `instalarTriggersAutomaticos()` para activar:
 *    - Actualización automática de REGISTRO_JORNADAS e INVENTARIO.
 *    - Ping Keep-Alive cada 24 horas para evitar que el plan gratuito de
 *      Supabase entre en pausa por 7 días de inactividad.
 */

function getSupabaseConfig() {
  const props = PropertiesService.getScriptProperties();
  const url = (props.getProperty("SUPABASE_URL") || "").replace(/\/$/, "");
  const key = props.getProperty("SUPABASE_PUBLISHABLE_KEY") || props.getProperty("SUPABASE_ANON_KEY") || "";
  if (!url || !key) {
    throw new Error("Faltan SUPABASE_URL o SUPABASE_PUBLISHABLE_KEY en Propiedades de secuencia de comandos (Script Properties).");
  }
  return { url, key };
}

const SHEET_REGISTRO_JORNADAS = "REGISTRO_JORNADAS";
const SHEET_INVENTARIO = "INVENTARIO";

/**
 * Crea un menú personalizado en Google Sheets para sincronizar con 1 clic.
 */
function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu("🌊 Logística Verde")
    .addItem("🔄 Sincronizar Jornadas e Inventario desde Supabase", "sincronizarTodoDesdeSupabase")
    .addItem("💓 Ejecutar Ping Keep-Alive Supabase", "keepAliveSupabasePing")
    .addSeparator()
    .addItem("⚙️ Instalar Triggers Automáticos (Keep-Alive + Sync)", "instalarTriggersAutomaticos")
    .addToUi();
}

/**
 * Función principal: Descarga `vista_reporte_jornadas` y `herramienta_catalogo`
 * desde Supabase PostgreSQL y actualiza las pestañas de Google Sheets.
 */
function sincronizarTodoDesdeSupabase() {
  sincronizarJornadas();
  sincronizarInventarioHerramientas();
}

/**
 * Consulta `public.vista_reporte_jornadas` y vuelca los registros en `REGISTRO_JORNADAS`.
 */
function sincronizarJornadas() {
  const { url } = getSupabaseConfig();
  const endpoint = `${url}/rest/v1/vista_reporte_jornadas?select=*`;
  const registros = fetchSupabaseJson(endpoint);
  if (!registros) return;

  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(SHEET_REGISTRO_JORNADAS);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_REGISTRO_JORNADAS);
  }

  const headers = [
    "ID Registro",
    "Cuadrilla",
    "ID Líder (FK)",
    "Nombre Líder",
    "Voluntarios",
    "Sectores Intervenidos",
    "Área Total (m²)",
    "Horas Trabajadas",
    "Km Recorridos",
    "Cantidad Sacos",
    "Kg Basura Normal",
    "Kg Basura Reciclable",
    "Kg Totales",
    "Destino Basura Normal",
    "N° Guía Despacho",
    "Destino Basura Reciclable",
    "Herramientas y Estado Final",
    "Desglose Basura Recolectada",
    "URL Foto 1 (Inicio Sucio)",
    "URL Foto 2 (Sector Limpio)",
    "URL Foto 3 (Sacos Acopiados)",
    "Fecha Sincronización"
  ];

  sheet.clearContents();
  sheet.getRange(1, 1, 1, headers.length).setValues([headers]).setFontWeight("bold");

  if (registros.length === 0) return;

  const rows = registros.map(r => [
    r.id_registro || "",
    r.cuadrilla || "",
    r.id_usuario || "",
    r.nombre_lider || "",
    r.voluntarios_csv || "",
    r.sectores_csv || "",
    Number(r.area_total_m2 || 0),
    Number(r.horas_trabajadas || 0),
    Number(r.km_recorridos || 0),
    Number(r.cantidad_sacos || 0),
    Number(r.kg_basura_normal || 0),
    Number(r.kg_basura_reciclable || 0),
    Number(r.kg_totales || 0),
    r.destino_basura_normal || "",
    r.nro_guia_despacho || "Sin guía",
    r.destino_basura_reciclable || "",
    r.herramientas_y_estado || "",
    r.desglose_basura_recolectada || "",
    r.url_foto_inicio || "",
    r.url_foto_limpio || "",
    r.url_foto_sacos || "",
    r.fecha_sincronizacion || ""
  ]);

  sheet.getRange(2, 1, rows.length, headers.length).setValues(rows);
}

/**
 * Consulta `public.herramienta_catalogo` y actualiza el estado operativo en `INVENTARIO`.
 */
function sincronizarInventarioHerramientas() {
  const { url } = getSupabaseConfig();
  const endpoint = `${url}/rest/v1/herramienta_catalogo?select=*&order=qr_codigo.asc`;
  const herramientas = fetchSupabaseJson(endpoint);
  if (!herramientas) return;

  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(SHEET_INVENTARIO);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_INVENTARIO);
  }

  const headers = ["Código QR", "Nombre Herramienta", "Estado Actual", "Última Verificación"];
  sheet.clearContents();
  sheet.getRange(1, 1, 1, headers.length).setValues([headers]).setFontWeight("bold");

  if (herramientas.length === 0) return;

  const timestamp = new Date().toISOString();
  const rows = herramientas.map(h => [
    h.qr_codigo || "",
    h.nombre_herramienta || "",
    h.estado || "Operativo",
    timestamp
  ]);

  sheet.getRange(2, 1, rows.length, headers.length).setValues(rows);
}

/**
 * Ping Keep-Alive: Realiza una lectura ligera a Supabase y actualiza las hojas
 * para evitar que el proyecto gratuito de Supabase se pause tras 7 días sin actividad.
 */
function keepAliveSupabasePing() {
  const { url } = getSupabaseConfig();
  const endpoint = `${url}/rest/v1/sector_catalogo?select=id_sector&limit=1`;
  fetchSupabaseJson(endpoint);
  sincronizarTodoDesdeSupabase();
}

/**
 * Configura un Trigger basado en tiempo que ejecuta `keepAliveSupabasePing` cada 1 día.
 */
function instalarTriggersAutomaticos() {
  const triggers = ScriptApp.getProjectTriggers();
  triggers.forEach(t => {
    if (t.getHandlerFunction() === "keepAliveSupabasePing") {
      ScriptApp.deleteTrigger(t);
    }
  });

  ScriptApp.newTrigger("keepAliveSupabasePing")
    .timeBased()
    .everyDays(1)
    .atHour(8)
    .create();
}

/**
 * Helper HTTP GET contra PostgREST de Supabase.
 */
function fetchSupabaseJson(url) {
  const { key } = getSupabaseConfig();
  const headers = {
    "apikey": key,
    "Accept": "application/json"
  };
  if (key.startsWith("eyJ")) {
    headers["Authorization"] = `Bearer ${key}`;
  }
  const options = {
    method: "get",
    headers: headers,
    muteHttpExceptions: true
  };

  const response = UrlFetchApp.fetch(url, options);
  const code = response.getResponseCode();
  if (code >= 200 && code < 300) {
    return JSON.parse(response.getContentText());
  } else {
    Logger.log(`Error HTTP ${code} desde Supabase: ${response.getContentText()}`);
    return null;
  }
}
