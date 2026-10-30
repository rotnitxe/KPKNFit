# Pulido v1 · informe de la noche del 30-oct-2026

Trabajo hecho mientras dormías (23:20 → ~08:30). Estado vivo por paquete: `ESTADO_IMPL.md`; reglas: `PROTOCOLO_1A1.md`. Rama: `feat/pulido-v1` en `C:\kw\pulido` (solo commits locales, sin push, sin tocar master).

## Qué quedó hecho (integrado y compilando junto)
| Paquete | Puntos tuyos | Estado |
|---|---|---|
| Cimientos de profundidad | 1:1 de sombras, vidrio, degradados | Sombras de caída 0,65–0,82/255 de error medio, interiores ≤ 0,04, resplandores 1–1,5, degradados 0,07–0,55; vidrio real: ver «Lo que no está igual» |
| Cimientos de movimiento | 1:1 de animaciones | 274 fotogramas contra el lienzo: curvas y duraciones ≤ 0,04 dp, 32 de 33 familias ≤ 1,94/255 |
| Editor (series, lista, islas, barra, borrar, ordenar, hojas) | 3b, 10, 12, 12b | 101 filas de la matriz cerradas, 16 con desviación |
| Cardio del editor | 5, 6, 7, 8, 9, 11, 26 | 18 filas cerradas, 7 con desviación, 8 abiertas del marco del editor |
| Asistente de sesión | 4 | 73 filas cerradas, 1 desviación |
| Roadmap B/A | 14 | 23 cerradas, 35 desviaciones (casi todas reglas del roadmap viejo sin escena) |
| Tarjeta en vivo (reps, esfuerzo, ROM) | 13, 15 | 72 cerradas, 40 desviaciones |
| En vivo, el resto (tiempo, completada, Lista, visual) | 16, 17, 18, 19 | 50 cerradas, 19 desviaciones |
| Reparto: motor y datos + interfaz tanda 1 | 23 (parcial) | 188 pruebas JVM; catálogo, vista previa, revisión, resumen y deshacer |

## Lo que NO está hecho
`cv` cardio en vivo (25), `tc` técnica (2), `ct` catálogo (3), `nc` comida y cuerpo (20, 21: quedan los arreglos directos ya hechos), `sp` suplementos (24), `aj` ajustes (22) y la tanda 2 del reparto (la hoja «Cómo aplicar» sigue siendo la de siempre y aplica por su cuenta). Motivo: la cuota semanal llegó al 91 % y la regla de relevo es parar al 94 %; la cola de Gradle de una sola ranura (10–30 min por corrida) fue el otro límite.

## Lo que no está igual (desviaciones y límites)
- Ninguna escena llega al objetivo de 2,5/255 de error medio en las hojas de Robolectric (7–25/255): suelo de texto Chrome/Skia, el marco y la franja de estado del lienzo, y vidrio sin desenfoque; a ojo, la estructura coincide en lo que miré (lista del editor, semana del reparto).
- **Vidrio real** (emulador del Flip5): desenfoque, bordes y geometría coinciden; el color sale más saturado (11–20/255 dentro del vidrio sobre franjas saturadas). Abierto: calibrar en dispositivo (`DESV-100`).
- La «Ruta» del roadmap y las hojas del reparto no usan aún la hoja del lienzo (`KpknSheetSpec` ya existe y el editor la usa): `DESV-653`, `DESV-1000`.
- Margen inferior del roadmap (`DESV-655`), «Listo» del temporizador empuja el contenido (`DESV-701`), teclado del sistema en lugar del de maqueta (`DESV-506`).
- 4 pruebas rojas de render de la cara de medios de la tarjeta (`LiveCardRenderSheetMediaTest`) sin saber si ya fallaban en master; una prueba de geometría que solo corre con `KPKN_RENDER_DIR` (`serieCheckGeometria360`: la base de la tarjeta queda a 645 dp frente a 652 dp del cajón).
- Sin probar en el Flip real (no estaba conectado): gestos del carrusel, borrar/ordenar con el dedo, vibración de la tira, tacto 48 dp, fuego del esfuerzo.

## Decisiones que tomé por ti (con su razón)
1. **No unificar los 20 pares de sombras casi iguales**: cambiaría sombras de un lienzo que ya aprobaste.
2. **El preset nombra; se dibuja el valor exacto de cada fila**: la tolerancia del catálogo no debe redondear nada en pantalla.
3. **Vidrio calibrado para Android 12+**; debajo, velo sin desenfoque (Haze solo desenfoca desde API 31).
4. **Roadmap B desde 820 dp de alto de pantalla** (mide `screenHeightDp`; el Flip5 con 880 dp sale B).
5. **Objetivos táctiles de 48 dp sin cambiar el tamaño visual** (los tomé de la regla de accesibilidad aunque el lienzo dibuje 44).
6. **Cuota**: no lancé más paquetes y pedí a los últimos agentes que cerraran con su próxima corrida; al 94 % paro.
7. **Teléfono**: no estaba conectado; usé un emulador con la pantalla exacta del Flip (1080×2640 a 480 dpi).
8. Cardio del editor: «Avanzado» desaparece entero sin migrar datos; avisos Sonidos/Voz/Vibración por cardio también en Intervalos (3 campos nuevos en `CardioDetails`).
9. Tarjeta en vivo: el ROM solo se anota y se muestra (no excluye de PR ni de progresión); «Guardar también en el programa» solo escribe `trackRom` en la sesión de la semana en curso.
10. En vivo, el resto: borré `Cal3SummaryUiTest` (el resumen de calibración no está en el lienzo y la decisión se sigue guardando); el reloj libre usa una alarma del sistema para el fin del temporizador.
11. Reparto: solo las sesiones hechas ocupan un día del reparto; deshacer guarda un nivel (el tercer cambio pierde el más viejo); `targetMinutes` queda en null.
12. Asistente: «Reglas» pasó a ser la 4.ª pestaña; se eliminaron Contenido, `RulesSheet` y `RulesTimeTab`.

## Cómo seguir
1. Revisar en el Flip el APK «KPKN Pulido v1 - KPKN Dev» (instala aparte, id `.dbg`, no toca tus datos).
2. Con cuota nueva (reinicio el 3-nov 04:00 hora local): `cv`, luego `ct`, `rp` tanda 2, `sp`, `aj`, `nc`, `tc`; después calibrar el color del vidrio en dispositivo y cerrar DESV-653/1000.
3. Integrar a master por turnos (`turno.sh`, `INTEGRACION_GUIA.md`) cuando lo apruebes.
