// apps/mobile/src/utils/dateUtils.ts
// Utilidades de fecha para respetar la zona horaria local.
// El día termina a las 00:00 (medianoche) en hora local, NO en UTC.

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
export function formatDateForDisplay(
  dateStr: string,
  options?: Intl.DateTimeFormatOptions
): string {
  const d = parseDateStringAsLocal(dateStr);
  if (isNaN(d.getTime())) return dateStr;
  return d.toLocaleDateString('es-ES', options || { day: '2-digit', month: 'short' });
}

/**
 * Formatea fecha relativa: "Hoy", "Ayer", "Hace X días".
 */
export function formatRelativeDate(dateStr: string): string {
  const date = parseDateStringAsLocal(dateStr);
  const today = parseDateStringAsLocal(getLocalDateString());
  
  const diffMs = today.getTime() - date.getTime();
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
  
  if (diffDays === 0) return 'Hoy';
  if (diffDays === 1) return 'Ayer';
  if (diffDays < 7) return `Hace ${diffDays} días`;
  if (diffDays < 14) return 'Hace 1 semana';
  if (diffDays < 30) return `Hace ${Math.floor(diffDays / 7)} semanas`;
  if (diffDays < 60) return 'Hace 1 mes';
  return `Hace ${Math.floor(diffDays / 30)} meses`;
}

/**
 * Obtiene el día de la semana (0 = Domingo, 1 = Lunes, etc.)
 */
export function getDayOfWeek(dateStr: string): number {
  return parseDateStringAsLocal(dateStr).getDay();
}

/**
 * Suma días a una fecha y retorna el string YYYY-MM-DD.
 */
export function addDays(dateStr: string, days: number): string {
  const date = parseDateStringAsLocal(dateStr);
  date.setDate(date.getDate() + days);
  return getLocalDateString(date);
}

/**
 * Obtiene la diferencia en días entre dos fechas.
 */
export function getDaysBetween(dateStr1: string, dateStr2: string): number {
  const d1 = parseDateStringAsLocal(dateStr1);
  const d2 = parseDateStringAsLocal(dateStr2);
  const diffMs = Math.abs(d2.getTime() - d1.getTime());
  return Math.floor(diffMs / (1000 * 60 * 60 * 24));
}

/**
 * Verifica si una fecha es hoy.
 */
export function isToday(dateStr: string): boolean {
  return dateStr === getLocalDateString();
}

/**
 * Verifica si una fecha es hoy o en el pasado.
 */
export function isTodayOrPast(dateStr: string): boolean {
  const today = parseDateStringAsLocal(getLocalDateString());
  const date = parseDateStringAsLocal(dateStr);
  return date <= today;
}
