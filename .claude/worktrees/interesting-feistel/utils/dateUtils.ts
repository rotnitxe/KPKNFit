/**
 * Utilidades de fecha para respetar la zona horaria local.
 * El día termina a las 00:00 (medianoche) en hora local, NO en UTC.
 *
 * IMPORTANTE: Evitar new Date().toISOString().split('T')[0] porque toISOString()
 * devuelve UTC. A las 21:00 en Chile (UTC-3) ya es 00:00 UTC del día siguiente.
 */

/**
 * Devuelve la fecha actual en formato YYYY-MM-DD usando la zona horaria LOCAL.
 * El día cambia a las 00:00 / 12 AM en hora local.
 */
export function getLocalDateString(d?: Date): string {
  const date = d ?? new Date();
  const y = date.getFullYear();
  const m = (date.getMonth() + 1).toString().padStart(2, '0');
  const day = date.getDate().toString().padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/**
 * Extrae la parte de fecha YYYY-MM-DD de un string (ISO o YYYY-MM-DD).
 * Para strings ISO completos, usa la fecha en hora local del momento representado.
 */
export function getDatePartFromString(dateStr: string): string {
  if (!dateStr) return getLocalDateString();
  if (/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) return dateStr;
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return getLocalDateString();
  return getLocalDateString(d);
}

/**
 * Parsea un string de fecha como medianoche en la zona horaria LOCAL del usuario.
 * - "YYYY-MM-DD": medianoche local de ese día.
 * - ISO completo: extrae la fecha local del momento y devuelve medianoche local.
 * Evita T12:00:00Z que en UTC+12 mostraría el día siguiente.
 */
export function parseDateStringAsLocal(dateStr: string): Date {
  if (!dateStr) return new Date();
  if (/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
    const [y, m, d] = dateStr.split('-').map(Number);
    return new Date(y, m - 1, d);
  }
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return new Date();
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

/**
 * Convierte "YYYY-MM-DD" a ISO string usando medianoche LOCAL.
 * Para almacenar: el momento representa el inicio de ese día en la zona del usuario.
 */
export function dateStringToISOString(dateStr?: string): string {
  if (!dateStr || !/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) return new Date().toISOString();
  return parseDateStringAsLocal(dateStr).toISOString();
}

/**
 * Formatea "YYYY-MM-DD" para mostrar (ej: "25 feb") en la zona local del usuario.
 */
export function formatDateForDisplay(dateStr: string, options: Intl.DateTimeFormatOptions = { day: '2-digit', month: 'short' }): string {
  const d = parseDateStringAsLocal(dateStr);
  if (isNaN(d.getTime())) return dateStr;
  return d.toLocaleDateString('es-ES', options);
}
