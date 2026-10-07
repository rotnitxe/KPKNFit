package com.example.kpkn.data.programs

import com.example.kpkn.data.models.ProgramMode

/**
 * Paquete C · C.P6 (curaduría de programas, 2026-10-03) — cómo se llama y de qué modo es el programa que sale de
 * una entrada del catálogo. Es la ÚNICA fuente: el asistente, la biblioteca y el generador nativo la usan, así que
 * el mismo plan se llama igual lo cree quien lo cree.
 *
 * Antes cada camino escribía el suyo: el asistente ponía «Plan de {nombre de la persona}» a las plantillas y a los
 * métodos (el nombre del plan desaparecía del detalle del programa), la biblioteca ponía el nombre crudo de la
 * plantilla y todos los métodos de la biblioteca salían con el modo de powerlifting, también PHUL (powerbuilding) o
 * una rutina de hipertrofia.
 *
 * Dominio puro: sin Android.
 */

/** Nombre del programa: el de la ficha editorial de [entry]. Nunca un id, un prefijo ni «Plan de {persona}». */
fun programNameFor(entry: CatalogEntry): String = entry.displayName

/**
 * Modo del programa según la disciplina que declara la entrada ([CatalogEntry.references]): powerlifting →
 * [ProgramMode.POWERLIFTING], powerbuilding → [ProgramMode.POWERBUILDING] y todo lo demás —hipertrofia, los planes
 * nativos sin disciplina y Atleta completo— → [ProgramMode.HYPERTROPHY]. Si una entrada declara varias, gana la de
 * más fuerza (powerlifting antes que powerbuilding).
 *
 * Coincide con el modo que el generador nativo fijaba por perfil (`NativeProfileKind`): Fuerza → powerlifting,
 * Fuerza y músculo → powerbuilding, Músculo y Atleta completo → hipertrofia.
 *
 * Los programas «a medida» del generador de rutinas (Entreno v2) llevan el modo que fija el propio generador: sus
 * referencias ya lo dicen (powerlifting, powerbuilding, culturismo) salvo Strongman y la base de halterofilia, que no
 * son una disciplina del catálogo de planes pero se entrenan como fuerza máxima (modo powerlifting).
 */
fun programModeFor(entry: CatalogEntry): ProgramMode = when {
    entry.isGenerated && entry.sourceId in GENERATED_MAX_STRENGTH_SOURCES -> ProgramMode.POWERLIFTING
    TrainingReference.POWERLIFTING in entry.references -> ProgramMode.POWERLIFTING
    TrainingReference.POWERBUILDING in entry.references -> ProgramMode.POWERBUILDING
    else -> ProgramMode.HYPERTROPHY
}

/** Programas «a medida» que el generador arma como fuerza máxima sin ser una disciplina del catálogo de planes. */
private val GENERATED_MAX_STRENGTH_SOURCES = setOf("strongman", "weightlifting-base")
