/**
 * ============================================================================
 * CONECTOR SUPABASE -> GOOGLE SHEETS v5 ("DEJA TU PLAYA LIMPIA")
 * Iniciativa Logística Verde — Planilla de registro y KPI (ISSUE-27 / ISSUE-30)
 * ============================================================================
 *
 * QUÉ HACE
 *   - crearEstructura(): arma las pestañas Inicio, KPI, Jornadas, Residuos, Herramientas y
 *     Listas (oculta) con encabezados, formatos, listas desplegables y fórmulas del KPI.
 *     Se puede volver a ejecutar: no duplica nada, no borra datos ni cambia los filtros del KPI
 *     (sí vuelve a aplicar títulos, encabezados, formatos y fórmulas).
 *   - sincronizar(): lee las vistas de Supabase (vista_jornada, vista_residuos,
 *     vista_herramientas, vista_area_diaria) y los catálogos de playas y brigadas, y reemplaza
 *     los datos de cada pestaña desde la fila 6. Primero descarga todo y recién después escribe:
 *     si Supabase falla, la planilla queda como estaba.
 *   - instalarSincronizacionDiaria(): sincroniza todos los días a las 8:00. Esa lectura diaria
 *     también es el keep-alive (el plan gratuito de Supabase se pausa tras 7 días sin uso).
 *
 * INSTALACIÓN
 *   1. Crea una planilla nueva en Google Sheets → Extensiones → Apps Script.
 *   2. Pega este archivo completo en Código.gs y guarda.
 *   3. Configuración del proyecto (engranaje) → Propiedades de secuencia de comandos:
 *        SUPABASE_URL         = https://<proyecto>.supabase.co
 *        SUPABASE_SECRET_KEY  = sb_secret_…   (la clave SECRETA: las vistas no se leen con la pública)
 *   4. Ejecuta crearEstructura() (pide autorización la primera vez), luego sincronizar()
 *      y por último instalarSincronizacionDiaria().
 *   5. Al recargar la planilla aparece el menú "Logística Verde".
 *
 * ⚠️ SEGURIDAD
 *   - La clave secreta salta RLS y puede escribir en toda la base. Nunca va en este archivo
 *     ni en el repositorio: solo en las Propiedades del script.
 *   - Quien tenga permiso de EDICIÓN en la planilla puede abrir Apps Script y leer esas
 *     propiedades. Comparte la planilla con los demás solo como LECTOR o COMENTADOR.
 *   - Inyección de fórmulas (ISSUE-58): todo texto que viene de Supabase se escribe con un
 *     apóstrofo inicial, así "=IMPORTXML(…)" queda como texto. Las únicas fórmulas que escribe
 *     el script son las del KPI; los enlaces a fotos son texto con vínculo (ruta validada).
 */

// ============================================================================
// 1. CONFIGURACIÓN
// ============================================================================
const CFG = {
  ZONA: "America/Santiago",
  BUCKET: "evidencias-fotos",
  FILA_ENCABEZADO: 5,
  FILA_DATOS: 6,
  // Filas por consulta. Debe ser ≤ "Max rows" de la API de Supabase (1.000 por defecto).
  PAGINA: 1000,
  // Filas de la tabla "Comparación por brigada" del KPI.
  MAX_BRIGADAS_KPI: 8,
  COLOR_ENCABEZADO: "#1B5E20",
  COLOR_TITULO: "#1B5E20",
};

const HOJA = {
  INICIO: "Inicio",
  KPI: "KPI",
  JORNADAS: "Jornadas",
  RESIDUOS: "Residuos",
  HERRAMIENTAS: "Herramientas",
  LISTAS: "Listas",
};

function getSupabaseConfig() {
  const props = PropertiesService.getScriptProperties();
  const url = (props.getProperty("SUPABASE_URL") || "").trim().replace(/\/+$/, "");
  const key = (props.getProperty("SUPABASE_SECRET_KEY") || "").trim();
  if (!url || !key) {
    throw new Error("Faltan SUPABASE_URL o SUPABASE_SECRET_KEY en las Propiedades del script.");
  }
  if (!/^https:\/\/[^\s"]+$/.test(url)) {
    throw new Error("SUPABASE_URL debe empezar con https:// (ej. https://<proyecto>.supabase.co).");
  }
  if (key.startsWith("sb_publishable_")) {
    throw new Error("SUPABASE_SECRET_KEY tiene la clave PÚBLICA. Las vistas solo se leen con la clave secreta (sb_secret_…).");
  }
  return { url, key };
}

// ============================================================================
// 2. CONVERSIÓN DE VALORES (fechas reales, números y texto seguro)
// ============================================================================

/** Texto de Supabase → celda de texto. El apóstrofo inicial impide que se ejecute como fórmula. */
function texto(v) {
  if (v === null || v === undefined || v === "") return "";
  return "'" + String(v);
}

function numero(v) {
  if (v === null || v === undefined || v === "") return "";
  const n = Number(v);
  return isNaN(n) ? "" : n;
}

/** Número de serie de Sheets (días desde 1899-12-30): no depende de la zona horaria del script. */
function serial(y, mo, d, h, mi, s) {
  return (Date.UTC(y, mo - 1, d, h || 0, mi || 0, s || 0) - Date.UTC(1899, 11, 30)) / 86400000;
}

/** "2026-11-16" (DATE de Postgres) → fecha de Sheets. */
function fecha(v) {
  if (!v) return "";
  const p = String(v).slice(0, 10).split("-").map(Number);
  return p.length === 3 && !p.some(isNaN) ? serial(p[0], p[1], p[2]) : "";
}

/** TIMESTAMPTZ (ISO con zona) → fecha y hora de Chile como fecha de Sheets. */
function fechaHora(v) {
  if (!v) return "";
  const d = new Date(v);
  if (isNaN(d.getTime())) return "";
  return serialChile(d);
}

function serialChile(d) {
  const p = Utilities.formatDate(d, CFG.ZONA, "yyyy-MM-dd-HH-mm-ss").split("-").map(Number);
  return serial(p[0], p[1], p[2], p[3], p[4], p[5]);
}

/**
 * Foto: "Ver foto" con el enlace incrustado (texto enriquecido, sin fórmula HYPERLINK: una fórmula
 * en inglés en una planilla en español quedaba como texto y el enlace terminaba en «","Ver foto"»).
 * Si se omitió: "Omitida: motivo" sin enlace. Devuelve { texto, url } (ver escribirDatos).
 */
function foto(ruta, motivo, base) {
  if (ruta) {
    // La RPC valida la ruta (REG-…_1_inicio.jpg); se vuelve a validar porque arma una URL.
    if (!/^[A-Za-z0-9._-]+$/.test(ruta)) return { texto: "Ruta inválida", url: null };
    return { texto: "Ver foto", url: `${base}/storage/v1/object/public/${CFG.BUCKET}/${ruta}` };
  }
  return { texto: motivo ? "Omitida: " + motivo : "", url: null };
}

// ============================================================================
// 3. DEFINICIÓN DE LAS PESTAÑAS DE DATOS
//    t = encabezado, v = valor desde la fila de la vista, f = formato, w = ancho (px)
// ============================================================================
const TABLAS = {
  [HOJA.JORNADAS]: {
    titulo: "DATOS | Jornadas (una fila por brigada y jornada)",
    vista: "vista_jornada",
    orden: "fecha_jornada.asc,hora_inicio.asc,id_registro.asc",
    columnas: [
      { t: "ID jornada", v: r => texto(r.id_registro), w: 200 },
      { t: "Fecha", v: r => fecha(r.fecha_jornada), f: "dd-mm-yyyy", w: 90 },
      { t: "Playa", v: r => texto(r.nombre_playa), w: 180 },
      { t: "Sectores", v: r => texto(r.sectores), w: 260 },
      { t: "Brigada", v: r => texto(r.nombre_brigada), w: 90 },
      { t: "Líder", v: r => texto(r.nombre_lider), w: 160 },
      { t: "N.º participantes", v: r => numero(r.total_participantes), f: "0", w: 100 },
      { t: "Hora inicio", v: r => fechaHora(r.hora_inicio), f: "hh:mm", w: 80 },
      { t: "Hora término", v: r => fechaHora(r.hora_fin), f: "hh:mm", w: 80 },
      { t: "Duración (h)", v: r => numero(r.duracion_h), f: "0.00", w: 85 },
      { t: "Área (m²)", v: r => numero(r.area_total_m2), f: "#,##0", w: 85 },
      { t: "Km recorridos", v: r => numero(r.km_recorridos), f: "0.00", w: 95 },
      { t: "Voluntario-horas", v: r => numero(r.voluntario_horas), f: "0.00", w: 110 },
      { t: "Sacos", v: r => numero(r.cantidad_sacos), f: "0", w: 60 },
      { t: "Kg normal", v: r => numero(r.kg_basura_normal), f: "0.00", w: 85 },
      { t: "Kg reciclable", v: r => numero(r.kg_basura_reciclable), f: "0.00", w: 90 },
      { t: "Kg totales", v: r => numero(r.kg_totales), f: "0.00", w: 85 },
      { t: "Encargado de pesaje", v: r => texto(r.encargado_pesaje), w: 160 },
      { t: "Destino basura normal", v: r => texto(r.destino_basura_normal), w: 200 },
      { t: "N.º guía despacho", v: r => texto(r.nro_guia_despacho), w: 110 },
      { t: "Destino basura reciclable", v: r => texto(r.destino_basura_reciclable), w: 220 },
      { t: "Foto inicio", enlace: true, v: (r, b) => foto(r.ruta_foto_inicio, r.motivo_omision_inicio, b), w: 150 },
      { t: "Foto limpio", enlace: true, v: (r, b) => foto(r.ruta_foto_limpio, r.motivo_omision_limpio, b), w: 150 },
      { t: "Foto sacos", enlace: true, v: (r, b) => foto(r.ruta_foto_sacos, r.motivo_omision_sacos, b), w: 150 },
    ],
  },
  [HOJA.RESIDUOS]: {
    titulo: "DATOS | Residuos (una fila por tipo de residuo y jornada; sin cantidades 0)",
    vista: "vista_residuos",
    orden: "fecha_jornada.asc,id_registro.asc,id_tipo_basura.asc",
    columnas: [
      { t: "ID jornada", v: r => texto(r.id_registro), w: 200 },
      { t: "Fecha", v: r => fecha(r.fecha_jornada), f: "dd-mm-yyyy", w: 90 },
      { t: "Playa", v: r => texto(r.nombre_playa), w: 180 },
      { t: "Brigada", v: r => texto(r.nombre_brigada), w: 90 },
      { t: "Tipo de residuo", v: r => texto(r.nombre_visible), w: 280 },
      { t: "Categoría de origen", v: r => texto(r.categoria_origen), w: 130 },
      { t: "Cantidad", v: r => numero(r.cantidad), f: "#,##0", w: 80 },
    ],
  },
  [HOJA.HERRAMIENTAS]: {
    titulo: "DATOS | Herramientas (una fila por herramienta que llevó una brigada)",
    vista: "vista_herramientas",
    orden: "fecha_jornada.asc,id_registro.asc,qr_codigo.asc",
    columnas: [
      { t: "ID jornada", v: r => texto(r.id_registro), w: 200 },
      { t: "Fecha", v: r => fecha(r.fecha_jornada), f: "dd-mm-yyyy", w: 90 },
      { t: "Playa", v: r => texto(r.nombre_playa), w: 180 },
      { t: "Brigada", v: r => texto(r.nombre_brigada), w: 90 },
      { t: "Herramienta_ID", v: r => texto(r.qr_codigo), w: 170 },
      { t: "Nombre", v: r => texto(r.nombre_herramienta), w: 110 },
      { t: "Estado final", v: r => texto(r.estado_final), w: 100 },
    ],
  },
};

/** Listas (oculta): filtros del KPI y área diaria sin sectores repetidos. Datos desde la fila 2. */
const LISTAS = {
  encabezados: [["Playas (filtro)", "Brigadas (filtro)", "", "Fecha", "Playa", "Área m² (sin repetir sectores)"]],
  colPlayas: "A",
  colBrigadas: "B",
  colAreaFecha: "D",
  colAreaPlaya: "E",
  colAreaM2: "F",
};

// ============================================================================
// 4. MENÚ Y DISPARADOR DIARIO
// ============================================================================
function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu("Logística Verde")
    .addItem("Sincronizar ahora", "sincronizar")
    .addSeparator()
    .addItem("Crear / reparar estructura", "crearEstructura")
    .addItem("Instalar sincronización diaria (8:00)", "instalarSincronizacionDiaria")
    .addToUi();
}

function instalarSincronizacionDiaria() {
  const antiguos = ["sincronizar", "keepAliveSupabasePing", "sincronizarTodoDesdeSupabase"];
  ScriptApp.getProjectTriggers()
    .filter(t => antiguos.indexOf(t.getHandlerFunction()) >= 0)
    .forEach(t => ScriptApp.deleteTrigger(t));
  ScriptApp.newTrigger("sincronizar").timeBased().everyDays(1).atHour(8).inTimezone(CFG.ZONA).create();
  avisar("Sincronización diaria instalada (8:00, hora de Chile).");
}

/** Compatibilidad: un disparador antiguo que aún llame a esta función sigue funcionando. */
function keepAliveSupabasePing() {
  sincronizar();
}

function avisar(msg) {
  try {
    SpreadsheetApp.getActiveSpreadsheet().toast(msg, "Logística Verde", 8);
  } catch (e) {
    // Sin interfaz (disparador): solo registro.
  }
  Logger.log(msg);
}

// ============================================================================
// 5. SINCRONIZACIÓN
// ============================================================================
function sincronizar() {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(30000)) throw new Error("Ya hay una sincronización en curso. Intenta en un minuto.");
  try {
    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const faltantes = Object.keys(HOJA).map(k => HOJA[k]).filter(n => !ss.getSheetByName(n));
    if (faltantes.length) {
      throw new Error("Faltan pestañas (" + faltantes.join(", ") + "). Ejecuta primero «Crear / reparar estructura».");
    }
    const { url } = getSupabaseConfig();

    // 1) Descargar todo antes de escribir: si algo falla, la planilla no queda a medias.
    const datos = {};
    Object.keys(TABLAS).forEach(h => {
      datos[h] = leerTodo(TABLAS[h].vista, "*", TABLAS[h].orden);
    });
    // Incluye playas y brigadas inactivas para poder filtrar jornadas antiguas.
    const playas = unicos(leerTodo("playa_catalogo", "nombre_playa", "nombre_playa.asc").map(r => r.nombre_playa));
    const brigadas = unicos(leerTodo("brigada_catalogo", "nombre_brigada", "nombre_brigada.asc").map(r => r.nombre_brigada));
    const area = leerTodo("vista_area_diaria", "fecha_jornada,id_playa,nombre_playa,area_m2", "fecha_jornada.asc,id_playa.asc");

    // 2) Escribir.
    Object.keys(TABLAS).forEach(h => {
      const def = TABLAS[h];
      const filas = datos[h].map(r => def.columnas.map(c => c.v(r, url)));
      escribirDatos(ss.getSheetByName(h), filas, def.columnas);
    });
    escribirListas(ss.getSheetByName(HOJA.LISTAS), playas, brigadas, area);
    ss.getSheetByName(HOJA.KPI).getRange("E3").setNumberFormat("dd-mm-yyyy hh:mm").setValue(serialChile(new Date()));

    avisar(`Sincronizado: ${datos[HOJA.JORNADAS].length} jornadas, ${datos[HOJA.RESIDUOS].length} filas de residuos, ` +
      `${datos[HOJA.HERRAMIENTAS].length} herramientas.`);
  } finally {
    lock.releaseLock();
  }
}

/** Lee una tabla o vista completa, por páginas (PostgREST entrega como máximo "Max rows" por consulta). */
function leerTodo(recurso, select, orden) {
  const { url } = getSupabaseConfig();
  const filas = [];
  for (let offset = 0; ; offset += CFG.PAGINA) {
    const pagina = pedirJson(
      `${url}/rest/v1/${recurso}?select=${encodeURIComponent(select)}&order=${orden}&limit=${CFG.PAGINA}&offset=${offset}`,
      recurso
    );
    for (let i = 0; i < pagina.length; i++) filas.push(pagina[i]);
    if (pagina.length < CFG.PAGINA) return filas;
  }
}

function pedirJson(endpoint, recurso) {
  const { key } = getSupabaseConfig();
  const headers = { apikey: key, Accept: "application/json" };
  // Las claves nuevas (sb_secret_…) van solo en "apikey"; la service_role antigua (JWT) también como Bearer.
  if (key.startsWith("eyJ")) headers.Authorization = `Bearer ${key}`;
  const resp = UrlFetchApp.fetch(endpoint, { method: "get", headers: headers, muteHttpExceptions: true });
  const code = resp.getResponseCode();
  const cuerpo = resp.getContentText();
  if (code < 200 || code >= 300) {
    throw new Error(`Supabase respondió ${code} al leer ${recurso}: ${cuerpo.slice(0, 300)}`);
  }
  const json = JSON.parse(cuerpo);
  if (!Array.isArray(json)) throw new Error(`Respuesta inesperada al leer ${recurso}.`);
  return json;
}

function unicos(lista) {
  const vistos = {};
  return lista.filter(v => v && !vistos[v] && (vistos[v] = true));
}

/**
 * Reemplaza solo el rango de datos (desde la fila 6); títulos, encabezados y formatos quedan.
 * Las columnas con `enlace` reciben { texto, url } y se escriben como texto enriquecido con vínculo.
 */
function escribirDatos(sheet, filas, columnas) {
  const inicio = CFG.FILA_DATOS;
  const nCols = columnas.length;
  asegurarFilas(sheet, inicio - 1 + Math.max(filas.length, 1));
  const ocupadas = sheet.getMaxRows() - inicio + 1;
  sheet.getRange(inicio, 1, ocupadas, nCols).clearContent();
  if (filas.length) {
    sheet.getRange(inicio, 1, filas.length, nCols)
      .setValues(filas.map(f => f.map((v, i) => (columnas[i].enlace ? "" : v))));
    columnas.forEach((c, i) => {
      if (!c.enlace) return;
      const ricos = filas.map(f => {
        const b = SpreadsheetApp.newRichTextValue().setText(f[i].texto || "");
        if (f[i].url) b.setLinkUrl(f[i].url);
        return [b.build()];
      });
      sheet.getRange(inicio, i + 1, filas.length, 1).setRichTextValues(ricos);
    });
  }
  asegurarFiltro(sheet, inicio - 1 + Math.max(filas.length, 1), nCols);
}

function escribirListas(sheet, playas, brigadas, area) {
  const filas = Math.max(playas.length, brigadas.length, area.length) + 1;
  asegurarFilas(sheet, filas + 1);
  sheet.getRange(2, 1, sheet.getMaxRows() - 1, 6).clearContent();
  const col = (valores) => [["Todos"]].concat(valores.map(v => [texto(v)]));
  sheet.getRange(2, 1, playas.length + 1, 1).setValues(col(playas));
  sheet.getRange(2, 2, brigadas.length + 1, 1).setValues(col(brigadas));
  if (area.length) {
    sheet.getRange(2, 4, area.length, 3)
      .setValues(area.map(r => [fecha(r.fecha_jornada), texto(r.nombre_playa), numero(r.area_m2)]));
  }
}

function asegurarFilas(sheet, ultimaFila) {
  const faltan = ultimaFila - sheet.getMaxRows();
  if (faltan > 0) sheet.insertRowsAfter(sheet.getMaxRows(), faltan);
}

/** Filtro sobre encabezados + datos; se rehace solo si los datos crecieron fuera de su rango. */
function asegurarFiltro(sheet, ultimaFila, nCols) {
  const f = sheet.getFilter();
  if (f && f.getRange().getLastRow() >= ultimaFila && f.getRange().getNumColumns() === nCols) return;
  if (f) f.remove();
  sheet.getRange(CFG.FILA_ENCABEZADO, 1, ultimaFila - CFG.FILA_ENCABEZADO + 1, nCols).createFilter();
}

// ============================================================================
// 6. ESTRUCTURA DE LA PLANILLA
// ============================================================================
function crearEstructura() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ss.setSpreadsheetTimeZone(CFG.ZONA);

  const orden = [HOJA.INICIO, HOJA.KPI, HOJA.JORNADAS, HOJA.RESIDUOS, HOJA.HERRAMIENTAS, HOJA.LISTAS];
  orden.forEach((nombre, i) => {
    const sh = ss.getSheetByName(nombre) || ss.insertSheet(nombre);
    ss.setActiveSheet(sh);
    ss.moveActiveSheet(i + 1);
  });
  borrarHojaPorDefecto(ss);

  Object.keys(TABLAS).forEach(h => estructurarTabla(ss.getSheetByName(h), TABLAS[h]));
  estructurarListas(ss.getSheetByName(HOJA.LISTAS));
  estructurarInicio(ss.getSheetByName(HOJA.INICIO));
  conFormulasEnIngles(ss, () => estructurarKpi(ss.getSheetByName(HOJA.KPI), ss.getSheetByName(HOJA.LISTAS)));

  ss.setActiveSheet(ss.getSheetByName(HOJA.INICIO));
  avisar("Estructura lista. Ahora usa «Sincronizar ahora».");
}

/**
 * Las fórmulas del KPI están en inglés (COUNTIFS, separador ","). En una planilla en español
 * no se traducían y daban error. Se escriben con la configuración regional en en_US y luego se
 * restaura la original: Google traduce las fórmulas solo (CONTAR.SI.CONJUNTO con ";").
 */
function conFormulasEnIngles(ss, escribir) {
  const original = ss.getSpreadsheetLocale();
  const cambiar = original !== "en_US";
  if (cambiar) ss.setSpreadsheetLocale("en_US");
  try {
    escribir();
    SpreadsheetApp.flush();
  } finally {
    if (cambiar) ss.setSpreadsheetLocale(original);
  }
}

function borrarHojaPorDefecto(ss) {
  ["Hoja 1", "Hoja1", "Sheet1"].forEach(n => {
    const sh = ss.getSheetByName(n);
    if (sh && sh.getLastRow() === 0 && ss.getSheets().length > 1) ss.deleteSheet(sh);
  });
}

function titulo(sheet, celda, txt) {
  sheet.getRange(celda).setValue(txt).setFontSize(14).setFontWeight("bold").setFontColor(CFG.COLOR_TITULO);
}

function encabezado(range) {
  range.setFontWeight("bold").setFontColor("#FFFFFF").setBackground(CFG.COLOR_ENCABEZADO)
    .setWrap(true).setVerticalAlignment("middle");
}

function estructurarTabla(sheet, def) {
  const n = def.columnas.length;
  sheet.getRange(1, 1, 4, Math.max(n, 2)).clearContent();
  titulo(sheet, "A1", def.titulo);
  sheet.getRange("A3").setValue("Datos de Supabase: no editar. «Logística Verde → Sincronizar ahora» los reemplaza.")
    .setFontStyle("italic").setFontColor("#666666");

  encabezado(sheet.getRange(CFG.FILA_ENCABEZADO, 1, 1, n).setValues([def.columnas.map(c => c.t)]));
  sheet.setRowHeight(CFG.FILA_ENCABEZADO, 36);
  asegurarFilas(sheet, CFG.FILA_DATOS + 1);
  const filasDatos = sheet.getMaxRows() - CFG.FILA_DATOS + 1;
  def.columnas.forEach((c, i) => {
    if (c.w) sheet.setColumnWidth(i + 1, c.w);
    // Columnas abiertas (A6:A) para que el formato alcance a filas nuevas.
    if (c.f) sheet.getRange(CFG.FILA_DATOS, i + 1, filasDatos, 1).setNumberFormat(c.f);
  });
  sheet.setFrozenRows(CFG.FILA_ENCABEZADO);
  sheet.setFrozenColumns(1);
  asegurarFiltro(sheet, Math.max(sheet.getLastRow(), CFG.FILA_DATOS), n);
  protegerConAviso(sheet);
}

function estructurarListas(sheet) {
  sheet.getRange(1, 1, 1, 6).setValues(LISTAS.encabezados).setFontWeight("bold");
  sheet.getRange("D2:D").setNumberFormat("dd-mm-yyyy");
  sheet.getRange("F2:F").setNumberFormat("#,##0");
  if (!sheet.getRange("A2").getValue()) sheet.getRange("A2:B2").setValues([["Todos", "Todos"]]);
  protegerConAviso(sheet);
  sheet.hideSheet();
}

/** Aviso (no bloqueo) al editar a mano una pestaña que la sincronización reemplaza. */
function protegerConAviso(sheet) {
  const ya = sheet.getProtections(SpreadsheetApp.ProtectionType.SHEET).length > 0;
  if (!ya) sheet.protect().setDescription("Datos de Supabase: se reemplazan al sincronizar").setWarningOnly(true);
}

function estructurarInicio(sheet) {
  sheet.clear();
  titulo(sheet, "A1", "LOGÍSTICA VERDE | Registro de jornadas y KPI");
  const filas = [
    ["Propósito", "Consolidar las jornadas que las brigadas registran en la app «Deja tu Playa Limpia» y calcular indicadores descriptivos."],
    ["Origen de los datos", "Supabase. Menú «Logística Verde → Sincronizar ahora»; además se sincroniza sola todos los días a las 8:00."],
    ["", ""],
    ["Reglas de uso", ""],
    ["1. No editar los datos", "Jornadas, Residuos y Herramientas se reemplazan en cada sincronización: lo escrito a mano se pierde. Para analizar, usa los filtros."],
    ["2. Jornadas", "Una fila por brigada y jornada: cada líder registra la suya con su teléfono. El ID de jornada es único por brigada."],
    ["3. Residuos", "Una fila por tipo de residuo contado en una jornada (solo cantidades mayores que 0). La app no pesa por tipo: el peso está en Jornadas."],
    ["4. Herramientas", "Una fila por herramienta que llevó una brigada. Herramienta_ID es el código interno del inventario."],
    ["5. Ceros", "Un cero o una celda vacía en el KPI puede significar que no hay registros para los filtros elegidos, no que no hubo residuos."],
    ["6. Filtros", "El KPI se filtra por fechas, playa y brigada. Cada pestaña de datos tiene su propio filtro."],
    ["", ""],
    ["Definiciones", ""],
    ["Jornada", "Trabajo de una brigada en una fecha, playa y sectores."],
    ["Voluntario-hora", "N.º de participantes (líder + voluntarios) × duración de la jornada, en horas."],
    ["Kg valorizados", "Todos los kg de basura reciclable, incluidos los que quedaron «Pendiente de entrega»: ya se retiraron de la playa y se entregarán."],
    ["% valorizado", "Kg reciclables ÷ kg totales."],
    ["Área", "Superficie de los sectores elegidos en la app (catálogo), no una medición en terreno."],
    ["Densidad (kg/m²)", "Kg totales ÷ m² limpiados. Con todas las brigadas, cada sector cuenta una sola vez por día aunque lo hayan limpiado dos brigadas."],
  ];
  sheet.getRange(3, 1, filas.length, 2).setValues(filas);
  sheet.getRange(3, 1, filas.length, 1).setFontWeight("bold");
  [3 + 3, 3 + 11].forEach(r => sheet.getRange(r, 1).setFontSize(12).setFontColor(CFG.COLOR_TITULO));
  sheet.getRange(3, 2, filas.length, 1).setWrap(true);
  sheet.setColumnWidth(1, 200);
  sheet.setColumnWidth(2, 760);
  sheet.setFrozenRows(1);
}

// ---------- KPI ----------

/** Letra de la columna de una pestaña de datos según su encabezado (evita fórmulas desalineadas). */
function letra(hoja, encabezadoTxt) {
  const i = TABLAS[hoja].columnas.findIndex(c => c.t === encabezadoTxt);
  if (i < 0) throw new Error(`No existe la columna «${encabezadoTxt}» en ${hoja}.`);
  let n = i + 1, s = "";
  while (n > 0) { const m = (n - 1) % 26; s = String.fromCharCode(65 + m) + s; n = Math.floor((n - 1) / 26); }
  return s;
}

function rj(encabezadoTxt) {
  const L = letra(HOJA.JORNADAS, encabezadoTxt);
  return `${HOJA.JORNADAS}!$${L}$${CFG.FILA_DATOS}:$${L}`;
}

function rl(col) {
  return `${HOJA.LISTAS}!$${col}$2:$${col}`;
}

/** Criterios comunes: fechas, playa y brigada (por defecto, el filtro B6). */
function criterios(brigada) {
  return [
    rj("Fecha"), `">="&$B$3`,
    rj("Fecha"), `"<="&$B$4`,
    rj("Playa"), `IF($B$5="Todos","*",$B$5)`,
    rj("Brigada"), brigada || `IF($B$6="Todos","*",$B$6)`,
  ].join(",");
}

function estructurarKpi(sheet, listas) {
  // Conserva los filtros elegidos (B3:B6) si son válidos; el resto se rehace.
  const prev = sheet.getRange("B3:B6").getValues().map(r => r[0]);
  // clear() no borra las validaciones: si quedaran, escribir B3 antes de darle formato de fecha
  // viola "requireDate" y el script se detiene a mitad del KPI.
  sheet.getRange(1, 1, sheet.getMaxRows(), sheet.getMaxColumns()).breakApart().clearDataValidations();
  sheet.clear();

  titulo(sheet, "A1", "PANEL KPI | Resumen por filtros");
  sheet.getRange("A3:A6").setValues([["Desde"], ["Hasta"], ["Playa"], ["Brigada"]]).setFontWeight("bold");
  const hoy = Utilities.formatDate(new Date(), CFG.ZONA, "yyyy-MM-dd").split("-").map(Number);
  const fechaPrevia = v => (v instanceof Date && !isNaN(v.getTime())) ? serialChile(v) : (typeof v === "number" && v > 0 ? v : "");
  const textoPrevio = v => (typeof v === "string" && v.trim() ? v : "Todos");
  sheet.getRange("B3:B4").setNumberFormat("dd-mm-yyyy");
  sheet.getRange("B3:B6").setValues([
    [fechaPrevia(prev[0]) || serial(hoy[0], hoy[1], 1)],
    [fechaPrevia(prev[1]) || serial(hoy[0], 12, 31)],
    [textoPrevio(prev[2])],
    [textoPrevio(prev[3])],
  ]);
  sheet.getRange("B3:B4")
    .setDataValidation(SpreadsheetApp.newDataValidation().requireDate().setAllowInvalid(false).build());
  sheet.getRange("B5").setDataValidation(SpreadsheetApp.newDataValidation()
    .requireValueInRange(listas.getRange("A2:A"), true).setAllowInvalid(false).build());
  sheet.getRange("B6").setDataValidation(SpreadsheetApp.newDataValidation()
    .requireValueInRange(listas.getRange("B2:B"), true).setAllowInvalid(false).build());
  sheet.getRange("B3:B6").setBackground("#FFF8E1");
  sheet.getRange("D3").setValue("Última sincronización").setFontWeight("bold");
  sheet.getRange("E3").setNumberFormat("dd-mm-yyyy hh:mm");

  // ---- Indicadores ----
  const c = criterios();
  const R = {};
  const ind = [
    { k: "jor", t: "Jornadas/brigada registradas", fmt: "0",
      f: () => `=COUNTIFS(${c})`,
      n: "Cada fila de Jornadas es una brigada en una jornada; no equivale a una jornada colectiva." },
    { k: "kgT", t: "Kg totales", fmt: "#,##0.00",
      f: () => `=SUMIFS(${rj("Kg totales")},${c})`,
      n: "Kg normal + kg reciclable, pesados en el punto de acopio." },
    { k: "kgN", t: "Kg basura normal", fmt: "#,##0.00",
      f: () => `=SUMIFS(${rj("Kg normal")},${c})`,
      n: "No valorizable: va a camión municipal o relleno." },
    { k: "kgR", t: "Kg reciclables (valorizados)", fmt: "#,##0.00",
      f: () => `=SUMIFS(${rj("Kg reciclable")},${c})`,
      n: "Incluye los «Pendiente de entrega»: ya se retiraron de la playa y se entregarán." },
    { k: "pct", t: "% valorizado", fmt: "0.0%",
      f: () => `=IF(${R.kgT}=0,"",${R.kgR}/${R.kgT})`,
      n: "Kg reciclables ÷ kg totales." },
    { k: "vh", t: "Voluntario-horas", fmt: "#,##0.00",
      f: () => `=SUMIFS(${rj("Voluntario-horas")},${c})`,
      n: "Participantes (líder + voluntarios) × duración." },
    { k: "kvh", t: "Kg por voluntario-hora", fmt: "0.00",
      f: () => `=IF(OR(${R.kgT}=0,${R.vh}=0),"",${R.kgT}/${R.vh})`,
      n: "Productividad del trabajo." },
    { k: "area", t: "Área total (m²)", fmt: "#,##0",
      f: () => `=IF($B$6="Todos",SUMIFS(${rl(LISTAS.colAreaM2)},${rl(LISTAS.colAreaFecha)},">="&$B$3,` +
        `${rl(LISTAS.colAreaFecha)},"<="&$B$4,${rl(LISTAS.colAreaPlaya)},IF($B$5="Todos","*",$B$5)),` +
        `SUMIFS(${rj("Área (m²)")},${c}))`,
      n: "Con «Todos» en Brigada, cada sector cuenta una vez por día. Con una brigada, suma sus sectores." },
    { k: "kgm2", t: "Kg por m²", fmt: "0.000",
      f: () => `=IF(OR(${R.kgT}=0,${R.area}=0),"",${R.kgT}/${R.area})`,
      n: "Área del catálogo de sectores, no medida en terreno." },
    { k: "km", t: "Km recorridos", fmt: "#,##0.00",
      f: () => `=SUMIFS(${rj("Km recorridos")},${c})`,
      n: "Suma del GPS de cada líder." },
    { k: "sac", t: "Sacos", fmt: "#,##0",
      f: () => `=SUMIFS(${rj("Sacos")},${c})`,
      n: "Sacos entregados en el punto de acopio." },
  ];
  const fila0 = 9;
  ind.forEach((x, i) => { R[x.k] = "$B$" + (fila0 + i); });
  encabezado(sheet.getRange(fila0 - 1, 1, 1, 3).setValues([["Indicador", "Resultado", "Interpretación / precaución"]]));
  encabezado(sheet.getRange(fila0 - 1, 3, 1, 7).merge());
  sheet.getRange(fila0, 1, ind.length, 1).setValues(ind.map(x => [x.t]));
  sheet.getRange(fila0, 2, ind.length, 1).setFormulas(ind.map(x => [x.f()]));
  // La nota ocupa C:I (la columna C también la usa la tabla por brigada).
  ind.forEach((x, i) => sheet.getRange(fila0 + i, 3, 1, 7).merge().setValue(x.n)
    .setFontColor("#555555").setWrap(true));
  ind.forEach((x, i) => sheet.getRange(fila0 + i, 2).setNumberFormat(x.fmt));
  sheet.getRange(fila0, 2, ind.length, 1).setFontWeight("bold");

  // ---- Comparación por brigada (fechas y playa elegidas; ignora el filtro de brigada) ----
  const fT = fila0 + ind.length + 2;
  sheet.getRange(fT, 1).setValue("Comparación por brigada (fechas y playa seleccionadas)")
    .setFontWeight("bold").setFontColor(CFG.COLOR_TITULO);
  const cab = ["Brigada", "Jornadas", "Kg totales", "Kg reciclable", "% valorizado",
    "Voluntario-horas", "Kg/voluntario-hora", "Área m²", "Kg/m²"];
  encabezado(sheet.getRange(fT + 1, 1, 1, cab.length).setValues([cab]));
  const formulas = [];
  for (let i = 0; i < CFG.MAX_BRIGADAS_KPI; i++) {
    const r = fT + 2 + i;
    const a = `$A${r}`;
    const cb = criterios(a);
    const si = (expr) => `=IF(${a}="","",${expr})`;
    formulas.push([
      `=IFERROR(IF(INDEX(${HOJA.LISTAS}!$B$3:$B,${i + 1})="","",INDEX(${HOJA.LISTAS}!$B$3:$B,${i + 1})),"")`,
      si(`COUNTIFS(${cb})`),
      si(`SUMIFS(${rj("Kg totales")},${cb})`),
      si(`SUMIFS(${rj("Kg reciclable")},${cb})`),
      si(`IF(C${r}=0,"",D${r}/C${r})`),
      si(`SUMIFS(${rj("Voluntario-horas")},${cb})`),
      si(`IF(OR(C${r}=0,F${r}=0),"",C${r}/F${r})`),
      si(`SUMIFS(${rj("Área (m²)")},${cb})`),
      si(`IF(OR(C${r}=0,H${r}=0),"",C${r}/H${r})`),
    ]);
  }
  const rTot = fT + 2 + CFG.MAX_BRIGADAS_KPI;
  formulas.push([
    "", `=${R.jor}`, `=${R.kgT}`, `=${R.kgR}`, `=${R.pct}`,
    `=${R.vh}`, `=${R.kvh}`, `=${R.area}`, `=${R.kgm2}`,
  ]);
  sheet.getRange(fT + 2, 1, formulas.length, cab.length).setFormulas(formulas);
  sheet.getRange(rTot, 1).setValue("TOTAL filtrado");
  const filasT = formulas.length;
  [["B", "0"], ["C", "#,##0.00"], ["D", "#,##0.00"], ["E", "0.0%"], ["F", "#,##0.00"],
    ["G", "0.00"], ["H", "#,##0"], ["I", "0.000"]]
    .forEach(([col, fmt]) => sheet.getRange(`${col}${fT + 2}:${col}${fT + 1 + filasT}`).setNumberFormat(fmt));
  sheet.getRange(rTot, 1, 1, cab.length).setFontWeight("bold").setBackground("#E8F5E9");

  // ---- Lectura responsable ----
  const fL = rTot + 3;
  sheet.getRange(fL, 1).setValue("Lectura responsable").setFontWeight("bold").setFontColor(CFG.COLOR_TITULO);
  sheet.getRange(fL + 1, 1, 1, cab.length).merge().setWrap(true).setVerticalAlignment("top").setValue(
    "La fila TOTAL usa los filtros de arriba, incluida la brigada. El área total con «Todos» cuenta cada sector una vez " +
    "por día; la suma de la columna «Área m²» por brigada puede ser mayor si dos brigadas limpiaron el mismo sector. " +
    "Un cero puede significar ausencia de registros, no ausencia de residuos.");
  sheet.setRowHeight(fL + 1, 54);

  sheet.setColumnWidth(1, 240);
  sheet.setColumnWidth(2, 130);
  sheet.setColumnWidth(3, 120);
  for (let col = 4; col <= cab.length; col++) sheet.setColumnWidth(col, 115);
  sheet.setFrozenRows(6);
}
