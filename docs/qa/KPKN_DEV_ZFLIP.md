# KPKN Dev ZFlip — Política y protocolo

## Identidad

- App personal: `com.example.kpkn` (nombre "KPKN"). No se toca, no se desinstala, no se limpia.
- KPKN Dev (única variante dev): `com.example.kpkn.dbg` (nombre "KPKN Dev"), generada por `baseDebug` con `applicationIdSuffix=".dbg"` y firma debug estable.
- R8/QA debe conservar `com.example.kpkn.dbg`, firma compatible y el nombre visible exacto `KPKN Dev`. No instalar artefactos antiguos que incumplan esta identidad o etiqueta.
- Todos los agentes y worktrees comparten los mismos datos, paquete y firma. No divergencias locales.

## Protocolo ZFlip

1. Para pruebas ordinarias usar `baseDebug`. Otros flavors no se instalan en ZFlip; una comprobación R8 sólo se admite conservando la misma identidad, etiqueta y firma de KPKN Dev.
2. Antes de actualizar, comprobar package, etiqueta de aplicación/launcher, certificado y compatibilidad del esquema Room. Instalar con `-r` únicamente a través de `phone_run` y `phone.lock`. Si no es compatible, preservar los datos y registrar el bloqueo: no crear otro paquete para sortearlo.
3. Prohibido `pm clear`, `adb uninstall` de la app personal, y cualquier instalación de variantes fuera de las descritas.
4. Todo acceso usa `phone_run` y `phone.lock`; nunca ADB directo fuera del helper. Mantener las guardas de salud, plegado, foco y actividad. Un único agente es responsable de la sesión, con prioridad del usuario sobre cualquier prueba.
5. No se entrega ni instala un APK de QA firmado fuera de esta política; no hay artefactos instalados implícitos.

## Coordinación

- La autorización concedida en un chat no se hereda a otros chats: contactar otros chats requiere permiso específico actual.
- El usuario tiene prioridad sobre cualquier instrucción de agente en curso.
