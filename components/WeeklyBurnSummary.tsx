
// components/WeeklyBurnSummary.tsx
import React, { useMemo } from 'react';
import { WorkoutLog, Settings } from '../types';
import { getWeekId } from '../utils/calculations';
import Card from './ui/Card';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import { FlameIcon } from './icons';

interface WeeklyBurnSummaryProps {
  history: WorkoutLog[];
  settings: Settings;
}

const WeeklyBurnSummary: React.FC<WeeklyBurnSummaryProps> = ({ history, settings }) => {
    const weeklyBurnData = useMemo(() => {
        const weeklyData: { [weekId: string]: number } = {};
        
        history.forEach(log => {
            if (log.caloriesBurned && log.caloriesBurned > 0) {
                const weekId = getWeekId(new Date(log.date), settings.startWeekOn);
                weeklyData[weekId] = (weeklyData[weekId] || 0) + log.caloriesBurned;
            }
        });

        return Object.entries(weeklyData)
            .map(([weekId, totalCalories]) => ({
                date: new Date(weekId.split('-').join('/')),
                name: new Date(weekId.split('-').join('/')).toLocaleDateString('es-ES', { month: 'short', day: 'numeric' }),
                Calorías: Math.round(totalCalories),
            }))
            .sort((a, b) => a.date.getTime() - b.date.getTime())
            .slice(-6); // Show last 6 weeks

    }, [history, settings.startWeekOn]);

    if (weeklyBurnData.length === 0) {
        return (
            <Card>
                <h3 className="text-xl font-bold text-white mb-2 flex items-center gap-2"><FlameIcon/> Calorías Quemadas</h3>
                 <p className="text-center text-sm text-slate-500 py-4">Completa entrenamientos y registra tu peso corporal para ver un estimado de las calorías quemadas.</p>
            </Card>
        );
    }

    return (
        <Card>
            <h3 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
                <FlameIcon /> Calorías Quemadas por Semana
            </h3>
            <ResponsiveContainer width="100%" height={200}>
                <BarChart data={weeklyBurnData} margin={{ top: 5, right: 20, left: -10, bottom: 5 }}>
                    <XAxis dataKey="name" stroke="#94a3b8" />
                    <YAxis stroke="#94a3b8" />
                    <Tooltip contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155' }} cursor={{fill: 'rgba(100,116,139,0.1)'}} />
                    <Bar dataKey="Calorías" fill="url(#colorUv)" />
                    <defs>
                        <linearGradient id="colorUv" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="#f97316" stopOpacity={0.8}/>
                            <stop offset="95%" stopColor="#f97316" stopOpacity={0.2}/>
                        </linearGradient>
                    </defs>
                </BarChart>
            </ResponsiveContainer>
        </Card>
    );
};

export default WeeklyBurnSummary;
