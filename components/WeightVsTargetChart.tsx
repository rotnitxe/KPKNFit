// components/WeightVsTargetChart.tsx
import React, { useMemo } from 'react';
import { useAppState } from '../contexts/AppContext';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import Card from './ui/Card';

const WeightVsTargetChart: React.FC = () => {
    const { bodyProgress, settings } = useAppState();

    const chartData = useMemo(() => {
        if (bodyProgress.length < 2) return [];

        const sortedLogs = [...bodyProgress].filter(l => l.weight).sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
        if (sortedLogs.length < 2) return [];

        const firstLog = sortedLogs[0];
        const lastLog = sortedLogs[sortedLogs.length - 1];
        const targetWeight = settings.userVitals.targetWeight;

        if (!targetWeight || !firstLog.weight) return sortedLogs.map(log => ({
            date: new Date(log.date).toLocaleDateString('es-ES', { month: 'short', day: 'numeric' }),
            'Peso Real': log.weight
        }));

        const startDate = new Date(firstLog.date).getTime();
        const endDate = new Date(lastLog.date).getTime();
        const duration = Math.max(1, (endDate - startDate) / (1000 * 3600 * 24)); // in days

        return sortedLogs.map(log => {
            const logDate = new Date(log.date).getTime();
            const elapsed = (logDate - startDate) / (1000 * 3600 * 24);
            const progressRatio = duration > 0 ? elapsed / duration : 0;
            const target = firstLog.weight! + (targetWeight - firstLog.weight!) * progressRatio;

            return {
                date: new Date(log.date).toLocaleDateString('es-ES', { month: 'short', day: 'numeric' }),
                'Peso Real': log.weight,
                'Línea de Objetivo': parseFloat(target.toFixed(1))
            };
        });
    }, [bodyProgress, settings.userVitals.targetWeight]);
    
    if (chartData.length < 2) return null;

    return (
        <Card>
             <h3 className="text-xl font-bold mb-4">Evolución del Peso</h3>
             <ResponsiveContainer width="100%" height={250}>
                <LineChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#475569" />
                    <XAxis dataKey="date" stroke="#94a3b8" />
                    <YAxis domain={['dataMin - 2', 'dataMax + 2']} stroke="#94a3b8" unit={settings.weightUnit} />
                    <Tooltip contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155' }} />
                    <Legend />
                    <Line type="monotone" dataKey="Peso Real" stroke="#3b82f6" strokeWidth={2} dot={{r: 3}} activeDot={{r: 6}} isAnimationActive={true} animationDuration={800}/>
                    {chartData[0]['Línea de Objetivo'] && <Line type="monotone" dataKey="Línea de Objetivo" stroke="#a855f7" strokeWidth={2} strokeDasharray="5 5" isAnimationActive={true} animationDuration={800}/>}
                </LineChart>
             </ResponsiveContainer>
        </Card>
    );
};

export default WeightVsTargetChart;