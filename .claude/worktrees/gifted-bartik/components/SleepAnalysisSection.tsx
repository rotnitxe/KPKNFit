// components/SleepAnalysisSection.tsx
import React, { useMemo, useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, ReferenceLine, CartesianGrid } from 'recharts';
import Card from './ui/Card';
import { BedIcon, PencilIcon, MoonIcon, AlertTriangleIcon } from './icons';
import Button from './ui/Button';
import EditSleepLogModal from './EditSleepLogModal';
import { SleepLog } from '../types';

const SleepAnalysisSection: React.FC = () => {
    const { sleepLogs } = useAppState();
    const { setSleepLogs } = useAppDispatch();
    const [editingLog, setEditingLog] = useState<SleepLog | null>(null);

    const chartData = useMemo(() => {
        if (!sleepLogs || sleepLogs.length === 0) return [];
        const logs = [...sleepLogs].sort((a,b) => new Date(a.endTime).getTime() - new Date(b.endTime).getTime()).slice(-14);
        
        return logs.map(log => ({
            name: new Date(log.endTime).toLocaleDateString('es-ES', { weekday: 'short', day: 'numeric' }),
            hours: parseFloat(log.duration.toFixed(1)),
            log,
        }));
    }, [sleepLogs]);

    const stats = useMemo(() => {
        if (chartData.length === 0) return { avg: 0, debt: 0 };
        const total = chartData.reduce((acc, curr) => acc + curr.hours, 0);
        const avg = total / chartData.length;
        
        // Deuda basada en 8 horas objetivo
        const debt = chartData.reduce((acc, curr) => acc + Math.max(0, 8 - curr.hours), 0);
        
        return { avg, debt };
    }, [chartData]);
    
    const handleSaveLog = (updatedLog: SleepLog) => {
        setSleepLogs(prev => prev.map(l => l.id === updatedLog.id ? updatedLog : l));
        setEditingLog(null);
    };

    if (chartData.length === 0) {
        return (
            <Card>
                <div className="text-center py-6">
                    <BedIcon size={40} className="mx-auto text-slate-600 mb-2" />
                    <p className="text-slate-400">Aún no hay datos de sueño registrados.</p>
                    <p className="text-xs text-slate-500 mt-1">El sistema registrará automáticamente 8h si olvidas hacerlo.</p>
                </div>
            </Card>
        );
    }

    return (
        <Card>
            <EditSleepLogModal isOpen={!!editingLog} onClose={() => setEditingLog(null)} onSave={handleSaveLog} log={editingLog} />
            
            <div className="grid grid-cols-2 gap-4 mb-6">
                <div className="bg-slate-900/50 p-4 rounded-lg text-center border border-slate-700/50">
                    <p className="text-xs text-slate-400 uppercase font-bold mb-1">Promedio (14 días)</p>
                    <p className={`text-3xl font-black ${stats.avg >= 7 ? 'text-green-400' : stats.avg >= 6 ? 'text-yellow-400' : 'text-red-400'}`}>
                        {stats.avg.toFixed(1)}h
                    </p>
                </div>
                <div className="bg-slate-900/50 p-4 rounded-lg text-center border border-slate-700/50">
                    <p className="text-xs text-slate-400 uppercase font-bold mb-1 flex items-center justify-center gap-1">
                         Deuda de Sueño
                    </p>
                    <p className={`text-3xl font-black ${stats.debt < 5 ? 'text-green-400' : 'text-red-400'}`}>
                        {stats.debt.toFixed(1)}h
                    </p>
                </div>
            </div>

            <h4 className="text-sm font-semibold text-slate-300 mb-4 ml-2">Historial Reciente</h4>
            <div className="h-48 w-full mb-6">
                 <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartData}>
                         <CartesianGrid strokeDasharray="3 3" stroke="#334155" vertical={false} />
                        <XAxis dataKey="name" stroke="#94a3b8" fontSize={10} tickMargin={5} />
                        <YAxis stroke="#94a3b8" fontSize={10} domain={[0, 10]} hide />
                        <Tooltip 
                            contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155', borderRadius: '8px' }}
                            cursor={{fill: 'rgba(255,255,255,0.05)'}}
                            formatter={(value: number) => [`${value}h`, 'Sueño']}
                        />
                        <ReferenceLine y={8} stroke="#10b981" strokeDasharray="3 3" />
                        <Bar dataKey="hours" fill="#6366f1" radius={[4, 4, 0, 0]} />
                    </BarChart>
                </ResponsiveContainer>
            </div>

            <div className="space-y-2 max-h-60 overflow-y-auto pr-1 custom-scrollbar">
                {[...chartData].reverse().map((item) => (
                    <div key={item.log.id} className="flex items-center justify-between p-3 bg-slate-800/40 rounded-lg hover:bg-slate-800/60 transition-colors group">
                        <div className="flex items-center gap-3">
                            <div className={`p-2 rounded-full ${item.log.isAuto ? 'bg-yellow-500/10 text-yellow-500' : 'bg-indigo-500/10 text-indigo-400'}`}>
                                {item.log.isAuto ? <AlertTriangleIcon size={14} /> : <MoonIcon size={14} />}
                            </div>
                            <div>
                                <p className="text-sm font-semibold text-slate-200">{item.name}</p>
                                <p className="text-xs text-slate-500">
                                    {new Date(item.log.startTime).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})} - 
                                    {new Date(item.log.endTime).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}
                                    {item.log.isAuto && <span className="text-yellow-500/80 ml-1">(Auto)</span>}
                                </p>
                            </div>
                        </div>
                        <div className="flex items-center gap-3">
                             <span className="font-mono font-bold text-white">{item.hours}h</span>
                             <button onClick={() => setEditingLog(item.log)} className="p-1.5 text-slate-500 hover:text-white rounded-md hover:bg-slate-700 opacity-0 group-hover:opacity-100 transition-all">
                                <PencilIcon size={14} />
                             </button>
                        </div>
                    </div>
                ))}
            </div>
        </Card>
    );
};

export default SleepAnalysisSection;