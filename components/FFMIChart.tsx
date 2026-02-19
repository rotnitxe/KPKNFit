// components/FFMIChart.tsx
import React, { useMemo } from 'react';
import { useAppState } from '../contexts/AppContext';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import Card from './ui/Card';
import { calculateFFMI } from '../utils/calculations';

const FFMIChart: React.FC = () => {
    const { bodyProgress, settings } = useAppState();

    const chartData = useMemo(() => {
        if (!settings.userVitals.height) return [];
        
        return bodyProgress
            .filter(log => log.weight && log.bodyFatPercentage)
            .sort((a,b) => new Date(a.date).getTime() - new Date(b.date).getTime())
            .map(log => {
                const ffmi = calculateFFMI(settings.userVitals.height!, log.weight!, log.bodyFatPercentage!);
                return {
                    date: new Date(log.date).toLocaleDateString('es-ES', { month: 'short', day: 'numeric' }),
                    FFMI: ffmi ? parseFloat(ffmi.normalizedFfmi) : null
                };
            }).filter(d => d.FFMI !== null);
    }, [bodyProgress, settings.userVitals.height]);
    
    if (chartData.length < 2) return null;

    return (
        <Card>
            <h3 className="text-xl font-bold mb-4">Evolución de FFMI Normalizado</h3>
            <ResponsiveContainer width="100%" height={250}>
                <LineChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#475569" />
                    <XAxis dataKey="date" stroke="#94a3b8" />
                    <YAxis domain={['dataMin - 1', 'dataMax + 1']} stroke="#94a3b8" />
                    <Tooltip contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155' }} />
                    <Legend />
                    <Line type="monotone" dataKey="FFMI" stroke="#10b981" strokeWidth={2} dot={{r: 3}} activeDot={{r: 6}} isAnimationActive={true} animationDuration={800}/>
                </LineChart>
            </ResponsiveContainer>
        </Card>
    );
};

export default FFMIChart;