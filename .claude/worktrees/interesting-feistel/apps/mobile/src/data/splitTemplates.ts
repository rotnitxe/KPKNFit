import { SplitTemplate } from '../types/workout';

export const SPLIT_TEMPLATES: SplitTemplate[] = [
    { 
        id: 'ul_x4', 
        name: 'Upper / Lower x4', 
        description: 'El estándar de oro. Equilibrio perfecto.', 
        tags: ['Recomendado por KPKN', 'Balanceado', 'Alta Tolerancia'], 
        pattern: ['Torso', 'Pierna', 'Descanso', 'Torso', 'Pierna', 'Descanso', 'Descanso'], 
        difficulty: 'Intermedio', 
        pros: ['Frecuencia 2x/semana óptima para naturales', '48-72h recuperación por grupo muscular'], 
        cons: ['Requiere 4 días mínimos', 'Sesiones de pierna demandantes'],
        daysPerWeek: 4,
        sessions: [
            { name: 'Torso', focus: 'Empuje + Tirón', muscles: ['Pectorales', 'Dorsales', 'Hombros', 'Bíceps', 'Tríceps'] },
            { name: 'Pierna', focus: 'Cuádriceps + Isquios', muscles: ['Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pantorrillas'] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] },
            { name: 'Torso', focus: 'Empuje + Tirón', muscles: ['Pectorales', 'Dorsales', 'Hombros', 'Bíceps', 'Tríceps'] },
            { name: 'Pierna', focus: 'Cuádriceps + Isquios', muscles: ['Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pantorrillas'] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] }
        ]
    },
    { 
        id: 'ppl_ul', 
        name: 'PPL + Upper/Lower', 
        description: 'Híbrido de 5 días. Volumen y frecuencia.', 
        tags: ['Recomendado por KPKN', 'Alto Volumen', 'Balanceado'], 
        pattern: ['Torso', 'Pierna', 'Descanso', 'Empuje', 'Tirón', 'Pierna', 'Descanso'], 
        difficulty: 'Intermedio',
        pros: ['Combina frecuencia UL con especificidad PPL', '5 días manejables'], 
        cons: ['Coordinación compleja', 'Fatiga acumulativa media-alta'],
        daysPerWeek: 5,
        sessions: [
            { name: 'Torso', focus: 'Empuje + Tirón', muscles: ['Pectorales', 'Dorsales', 'Hombros', 'Bíceps', 'Tríceps'] },
            { name: 'Pierna', focus: 'Cuádriceps + Isquios', muscles: ['Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pantorrillas'] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] },
            { name: 'Empuje', focus: 'Pecho, Hombros, Tríceps', muscles: ['Pectorales', 'Hombros', 'Tríceps'] },
            { name: 'Tirón', focus: 'Espalda, Bíceps', muscles: ['Dorsales', 'Bíceps', 'Trapecio'] },
            { name: 'Pierna', focus: 'Cuádriceps + Isquios', muscles: ['Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pantorrillas'] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] }
        ]
    },
    { 
        id: 'fullbody_x3', 
        name: 'Full Body x3', 
        description: 'Alta frecuencia, ideal para agendas ocupadas.', 
        tags: ['Recomendado por KPKN', 'Baja Frecuencia'], 
        pattern: ['Cuerpo Completo A', 'Descanso', 'Cuerpo Completo B', 'Descanso', 'Cuerpo Completo C', 'Descanso', 'Descanso'], 
        difficulty: 'Principiante',
        pros: ['Frecuencia 3x/semana máxima para principiantes', 'Ideal para aprendizaje motor'], 
        cons: ['Volumen por músculo limitado por sesión', 'Sesiones sistémicamente fatigosas'],
        daysPerWeek: 3,
        sessions: [
            { name: 'Cuerpo Completo A', focus: 'Fuerza Básica', muscles: ['Cuádriceps', 'Pectorales', 'Dorsales'] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] },
            { name: 'Cuerpo Completo B', focus: 'Hipertrofia', muscles: ['Glúteos', 'Hombros', 'Bíceps', 'Tríceps'] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] },
            { name: 'Cuerpo Completo C', focus: 'Accesorios', muscles: ['Isquiosurales', 'Pantorrillas', 'Abdomen'] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] }
        ]
    },
    { 
        id: 'ppl_x6', 
        name: 'Push Pull Legs x6', 
        description: 'Máximo volumen. Solo expertos.', 
        tags: ['Alta Frecuencia', 'Alto Volumen', 'Alta Tolerancia'], 
        pattern: ['Empuje', 'Tirón', 'Pierna', 'Empuje', 'Tirón', 'Pierna', 'Descanso'], 
        difficulty: 'Avanzado',
        pros: ['Volumen máximo por grupo muscular', 'Frecuencia 2x/semana'], 
        cons: ['6 días requeridos', 'Fatiga acumulativa extrema'],
        daysPerWeek: 6,
        sessions: [
            { name: 'Empuje', focus: 'Pecho, Hombros, Tríceps', muscles: ['Pectorales', 'Hombros', 'Tríceps'] },
            { name: 'Tirón', focus: 'Espalda, Bíceps', muscles: ['Dorsales', 'Bíceps', 'Trapecio'] },
            { name: 'Pierna', focus: 'Cuádriceps + Isquios', muscles: ['Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pantorrillas'] },
            { name: 'Empuje', focus: 'Pecho, Hombros, Tríceps', muscles: ['Pectorales', 'Hombros', 'Tríceps'] },
            { name: 'Tirón', focus: 'Espalda, Bíceps', muscles: ['Dorsales', 'Bíceps', 'Trapecio'] },
            { name: 'Pierna', focus: 'Cuádriceps + Isquios', muscles: ['Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pantorrillas'] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] }
        ]
    },
    { 
        id: 'bro_split', 
        name: 'Bro Split Clásico', 
        description: 'Un grupo muscular por día.', 
        tags: ['Baja Frecuencia', 'Alto Volumen'], 
        pattern: ['Pecho', 'Espalda', 'Piernas', 'Hombros', 'Brazos', 'Descanso', 'Descanso'], 
        difficulty: 'Principiante',
        pros: ['Volumen máximo por sesión', 'Foco mental óptimo'], 
        cons: ['Frecuencia 1x/semana subóptima', 'No recomendado para naturales'],
        daysPerWeek: 5,
        sessions: [
            { name: 'Pecho', focus: 'Pectorales', muscles: ['Pectorales'] },
            { name: 'Espalda', focus: 'Dorsales, Trapecio', muscles: ['Dorsales', 'Trapecio'] },
            { name: 'Piernas', focus: 'Cuádriceps, Isquios, Glúteos', muscles: ['Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pantorrillas'] },
            { name: 'Hombros', focus: 'Hombros', muscles: ['Hombros'] },
            { name: 'Brazos', focus: 'Bíceps, Tríceps', muscles: ['Bíceps', 'Tríceps'] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] }
        ]
    },
    { 
        id: 'minimalist_x2', 
        name: 'Minimalista x2', 
        description: 'Dosis mínima efectiva.', 
        tags: ['Baja Frecuencia', 'Balanceado'], 
        pattern: ['Full Body A', 'Descanso', 'Descanso', 'Full Body B', 'Descanso', 'Descanso', 'Descanso'], 
        difficulty: 'Principiante',
        pros: ['Solo 2 días requeridos', 'Máxima eficiencia temporal'], 
        cons: ['Progreso lento', 'Volumen semanal limitado'],
        daysPerWeek: 2,
        sessions: [
            { name: 'Full Body A', focus: 'Fuerza General', muscles: ['Cuádriceps', 'Pectorales', 'Dorsales'] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] },
            { name: 'Full Body B', focus: 'Hipertrofia General', muscles: ['Glúteos', 'Hombros', 'Bíceps', 'Tríceps', 'Abdomen'] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] }
        ]
    },
    // Add more templates to reach at least 8
    { 
        id: 'arnold_split', 
        name: 'Arnold Split', 
        description: 'Torso/Pierna clásico de Arnold.', 
        tags: ['Baja Frecuencia', 'Alto Volumen'], 
        pattern: ['Torso', 'Pierna', 'Descanso', 'Torso', 'Pierna', 'Descanso', 'Descanso'], 
        difficulty: 'Intermedio',
        pros: ['Foco intenso por grupo muscular', 'Recuperación completa'], 
        cons: ['Frecuencia 1x/semana subóptima'],
        daysPerWeek: 4,
        sessions: [
            { name: 'Torso', focus: 'Pecho, Espalda, Hombros', muscles: ['Pectorales', 'Dorsales', 'Hombros'] },
            { name: 'Pierna', focus: 'Cuádriceps, Isquios, Glúteos', muscles: ['Cuádriceps', 'Isquiosurales', 'Glúteos'] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] },
            { name: 'Torso', focus: 'Brazos, Abdomen', muscles: ['Bíceps', 'Tríceps', 'Abdomen'] },
            { name: 'Pierna', focus: 'Pantorrillas', muscles: ['Pantorrillas'] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] }
        ]
    },
    { 
        id: 'torso_pierma', 
        name: 'Torso / Pierna', 
        description: 'Split clásico de 4 días.', 
        tags: ['Balanceado', 'Alta Tolerancia'], 
        pattern: ['Torso', 'Pierna', 'Descanso', 'Torso', 'Pierna', 'Descanso', 'Descanso'], 
        difficulty: 'Intermedio',
        pros: ['Frecuencia 2x/semana por grupo', 'Equilibrio volumen/frecuencia'], 
        cons: ['Sesiones largas'],
        daysPerWeek: 4,
        sessions: [
            { name: 'Torso', focus: 'Empuje + Tirón', muscles: ['Pectorales', 'Dorsales', 'Hombros', 'Bíceps', 'Tríceps'] },
            { name: 'Pierna', focus: 'Completo', muscles: ['Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pantorrillas'] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] },
            { name: 'Torso', focus: 'Empuje + Tirón', muscles: ['Pectorales', 'Dorsales', 'Hombros', 'Bíceps', 'Tríceps'] },
            { name: 'Pierna', focus: 'Completo', muscles: ['Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pantorrillas'] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] },
            { name: 'Descanso', focus: 'Recuperación', muscles: [] }
        ]
    }
];
