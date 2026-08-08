# Cardio GPS spike (Beta 10)

## Alcance

El modelo cardio de Beta 10 ya puede marcar una actividad exterior con `requiresGps`, registrar tiempo/distancia/FC de forma manual o por voz y guardar una estimación offline. El seguimiento GPS continuo queda aislado como spike opcional: no bloquea el flujo base ni solicita permisos al instalar la app.

## Decisión técnica

- Rama propuesta: `spike/cardio-gps`.
- API: `FusedLocationProviderClient` con actualización nominal cada 5 s y precisión balanceada.
- Servicio: `CardioGpsTracker` encapsulado detrás de un foreground service con `FOREGROUND_SERVICE_LOCATION` y notificación persistente.
- Distancia: suma haversiana entre puntos válidos; descarta saltos imposibles y conserva el último total local para funcionar sin red.
- Fallback: si el usuario rechaza, pausa o pierde GPS, el cardio sigue registrándose con tiempo, distancia introducida y calorías MET; nunca se borra una sesión válida.
- Privacidad: opt-in por sesión, indicador visible mientras está activo y stop explícito antes de salir.

## Permisos y aceptación del spike

La rama debe declarar `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` y `FOREGROUND_SERVICE_LOCATION`, pedirlos en runtime mediante la guía existente y validar el estado antes de iniciar el tracker. El flujo debe cubrir permiso denegado, ubicación desactivada, proceso recreado y pérdida de conectividad.

Checklist antes de integrar en Beta 10:

1. Medir deriva de distancia y consumo en una caminata de 20 minutos.
2. Verificar que pausar/reanudar no duplica puntos ni tiempo.
3. Verificar que el registro offline se recupera al recrear la actividad.
4. Confirmar con Android 14/15 y un dispositivo físico, no solo con el emulador.
5. Integrar solo después de revisar privacidad, batería y la notificación del foreground service.

Health Connect queda igualmente opcional: el flavor `health` expone `READ_HEART_RATE` y `readHeartRateSeries`; el flavor base no depende de esa librería.
