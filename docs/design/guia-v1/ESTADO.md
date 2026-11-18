# Estado · Bienvenida y guía v1 (se actualiza a mano; la última línea manda)

| Paquete | Worktree / rama | Modelo | Estado |
|---|---|---|---|
| wiz-a (auditoría + W1–W9) | `C:\kw\gu-wiz` / `guia/wiz` | Sonnet high | lanzado 30-oct |
| wiz-b (W10–W11 limpieza) | `C:\kw\gu-wiz` / `guia/wiz` | Haiku max | tras wiz-a |
| tour-a (motor v2) | `C:\kw\gu-tour` / `guia/tour` | Opus high | lanzado 30-oct |
| demo (siembra + guion de grabación + APK .dbg) | `C:\kw\gu-demo` / `guia/demo` | Sonnet medium | lanzado 30-oct |
| wel-a (marco 9:22, página Progreso + Cuerpo) | `C:\kw\gu-wel` / `guia/wel` | Sonnet high | lanzado 30-oct |
| Sesión supervisada 1 del Flip | — | yo | tras demo (APK) |
| Lienzo `docs/design/guia-v1/` | `C:\kw\guia` | Opus high | tras la sesión 1 |
| Implementación 1:1, vídeos finales, integración | — | — | tras aprobar el lienzo |

Última línea: 30-oct, EN PAUSA por pedido del usuario. Los 4 agentes se detuvieron sin commits; para reactivar, relanzar cada paquete con su brief (`briefs/*.md`) y decirle que parta de lo que haya en su worktree:
- gu-wiz y gu-tour: sin cambios (empiezan de cero).
- gu-demo: `DemoDataPlan.kt` modificado a medias (revisar o descartar).
- gu-wel: borradas (en el índice) las escenas Canvas viejas y sus 4 pruebas; aún sin el resto del brief.
Sin corridas de Gradle de esta ola en la cola.
