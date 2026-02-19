
// data/supplements.ts

export interface Supplement {
    id: string;
    name: string;
    group: 'A' | 'B' | 'C' | 'D';
    description: string;
    defaultDose: number; // en gramos o unidades
    unit: string;
    category: 'Performance' | 'Medical' | 'Food';
}

export const AIS_SUPPLEMENTS: Supplement[] = [
    // GRUPO A: Evidencia Sólida
    {
        id: 'sup_creatine',
        name: 'Creatina Monohidrato',
        group: 'A',
        description: 'Mejora el rendimiento en ejercicios de alta intensidad y aumenta la masa magra.',
        defaultDose: 5,
        unit: 'g',
        category: 'Performance'
    },
    {
        id: 'sup_caffeine',
        name: 'Cafeína (Anhidra)',
        group: 'A',
        description: 'Reduce la percepción del esfuerzo y mejora la concentración.',
        defaultDose: 0.2, // 200mg
        unit: 'g',
        category: 'Performance'
    },
    {
        id: 'sup_beta_alanine',
        name: 'Beta-Alanina',
        group: 'A',
        description: 'Tamponador intracelular. Mejora el rendimiento en esfuerzos de 1-4 minutos.',
        defaultDose: 3.2,
        unit: 'g',
        category: 'Performance'
    },
    {
        id: 'sup_bicarb',
        name: 'Bicarbonato de Sodio',
        group: 'A',
        description: 'Tamponador extracelular para esfuerzos anaeróbicos lácticos.',
        defaultDose: 15,
        unit: 'g',
        category: 'Performance'
    },
    {
        id: 'sup_whey',
        name: 'Proteína Whey (Suero)',
        group: 'A',
        description: 'Fuente de proteína de rápida absorción para recuperación muscular.',
        defaultDose: 30,
        unit: 'g',
        category: 'Food'
    },
    {
        id: 'sup_beetroot',
        name: 'Jugo de Remolacha (Nitratos)',
        group: 'A',
        description: 'Mejora la eficiencia del oxígeno y el flujo sanguíneo.',
        defaultDose: 5, // aprox extracto
        unit: 'g',
        category: 'Food'
    },
    {
        id: 'sup_glycerol',
        name: 'Glicerol',
        group: 'A',
        description: 'Ayuda a la hiperhidratación antes del ejercicio en calor.',
        defaultDose: 10,
        unit: 'ml',
        category: 'Performance'
    },
    // GRUPO B: Evidencia Emergente / Consideración
    {
        id: 'sup_omega3',
        name: 'Omega-3 (Aceite Pescado)',
        group: 'B',
        description: 'Antiinflamatorio y salud cardiovascular.',
        defaultDose: 2,
        unit: 'g',
        category: 'Medical'
    },
    {
        id: 'sup_collagen',
        name: 'Colágeno Hidrolizado',
        group: 'B',
        description: 'Apoyo a la salud de tendones y ligamentos (tomar con Vit C antes de entrenar).',
        defaultDose: 15,
        unit: 'g',
        category: 'Medical'
    },
    {
        id: 'sup_carnitine',
        name: 'L-Carnitina',
        group: 'B',
        description: 'Potencial mejora en la oxidación de grasas y recuperación.',
        defaultDose: 2,
        unit: 'g',
        category: 'Performance'
    }
];
