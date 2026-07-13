// components/CorrelationDashboard.tsx
import React, { useMemo, useState } from 'react';
import { useAppState } from '../contexts/AppContext';
import { getWeekId } from '../utils/calculations';
import { generateCorrelationAnalysis } from '../services/aiService';
import { ComposedChart, Line, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend, ReferenceArea } from 'recharts';
import Card from './ui/Card';
import Button from './ui/Button';
import { BrainIcon, SparklesIcon } from './icons';
import SkeletonLoader from './ui/SkeletonLoader';
import { BodyProgressLog, NutritionLog, WorkoutLog } from '../types';

interface WeeklyData {
    weekId: string;
    weekName: string;
    avgWeight: number | null;
    avgCalories: number | null;
    totalVolume: number;
    avgSleep: number | null;
    avgProtein: number | null;
}

const CustomTooltip = ({ active, payload, label }: any) => {
  if (active && payload && payload.length) {
    return (
      <div className="bg-slate-800/80 backdrop-blur-sm p-3 rounded-lg border border-slate-700 text-sm">
        <p className="font-bold text-white">{label}</p>
        {payload.map((p: any) => (
          <p key={p.dataKey} style={{ color: p.color }}>
            {`${p.name}: ${p.value.toFixed(1)} ${p.unit || ''}`}
          </p>
        ))}
      </div>
    );
  }
  return null;
};

export const CorrelationDashboard: React.FC = () => {
    const { bodyProgress, nutritionLogs, history: workoutHistory, settings, isOnline } = useAppState();
    const [analysis, setAnalysis] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(false);

    const weeklyData = useMemo<WeeklyData[]>(() => {
        const dataMap = new Map<string, {
            weights: number[],
            calories: number[],
            proteins: number[],
            volumes: number[],
            sleeps: number[],
            calorieDays: Set<string>,
            proteinDays: Set<string>,
        }>();

        const processLogs = <T extends { date: string }>(logs: T[], processor: (log: T, weekData: any) => void) => {
            logs.forEach(log => {
                const weekId = getWeekId(new Date(log.date), settings.startWeekOn);
                if (!dataMap.has(weekId)) {
                    dataMap.set(weekId, { weights: [], calories: [], proteins: [], volumes: [], sleeps: [], calorieDays: new Set(), proteinDays: new Set() });
                }
                processor(log, dataMap.get(weekId)!);
            });
        };

        processLogs(bodyProgress, (log: BodyProgressLog, weekData) => {
            if ('weight' in log && typeof log.weight === 'number') weekData.weights.push(log.weight);
        });

        processLogs(nutritionLogs, (log: NutritionLog, weekData) => {
            const dailyTotal = log.foods.reduce((acc, food) => ({
                calories: acc.calories + (food.calories || 0),
                protein: acc.protein + (food.protein || 0),
            }), { calories: 0, protein: 0 });
            
            if (dailyTotal.calories > 0) {
                weekData.calories.push(dailyTotal.calories);
                weekData.calorieDays.add(log.date.split('T')[0]);
            }
            if (dailyTotal.protein > 0) {
                weekData.proteins.push(dailyTotal.protein);
                weekData.proteinDays.add(log.date.split('T')[0]);
            }
        });

        processLogs(workoutHistory, (log: WorkoutLog, weekData) => {
            const volume = log.completedExercises.reduce((total, ex) =>
                total + ex.sets.reduce((setTotal, s) => setTotal + (s.weight || 0) * (s.completedReps || 0), 0), 0);
            weekData.volumes.push(volume);
            if (log.readiness?.sleepQuality) {
                weekData.sleeps.push(log.readiness.sleepQuality);
            }
        });

        const sortedWeeks = Array.from(dataMap.keys()).sort((a, b) => new Date(a).getTime() - new Date(b).getTime());

        return sortedWeeks.map(weekId => {
            const data = dataMap.get(weekId)!;
            const avgWeight = data.weights.length > 0 ? data.weights.reduce((a, b) => a + b, 0) / data.weights.length : null;
            const avgCalories = data.calorieDays.size > 0 ? data.calories.reduce((a, b) => a + b, 0) / data.calorieDays.size : null;
            const avgProtein = data.proteinDays.size > 0 ? data.proteins.reduce((a, b) => a + b, 0) / data.proteinDays.size : null;
            const totalVolume = data.volumes.reduce((a, b) => a + b, 0);
            const avgSleep = data.sleeps.length > 0 ? data.sleeps.reduce((a, b) => a + b, 0) / data.sleeps.length : null;
            
            return {
                weekId,
                weekName: new Date(weekId).toLocaleDateString('es-ES', { month: 'short', day: 'numeric' }),
                avgWeight,
                avgCalories,
                totalVolume,
                avgSleep,
                avgProtein,
            };
        }).slice(-12); // Last 12 weeks
    }, [bodyProgress, nutritionLogs, workoutHistory, settings.startWeekOn]);
    
    const handleGenerateAnalysis = async () => {
        if (!isOnline) return;
        setIsLoading(true);
        setAnalysis(null);
        try {
            const result = await generateCorrelationAnalysis(weeklyData, settings);
            setAnalysis(result.analysis);
        } catch (e) {
            console.error(e);
        } finally {
            setIsLoading(false);
        }
    };

    if (weeklyData.length < 2) {
        return (
             <Card>
                <h2 className="text-xl font-bold text-slate-300">Correlaciones de Datos</h2>
                <p className="text-center text-slate-400 p-8">Se necesitan más datos (al menos 2 semanas de registros de peso, nutrición y entrenamiento) para mostrar correlaciones.</p>
            </Card>
        );
    }

    return (
        <div className="space-y-6">
            <h2 className="text-2xl font-bold text-slate-300 border-b-2 border-slate-700 pb-2">Correlaciones de Datos</h2>
            <Card>
                <h3 className="font-bold text-lg text-white mb-4">Peso Corporal vs. Ingesta Calórica</h3>
                <ResponsiveContainer width="100%" height={300}>
                    <ComposedChart data={weeklyData}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#475569" />
                        <XAxis dataKey="weekName" stroke="#94a3b8" fontSize={10} />
                        <YAxis yAxisId="left" stroke="#82ca9d" unit={settings.weightUnit} fontSize={10} domain={['dataMin - 1', 'dataMax + 1']} />
                        <YAxis yAxisId="right" orientation="right" stroke="#f97316" unit="kcal" fontSize={10} domain={['dataMin - 200', 'dataMax + 200']} />
                        <Tooltip content={<CustomTooltip />} />
                        <Legend wrapperStyle={{fontSize: "12px"}}/>
                        <Line yAxisId="left" type="monotone" dataKey="avgWeight" name="Peso Prom." stroke="#82ca9d" strokeWidth={2} unit={settings.weightUnit} connectNulls />
                        <Bar yAxisId="right" dataKey="avgCalories" name="Calorías Prom." fill="#f97316" barSize={20} unit="kcal" />
                    </ComposedChart>
                </ResponsiveContainer>
            </Card>
            <Card>
                 <h3 className="font-bold text-lg text-white mb-4">Volumen de Entrenamiento vs. Peso Corporal y Sueño</h3>
                 <ResponsiveContainer width="100%" height={300}>
                    <ComposedChart data={weeklyData}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#475569" />
                        <XAxis dataKey="weekName" stroke="#94a3b8" fontSize={10} />
                        <YAxis yAxisId="left" stroke="#8884d8" fontSize={10}/>
                        <YAxis yAxisId="right" orientation="right" stroke="#82ca9d" unit={settings.weightUnit} fontSize={10}/>
                        <Tooltip content={<CustomTooltip />} />
                        <Legend wrapperStyle={{fontSize: "12px"}}/>
                        <Bar yAxisId="left" dataKey="totalVolume" name="Volumen" fill="#8884d8" barSize={20} unit={settings.weightUnit} />
                        <Line yAxisId="right" type="monotone" dataKey="avgWeight" name="Peso Prom." stroke="#82ca9d" strokeWidth={2} unit={settings.weightUnit} connectNulls />
                        <Line yAxisId="right" type="monotone" dataKey="avgSleep" name="Sueño Prom." stroke="#eab308" strokeDasharray="3 3" unit="/5" connectNulls />
                    </ComposedChart>
                </ResponsiveContainer>
            </Card>
             <Card>
                <h3 className="font-bold text-lg text-white mb-4 flex items-center gap-2"><BrainIcon /> Análisis del Coach IA</h3>
                {isLoading && <SkeletonLoader lines={3} />}
                {analysis && !isLoading && <div className="prose prose-sm prose-invert max-w-none animate-fade-in" dangerouslySetInnerHTML={{ __html: analysis.replace(/\n/g, '<br />') }} />}
                <Button onClick={handleGenerateAnalysis} isLoading={isLoading} disabled={!isOnline} className="w-full mt-4">
                    <SparklesIcon /> {analysis ? 'Regenerar Análisis' : 'Generar Análisis'}
                </Button>
            </Card>
        </div>
    );
};
