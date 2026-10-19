package com.example.kpkn.domain.biomechanics.metrics

/**
 * Clase de repetición (§3.5): FULL llega al extremo lejano y vuelve (a ≥ 0,70); PARTIAL recorre entre el 30 % y el 70 %;
 * FAILED se queda a medio camino con la barra parada (el intento que no sube, o la vuelta que no sale del fondo).
 */
enum class RepKind { FULL, PARTIAL, FAILED }

/**
 * Dónde cae una repetición PARCIAL. FAR_END: llega al extremo lejano pero no vuelve al reposo (parciales «en estiramiento»,
 * rebotando abajo en la sentadilla). REST_END: no llega al extremo lejano (pico < 85 % de la amplitud).
 *
 * Ojo: en el generador sintético es al revés de lo que dice el nombre: PARTIAL_FAR (0→55 %) es REST_END aquí, y
 * PARTIAL_REST (45⇄100 %) es FAR_END aquí.
 */
enum class PartialKind { FAR_END, REST_END }
