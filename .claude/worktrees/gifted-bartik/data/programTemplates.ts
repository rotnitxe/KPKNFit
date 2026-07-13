export interface ProgramTemplateOption {
    id: string;
    name: string;
    description: string;
    type: 'simple' | 'complex';
    weeks: number;
    iconType: 'trending' | 'barchart' | 'star';
    blockNames?: string[];
    defaultBlockDurations?: number[];
}

export const PROGRAM_TEMPLATES: ProgramTemplateOption[] = [
    { id: 'simple-1', name: 'Lineal Simple', type: 'simple', weeks: 1, iconType: 'trending', description: 'Progresión cíclica estándar.' },
    { id: 'simple-2', name: 'Ondulante (A/B)', type: 'simple', weeks: 2, iconType: 'trending', description: 'Ciclo de 2 semanas (A/B).' },
    {
        id: 'power-complex', name: 'Bloques: Powerlifting', type: 'complex', weeks: 16, iconType: 'barchart',
        description: 'Estructura profesional de Fuerza.',
        blockNames: ['Hipertrofia', 'Fuerza Base', 'Volumen', 'Peaking', 'Tapering'],
        defaultBlockDurations: [4, 4, 3, 3, 2],
    },
    {
        id: 'bodybuilding-complex', name: 'Bloques: Culturismo', type: 'complex', weeks: 12, iconType: 'star',
        description: 'Estructura PRO Hipertrofia.',
        blockNames: ['Volumen Base', 'Intensificación', 'Peaking Estético'],
        defaultBlockDurations: [5, 4, 3],
    },
];

export const GOAL_OPTIONS = ['Acumulación', 'Intensificación', 'Realización', 'Descarga', 'Custom'] as const;
export type GoalOption = typeof GOAL_OPTIONS[number];
