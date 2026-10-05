package com.example.kpkn.data.programs

import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.protocols.definitions.AuthoredSourceRecord
import com.example.kpkn.data.protocols.definitions.AuthoredSources

/**
 * Tabla editorial de las 55 entradas del catálogo (diseño editorial §2): la
 * ÚNICA fuente de texto de usuario, orden y niveles. `PersonalizedPlanCatalog`
 * no escribe títulos, subtítulos ni descripciones: los lee de aquí, y el test
 * `PlanCatalogEditorialContractTest` exige una correspondencia exacta entre los
 * ids de esta tabla y los de `PersonalizedPlanCatalog.entries()`.
 *
 * Reglas del texto: español llano, sin ids ni inglés gratuito, resúmenes de 2 a
 * 4 frases, y los términos técnicos (máximo de entrenamiento, máximo de
 * repeticiones, repeticiones en reserva…) se explican en la misma frase.
 *
 * C.P2b (decisión D2, DEC-w2-07): dos campos de ficha dejan de ser inertes.
 * - `listed = false` en cinco nativos históricos (`full-body`, `gym-muscle`,
 *   `one-day`, `return-training`, `home-training`): desaparecen del planner y de
 *   la biblioteca, pero siguen en `entries()` y `find()` (programas ya activados,
 *   `lookup`, ruta histórica del personalizador).
 * - `references` (override de disciplina) solo en dos fichas: `native:strength-cardio`
 *   con `emptySet()` (sale del objetivo Músculo; el modo mixto sigue funcionando
 *   porque `schedulesCardio` no depende de las referencias) y `protocol:wendler-531-bbb`
 *   con powerlifting y powerbuilding. El resto de fichas llevan `references = null`
 *   y conservan la disciplina que calcula `PersonalizedPlanCatalog`.
 */
internal object PlanEditorialTable {
    private const val OWN_PLAN = "Plan propio de KPKN."

    private val ALL_LEVELS: Set<CatalogLevel> = CatalogLevel.entries.toSet()
    private val ONLY_BEGINNER: Set<CatalogLevel> = setOf(CatalogLevel.BEGINNER)
    private val ONLY_INTERMEDIATE: Set<CatalogLevel> = setOf(CatalogLevel.INTERMEDIATE)
    private val ONLY_ADVANCED: Set<CatalogLevel> = setOf(CatalogLevel.ADVANCED)

    // «Notas del método» (§2.4): sustituyen a las «Notas KPKN» técnicas.
    private const val NOTE_TEXAS3 =
        "En lugar de la cargada de potencia usamos remo Pendlay explosivo."
    private const val NOTE_WESTSIDE =
        "En los días dinámicos usamos solo la barra, sin bandas."
    private const val NOTE_CUBE =
        "Cada tipo de día lleva siempre el mismo levantamiento."
    private const val NOTE_SMOLOV =
        "Solo trabaja la sentadilla, más dos accesorios de espalda."
    private const val NOTE_SMOLOV_JR =
        "En origen es un programa de banca; aquí solo trabaja la sentadilla, más dos accesorios de espalda."
    private const val NOTE_LILLIEBRIDGE =
        "Los porcentajes semanales son los de KPKN: la hoja original no es pública."
    private const val NOTE_KORTE =
        "Sentadilla y peso muerto con 8×5 el mismo día, como en el método."
    private const val NOTE_SHEIKO =
        "Repite el mismo levantamiento varias veces en la sesión, como en el método."
    private const val NOTE_NSUNS =
        "9 series del principal y 8 del complementario el mismo día, como en el método."
    private const val NOTE_COAN =
        "Se suma a tu plan."
    private const val NOTE_LEGACY =
        "Sustituida por la versión original."

    /**
     * Ficha de la entrada [id]. La tabla es la fuente de verdad: una entrada sin ficha es un
     * error de datos, no un caso a tolerar.
     */
    fun forId(id: String): PlanEditorial =
        byId[id] ?: error("Falta la ficha editorial de «$id» en PlanEditorialTable")

    val byId: Map<String, PlanEditorial> = buildMap {
        fun add(id: String, editorial: PlanEditorial) {
            check(put(id, editorial) == null) { "Id duplicado en PlanEditorialTable: $id" }
        }

        // ── Planes propios de KPKN (§2.1): los cuatro perfiles de seis semanas ──
        add(
            "native:strength-foundation-v2",
            own(
                displayName = "Fuerza KPKN",
                summary = "Sentadilla, banca y peso muerto con barra todas las semanas, con series de técnica y de " +
                    "fuerza. Son seis semanas, la última más ligera para recuperarte; cuando completas el " +
                    "máximo de repeticiones dos veces, el plan te propone subir la carga. Necesitas barra con " +
                    "discos, rack y banco.",
                rank = 100,
                terms = setOf(PlanTerm.SBD, PlanTerm.AMRAP, PlanTerm.DELOAD),
            ),
        )
        add(
            "native:muscle-foundation-v2",
            own(
                displayName = "Músculo KPKN",
                summary = "Entrenamiento para ganar músculo con rangos de repeticiones y esfuerzo controlado: " +
                    "siempre dejas repeticiones en reserva. Son seis semanas, la última más ligera. Funciona " +
                    "con mancuernas, bandas o solo tu peso corporal; si no tienes nada para tirar (remos, " +
                    "jalones, dominadas), te avisamos en lugar de inventar ejercicios de espalda.",
                rank = 110,
                terms = setOf(PlanTerm.RIR, PlanTerm.DELOAD),
            ),
        )
        add(
            "native:powerbuilding-foundation-v2",
            own(
                displayName = "Fuerza y músculo KPKN",
                summary = "Ejercicios principales de pocas repeticiones para ganar fuerza y trabajo muscular para " +
                    "ganar volumen, durante seis semanas con la última más ligera. Con barra, rack y banco " +
                    "entrenas sentadilla, banca y peso muerto; con mancuernas haces versiones equivalentes, " +
                    "que no preparan para competir.",
                rank = 120,
                terms = setOf(PlanTerm.POWERBUILDING, PlanTerm.SBD, PlanTerm.DELOAD),
            ),
        )
        add(
            "native:complete-athlete-v2",
            own(
                displayName = "Atleta completo KPKN",
                summary = "Cada semana combina fuerza, músculo, ejercicios explosivos y cardio, repartidos según " +
                    "tus días disponibles. La fuerza puede trabajarse con tu peso corporal y los ejercicios " +
                    "explosivos se hacen rápido de verdad, sin series pesadas y lentas. Son seis semanas, la " +
                    "última más ligera.",
                rank = 130,
                terms = setOf(PlanTerm.SPEED, PlanTerm.DELOAD),
            ),
        )

        // ── Familias nativas históricas (§2.2): relegadas al final del orden ──
        // C.P2b (D2): `machine-muscle`, `bodyweight` y `strength-cardio` siguen listados (relegados);
        // `home-training`, `gym-muscle`, `full-body`, `return-training` y `one-day` llevan
        // `listed = false`: no se ofrecen en el planner ni en la biblioteca, pero `entries()` y
        // `find()` los conservan para los programas ya activados y los ids antiguos.
        add(
            "native:machine-muscle",
            own(
                displayName = "Construye músculo con máquinas",
                summary = "Una semana de entrenamiento con máquinas de trayectoria guiada que repites cada semana. " +
                    "Solo incluye ejercicios en máquina: no añade barras, mancuernas ni poleas. Ajustamos las " +
                    "series a tu experiencia y al tiempo que tengas por sesión.",
                rank = 600,
                terms = setOf(PlanTerm.CYCLE),
            ),
        )
        add(
            "native:bodyweight",
            own(
                displayName = "Músculo con tu peso corporal",
                summary = "Una semana que repites con ejercicios de peso corporal, más fáciles o más difíciles " +
                    "según tu nivel. Los de tirón (dominadas, remos invertidos) necesitan una barra fija o un " +
                    "apoyo estable; si no los tienes, te lo indicamos.",
                rank = 610,
                terms = setOf(PlanTerm.CYCLE),
            ),
        )
        add(
            "native:home-training",
            own(
                displayName = "Entrena en casa sin gimnasio",
                summary = "Una semana que repites con tus bandas, mancuernas y peso corporal. Si falta material " +
                    "para algún movimiento te lo indicamos, en lugar de cambiarlo sin avisarte. Ajustamos las " +
                    "series a tu experiencia y a tu tiempo.",
                rank = 620,
                terms = setOf(PlanTerm.CYCLE),
                listed = false,
            ),
        )
        add(
            "native:gym-muscle",
            own(
                displayName = "Construye músculo en el gimnasio",
                summary = "Una semana de gimnasio que repites, con 3 a 6 días. Reparte el trabajo entre torso y " +
                    "piernas y da prioridad a la zona que quieras desarrollar, sin abandonar el resto del " +
                    "cuerpo.",
                rank = 630,
                terms = setOf(PlanTerm.CYCLE),
                listed = false,
            ),
        )
        add(
            "native:full-body",
            own(
                displayName = "Empieza con todo el cuerpo",
                summary = "Una semana de gimnasio que repites y trabaja los principales grupos musculares, con 2 o " +
                    "3 días. Ajustamos las series a tu experiencia y al tiempo que tengas por sesión.",
                rank = 640,
                terms = setOf(PlanTerm.CYCLE),
                listed = false,
            ),
        )
        add(
            "native:return-training",
            own(
                displayName = "Vuelve a entrenar",
                summary = "Una semana que repites con pocas series para retomar la constancia sin agotarte. No sube " +
                    "el volumen por sí sola: cuando te sientas listo, cambia a otro plan. Sirve con 2 o 3 " +
                    "días por semana.",
                rank = 650,
                terms = setOf(PlanTerm.CYCLE),
                listed = false,
            ),
        )
        add(
            "native:one-day",
            own(
                displayName = "Aprovecha un solo día",
                summary = "Una sesión completa a la semana para cuando tienes poco tiempo. Priorizamos lo esencial " +
                    "de tu entrenamiento, sin prometer los resultados de un plan de varios días.",
                rank = 660,
                listed = false,
            ),
        )
        // C.P2b: references = ∅ saca esta entrada del objetivo Músculo (antes HYPERTROPHY). El modo
        // mixto legacy sigue funcionando: `schedulesCardio` depende del id, no de las referencias.
        add(
            "native:strength-cardio",
            own(
                displayName = "Músculo y cardio (versión anterior)",
                summary = "Una semana que repites con series de trabajo muscular y un bloque de cardio moderado en " +
                    "cada sesión; tú eliges el tipo y los minutos. Se mantiene por compatibilidad: para " +
                    "combinar fuerza, músculo y cardio es mejor Atleta completo KPKN.",
                rank = 670,
                terms = setOf(PlanTerm.CYCLE),
                references = emptySet(),
            ),
        )

        // ── Plantillas de programa (§2.3) ──
        add(
            "template:power-12-3",
            own(
                displayName = "Powerlifting para principiantes",
                summary = "12 semanas, 3 días por semana: un día de sentadilla, uno de banca y uno de peso muerto. " +
                    "Empiezas con cargas moderadas de 5 repeticiones y terminas con series pesadas de 2. " +
                    "Las semanas 4 y 8 son de descarga, con menos series. Para quien empieza en powerlifting " +
                    "(fuerza máxima en sentadilla, banca y peso muerto).",
                rank = 310,
                levels = ONLY_BEGINNER,
                terms = setOf(PlanTerm.POWERLIFTING, PlanTerm.SBD, PlanTerm.DELOAD),
            ),
        )
        add(
            "template:power-16-4",
            own(
                displayName = "Powerlifting intermedio",
                summary = "16 semanas, 4 días por semana: sentadilla, banca, peso muerto y un segundo día de banca. " +
                    "Primero variantes de los levantamientos (como sentadilla a cajón o peso muerto en " +
                    "déficit), luego series de fuerza, un pico de pocas repeticiones y dos semanas finales de " +
                    "descarga y prueba de máximos.",
                rank = 311,
                levels = ONLY_INTERMEDIATE,
                terms = setOf(PlanTerm.POWERLIFTING, PlanTerm.SBD, PlanTerm.DELOAD),
            ),
        )
        add(
            "template:power-20-5",
            own(
                displayName = "Powerlifting avanzado",
                summary = "20 semanas, 5 días por semana: sentadilla, banca, peso muerto, un segundo día de banca y " +
                    "un día técnico. Cinco fases (volumen, intensidad, específico, pico y descarga final con " +
                    "prueba de máximos), con variantes de los levantamientos que cambian en cada fase.",
                rank = 312,
                levels = ONLY_ADVANCED,
                terms = setOf(PlanTerm.POWERLIFTING, PlanTerm.SBD, PlanTerm.DELOAD),
            ),
        )
        add(
            "template:powerbuild-16-4",
            own(
                displayName = "Fuerza y músculo avanzado",
                summary = "16 semanas, 4 días por semana: sentadilla, un día de torso para músculo, peso muerto y " +
                    "banca pesada. Cuatro fases de cuatro semanas: volumen, fuerza, volumen moderado y pico " +
                    "de pocas repeticiones. Las semanas 4, 8 y 12 son de descarga, con menos series. " +
                    "Para quien ya entrena con constancia.",
                rank = 320,
                levels = ONLY_ADVANCED,
                terms = setOf(PlanTerm.POWERBUILDING, PlanTerm.DELOAD),
            ),
        )
        add(
            "template:body-12-3",
            own(
                displayName = "Torso y pierna para ganar músculo",
                summary = "12 semanas, 4 días por semana: dos de torso y dos de pierna. Cinco semanas de volumen " +
                    "con menos repeticiones en reserva cada vez, una de descarga, cinco de intensidad y una " +
                    "descarga final. Para quien ya entrena y busca músculo.",
                rank = 330,
                levels = ONLY_INTERMEDIATE,
                terms = setOf(PlanTerm.UL_PPL, PlanTerm.RIR, PlanTerm.DELOAD),
            ),
        )
        add(
            "template:body-16-4",
            own(
                displayName = "Empuje, tirón y pierna",
                summary = "16 semanas, 6 días por semana: empuje, tirón y pierna, cada uno dos veces. El esfuerzo " +
                    "cambia por fases, dejando entre 1 y 3 repeticiones en reserva en las semanas de trabajo. " +
                    "Las semanas 4, 8, 12 y 16 son de descarga, con menos series y más repeticiones en reserva.",
                rank = 331,
                levels = ONLY_ADVANCED,
                terms = setOf(PlanTerm.UL_PPL, PlanTerm.RIR, PlanTerm.DELOAD),
            ),
        )
        add(
            "template:body-20-5",
            own(
                displayName = "Músculo en bloques con énfasis",
                summary = "20 semanas, 5 días por semana de empuje, tirón y pierna, en bloques de cuatro semanas: " +
                    "base, volumen, énfasis en torso, énfasis en pierna y una fase final más exigente. " +
                    "Las semanas 6, 12 y 18 son de descarga, con menos series. Para quien ya entrena mucho " +
                    "y quiere variar el foco.",
                rank = 332,
                levels = ONLY_ADVANCED,
                terms = setOf(PlanTerm.UL_PPL, PlanTerm.DELOAD),
            ),
        )
        add(
            "template:simple-1",
            own(
                displayName = "Semana en blanco",
                summary = "Una estructura de una semana que se repite. Se rellena con sesiones sugeridas según el " +
                    "reparto semanal que elijas; después cambias ejercicios, series y cargas como quieras.",
                rank = 950,
                kind = PlanKind.ESTRUCTURA,
                terms = setOf(PlanTerm.CYCLE),
            ),
        )
        add(
            "template:simple-ab",
            own(
                displayName = "Dos semanas alternas (A/B)",
                summary = "Dos semanas distintas que se alternan: la A y la B. Se rellenan con sesiones sugeridas " +
                    "según tu reparto semanal y después las editas libremente.",
                rank = 951,
                kind = PlanKind.ESTRUCTURA,
            ),
        )
        add(
            "template:simple-4",
            own(
                displayName = "Cuatro semanas en blanco",
                summary = "Un bloque de cuatro semanas para organizar tu propia progresión. Se rellena con sesiones " +
                    "sugeridas según tu reparto semanal; tú decides los cambios de una semana a otra.",
                rank = 952,
                kind = PlanKind.ESTRUCTURA,
            ),
        )

        // ── Métodos de tercero (§2.4): versión KPKN del método publicado ──
        add(
            "protocol:texas-method-3d",
            thirdParty(
                displayName = "Texas Method (3 días)",
                summary = "Ciclo de 4 semanas que se repite, con 3 días por semana: lunes de mucho volumen (5 " +
                    "series de 5), miércoles ligero para recuperar y viernes con un intento de récord a 5 " +
                    "repeticiones. Para quien ya no progresa en cada sesión con un plan de principiante. " +
                    "Necesitas barra, rack y banco.",
                rank = 400,
                level = CatalogLevel.INTERMEDIATE,
                methodName = "Texas Method",
                authors = "Mark Rippetoe y Glenn Pendlay",
                notes = listOf(NOTE_TEXAS3),
                terms = setOf(PlanTerm.DUP, PlanTerm.CYCLE, PlanTerm.POWERLIFTING),
            ),
        )
        add(
            "protocol:texas-method-4d",
            thirdParty(
                displayName = "Texas Method (4 días)",
                summary = "Ciclo de 4 semanas que se repite, con 4 días: banca, sentadilla, press y peso muerto " +
                    "tienen cada uno un día pesado y otro de volumen más ligero. Para quien ya no progresa " +
                    "cada sesión con un plan de principiante. Necesitas barra, rack y banco.",
                rank = 401,
                level = CatalogLevel.INTERMEDIATE,
                methodName = "Texas Method",
                authors = "Andy Baker y Mark Rippetoe",
                terms = setOf(PlanTerm.DUP, PlanTerm.CYCLE),
            ),
        )
        // C.P2b: references = PL+PB (antes solo PL) para que aparezca también en «Fuerza y músculo».
        add(
            "protocol:wendler-531-bbb",
            thirdParty(
                displayName = "5/3/1 Boring But Big",
                summary = "Ciclos de 4 semanas (de 5, de 3 y de 1 repeticiones, y descarga), con 4 días: " +
                    "sentadilla, banca, peso muerto y press. En las tres primeras, tras el levantamiento " +
                    "principal (con una última serie al máximo de repeticiones), haces 5 series de 10 al 50 % " +
                    "de tu máximo de entrenamiento; la descarga no las lleva. Cada ciclo ese máximo sube " +
                    "2,5 kg (banca y press) o 5 kg.",
                rank = 410,
                level = CatalogLevel.INTERMEDIATE,
                methodName = "5/3/1 Boring But Big",
                authors = "Jim Wendler",
                terms = setOf(
                    PlanTerm.AMRAP,
                    PlanTerm.DELOAD,
                    PlanTerm.CYCLE,
                    PlanTerm.POWERLIFTING,
                    PlanTerm.POWERBUILDING,
                ),
                references = setOf(TrainingReference.POWERLIFTING, TrainingReference.POWERBUILDING),
            ),
        )
        add(
            "protocol:wendler-531-fsl",
            thirdParty(
                displayName = "5/3/1 First Set Last",
                summary = "Las mismas olas de 5/3/1, pero tras el levantamiento principal haces 5×5 con el peso de " +
                    "tu primera serie de trabajo. Tiene menos volumen que Boring But Big y se centra más en " +
                    "la fuerza. 4 días por semana, ciclos de 4 semanas; la cuarta es de descarga y no lleva " +
                    "esos 5×5.",
                rank = 411,
                level = CatalogLevel.INTERMEDIATE,
                methodName = "5/3/1 First Set Last",
                authors = "Jim Wendler",
                terms = setOf(PlanTerm.CYCLE, PlanTerm.POWERLIFTING),
            ),
        )
        add(
            "protocol:madcow-5x5",
            thirdParty(
                displayName = "Madcow 5×5",
                summary = "Ciclo de 4 semanas que se repite, 3 días: lunes de volumen con rampa de 5 series de 5, " +
                    "miércoles más ligero y viernes con una serie pesada de 3 repeticiones. Las cargas suben " +
                    "un 2,5 % cada semana. Al cerrar el ciclo tu máximo de entrenamiento sube 2,5 kg en banca " +
                    "o 5 kg en sentadilla y peso muerto, y la rampa vuelve a empezar.",
                rank = 420,
                level = CatalogLevel.INTERMEDIATE,
                methodName = "Madcow 5×5",
                authors = "Madcow, a partir de Bill Starr",
                terms = setOf(PlanTerm.CYCLE),
            ),
        )
        add(
            "protocol:gzclp",
            thirdParty(
                displayName = "GZCLP",
                summary = "Ciclos de 4 semanas con 4 días: cada día un levantamiento principal (5 series de 3, la " +
                    "última al máximo de repeticiones), uno complementario de 3×10 y accesorios de 15 a 20 " +
                    "repeticiones. Es solo la primera etapa del método de Cody Lefever. Tu máximo de " +
                    "entrenamiento sube al cerrar cada ciclo (2,5 kg banca y press, 5 kg sentadilla y peso " +
                    "muerto), más despacio que el método original.",
                rank = 430,
                level = CatalogLevel.INTERMEDIATE,
                methodName = "GZCLP",
                authors = "Cody Lefever",
                terms = setOf(PlanTerm.TIERS, PlanTerm.AMRAP, PlanTerm.CYCLE),
            ),
        )
        add(
            "protocol:candito-6",
            thirdParty(
                displayName = "Candito 6 semanas",
                summary = "6 semanas, 4 días por semana con sentadilla, banca y peso muerto. Empiezas con dos " +
                    "semanas de series de 6 a 8 repeticiones, pasas a series de 3, luego a dobles pesadas y " +
                    "terminas con simples cerca de tu máximo. La hoja original tiene 5 sesiones en la " +
                    "primera semana; aquí son 4, como en el resto del plan.",
                rank = 435,
                level = CatalogLevel.INTERMEDIATE,
                methodName = "Candito 6 semanas",
                authors = "Jonnie Candito",
                terms = setOf(PlanTerm.SBD),
            ),
        )
        add(
            "protocol:calgary-16",
            thirdParty(
                displayName = "Calgary Barbell 16 semanas",
                summary = "16 semanas, 4 días por semana: sentadilla, banca, peso muerto y banca de volumen. Cuatro " +
                    "fases: series de 5 a 7 repeticiones, series pesadas seguidas de otras más ligeras y, al " +
                    "final, trabajo por esfuerzo percibido con prueba de máximos.",
                rank = 440,
                level = CatalogLevel.INTERMEDIATE,
                methodName = "Calgary Barbell 16 semanas",
                authors = "Bryce Krawczyk",
                terms = setOf(PlanTerm.RPE, PlanTerm.SBD),
            ),
        )
        add(
            "protocol:tsa-9",
            thirdParty(
                displayName = "TSA 9 semanas (intermedio)",
                summary = "9 semanas, 4 días por semana: sentadilla, banca, peso muerto y banca de volumen, con un " +
                    "esquema distinto cada día. Cuatro semanas de volumen, una de descarga, tres de " +
                    "intensidad y una de prueba. Programa de The Strength Athlete.",
                rank = 441,
                level = CatalogLevel.INTERMEDIATE,
                methodName = "TSA 9 semanas",
                authors = "The Strength Athlete",
                terms = setOf(PlanTerm.DELOAD, PlanTerm.SBD),
            ),
        )
        add(
            "protocol:korte-3x3",
            thirdParty(
                displayName = "Korte 3×3",
                summary = "8 semanas, 3 días por semana, con sentadilla, banca y peso muerto en cada sesión. Las 4 " +
                    "primeras semanas haces muchas series ligeras (58–64 % de tu máximo) y las 4 últimas una " +
                    "sola serie pesada por día (80–95 %), rotando el levantamiento.",
                rank = 450,
                level = CatalogLevel.ADVANCED,
                methodName = "Korte 3×3",
                authors = "Stephan Korte",
                notes = listOf(NOTE_KORTE),
                terms = setOf(PlanTerm.ONE_RM, PlanTerm.SBD),
            ),
        )
        add(
            "protocol:lilliebridge",
            thirdParty(
                displayName = "Lilliebridge",
                summary = "10 semanas, 3 días por semana. El lunes alternas una sentadilla pesada (semanas " +
                    "impares) y un peso muerto pesado (semanas pares), con el otro levantamiento más ligero; " +
                    "el miércoles haces banca pesada (simples, o series de 5 con la última al máximo de " +
                    "repeticiones) y el viernes banca de volumen. La semana 10 es una descarga ligera.",
                rank = 451,
                level = CatalogLevel.ADVANCED,
                methodName = "Lilliebridge",
                authors = "Ernie Lilliebridge (familia Lilliebridge)",
                notes = listOf(NOTE_LILLIEBRIDGE),
                terms = setOf(PlanTerm.AMRAP, PlanTerm.DELOAD, PlanTerm.SBD),
            ),
        )
        add(
            "protocol:sheiko-29-32",
            thirdParty(
                displayName = "Sheiko (programas 29 a 32)",
                summary = "16 semanas, 3 días por semana: cuatro programas de Boris Sheiko seguidos (preparación, " +
                    "volumen, intensidad y pico). Cada sesión combina sentadilla, banca y peso muerto con " +
                    "muchas series de pocas repeticiones.",
                rank = 452,
                level = CatalogLevel.ADVANCED,
                methodName = "Sheiko, programas 29 a 32",
                authors = "Boris Sheiko",
                notes = listOf(NOTE_SHEIKO),
                terms = setOf(PlanTerm.SBD),
            ),
        )
        // El texto describe la receta de hoy (cada tipo de día lleva siempre el mismo levantamiento).
        // Si D3 fase 2 implementa la rotación semanal, usar la alternativa de §2.8 del diseño editorial.
        add(
            "protocol:cube-method",
            thirdParty(
                displayName = "Cube Method",
                summary = "10 semanas, 4 días por semana: sentadilla pesada, sentadilla en cajón y banca rápidas, " +
                    "peso muerto por repeticiones y un día de torso para músculo. La carga sube por etapas y " +
                    "la décima semana es de prueba.",
                rank = 453,
                level = CatalogLevel.ADVANCED,
                methodName = "Cube Method",
                authors = "Brandon Lilly",
                notes = listOf(NOTE_CUBE),
                terms = setOf(PlanTerm.SPEED, PlanTerm.SBD),
            ),
        )
        add(
            "protocol:westside-conjugate",
            thirdParty(
                displayName = "Westside (método conjugado)",
                summary = "Ciclo de 3 semanas que se repite, con 4 días. Dos son de máximo esfuerzo (una serie de 2 " +
                    "repeticiones en una variante que rota, de pierna y de torso) y dos de esfuerzo dinámico, " +
                    "con series rápidas y ligeras.",
                rank = 454,
                level = CatalogLevel.ADVANCED,
                methodName = "Westside, método conjugado",
                authors = "Louie Simmons",
                notes = listOf(NOTE_WESTSIDE),
                terms = setOf(PlanTerm.SPEED, PlanTerm.CYCLE),
            ),
        )
        add(
            "protocol:juggernaut-2",
            thirdParty(
                displayName = "Juggernaut Method 2.0",
                summary = "16 semanas en cuatro olas de 4, de 10, 8, 5 y 3 repeticiones, con 4 días. La tercera " +
                    "semana incluye una serie al máximo de repeticiones que puede proponer ajustar tu máximo " +
                    "de entrenamiento; la cuarta es de descarga. Al empezar la siguiente ola, si cumpliste " +
                    "las repeticiones previstas, ese máximo " +
                    "sube 2,5 kg en banca y press y 5 kg en sentadilla y peso muerto.",
                rank = 460,
                level = CatalogLevel.ADVANCED,
                methodName = "Juggernaut Method 2.0",
                authors = "Chad Wesley Smith",
                terms = setOf(
                    PlanTerm.TM,
                    PlanTerm.AMRAP,
                    PlanTerm.DELOAD,
                    PlanTerm.SBD,
                ),
            ),
        )
        add(
            "protocol:nsuns-531-lp-4d",
            thirdParty(
                displayName = "nSuns 5/3/1",
                summary = "4 días por semana, ciclos de 4 semanas: cada día haces 9 series de un levantamiento " +
                    "principal y 8 de uno complementario. En sentadilla, peso muerto y banca pesada, la " +
                    "tercera serie es un intento al 95 % de tu máximo de entrenamiento, hecho al máximo de " +
                    "repeticiones: según cuántas hagas, el plan puede proponerte subir o bajar ese máximo. " +
                    "Es muy exigente.",
                rank = 461,
                level = CatalogLevel.ADVANCED,
                methodName = "nSuns 5/3/1",
                authors = "nSuns",
                notes = listOf(NOTE_NSUNS),
                terms = setOf(PlanTerm.TM, PlanTerm.AMRAP, PlanTerm.CYCLE),
            ),
        )
        add(
            "protocol:gzcl-jt-2",
            thirdParty(
                displayName = "GZCL Jacked & Tan 2.0",
                summary = "12 semanas en dos olas de 6, con 4 días (sentadilla, banca, peso muerto y press). Cada " +
                    "semana buscas el máximo de repeticiones con un peso mayor, de 10 repeticiones a 1, y " +
                    "cada ola termina con una prueba de máximos.",
                rank = 462,
                level = CatalogLevel.ADVANCED,
                methodName = "GZCL Jacked & Tan 2.0",
                authors = "Cody Lefever",
                terms = setOf(PlanTerm.TIERS, PlanTerm.AMRAP),
            ),
        )
        add(
            "protocol:gzcl-rippler",
            thirdParty(
                displayName = "GZCL The Rippler",
                summary = "12 semanas en tres bloques de 4, con 4 días. Haces series pesadas de 2 repeticiones, la " +
                    "última al máximo, cuyo peso sube cada semana y arranca más alto en cada bloque; las dos " +
                    "últimas semanas son de pico.",
                rank = 463,
                level = CatalogLevel.INTERMEDIATE,
                methodName = "GZCL The Rippler",
                authors = "Cody Lefever",
                terms = setOf(PlanTerm.TIERS, PlanTerm.AMRAP),
            ),
        )
        add(
            "protocol:gzcl-uhf-9",
            thirdParty(
                displayName = "GZCL UHF 9",
                summary = "9 semanas, 5 días por semana: sentadilla y banca dos veces (un día pesado y otro más " +
                    "ligero) y peso muerto una vez. Primero acumulas volumen y luego subes la intensidad " +
                    "hasta un pico. UHF significa \"ultra alta frecuencia\".",
                rank = 464,
                level = CatalogLevel.ADVANCED,
                methodName = "GZCL UHF 9",
                authors = "Cody Lefever",
                terms = setOf(PlanTerm.TIERS),
            ),
        )
        add(
            "protocol:smolov",
            thirdParty(
                displayName = "Smolov (solo sentadilla)",
                summary = "13 semanas solo de sentadilla: 4 sesiones por semana las primeras 8 (introducción, " +
                    "volumen y sentadilla a cajón) y 3 las últimas 5 (fase intensa y prueba de máximo), con " +
                    "dos accesorios de espalda. Es extremadamente exigente y el resto de tu entrenamiento " +
                    "queda fuera.",
                rank = 900,
                level = CatalogLevel.ADVANCED,
                methodName = "Smolov",
                authors = "Sergey Smolov",
                kind = PlanKind.ESPECIALIZACION,
                notes = listOf(NOTE_SMOLOV),
                terms = setOf(PlanTerm.ONE_RM),
            ),
        )
        add(
            "protocol:smolov-jr",
            thirdParty(
                displayName = "Smolov Jr (solo sentadilla)",
                summary = "3 semanas, 4 sesiones por semana solo de sentadilla: 6×6, 7×5, 8×4 y 10×3 con el mismo " +
                    "porcentaje de tu máximo las tres semanas, más 5 kg en la segunda y 10 kg en la tercera, " +
                    "y dos accesorios de espalda. Muy exigente: para avanzados que quieren subir la " +
                    "sentadilla.",
                rank = 901,
                level = CatalogLevel.ADVANCED,
                methodName = "Smolov Jr",
                authors = "Sergey Smolov",
                kind = PlanKind.ESPECIALIZACION,
                notes = listOf(NOTE_SMOLOV_JR),
                terms = setOf(PlanTerm.ONE_RM),
            ),
        )
        add(
            "protocol:coan-phillipi-dl",
            thirdParty(
                displayName = "Complemento de peso muerto (Coan y Philippi)",
                summary = "Una sesión a la semana durante 10 semanas solo de peso muerto: series rápidas ligeras, " +
                    "una serie pesada cuyo peso sube hasta tu máximo y cuatro accesorios de espalda y cadera. " +
                    "Se suma a tu plan, no lo sustituye.",
                rank = 920,
                level = CatalogLevel.ADVANCED,
                methodName = "Coan-Philippi (peso muerto)",
                authors = "Ed Coan y Mark Philippi",
                kind = PlanKind.COMPLEMENTO,
                notes = listOf(NOTE_COAN),
                terms = setOf(PlanTerm.SPEED, PlanTerm.ONE_RM),
            ),
        )
        add(
            "protocol:phul-verified",
            thirdParty(
                displayName = "PHUL (versión anterior)",
                summary = "Versión anterior de PHUL, con porcentajes de tu máximo y ciclos de 4 semanas que se " +
                    "repiten. Sigue disponible para quien ya la usa; para empezar de nuevo elige PHUL " +
                    "original o PHUL adaptado KPKN.",
                rank = 990,
                level = CatalogLevel.INTERMEDIATE,
                methodName = "PHUL",
                authors = "Brandon Campbell",
                legacy = true,
                notes = listOf(NOTE_LEGACY),
                terms = setOf(PlanTerm.POWERBUILDING, PlanTerm.UL_PPL, PlanTerm.CYCLE),
            ),
        )
        add(
            "protocol:phat-verified",
            thirdParty(
                displayName = "PHAT (versión anterior)",
                summary = "Versión anterior de PHAT, con porcentajes de tu máximo y ciclos de 4 semanas que se " +
                    "repiten. Sigue disponible para quien ya la usa; para empezar de nuevo elige PHAT " +
                    "original o PHAT adaptado KPKN.",
                rank = 991,
                level = CatalogLevel.ADVANCED,
                methodName = "PHAT",
                authors = "Layne Norton",
                legacy = true,
                notes = listOf(NOTE_LEGACY),
                terms = setOf(PlanTerm.POWERBUILDING, PlanTerm.CYCLE),
            ),
        )

        // ── Planes KPKN con receta fija (§2.5) ──
        add(
            "protocol:kpkn-native-sbd-4",
            own(
                displayName = "Sentadilla, banca y peso muerto KPKN",
                summary = "11 semanas, 4 días por semana: sentadilla el lunes, banca de volumen el martes, peso " +
                    "muerto el jueves y banca pesada el viernes. Cuatro semanas de base, cuatro de " +
                    "intensificación, dos de pico (series de 2 y de 1 repeticiones hasta cerca del 90 % de " +
                    "tu máximo) y una final de descarga; el peso muerto también llega pesado al pico. Las " +
                    "cargas parten de tus marcas.",
                rank = 300,
                levels = ONLY_INTERMEDIATE,
                attributionLine = "$OWN_PLAN No afiliado a federaciones de powerlifting.",
                terms = setOf(PlanTerm.SBD, PlanTerm.POWERLIFTING, PlanTerm.DELOAD),
            ),
        )
        add(
            "protocol:kpkn-ppl-6",
            own(
                displayName = "Empuje, tirón y pierna KPKN",
                summary = "12 semanas, 6 días por semana: empuje, tirón y pierna, cada uno dos veces. A lo largo " +
                    "de las semanas te acercas más al fallo (de 3 repeticiones en reserva a 1) y la segunda " +
                    "sesión de cada grupo va un punto más cerca, sin bajar nunca de 1. Las dos últimas " +
                    "semanas son de descarga, con cerca de la mitad de las series.",
                rank = 340,
                levels = ONLY_INTERMEDIATE,
                terms = setOf(PlanTerm.UL_PPL, PlanTerm.RIR, PlanTerm.DELOAD),
            ),
        )
        // El texto describe la receta de hoy (el número de series no sube de una semana a otra).
        // Si D3 fase 2 implementa la rampa de volumen, usar la alternativa de §2.8 del diseño editorial.
        add(
            "protocol:kpkn-rp-style",
            own(
                displayName = "Mesociclo de músculo (estilo RP)",
                summary = "6 semanas, 4 días por semana (torso A, pierna A, torso B y pierna B). Cada dos semanas " +
                    "te acercas más al fallo, de 3 repeticiones en reserva a 1; la segunda pierna va un punto " +
                    "más cerca, sin bajar nunca de 1, y las series no aumentan. La sexta semana es de " +
                    "descarga, con cerca de la mitad de las series. Plan propio inspirado en Renaissance " +
                    "Periodization.",
                rank = 341,
                levels = ONLY_ADVANCED,
                attributionLine = "$OWN_PLAN Inspirado en Renaissance Periodization; sin afiliación con " +
                    "Renaissance Periodization.",
                terms = setOf(PlanTerm.UL_PPL, PlanTerm.RIR, PlanTerm.DELOAD),
            ),
        )
        // El texto describe la receta de hoy (solo la sentadilla va por serie pesada a esfuerzo).
        // Si D3 fase 2 lleva el método a series pesadas por esfuerzo en los tres levantamientos,
        // usar la alternativa de §2.8 del diseño editorial.
        add(
            "protocol:kpkn-rts-style",
            own(
                displayName = "Fuerza por esfuerzo (estilo RTS)",
                summary = "8 semanas, 4 días: sentadilla, banca, peso muerto y banca de volumen. En las semanas " +
                    "de trabajo, la sentadilla combina una serie pesada a esfuerzo 8 de 10 (9 al final) con " +
                    "series más ligeras; el resto usa porcentajes del máximo que levantas una sola vez. " +
                    "La semana 5 es de descarga, con menos series. Plan propio inspirado en Reactive Training Systems.",
                rank = 342,
                levels = ONLY_ADVANCED,
                attributionLine = "$OWN_PLAN Inspirado en Reactive Training Systems; sin afiliación con " +
                    "Reactive Training Systems.",
                terms = setOf(
                    PlanTerm.RPE,
                    PlanTerm.ONE_RM,
                    PlanTerm.DELOAD,
                    PlanTerm.TOP_SET,
                    PlanTerm.SBD,
                ),
            ),
        )
        add(
            "protocol:kpkn-sbs-rtf",
            own(
                displayName = "Fuerza por repeticiones al máximo (estilo SBS)",
                summary = "8 semanas, 4 días: la última serie de cada levantamiento principal va al máximo de " +
                    "repeticiones en las semanas de trabajo. Si superas el objetivo, el plan puede proponer " +
                    "subir tu máximo de entrenamiento un 0,5 % por repetición extra, o bajarlo si te quedas " +
                    "corto. La semana 4 es de descarga, con menos series. Plan propio inspirado en Stronger By Science.",
                rank = 343,
                levels = ONLY_INTERMEDIATE,
                attributionLine = "$OWN_PLAN Inspirado en Stronger By Science; sin afiliación con Stronger By " +
                    "Science.",
                terms = setOf(PlanTerm.AMRAP, PlanTerm.TM, PlanTerm.DELOAD),
            ),
        )

        // ── Planes de autor (§2.6): PHUL y PHAT, originales primero ──
        add(
            "original:phul-ms-2021-r1",
            authored(
                planId = AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID,
                displayName = "PHUL original",
                summary = "PHUL (Power Hypertrophy Upper Lower: fuerza e hipertrofia de torso y pierna) tal como lo " +
                    "publica Brandon Campbell en Muscle & Strength. Cuatro días por semana, dos de fuerza " +
                    "(3–5 repeticiones) y dos de músculo (8–12), repetidos 12 semanas y sin porcentajes: " +
                    "eliges cargas por sensación, dejando al menos una repetición en reserva. Necesitas " +
                    "barra, rack, banco, mancuernas, polea y máquinas de pierna.",
                rank = 200,
                level = CatalogLevel.INTERMEDIATE,
                source = AuthoredSources.phul,
                terms = setOf(PlanTerm.POWERBUILDING, PlanTerm.UL_PPL, PlanTerm.RIR),
            ),
        )
        add(
            "original:phat-biolayne-2016-r1",
            authored(
                planId = AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID,
                displayName = "PHAT original",
                summary = "PHAT (Power Hypertrophy Adaptive Training) de Layne Norton, como lo publicó en Biolayne " +
                    "en 2016. Cinco días por semana, dos de fuerza y tres de músculo, con series rápidas " +
                    "ligeras (6×3 al 65 % de tu carga habitual) al inicio de cada día de músculo; son seis " +
                    "semanas. Para quien aguanta mucho volumen; necesitas gimnasio completo.",
                rank = 210,
                level = CatalogLevel.ADVANCED,
                source = AuthoredSources.phat,
                terms = setOf(PlanTerm.POWERBUILDING, PlanTerm.SPEED),
            ),
        )
        add(
            "adapted:phul-kpkn-r1",
            authored(
                planId = AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID,
                displayName = "PHUL adaptado KPKN",
                summary = "El mismo PHUL de cuatro días, pero si te falta algún aparato sustituimos ese ejercicio " +
                    "por otro equivalente y te lo mostramos. Mantiene días y series; si algo no tiene " +
                    "sustituto válido, el plan no se ofrece en lugar de recortarse.",
                rank = 220,
                level = CatalogLevel.INTERMEDIATE,
                source = AuthoredSources.phul,
                terms = setOf(PlanTerm.POWERBUILDING, PlanTerm.UL_PPL),
            ),
        )
        add(
            "adapted:phat-kpkn-r1",
            authored(
                planId = AuthoredPhulPhatRecipes.PHAT_ADAPTED_ID,
                displayName = "PHAT adaptado KPKN",
                summary = "El mismo PHAT de cinco días con sustituciones elegidas ejercicio por ejercicio cuando te " +
                    "falta material. Si el volumen no cabe en tu tiempo por sesión, no lo recortamos: puedes " +
                    "elegir Fuerza y músculo KPKN.",
                rank = 230,
                level = CatalogLevel.ADVANCED,
                source = AuthoredSources.phat,
                terms = setOf(PlanTerm.POWERBUILDING, PlanTerm.SPEED),
            ),
        )
    }

    // ─── Constructores de fichas ──────────────────────────────────────────────

    /**
     * Plan propio de KPKN: nativos, plantillas y planes KPKN con receta fija.
     * [references] solo se rellena para sobrescribir la disciplina que calcula el catálogo;
     * [listed] = false oculta la entrada del planner y de la biblioteca (C.P2b).
     */
    private fun own(
        displayName: String,
        summary: String,
        rank: Int,
        levels: Set<CatalogLevel> = ALL_LEVELS,
        kind: PlanKind = PlanKind.PLAN,
        attributionLine: String = OWN_PLAN,
        terms: Set<PlanTerm> = emptySet(),
        references: Set<TrainingReference>? = null,
        listed: Boolean = true,
    ): PlanEditorial = PlanEditorial(
        displayName = displayName,
        summary = summary,
        kind = kind,
        origin = PlanOrigin.KPKN,
        rank = rank,
        levels = levels,
        attributionLine = attributionLine,
        terms = terms,
        references = references,
        listed = listed,
    )

    /**
     * Método de un tercero. `attributionLine` sigue §2.7: «Versión KPKN basada en
     * {método}, de {autores}. No afiliado a {autores}.»; las versiones anteriores
     * dicen «Versión anterior en KPKN de …». Nunca se afilia a KPKN.
     */
    private fun thirdParty(
        displayName: String,
        summary: String,
        rank: Int,
        level: CatalogLevel,
        methodName: String,
        authors: String,
        kind: PlanKind = PlanKind.PLAN,
        legacy: Boolean = false,
        notes: List<String> = emptyList(),
        terms: Set<PlanTerm> = emptySet(),
        references: Set<TrainingReference>? = null,
    ): PlanEditorial = PlanEditorial(
        displayName = displayName,
        summary = summary,
        kind = kind,
        origin = if (legacy) PlanOrigin.LEGACY_VERSION else PlanOrigin.KPKN_VERSION,
        rank = rank,
        levels = setOf(level),
        attributionLine = if (legacy) {
            "Versión anterior en KPKN de $methodName, de $authors. No afiliado a $authors."
        } else {
            "Versión KPKN basada en $methodName, de $authors. No afiliado a $authors."
        },
        notes = notes,
        terms = terms,
        references = references,
    )

    /**
     * PHUL y PHAT de autor. El origen sale de la procedencia declarada por la
     * receta (ORIGINAL o ADAPTED) y la atribución se construye desde la ficha de
     * la fuente, sin escribir autores ni ediciones a mano.
     */
    private fun authored(
        planId: String,
        displayName: String,
        summary: String,
        rank: Int,
        level: CatalogLevel,
        source: AuthoredSourceRecord,
        terms: Set<PlanTerm> = emptySet(),
    ): PlanEditorial {
        val (origin, lead) = when (AuthoredPhulPhatRecipes.recipeFor(planId)?.provenance?.category) {
            PlanProvenanceClass.ORIGINAL -> PlanOrigin.ORIGINAL to "Original fiel de"
            PlanProvenanceClass.ADAPTED -> PlanOrigin.ADAPTED to "Adaptación KPKN del original de"
            else -> error("La entrada de autor «$planId» no declara procedencia ORIGINAL ni ADAPTED")
        }
        return PlanEditorial(
            displayName = displayName,
            summary = summary,
            origin = origin,
            rank = rank,
            levels = setOf(level),
            attributionLine = "$lead ${source.author} (${source.sourceTitle}, ${source.edition}). " +
                "No afiliado a ${source.author}.",
            terms = terms,
        )
    }
}
