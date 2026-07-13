/**
 * generateId.ts
 *
 * Genera IDs únicos en formato UUID v4 sin depender de crypto.randomUUID()
 * (que requiere lib:"DOM" o lib:"WebCrypto" en tsconfig, no disponibles en un
 * proyecto Hermes/RN puro).
 *
 * Usa Math.random() que en Hermes es suficientemente robusto para IDs locales.
 * No es cryptographically secure, pero para primary keys de SQLite locales es
 * más que suficiente y no requiere dependencias externas.
 */
export function generateId(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    // eslint-disable-next-line no-bitwise
    const r = (Math.random() * 16) | 0;
    // eslint-disable-next-line no-bitwise
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
