// components/MacroDistributionSummary.tsx
import React, { useMemo } from 'react';
import { useAppState } from '../contexts/AppContext';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip, Legend } from 'recharts';
import Card from './ui/Card';

const MacroDistributionSummary: React.FC = () => {
    const { nutritionLogs, settings } = useAppState();
    
    const weeklyAverages = useMemo(() => {
        const oneWeekAgo = new Date();
        oneWeekAgo.setDate(oneWeekAgo.getDate() - 7);
        const recentLogs = nutritionLogs.filter(log => new Date(log.date) >= oneWeekAgo);
        
        if (recentLogs.length === 0) return null;

        const totals = recentLogs.reduce((acc, log) => {
             log.foods.forEach(food => {
                acc.protein += food.protein || 0;
                acc.carbs += food.carbs || 0;
                acc.fats += food.fats || 0;
            });
            return acc;
        }, { protein: 0, carbs: 0, fats: 0 });

        const daysWithLogs = new Set(recentLogs.map(log => new Date(log.date).toISOString().split('T')[0])).size;
        const avgDays = daysWithLogs > 0 ? daysWithLogs : 1;

        return {
            protein: totals.protein / avgDays,
            carbs: totals.carbs / avgDays,
            fats: totals.fats / avgDays,
        };
    }, [nutritionLogs]);

    const consumedData = useMemo(() => {
        if (!weeklyAverages) return [];
        const { protein, carbs, fats } = weeklyAverages;
        const total = protein + carbs + fats;
        if (total === 0) return []; // NaN-proofing
        return [
            { name: 'Proteína', value: protein, color: '#3b82f6' },
            { name: 'Carbs', value: carbs, color: '#f97316' },
            { name: 'Grasas', value: fats, color: '#eab308' },
        ];
    }, [weeklyAverages]);

    const targetData = useMemo(() => {
        const { dailyProteinGoal, dailyCarbGoal, dailyFatGoal } = settings;
        if (!dailyProteinGoal || !dailyCarbGoal || !dailyFatGoal) return [];
        const total = dailyProteinGoal + dailyCarbGoal + dailyFatGoal;
        if (total === 0) return []; // NaN-proofing
        return [
            { name: 'Proteína', value: dailyProteinGoal, color: '#3b82f6' },
            { name: 'Carbs', value: dailyCarbGoal, color: '#f97316' },
            { name: 'Grasas', value: dailyFatGoal, color: '#eab308' },
        ];
    }, [settings]);
    
    if (!weeklyAverages && targetData.length === 0) return null;

    const renderCustomizedLabel = ({ cx, cy, midAngle, innerRadius, outerRadius, percent, index, name }: any) => {
        const RADIAN = Math.PI / 180;
        const radius = innerRadius + (outerRadius - innerRadius) * 0.5;
        const x = cx + radius * Math.cos(-midAngle * RADIAN);
        const y = cy + radius * Math.sin(-midAngle * RADIAN);

        if (percent < 0.1) return null; // Don't render label if slice is too small

        return (
            <text x={x} y={y} fill="white" textAnchor="middle" dominantBaseline="central" fontSize="12px" fontWeight="bold">
                {`${(percent * 100).toFixed(0)}%`}
            </text>
        );
    };

    return (
        <Card>
            <h3 className="text-xl font-bold mb-4">Distribución de Macros</h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-center">
                {/* Consumed Macros */}
                <div className="text-center">
                    <h4 className="font-semibold text-white mb-2">Promedio Semanal</h4>
                    {consumedData.length > 0 ? (
                        <ResponsiveContainer width="100%" height={150}>
                            <PieChart>
                                <Pie data={consumedData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={60} labelLine={false} label={renderCustomizedLabel} isAnimationActive={true} animationDuration={800}>
                                    {consumedData.map((entry) => <Cell key={`cell-${entry.name}`} fill={entry.color} />)}
                                </Pie>
                                <Tooltip contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155' }} formatter={(value: number) => `${value.toFixed(1)}g`} />
                            </PieChart>
                        </ResponsiveContainer>
                    ) : <p className="text-xs text-slate-500 h-[150px] flex items-center justify-center">Sin datos de consumo.</p>}
                </div>

                {/* Target Macros */}
                <div className="text-center">
                    <h4 className="font-semibold text-white mb-2">Objetivo</h4>
                     {targetData.length > 0 ? (
                        <ResponsiveContainer width="100%" height={150}>
                            <PieChart>
                                <Pie data={targetData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={60} labelLine={false} label={renderCustomizedLabel} isAnimationActive={true} animationDuration={800}>
                                    {targetData.map((entry) => <Cell key={`cell-target-${entry.name}`} fill={entry.color} />)}
                                </Pie>
                                <Tooltip contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155' }} formatter={(value: number) => `${value.toFixed(1)}g`} />
                            </PieChart>
                        </ResponsiveContainer>
                    ) : <p className="text-xs text-slate-500 h-[150px] flex items-center justify-center">Sin objetivos definidos.</p>}
                </div>
            </div>
             <div className="flex justify-center flex-wrap gap-x-4 gap-y-1 mt-4">
                {consumedData.map(entry => (
                    <div key={entry.name} className="flex items-center gap-2 text-xs">
                        <div className="w-3 h-3 rounded-full" style={{backgroundColor: entry.color}}></div>
                        <span>{entry.name}</span>
                    </div>
                ))}
            </div>
        </Card>
    );
};

export default MacroDistributionSummary;