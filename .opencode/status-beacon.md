# Status Beacon — Notificaciones y color de pestaña para OpenCode y Cline

Extensión que avisa con una **notificación + sonido** y colorea la **pestaña de
Windows Terminal** según el estado de la sesión, tanto en **OpenCode** como en
**Cline**.

## Qué hace

### 1. Notificación + sonido
- **Al iniciar un build/test** → notificación "Iniciando build/test…" + sonido.
- **Al terminar un build/test** → notificación (OK / errores) + sonido.
- **Al terminar una tarea de chat** → notificación "Tarea terminada" + sonido.

### 2. Color de pestaña (requiere Windows Terminal)
| Estado    | Color   | Hex      | Cuándo                                  |
|-----------|---------|----------|-----------------------------------------|
| Libre     | Azul    | `#3b88c3`| Sin tareas en curso                     |
| Proceso   | Amarillo| `#c9a227`| Tarea de chat corriendo / build/test    |
| Terminado | Verde   | `#28a745`| Build/test o tarea terminada (4 s)      |

## Archivos

| Archivo | Rol |
|---------|-----|
| `.opencode/plugin/status-beacon.ts` | Plugin **TUI de OpenCode**: notificaciones, sonidos y color de pestaña. Se autocarrega. |
| `.cline/plugins/status-beacon.ts` | Plugin **Cline** (SDK): mismas notificaciones, sonidos y color de pestaña. Se autodetecta de `.cline/plugins`. |
| `.opencode/scripts/kpkn-launcher.psm1` | Módulo PowerShell: `Set-KpknTabColor` y `Send-KpknNotify`, y pestaña azul al abrir. |

## Cómo activar

### OpenCode
1. En el TUI ejecuta `/options` y activa *Notifications* y *Sound* (Attention).
   El plugin usa `api.attention.notify()`.
2. El plugin `status-beacon` se carga automáticamente desde `.opencode/plugin/`.

### Cline
1. El plugin `status-beacon` se autodetecta de `.cline/plugins` del workspace.
   No requiere instalación.
2. Las notificaciones/sonidos los delega a `Send-KpknNotify` (módulo de
   PowerShell ya cargado en tu perfil).

### Ambos
1. **Color de pestaña**: ejecuta OpenCode/Cline dentro de **Windows Terminal**
   (no en la consola clásica conhost). El launcher ya pinta la pestaña de azul
   al abrir.
2. Recarga el módulo en la sesión actual si lo usas en vivo:
   ```powershell
   Import-Module 'C:\Users\valen\Documents\KPKNFit\.opencode\scripts\kpkn-launcher.psm1' -Force
   ```

## Comandos de ayuda (PowerShell)
```powershell
Set-KpknTabColor -State Free | Working | Done   # cambia la pestaña de color
Set-KpknTabColor -Hex '#ff0000'                 # color personalizado
Set-KpknTabColor -Reset                         # color por defecto
Send-KpknNotify -Title 'KPKN' -Message 'Listo' -Sound Exclamation
```