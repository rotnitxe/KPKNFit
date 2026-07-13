// ============================================================
// structureTemplates.ts
// Template library for periodization structures (macrocycles)
// ============================================================

export type StructureTag =
    | 'Powerlifting'
    | 'Culturismo'
    | 'Powerbuilding'
    | 'Clásica'
    | 'URSS/Bulgaria'
    | 'GZCL'
    | 'Oldschool'
    | 'Atletismo'
    | 'General'
    | 'Recomendado';

export interface MesoTemplate {
    name: string;
    goal: 'Acumulación' | 'Intensificación' | 'Realización' | 'Descarga' | 'Custom';
    weeksCount: number;
}

export interface BlockTemplate {
    name: string;
    mesocycles: MesoTemplate[];
}

export interface StructureTemplate {
    id: string;
    name: string;
    emoji: string;
    description: string;
    tags: StructureTag[];
    durationWeeks: number; // approximate total weeks
    macrocycleName: string;
    blocks: BlockTemplate[];
}

export const STRUCTURE_TEMPLATES: StructureTemplate[] = [
    // ─────────────────────────────────────────────────────────
    // GENERAL / RECOMENDADOS
    // ─────────────────────────────────────────────────────────
    {
        id: 'linear-beginner',
        name: 'Progresión Lineal',
        emoji: '📈',
        description: 'Aumento de carga semana a semana. Ideal para principiantes e intermedios. Simple y efectivo.',
        tags: ['General', 'Recomendado', 'Clásica'],
        durationWeeks: 12,
        macrocycleName: 'Macrociclo Base',
        blocks: [
            {
                name: 'Bloque Lineal',
                mesocycles: [
                    { name: 'Fase de Adaptación', goal: 'Acumulación', weeksCount: 4 },
                    { name: 'Fase de Progresión', goal: 'Intensificación', weeksCount: 6 },
                    { name: 'Descarga', goal: 'Descarga', weeksCount: 2 },
                ],
            },
        ],
    },
    {
        id: 'undulating-dup',
        name: 'Periodización Ondulante (DUP)',
        emoji: '🌊',
        description: 'Varía la intensidad y volumen diaria o semanalmente. Excelente para intermedios buscando variedad y adherencia.',
        tags: ['General', 'Recomendado', 'Powerbuilding'],
        durationWeeks: 12,
        macrocycleName: 'Macrociclo DUP',
        blocks: [
            {
                name: 'Bloque DUP Estándar',
                mesocycles: [
                    { name: 'Semana Hipertrofia (alta rep)', goal: 'Acumulación', weeksCount: 4 },
                    { name: 'Semana Fuerza (med rep)', goal: 'Intensificación', weeksCount: 4 },
                    { name: 'Semana Potencia (baja rep)', goal: 'Realización', weeksCount: 2 },
                    { name: 'Descarga', goal: 'Descarga', weeksCount: 2 },
                ],
            },
        ],
    },

    // ─────────────────────────────────────────────────────────
    // CULTURISMO / HIPERTROFIA
    // ─────────────────────────────────────────────────────────
    {
        id: 'bodybuilding-classic',
        name: 'Culturismo Clásico',
        emoji: '💪',
        description: 'Estructura clásica de offseason y precompetencia. Alta frecuencia y volumen de entrenamiento.',
        tags: ['Culturismo', 'Clásica'],
        durationWeeks: 20,
        macrocycleName: 'Temporada Culturismo',
        blocks: [
            {
                name: 'Offseason (Volumen)',
                mesocycles: [
                    { name: 'Acumulación Baja Intensidad', goal: 'Acumulación', weeksCount: 4 },
                    { name: 'Volumen Pico', goal: 'Acumulación', weeksCount: 6 },
                ],
            },
            {
                name: 'Pre-Competencia (Definición)',
                mesocycles: [
                    { name: 'Transición a Déficit', goal: 'Intensificación', weeksCount: 4 },
                    { name: 'Peak Week', goal: 'Realización', weeksCount: 4 },
                    { name: 'Deload / Prep', goal: 'Descarga', weeksCount: 2 },
                ],
            },
        ],
    },
    {
        id: 'rp-mesocycle',
        name: 'Mesociclo RP (Renaissance)',
        emoji: '🔬',
        description: 'Mesociclo de 6 semanas basado en los principios de Renaissance Periodization. Progresión de volumen + deload.',
        tags: ['Culturismo', 'Recomendado'],
        durationWeeks: 6,
        macrocycleName: 'Meso RP',
        blocks: [
            {
                name: 'Mesociclo Hipertrofia',
                mesocycles: [
                    { name: 'Sem 1-2: MEV (Volumen Mínimo)', goal: 'Acumulación', weeksCount: 2 },
                    { name: 'Sem 3-4: Progresión', goal: 'Acumulación', weeksCount: 2 },
                    { name: 'Sem 5: MRV (Volumen Máx)', goal: 'Intensificación', weeksCount: 1 },
                    { name: 'Sem 6: Descarga', goal: 'Descarga', weeksCount: 1 },
                ],
            },
        ],
    },

    // ─────────────────────────────────────────────────────────
    // POWERLIFTING
    // ─────────────────────────────────────────────────────────
    {
        id: 'powerlifting-prep',
        name: 'Prep Powerlifting (16 sem)',
        emoji: '🏋️',
        description: 'Prep completo de 16 semanas para competencia de powerlifting. Acumulación → Intensificación → Peak → Taper.',
        tags: ['Powerlifting', 'Recomendado'],
        durationWeeks: 16,
        macrocycleName: 'Prep Competencia',
        blocks: [
            {
                name: 'Bloque de Acumulación',
                mesocycles: [
                    { name: 'GPP / Base', goal: 'Acumulación', weeksCount: 4 },
                    { name: 'Volumen Específico', goal: 'Acumulación', weeksCount: 4 },
                ],
            },
            {
                name: 'Bloque de Intensificación',
                mesocycles: [
                    { name: 'Fuerza Media', goal: 'Intensificación', weeksCount: 3 },
                    { name: 'Intensificación Alta', goal: 'Intensificación', weeksCount: 2 },
                ],
            },
            {
                name: 'Bloque Peak + Taper',
                mesocycles: [
                    { name: 'Peak (Peaking)', goal: 'Realización', weeksCount: 2 },
                    { name: 'Taper / Competencia', goal: 'Descarga', weeksCount: 1 },
                ],
            },
        ],
    },
    {
        id: 'powerlifting-off-prep',
        name: 'Powerlifting Off-Season + Prep',
        emoji: '🔋',
        description: 'Macrociclo anual con off-season para construir base y preparación para meet.',
        tags: ['Powerlifting'],
        durationWeeks: 24,
        macrocycleName: 'Temporada Powerlifting',
        blocks: [
            {
                name: 'Off-Season (Hipertrofia/GPP)',
                mesocycles: [
                    { name: 'Hipertrofia General', goal: 'Acumulación', weeksCount: 6 },
                    { name: 'Hipertrofia Específica', goal: 'Acumulación', weeksCount: 4 },
                ],
            },
            {
                name: 'Pre-Prep',
                mesocycles: [
                    { name: 'Transición Fuerza', goal: 'Intensificación', weeksCount: 4 },
                ],
            },
            {
                name: 'Meet Prep',
                mesocycles: [
                    { name: 'Acumulación Específica', goal: 'Acumulación', weeksCount: 4 },
                    { name: 'Peaking', goal: 'Realización', weeksCount: 4 },
                    { name: 'Taper', goal: 'Descarga', weeksCount: 2 },
                ],
            },
        ],
    },

    // ─────────────────────────────────────────────────────────
    // POWERBUILDING
    // ─────────────────────────────────────────────────────────
    {
        id: 'powerbuilding-conjugate',
        name: 'Powerbuilding por Bloques',
        emoji: '⚡',
        description: 'Combina fuerza y tamaño. Bloques alternados de volumen e intensidad con trabajo accesorio de culturismo.',
        tags: ['Powerbuilding', 'Recomendado'],
        durationWeeks: 16,
        macrocycleName: 'Macrociclo Powerbuilding',
        blocks: [
            {
                name: 'Bloque Volumen',
                mesocycles: [
                    { name: 'Hipertrofia + Técnica', goal: 'Acumulación', weeksCount: 4 },
                    { name: 'Volumen Progresivo', goal: 'Acumulación', weeksCount: 4 },
                ],
            },
            {
                name: 'Bloque Fuerza',
                mesocycles: [
                    { name: 'Fuerza con Accesorios', goal: 'Intensificación', weeksCount: 4 },
                    { name: 'Peak Express + Descarga', goal: 'Realización', weeksCount: 4 },
                ],
            },
        ],
    },

    // ─────────────────────────────────────────────────────────
    // GZCL
    // ─────────────────────────────────────────────────────────
    {
        id: 'gzcl-t1-t2-t3',
        name: 'Sistema GZCL (12 sem)',
        emoji: '📐',
        description: 'Periodización basada en el sistema GZCL de Cody Lefever: T1 (main lifts), T2 (supplemental), T3 (accessories).',
        tags: ['GZCL', 'Powerlifting', 'Powerbuilding'],
        durationWeeks: 12,
        macrocycleName: 'GZCL Macrociclo',
        blocks: [
            {
                name: 'Mesociclo de Volumen (T2 énfasis)',
                mesocycles: [
                    { name: 'Alta Rep T1 + T2', goal: 'Acumulación', weeksCount: 4 },
                ],
            },
            {
                name: 'Mesociclo de Fuerza (T1 énfasis)',
                mesocycles: [
                    { name: 'Baja Rep T1 + T2 med', goal: 'Intensificación', weeksCount: 4 },
                ],
            },
            {
                name: 'Peak + Deload',
                mesocycles: [
                    { name: 'Test Semana', goal: 'Realización', weeksCount: 2 },
                    { name: 'Deload', goal: 'Descarga', weeksCount: 2 },
                ],
            },
        ],
    },

    // ─────────────────────────────────────────────────────────
    // URSS / SOVIÉTICO / SHEIKO
    // ─────────────────────────────────────────────────────────
    {
        id: 'soviet-sheiko',
        name: 'Periodización Soviética (Sheiko)',
        emoji: '🇷🇺',
        description: 'Inspirado en el método de Boris Sheiko: alta frecuencia, volumen ondulante, variaciones técnicas específicas.',
        tags: ['URSS/Bulgaria', 'Powerlifting', 'Clásica'],
        durationWeeks: 16,
        macrocycleName: 'Prep Soviética',
        blocks: [
            {
                name: 'Mesociclo 1 (Alto Volumen)',
                mesocycles: [
                    { name: 'Sem 1-4: Vol Alto / Int Baja', goal: 'Acumulación', weeksCount: 4 },
                ],
            },
            {
                name: 'Mesociclo 2 (Integración)',
                mesocycles: [
                    { name: 'Sem 5-8: Vol Med / Int Med', goal: 'Intensificación', weeksCount: 4 },
                ],
            },
            {
                name: 'Mesociclo 3 (Pre-Comp)',
                mesocycles: [
                    { name: 'Sem 9-12: Vol Bajo / Int Alta', goal: 'Intensificación', weeksCount: 3 },
                    { name: 'Comp / Test', goal: 'Realización', weeksCount: 1 },
                ],
            },
            {
                name: 'Transición Descarga',
                mesocycles: [
                    { name: 'Recuperación Activa', goal: 'Descarga', weeksCount: 4 },
                ],
            },
        ],
    },
    {
        id: 'bulgarian-method',
        name: 'Método Búlgaro',
        emoji: '🏔️',
        description: 'Alta frecuencia extrema: entrenamiento diario con los levantamientos competitivos al máximo diario. Para avanzados.',
        tags: ['URSS/Bulgaria', 'Powerlifting', 'Culturismo'],
        durationWeeks: 8,
        macrocycleName: 'Bloque Búlgaro',
        blocks: [
            {
                name: 'Adaptación',
                mesocycles: [
                    { name: 'Adaptación (3-4x/siem lifts)', goal: 'Acumulación', weeksCount: 2 },
                ],
            },
            {
                name: 'Intensificación Extrema',
                mesocycles: [
                    { name: 'Daily Max (5-6x/sem)', goal: 'Intensificación', weeksCount: 4 },
                ],
            },
            {
                name: 'Taper / Test',
                mesocycles: [
                    { name: 'Reducción + Test 1RM', goal: 'Realización', weeksCount: 2 },
                ],
            },
        ],
    },

    // ─────────────────────────────────────────────────────────
    // OLDSCHOOL / CLÁSICA
    // ─────────────────────────────────────────────────────────
    {
        id: 'oldschool-3x3',
        name: 'Old School 3x3 + Hipertrofia',
        emoji: '🎖️',
        description: 'Entrenamiento de 3x3 en los básicos con trabajo de hipertrofia clásico. Inspirado en la era dorada del culturismo.',
        tags: ['Oldschool', 'Clásica', 'Powerbuilding'],
        durationWeeks: 12,
        macrocycleName: 'Bloque Old School',
        blocks: [
            {
                name: 'Fase Fundacional',
                mesocycles: [
                    { name: 'Volumen Hipertrofia 5x5', goal: 'Acumulación', weeksCount: 4 },
                    { name: 'Fuerza 3x3', goal: 'Intensificación', weeksCount: 4 },
                    { name: 'Test + Descarga', goal: 'Realización', weeksCount: 4 },
                ],
            },
        ],
    },
    {
        id: 'conjugate-westside',
        name: 'Conjugado (Westside)',
        emoji: '🔗',
        description: 'Método conjugado de Westside Barbell: días max effort y dynamic effort conviviendo simultáneamente.',
        tags: ['Powerlifting', 'Clásica', 'Oldschool'],
        durationWeeks: 12,
        macrocycleName: 'Conjugated Macrocycle',
        blocks: [
            {
                name: 'Bloque Conjugado Principal',
                mesocycles: [
                    { name: 'ME + DE Squat/Dead + Bench', goal: 'Custom', weeksCount: 4 },
                    { name: 'Variación ME (box squat, floor press...)', goal: 'Intensificación', weeksCount: 4 },
                    { name: 'Pre-meet Específico', goal: 'Realización', weeksCount: 3 },
                    { name: 'Meet Week', goal: 'Descarga', weeksCount: 1 },
                ],
            },
        ],
    },

    // ─────────────────────────────────────────────────────────
    // ATLETISMO / DEPORTE
    // ─────────────────────────────────────────────────────────
    {
        id: 'sport-periodization',
        name: 'Periodización Deportiva General',
        emoji: '🏃',
        description: 'Estructura clásica para deportistas: preparación general, especial, y competitiva.',
        tags: ['Atletismo', 'General'],
        durationWeeks: 16,
        macrocycleName: 'Temporada Deportiva',
        blocks: [
            {
                name: 'Prep General (GPP)',
                mesocycles: [
                    { name: 'Base Aeróbica + Fuerza', goal: 'Acumulación', weeksCount: 4 },
                ],
            },
            {
                name: 'Prep Especial (SPP)',
                mesocycles: [
                    { name: 'Cualidades Específicas del Deporte', goal: 'Intensificación', weeksCount: 6 },
                ],
            },
            {
                name: 'Competitiva',
                mesocycles: [
                    { name: 'Afinamiento Pre-Comp', goal: 'Realización', weeksCount: 4 },
                    { name: 'Taper + Comp', goal: 'Descarga', weeksCount: 2 },
                ],
            },
        ],
    },
];
