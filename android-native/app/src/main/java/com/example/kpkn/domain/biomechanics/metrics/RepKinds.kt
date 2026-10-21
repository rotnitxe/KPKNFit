package com.example.kpkn.domain.biomechanics.metrics

/**
 * Clase de repetición (§3.5): FULL llega al extremo lejano y vuelve (a ≥ 0,70); PARTIAL recorre entre el 30 % y el 70 %;
 * FAILED se queda a medio camino con la barra parada (el intento que no sube, o la vuelta que no sale del fondo).
 */
enum class RepKind { FULL, PARTIAL, FAILED }

/**
 * Dónde cae una repetición PARCIAL. FAR_END: llega al extremo lejano pero no vuelve al reposo (parciales «en estiramiento»,
 * rebotando abajo en la sentadilla). REST_END: no llega al extremo lejano.
 *
 * M4: sale del mismo criterio que [PartialZone] (el extremo que no se alcanza): FAR_END se queda junto al giro y REST_END
 * junto al reposo.
 *
 * Ojo: en el generador sintético es al revés de lo que dice el nombre: PARTIAL_FAR (0→55 %) es REST_END aquí, y
 * PARTIAL_REST (45⇄100 %) es FAR_END aquí.
 */
enum class PartialKind { FAR_END, REST_END }

/**
 * Zona muscular de una repetición PARCIAL (M4): [STRETCHED] se queda en la parte de estiramiento (lejos del final de la
 * concéntrica, con el músculo largo) y [SHORTENED] en la parte acortada. Sale del extremo que la repetición no alcanza y de
 * la fase con la que arranca el perfil: la excéntrica acaba en el estiramiento, así que en un perfil que arranca con la
 * excéntrica (sentadilla, banca) el giro es el estiramiento y el reposo la parte acortada; en uno que arranca con la
 * concéntrica (curl, jalón) es al revés. Se guarda por nombre: no reordenar ni renombrar.
 */
enum class PartialZone { STRETCHED, SHORTENED }
