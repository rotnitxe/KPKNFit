# Paquete AJUSTES · prefijo `aj` · pestaña `ajustes` · `maps/ajustes.md` + `maps/ajustes-inventario.md`
Lee antes `BRIEF_COMUN.md`. Punto del usuario: **22**.

## Palabras del usuario (literal)
> 22. Ajustes de la app necesita y rediseño completo, y que sea configurable solo lo estrictamente necesario. Hay opciones que no tienen sentido o huelen a relleno: descanso porr defecto se trabaja en el editor de sesiones, iniciar descanso al registrar una serie es una opcion de mierda que no tiene por que estar ahí, hay que eliminarla, "MI MATERIAL" es una mierda. Es decir, está lleno de cosas que no tienen sentido. Debe ser un apartado que se configure lo justo y necesario y tenga sentido, que no repita lo que se ve en otras pantalla y debe ajustarse al diseño actual de KPKN, por tanto necesito un prototipo.

## Dónde está hoy (léelo)
`screens/settings/` (TODA la carpeta), `data/` de preferencias (DataStore/SharedPreferences: `UserPreferences`…), y quién consume cada preferencia en el resto
de la app. Regla del usuario del 28-oct: **las mejoras son el sistema, no interruptores**; nada de «beta» ni «modo sombra».

## PRIMERO: inventario → `maps/ajustes-inventario.md`
Tabla con TODOS los ajustes actuales (incluye subpantallas): nombre en pantalla · dónde está en el código · valor por defecto · quién lo lee (archivo:línea)
· ¿se repite en otra pantalla? · **veredicto**: SE QUEDA (por qué es estrictamente necesario) / SE MUEVE (a qué pantalla, p. ej. el descanso por defecto
ya se configura en el editor de sesiones) / SE ELIMINA (por qué; qué hace la app sin él y si borrar exige migración). El usuario nombró expresamente para
eliminar: «descanso por defecto» (vive en el editor), «iniciar descanso al registrar una serie», «MI MATERIAL» (investiga su uso real: si algo del motor
lo necesita —sugerencias, sustituciones, equipamiento—, di dónde debería vivir ese dato, p. ej. perfil/onboarding, y qué se rompería al quitarlo).
Para el resto, tú propones: lo que no tiene consumidor, lo que repite lo que ya se ve en otra pantalla, o lo que es relleno, sale. Lo que se conserva debe ser
lo que solo tiene sentido en un sitio global: cuenta/perfil y unidades, apariencia/idioma si existen, avisos y permisos del sistema (notificaciones,
alarmas exactas, micrófono, ubicación), copia de seguridad/exportar/importar, integraciones (Health Connect/cuentas), sobre la app… — **decide con lo que
exista de verdad**, no con esta lista.

## Qué debe contener el prototipo
Pantalla principal de Ajustes (pocas secciones, nada de listas largas de interruptores), cada subpantalla que sobreviva, los controles reales (interruptor,
selector de unidades, hoja de elección, confirmación de acciones destructivas como borrar datos), estados de permisos (concedido/denegado/abrir ajustes del
sistema), y la «tabla de veredictos» como escena-documento (`doc:()=>html`, tipo las de `arreglos-v1/src/scenes-reglas.js`) para que el usuario apruebe qué
se queda, se mueve o se elimina. Estilo: el diseño actual de KPKN (negro continuo, contenedores con criterio, sin tarjetas dentro de tarjetas, cabecera
con título grande Syne, filas con icono de línea y valor a la derecha), 360 × 880 / 740, texto 130 %.
