// data/achievements.ts
import { Achievement, WorkoutLog } from '../types';
import { calculateBrzycki1RM } from '../utils/calculations';

export const ACHIEVEMENTS_LIST: Achievement[] = [
    // === CONSISTENCIA ===
    {
        id: 'consistency_1', name: 'Primer Paso', description: 'Completa tu primer entrenamiento.', icon: 'TrophyIcon', category: 'Consistencia',
        check: ({ history }) => !!history && history.length >= 1,
    },
    {
        id: 'consistency_10', name: 'Constancia de Acero', description: 'Completa 10 entrenamientos.', icon: 'TrophyIcon', category: 'Consistencia',
        check: ({ history }) => !!history && history.length >= 10,
    },
    {
        id: 'consistency_50', name: 'Máquina Imparable', description: 'Completa 50 entrenamientos.', icon: 'TrophyIcon', category: 'Consistencia',
        check: ({ history }) => !!history && history.length >= 50,
    },
    {
        id: 'consistency_100', name: 'Devoto del Hierro', description: 'Completa 100 entrenamientos.', icon: 'TrophyIcon', category: 'Consistencia',
        check: ({ history }) => !!history && history.length >= 100,
    },
    {
        id: 'consistency_weekend', name: 'Guerrero de Fin de Semana', description: 'Completa un entrenamiento en Sábado o Domingo.', icon: 'DumbbellIcon', category: 'Consistencia',
        check: ({ log }) => {
            if (!log) return false;
            const day = new Date(log.date).getDay();
            return day === 0 || day === 6;
        },
    },

    // === HITOS (Milestones) ===
    {
        id: 'volume_10k', name: 'Titán del Volumen', description: 'Levanta más de 10,000 kg/lbs en una sola sesión.', icon: 'FlameIcon', category: 'Hitos',
        check: ({ log }) => {
            if (!log) return false;
            const sessionVolume = log.completedExercises.reduce((total, ex) =>
                total + ex.sets.reduce((exTotal, set) => exTotal + (set.weight || 0) * (set.completedReps || 0), 0), 0);
            return sessionVolume > 10000;
        },
    },
    {
        id: 'volume_20k', name: 'Coloso de Hierro', description: 'Levanta más de 20,000 kg/lbs en una sola sesión.', icon: 'FlameIcon', category: 'Hitos',
        check: ({ log }) => {
            if (!log) return false;
            const sessionVolume = log.completedExercises.reduce((total, ex) =>
                total + ex.sets.reduce((exTotal, set) => exTotal + (set.weight || 0) * (set.completedReps || 0), 0), 0);
            return sessionVolume > 20000;
        },
    },
    {
        id: 'pr_1', name: 'Rompiendo Barreras', description: 'Supera un Récord Personal (1RM estimado) en cualquier ejercicio.', icon: 'TrendingUpIcon', category: 'Hitos',
        check: ({ log, history }) => {
            if (!log || !history || history.length < 2) return false;
            const previousHistory = history.slice(0, -1);
            return log.completedExercises.some(completedEx => {
                const maxSet1RM = Math.max(...completedEx.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)));
                if (maxSet1RM <= 0) return false;

                const historicalMax1RM = previousHistory.reduce((max, prevLog) => {
                    const prevEx = prevLog.completedExercises.find(pe => pe.exerciseName === completedEx.exerciseName);
                    if (!prevEx) return max;
                    const prevMaxSet1RM = Math.max(...prevEx.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)));
                    return Math.max(max, prevMaxSet1RM);
                }, 0);
                return maxSet1RM > historicalMax1RM;
            });
        },
    },
     {
        id: 'pr_bench_100kg', name: 'Club de los 100kg', description: 'Alcanza un 1RM estimado de 100kg o más en Press de Banca.', icon: 'TrophyIcon', category: 'Hitos',
        check: ({ log }) => {
            if (!log) return false;
            const benchPress = log.completedExercises.find(e => e.exerciseName.toLowerCase().includes('press de banca'));
            if (!benchPress) return false;
            const max1RM = Math.max(...benchPress.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)));
            return max1RM >= 100;
        }
    },

    // === EXPLORACIÓN ===
    {
        id: 'program_create', name: 'Arquitecto Fitness', description: 'Crea tu primer programa de entrenamiento.', icon: 'PencilIcon', category: 'Exploración',
        check: ({ programJustCreated }) => !!programJustCreated,
    },
    {
        id: 'ai_session', name: 'Consultor IA', description: 'Usa la IA para generar una sesión completa.', icon: 'SparklesIcon', category: 'Exploración',
        check: () => false, // This would require tracking AI usage, placeholder for now
    },
    {
        id: 'log_nutrition', name: 'Diario Alimenticio', description: 'Registra tu primera comida.', icon: 'UtensilsIcon', category: 'Exploración',
        check: () => false, // This would be checked in handleSaveNutritionLog
    },
     {
        id: 'log_photo', name: 'Fotógrafo del Progreso', description: 'Añade tu primera foto de progreso corporal.', icon: 'CameraIcon', category: 'Exploración',
        check: () => false, // This would be checked in handleSaveBodyLog
    },

    // === DEDICACIÓN ===
     {
        id: 'dedication_early', name: 'Pájaro Madrugador', description: 'Completa un entrenamiento antes de las 7 AM.', icon: 'ClockIcon', category: 'Dedicación',
        check: ({ log }) => {
            if (!log) return false;
            const hour = new Date(log.date).getHours();
            return hour < 7;
        },
    },
    {
        id: 'dedication_late', name: 'Búho Nocturno', description: 'Completa un entrenamiento después de las 9 PM.', icon: 'ClockIcon', category: 'Dedicación',
        check: ({ log }) => {
            if (!log) return false;
            const hour = new Date(log.date).getHours();
            return hour >= 21;
        },
    },
    {
        id: 'dedication_duration', name: 'Sesión Maratónica', description: 'Entrena por más de 90 minutos en una sola sesión.', icon: 'ClockIcon', category: 'Dedicación',
        check: ({ log }) => !!log && !!log.duration && log.duration > (90 * 60),
    },
];
