// services/healthConnectService.ts
/**
 * Health Connect / HealthKit integration placeholder.
 *
 * EVALUACIÓN DE COMPATIBILIDAD (Capacitor 6):
 * - @capgo/capacitor-health: requiere Capacitor >= 8
 * - @pianissimoproject/capacitor-health-connect: requiere Capacitor ^7.4.2
 *
 * La app actual usa Capacitor 6. Para integrar Health Connect (Android) / HealthKit (iOS):
 * 1. Actualizar a Capacitor 7 u 8
 * 2. Instalar @pianissimoproject/capacitor-health-connect (Cap 7) o @capgo/capacitor-health (Cap 8)
 * 3. Configurar permisos en AndroidManifest e Info.plist
 * 4. Implementar lectura de: pasos, peso, sueño para enriquecer batería AUGE y nutrición
 */

import { Capacitor } from '@capacitor/core';

export const HEALTH_AVAILABLE = false; // Cambiar a true cuando se instale un plugin compatible

export async function isHealthAvailable(): Promise<boolean> {
  return HEALTH_AVAILABLE && Capacitor.isNativePlatform();
}
