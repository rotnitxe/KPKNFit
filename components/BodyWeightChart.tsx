import React, { useMemo } from 'react';
import { BodyProgressLog, Settings } from '../types';
import Card from './ui/Card';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { TrendingUpIcon } from './icons';

interface BodyWeightChartProps {
    progress: BodyProgressLog[];
    settings: Settings;
}

const BodyWeightChart: React.FC<BodyWeightChartProps> = ({ progress, settings }) => {
    const chartData = useMemo(() => {
        return [...progress]
            .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
            .map(p => ({
                date: new Date(p.date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' }),
                Peso: p.weight,
            }));
    }, [progress]);

    if (progress.length < 2) {
        return null;
    }

    return (
        <Card>
            <h3 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
                <TrendingUpIcon /> Evolución del Peso Corporal
            </h3>
            <ResponsiveContainer width="100%" height={200}>
                <LineChart data={chartData} margin={{ top: 5, right: 20, left: -10, bottom: 5 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#475569" />
                    <XAxis dataKey="date" stroke="#94a3b8" />
                    <YAxis stroke="#94a3b8" domain={['dataMin - 2', 'dataMax + 2']} unit={settings.weightUnit} />
                    <Tooltip contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155' }} />
                    <Line type="monotone" dataKey="Peso" stroke="var(--primary-color-400)" strokeWidth={2} dot={{ r: 3 }} activeDot={{ r: 6 }} />
                </LineChart>
            </ResponsiveContainer>
        </Card>
    );
};

export default React.memo(BodyWeightChart);