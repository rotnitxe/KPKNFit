# Paquete REPARTO · prefijo `rp` · pestaña `reparto` · `maps/reparto.md` + `maps/reparto-motor.md`
Lee antes `BRIEF_COMUN.md`. Punto del usuario: **23**.

## Palabras del usuario (literal)
> 23. En "reparto" para cambiar de split una semana, necesito que las opciones tengan una breve descripción de qué tratan y también mas opciones interactivas, por ejemplo, para que el usuario vea como se va a distribuir sus sesiones (deberíamos tener un motor que permita que los ejercicios migren al nuevo sistema sin perjudicar la lógica y objetivos del usuario, así no tiene que crear series de nuevo).

## Dónde está hoy (léelo)
Busca «Reparto» y «split» en `screens/programdetail/` (vista Estructura de Program Detail, por semana), `screens/programs/`, `screens/sessioneditor/` y en
`domain/training/` (motores que reparten músculos por días: `SessionCompositionPolicy`, generadores de plan, `ProgramStructure…`). Entiende: qué splits existen
hoy (Full body, Upper/Lower, PPL, Torso-Pierna, Bro split… lee los reales), qué datos tiene cada semana (días de entreno, sesiones, ejercicios, series/reps/
intensidad), qué ocurre hoy al cambiar el reparto de una semana (¿regenera? ¿pierde series? ¿qué avisa?), y qué grupos musculares/objetivos guía el plan.
Capturas: no hay (revisa el código); la hoja de Estructura aparece en `h03-plantillas-metodos` (al fondo, las pestañas Semana · Progreso · Estructura).

## Qué debe contener el prototipo
1. **Selector de reparto** (hoja o pantalla) con TODAS las opciones reales, cada una con **descripción breve** (qué es, para quién, frecuencia por músculo,
   días por semana que requiere) y un mini diagrama de la distribución; filtros por días disponibles.
2. **Vista previa interactiva**: una semana de 7 días con las sesiones tal como quedarán; al elegir un reparto la vista se transforma (animación), muestra
   qué grupos musculares caen en cada día, el volumen semanal por músculo antes/después y avisos («pierdes frecuencia en hombro», «este reparto necesita 5
   días y tu semana tiene 4»). Permite mover sesiones entre días (arrastrar) y ver el impacto en tiempo real.
3. **Migración sin rehacer series**: cómo se ven los ejercicios al pasar al nuevo reparto: cada ejercicio conserva sus series, repeticiones, intensidad y
   descansos; qué se mueve (de qué sesión a cuál), qué se fusiona o se divide, qué queda sin sitio y se propone (con decisión del usuario). Pantalla de
   **revisión antes/después** (diferencias por sesión, ejercicios movidos resaltados, volumen por músculo conservado) con «Aplicar» y «Deshacer».
4. **Motor** (`maps/reparto-motor.md`): diseña el algoritmo de migración con reglas explícitas (mapear ejercicios a patrón/músculo principal, asignar a
   sesiones destino, conservar volumen semanal por músculo y progresiones del programa, respetar superseries y grupos, fatiga/orden), datos de entrada y
   salida, casos borde (semana con sesiones registradas/completadas, deload, cardio, ejercicios sin músculo claro), qué tests exigirá (en pseudocódigo/JSON
   de ejemplo) y cuánto cuesta. Es el contrato para el implementador: sé concreto.
5. Estados: semana vacía, semana con sesiones completadas (qué se bloquea), reparto no compatible con los días elegidos, confirmación, aplicado (resumen),
   texto 130 % y 360 × 740.
