// components/CarpeDiemCoachCard.tsx
import React, { useState, useEffect, useMemo } from 'react';
import { Program, CarpeDiemPlan } from '../types';
import { useAppState } from '../contexts/AppContext';
import { generateCarpeDiemWeeklyPlan } from '../services/aiService';
import { cacheService } from '../services/cacheService';
import { getWeekId } from '../utils/calculations';
import { calculateACWR } from '../services/analysisService';
import Card from './ui/Card';
import SkeletonLoader from './ui/SkeletonLoader';
import { SparklesIcon } from './icons';

interface CarpeDiemCoachCardProps {
    program: Program;
}

const CarpeDiemCoachCard: React.FC<CarpeDiemCoachCardProps> = ({ program }) => {
    const { settings, isOnline, history, exerciseList } = useAppState();
    const [plan, setPlan] = useState<CarpeDiemPlan | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const acwrData = useMemo(() => calculateACWR(history, settings, exerciseList), [history, settings, exerciseList]);
    
    useEffect(() => {
        const fetchPlan = async () => {
            if (!program || !program.carpeDiemEnabled) {
                setIsLoading(false);
                return;
            }

            if (history.length < 3) {
                setPlan({
                    coachMessage: "Completa al menos 3 sesiones de este programa para que pueda analizar tus tendencias y adaptar tu plan. ¡Sigue con el plan original por ahora, tú puedes!",
                    modifiedSessions: []
                });
                setIsLoading(false);
                return;
            }

            setIsLoading(true);
            setError(null);

            const currentWeekId = getWeekId(new Date(), settings.startWeekOn);
            const cacheKey = `carpe_diem_${program.id}_${currentWeekId}`;

            try {
                const cachedPlan = await cacheService.get<CarpeDiemPlan>(cacheKey);
                if (cachedPlan) {
                    setPlan(cachedPlan);
                    setIsLoading(false);
                    return;
                }

                if (!isOnline) {
                    throw new Error("Necesitas conexión a internet para recibir el plan de tu coach IA.");
                }

                const newPlan = await generateCarpeDiemWeeklyPlan(program, history, settings, settings.calorieGoalObjective);
                setPlan(newPlan);
                await cacheService.set(cacheKey, newPlan);

            } catch (err: any) {
                console.error("Error fetching Carpe Diem plan:", err);
                setError(err.message || "No se pudo contactar al Coach IA.");
            } finally {
                setIsLoading(false);
            }
        };

        fetchPlan();
    }, [program, settings, history, isOnline]);

    return (
        <Card className="bg-gradient-to-br from-sky-900/30 to-slate-900/50 border-sky-600/50">
            <h3 className="text-xl font-bold text-sky-300 mb-2 flex items-center gap-2">
                <SparklesIcon /> Mensaje del Coach
            </h3>
            {acwrData && (
                <div className="flex items-center gap-2 text-xs text-slate-400 mb-3">
                    <span>Analizando con ACWR:</span>
                    <span className={`font-bold ${acwrData.color}`}>{acwrData.acwr}</span>
                </div>
            )}
            {isLoading && <SkeletonLoader lines={4} />}
            {error && <p className="text-sm text-yellow-400 text-center">{error}</p>}
            {!isLoading && plan && (
                <div className="prose prose-sm prose-invert max-w-none animate-fade-in" dangerouslySetInnerHTML={{ __html: plan.coachMessage.replace(/\n/g, '<br />') }} />
            )}
        </Card>
    );
};

export default CarpeDiemCoachCard;
