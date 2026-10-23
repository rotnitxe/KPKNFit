# ED-D · Cardio y superseries · cierre 22-oct-2026

Se continúa y conserva el WIP recibido de C2–C7 sobre 0943266b8. El paquete queda compilado, probado y revisado en sus tableros congelados. No hay cambios de Room, migraciones, push ni APK en este cierre; la integración y el QA en el emulador siguen siendo pasos posteriores.

## Resultado

- C2: todas las entradas del editor abren el mismo creador y requieren confirmación. Selección de 2–4 miembros en orden A–D, atajo al catálogo, rondas y descansos. El cuerpo completo se desplaza; al 130 % las acciones fijas pasan a columna. La lista conserva al menos 96 dp útiles. El mínimo de rondas sigue las series reales de los miembros.
- Al editar una superserie, `sourceGroupId` conserva su identidad incluso si se desmarcan todos los miembros originales. Se pueden volver a marcar; los de otros grupos quedan excluidos. Se mantienen rondas, ajustes válidos y opcionalidad. Los miembros retirados quedan libres con sus series intactas. Este identificador es solo estado de UI.
- C3: módulo de descanso base, descanso propio por ronda y «Usar el base». Los mapas guardan solo ajustes explícitos. Crear o cambiar el base no materializa la herencia; las rondas heredadas siguen dos cambios sucesivos del base. Un ajuste explícito igual al base también puede borrarse. Los datos antiguos no se migran ni reinterpretan.
- C4/C5: avisos de fase disponibles cuando hay protocolo, pantalla encendida para cualquier cardio y objetivo Tiempo/kcal/Distancia enlazado a CardioRepeatGrammar.
- C6: resumen pluralizado con la duración del formateador común y espacio no separable; cinco bloques y una ronda muestran 17 min en la fixture aprobada.
- C7: el tablero contiene exactamente tres insignias «GPS en vivo»; la prueba exige las tres. Se conserva la agrupación de máquinas y la selección real.
- El botón compartido de icono incorpora `enabled` con valor predeterminado true, zona de 48 dp, atenuación y semántica deshabilitada.

## Verificación final

| Evidencia | Resultado |
|---|---|
| `C:/kw/logs/edd-superset-jvm-codex-3.log` | BUILD SUCCESSFUL, 2 min 52 s; 14 pruebas, 0 fallos/errores/omitidas |
| `C:/kw/logs/edd-codex-20261022-4.log` | BUILD SUCCESSFUL, 9 min 8 s; 9 clases, 67 pruebas, 0 fallos/errores/omitidas |
| Renders `C:/kw/shots/edd/` | 16 PNG, letra 100/130 %, C2 base/configuración, C3 base/descanso propio y C4–C7 |

Las 67 pruebas incluyen CardioEditorEddComposeTest (7), SupersetRoundsC3ComposeTest (7), SupersetCreatorC2ComposeTest (6), SessionEditorAuditRegressionTest (15), SessionEditorRmLoadEditTest (4), SessionEditorViewModelRulesTest (13), SessionEditorSupersetFrameComposeTest (3), SupersetQuickActionsComposeTest (6) y render (6). Se revisaron las escenas al 130 %, incluidas de nuevo C2 y C3 tras el último cambio: aparecen los tres miembros reales A/B/C, acciones completas y descanso propio con vuelta al base.

La fixture C2 recibida leía un borrador fijo fuera de la composición. Se corrigió para leer el estado dentro de setContent, conservando los clics físicos y todas las aserciones de letras, orden y confirmación. Dos aserciones antiguas de descanso ahora comprueban la herencia efectiva y mapas vacíos, conforme al contrato corregido. Las corridas anteriores con fallos no cuentan como cierre.

## Integración y límites

El motor y sus regresiones están en f3c079e57. Los cambios UI se guardan en commits pequeños posteriores de este paquete. Al integrar ED-B, adaptar las dos invocaciones de InlineSetRow de SupersetRoundsAndConfig a los focos específicos RM/AMRAP, preservando referencia declarada, lado y callbacks de C3; la base aislada de ED-D todavía no contiene esos componentes.

Robolectric acredita compilación, interacción y geometría, y genera imágenes de revisión; no sustituye al QA de navegación, teclado y texto en el ZFlip5_QA. Algunos textos del render nativo se recortan de forma intermitente aunque sus bounds y semántica sean válidos; ese límite se comprobará en el dispositivo final. No se ha instalado ni firmado una APK en este paquete.
