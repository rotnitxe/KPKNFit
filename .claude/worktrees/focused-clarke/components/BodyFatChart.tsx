// components/BodyFatChart.tsx
import React, { useMemo } from 'react';
import { useAppState } from '../contexts/AppContext';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import Card from './ui/Card';

const BodyFatChart: React.FC = () => {
    const { bodyProgress } = useAppState();

    const chartData = useMemo(() => {
        return bodyProgress
            .filter(log => log.bodyFatPercentage)
            .sort((a,b) => new Date(a.date).getTime() - new Date(b.date).getTime())
            .map(log => ({
                date: new Date(log.date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' }),
                'Grasa Corporal': log.bodyFatPercentage
            }));
    }, [bodyProgress]);
    
    if (chartData.length < 2) return null;
    
    return (
         <Card>
            <h3 className="text-xl font-bold mb-4">Evolución Grasa Corporal</h3>
            <ResponsiveContainer width="100%" height={250}>
                <LineChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#475569" />
                    <XAxis dataKey="date" stroke="#94a3b8" />
                    <YAxis domain={['dataMin - 1', 'dataMax + 1']} stroke="#94a3b8" unit="%" />
                    <Tooltip contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155' }} />
                    <Legend />
                    <Line type="monotone" dataKey="Grasa Corporal" stroke="#a855f7" strokeWidth={2} dot={{r: 3}} activeDot={{r: 6}} isAnimationActive={true} animationDuration={800}/>
                </LineChart>
            </ResponsiveContainer>
        </Card>
    );
};

export default BodyFatChart;