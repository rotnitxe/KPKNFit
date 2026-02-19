// components/BodyMeasurementsChart.tsx
import React, { useState, useMemo, useEffect } from 'react';
import { BodyProgressLog } from '../types';
import Card from './ui/Card';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { ActivityIcon } from './icons';

interface BodyMeasurementsChartProps {
    progress: BodyProgressLog[];
}

const BodyMeasurementsChart: React.FC<BodyMeasurementsChartProps> = ({ progress }) => {
    const [selectedMeasurement, setSelectedMeasurement] = useState<string | null>(null);

    const { availableMeasurements, chartData } = useMemo(() => {
        const measurements = new Set<string>();
        const data: { date: string; value: number }[] = [];

        // First pass: find all available measurement keys
        progress.forEach(log => {
            if (log.measurements) {
                Object.keys(log.measurements).forEach(key => measurements.add(key));
            }
        });

        const available = Array.from(measurements).sort();
        
        const currentSelection = selectedMeasurement || (available.length > 0 ? available[0] : null);

        // Second pass: build chart data for the selected measurement
        if (currentSelection) {
            [...progress]
                .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
                .forEach(log => {
                    if (log.measurements && log.measurements[currentSelection] !== undefined) {
                        data.push({
                            date: new Date(log.date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' }),
                            value: log.measurements[currentSelection]!,
                        });
                    }
                });
        }

        return { availableMeasurements: available, chartData: data, currentSelection };
    }, [progress, selectedMeasurement]);

    useEffect(() => {
        if (!selectedMeasurement && availableMeasurements.length > 0) {
            setSelectedMeasurement(availableMeasurements[0]);
        }
    }, [availableMeasurements, selectedMeasurement]);
    
    if (availableMeasurements.length === 0) {
        return (
             <Card>
                <h3 className="text-xl font-bold text-white mb-2 flex items-center gap-2">
                    <ActivityIcon /> Evolución de Medidas
                </h3>
                <p className="text-center text-sm text-slate-500 py-4">
                    No has registrado ninguna medida corporal. Añade medidas como "Pecho" o "Cintura" en tus registros corporales para ver el gráfico.
                </p>
            </Card>
        );
    }


    return (
        <Card>
            <div className="flex justify-between items-center mb-4 flex-wrap gap-2">
                <h3 className="text-xl font-bold text-white flex items-center gap-2">
                    <ActivityIcon /> Evolución de Medidas
                </h3>
                <select 
                    value={selectedMeasurement || ''} 
                    onChange={e => setSelectedMeasurement(e.target.value)}
                    className="bg-slate-800 border border-slate-700 rounded-md p-1.5 text-sm"
                >
                    {availableMeasurements.map(m => <option key={m} value={m}>{m}</option>)}
                </select>
            </div>
             {chartData.length > 1 ? (
                <ResponsiveContainer width="100%" height={200}>
                    <LineChart data={chartData} margin={{ top: 5, right: 20, left: -10, bottom: 5 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#475569" />
                        <XAxis dataKey="date" stroke="#94a3b8" />
                        <YAxis stroke="#94a3b8" domain={['dataMin - 1', 'dataMax + 1']} unit=" cm" />
                        <Tooltip contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155' }} />
                        <Line type="monotone" dataKey="value" name={selectedMeasurement || 'Medida'} stroke="var(--primary-color-400)" strokeWidth={2} dot={{ r: 3 }} activeDot={{ r: 6 }} />
                    </LineChart>
                </ResponsiveContainer>
            ) : (
                <p className="text-center text-sm text-slate-500 py-4">
                    Necesitas al menos dos registros de "{selectedMeasurement}" para mostrar el gráfico.
                </p>
            )}
        </Card>
    );
};

export default BodyMeasurementsChart;