// components/CalorieHistoryChart.tsx
import React, { useMemo } from 'react';
import { useAppState } from '../contexts/AppContext';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend, ReferenceLine } from 'recharts';
import Card from './ui/Card';

const CalorieHistoryChart: React.FC = () => {
    const { nutritionLogs, settings } = useAppState();

    const chartData = useMemo(() => {
        const dailyCalories: Record<string, number> = {};
        nutritionLogs.forEach(log => {
            const date = new Date(log.date).toISOString().split('T')[0];
            const totalCals = log.foods.reduce((sum, food) => sum + (food.calories || 0), 0);
            dailyCalories[date] = (dailyCalories[date] || 0) + totalCals;
        });
        
        return Object.entries(dailyCalories)
            .sort(([dateA], [dateB]) => new Date(dateA).getTime() - new Date(dateB).getTime())
            .slice(-30)
            .map(([date, calories]) => ({
                date: new Date(date + 'T12:00:00Z').toLocaleDateString('es-ES', { day: '2-digit', month: 'short' }),
                Calorías: Math.round(calories)
            }));
    }, [nutritionLogs]);

    if (chartData.length < 2) return null;

    return (
        <Card>
            <h3 className="text-xl font-bold mb-4">Historial de Calorías</h3>
            <ResponsiveContainer width="100%" height={250}>
                <BarChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#475569" />
                    <XAxis dataKey="date" stroke="#94a3b8" />
                    <YAxis domain={['dataMin - 100', 'dataMax + 100']} stroke="#94a3b8" unit=" kcal" />
                    <Tooltip contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155' }} />
                    <Legend />
                    <Bar dataKey="Calorías" fill="#f97316" isAnimationActive={true} animationDuration={800}/>
                    {settings.dailyCalorieGoal && settings.dailyCalorieGoal > 0 && (
                        <ReferenceLine 
                            y={settings.dailyCalorieGoal} 
                            label={{ value: 'Objetivo', position: 'insideTopLeft', fill: '#f87171' }} 
                            stroke="#f87171" 
                            strokeDasharray="3 3" 
                        />
                    )}
                </BarChart>
            </ResponsiveContainer>
        </Card>
    );
}

export default CalorieHistoryChart;