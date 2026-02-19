

import React, { useState, useEffect } from 'react';
import { Mesocycle } from '../types';
import { LightbulbIcon } from './icons';

interface WeekGoalCoachProps {
    goal: Mesocycle['goal'];
}

const GOAL_DESCRIPTIONS: Record<Mesocycle['goal'], { title: string; description: string; recommendations: string[] }> = {
    'Acumulación': {
        title: 'Fase de Acumulación',
        description: 'El objetivo es construir una base sólida acumulando volumen de entrenamiento. Esto prepara al cuerpo para fases más intensas.',
        recommendations: [
            '**Series:** Aumenta el número total de series por grupo muscular.',
            '**Repeticiones:** Trabaja en un rango de 8 a 15 repeticiones por serie.',
            '**Intensidad (RPE/RIR):** Mantén un RPE de 6-8 (RIR 2-4). No llegues al fallo muscular.',
            '**Descanso:** Descansos más cortos, entre 60 y 90 segundos.'
        ]
    },
    'Intensificación': {
        title: 'Fase de Intensificación',
        description: 'El foco se desplaza del volumen a la intensidad. El objetivo es levantar cargas más pesadas y aumentar la fuerza máxima.',
        recommendations: [
            '**Series:** Reduce ligeramente el número de series en comparación con la fase de acumulación.',
            '**Repeticiones:** Trabaja en un rango de 3 a 8 repeticiones.',
            '**Intensidad (RPE/RIR):** Aumenta el RPE a 8-9.5 (RIR 0-2). Estarás cerca del fallo.',
            '**Descanso:** Descansos más largos, entre 2 y 5 minutos para una recuperación completa.'
        ]
    },
    'Realización': {
        title: 'Fase de Realización (Peaking)',
        description: 'Esta fase busca maximizar el rendimiento y demostrar la fuerza ganada, a menudo antes de un test de 1RM o una competición.',
        recommendations: [
            '**Series:** Volumen bajo. Reduce significativamente el número de series.',
            '**Repeticiones:** Rango muy bajo, de 1 a 3 repeticiones.',
            '**Intensidad (RPE/RIR):** Muy alta, RPE 9-10 (RIR 0-1). Cargas máximas o cercanas.',
            '**Descanso:** Descansos completos y largos, más de 3 minutos.'
        ]
    },
    'Descarga': {
        title: 'Fase de Descarga (Deload)',
        description: 'El objetivo es reducir la fatiga acumulada para permitir la supercompensación y la recuperación, previniendo el sobreentrenamiento.',
        recommendations: [
            '**Series:** Reduce el número de series a la mitad, o incluso menos.',
            '**Repeticiones:** Mantén el rango de repeticiones o redúcelo ligeramente.',
            '**Intensidad (RPE/RIR):** Reduce drásticamente el peso. RPE 4-6 (RIR 4+). El entrenamiento debe sentirse muy fácil.',
            '**Descanso:** Mantén los descansos normales o acórtalos si te sientes bien.'
        ]
    },
    'Custom': {
        title: 'Objetivo Personalizado',
        description: 'Has seleccionado un objetivo personalizado. Define tus propios parámetros de series, repeticiones e intensidad para alcanzar tus metas específicas.',
        recommendations: [
            'Asegúrate de que tus parámetros sean coherentes con tu objetivo.',
            'Considera aplicar principios de sobrecarga progresiva de una semana a otra.',
            '¡Escucha a tu cuerpo y ajusta según sea necesario!'
        ]
    }
};

const WeekGoalCoach: React.FC<WeekGoalCoachProps> = ({ goal }) => {
    const [isVisible, setIsVisible] = useState(false);
    
    // This effect handles the animation when the goal changes.
    useEffect(() => {
        if (goal) {
            // If there's a goal, first hide quickly to reset, then show.
            setIsVisible(false);
            const timer = setTimeout(() => {
                setIsVisible(true);
            }, 50); // Short delay to allow DOM to update
            return () => clearTimeout(timer);
        } else {
            setIsVisible(false);
        }
    }, [goal]);

    if (!goal || !isVisible) {
        return null;
    }

    const content = GOAL_DESCRIPTIONS[goal];

    return (
        <div 
            className="bg-slate-900/50 border-l-4 border-sky-500 p-4 rounded-r-lg transition-all duration-500 ease-out"
            style={{
                opacity: isVisible ? 1 : 0,
                transform: isVisible ? 'translateY(0)' : 'translateY(-10px)',
                maxHeight: isVisible ? '500px' : '0',
                overflow: 'hidden'
            }}
        >
            <div className="flex items-start gap-3">
                <LightbulbIcon size={20} className="text-sky-400 flex-shrink-0 mt-1" />
                <div>
                    <h4 className="font-bold text-sky-300">Apunte del Coach: {content.title}</h4>
                    <p className="text-sm text-slate-300 mt-1 mb-3">{content.description}</p>
                    <ul className="space-y-1 text-sm text-slate-400 list-disc list-inside">
                        {content.recommendations.map((rec, index) => (
                           <li key={index} dangerouslySetInnerHTML={{ __html: rec.replace(/\*\*(.*?)\*\*/g, '<strong class="text-slate-200">$1</strong>') }} />
                        ))}
                    </ul>
                </div>
            </div>
        </div>
    );
};

export default WeekGoalCoach;