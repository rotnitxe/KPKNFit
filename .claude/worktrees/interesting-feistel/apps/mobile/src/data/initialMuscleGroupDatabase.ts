// apps/mobile/src/data/initialMuscleGroupDatabase.ts
// Datos iniciales de grupos musculares — Ported from PWA
import type { MuscleGroupInfo } from '../types/workout';

export const INITIAL_MUSCLE_GROUP_DATA: MuscleGroupInfo[] = [
    {
        id: 'pectoral',
        name: 'Pectoral',
        description: 'El pectoral mayor y menor son los principales músculos del pecho.',
        importance: { movement: 'Fundamental para empuje.', health: 'Contribuye a la postura.' },
        volumeRecommendations: { mev: '10', mav: '12-20', mrv: '22' }
    },
    {
        id: 'espalda',
        name: 'Espalda',
        description: 'Complejo grupo que incluye dorsales, trapecios y erectores.',
        importance: { movement: 'Esencial para tracción.', health: 'Base de columna sana.' },
        volumeRecommendations: { mev: '10', mav: '14-22', mrv: '25' }
    },
    {
        id: 'deltoides',
        name: 'Deltoides',
        description: 'Músculo del hombro con tres cabezas.',
        importance: { movement: 'Levanta el brazo en todas direcciones.', health: 'Estabilidad del hombro.' },
        volumeRecommendations: { mev: '8', mav: '12-20', mrv: '25' }
    },
    {
        id: 'cuadriceps',
        name: 'Cuádriceps',
        description: 'Parte frontal del muslo, extensión de rodilla.',
        importance: { movement: 'Locomoción y salto.', health: 'Estabilidad de rodilla.' },
        volumeRecommendations: { mev: '6', mav: '10-15', mrv: '18' }
    },
    {
        id: 'isquiosurales',
        name: 'Isquiosurales',
        description: 'Parte posterior del muslo.',
        importance: { movement: 'Flexión de rodilla y extensión de cadera.', health: 'Salud de rodilla y espalda.' },
        volumeRecommendations: { mev: '4-6', mav: '8-12', mrv: '16' }
    },
    {
        id: 'gluteos',
        name: 'Glúteos',
        description: 'Extensión y rotación de cadera.',
        importance: { movement: 'Motor principal de potencia.', health: 'Estabilidad pélvica.' },
        volumeRecommendations: { mev: '4-6', mav: '10-16', mrv: '20' }
    },
];
