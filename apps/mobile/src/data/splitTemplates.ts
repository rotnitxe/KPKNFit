import { SplitTemplate } from '../types/workout';

export const SPLIT_TEMPLATES: SplitTemplate[] = [
    { id: 'ul_x4', name: 'Upper / Lower x4', description: 'El estándar de oro. Equilibrio perfecto.', tags: ['Recomendado por KPKN', 'Balanceado', 'Alta Tolerancia'], pattern: ['Torso', 'Pierna', 'Descanso', 'Torso', 'Pierna', 'Descanso', 'Descanso'], difficulty: 'Intermedio', 
        pros: ['Frecuencia 2x/semana óptima para naturales', '48-72h recuperación por grupo muscular'], cons: ['Requiere 4 días mínimos', 'Sesiones de pierna demandantes'] },
    { id: 'ppl_ul', name: 'PPL + Upper/Lower', description: 'Híbrido de 5 días. Volumen y frecuencia.', tags: ['Recomendado por KPKN', 'Alto Volumen', 'Balanceado'], pattern: ['Torso', 'Pierna', 'Descanso', 'Empuje', 'Tirón', 'Pierna', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Combina frecuencia UL con especificidad PPL', '5 días manejables'], cons: ['Coordinación compleja', 'Fatiga acumulativa media-alta'] },
    { id: 'fullbody_x3', name: 'Full Body x3', description: 'Alta frecuencia, ideal para agendas ocupadas.', tags: ['Recomendado por KPKN', 'Baja Frecuencia'], pattern: ['Cuerpo Completo A', 'Descanso', 'Cuerpo Completo B', 'Descanso', 'Cuerpo Completo C', 'Descanso', 'Descanso'], difficulty: 'Principiante',
        pros: ['Frecuencia 3x/semana máxima para principiantes', 'Ideal para aprendizaje motor'], cons: ['Volumen por músculo limitado por sesión', 'Sesiones sistémicamente fatigosas'] },
    { id: 'ppl_x6', name: 'Push Pull Legs x6', description: 'Máximo volumen. Solo expertos.', tags: ['Alta Frecuencia', 'Alto Volumen', 'Alta Tolerancia'], pattern: ['Empuje', 'Tirón', 'Pierna', 'Empuje', 'Tirón', 'Pierna', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Volumen máximo por grupo muscular', 'Frecuencia 2x/semana'], cons: ['6 días requeridos', 'Fatiga acumulativa extrema'] },
    { id: 'bro_split', name: 'Bro Split Clásico', description: 'Un grupo muscular por día.', tags: ['Baja Frecuencia', 'Alto Volumen'], pattern: ['Pecho', 'Espalda', 'Piernas', 'Hombros', 'Brazos', 'Descanso', 'Descanso'], difficulty: 'Principiante',
        pros: ['Volumen máximo por sesión', 'Foco mental óptimo'], cons: ['Frecuencia 1x/semana subóptima', 'No recomendado para naturales'] },
    { id: 'minimalist_x2', name: 'Minimalista x2', description: 'Dosis mínima efectiva.', tags: ['Baja Frecuencia', 'Balanceado'], pattern: ['Full Body A', 'Descanso', 'Descanso', 'Full Body B', 'Descanso', 'Descanso', 'Descanso'], difficulty: 'Principiante',
        pros: ['Solo 2 días requeridos', 'Máxima eficiencia temporal'], cons: ['Progreso lento', 'Volumen semanal limitado'] },
];
