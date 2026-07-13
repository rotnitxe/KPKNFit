import React, { useMemo } from 'react';
import { WorkoutLog, Settings } from '../types';
import { BarChartIcon, ActivityIcon, XIcon } from './icons';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

interface ExerciseHistoryModalProps {
    exerciseName: string;
    history: WorkoutLog[];
    settings: Settings;
    onClose: () => void;
}

const ExerciseHistoryModal: React.FC<ExerciseHistoryModalProps> = ({
    exerciseName,
    history,
    settings,
    onClose,
}) => {
    const exerciseHistory = useMemo(() => {
        const searchName = (exerciseName || '').trim().toLowerCase();
        return history
            .map(log => {
                const completedEx = log.completedExercises.find(ce => {
                    const ceName = (ce.exerciseName || '').trim().toLowerCase();
                    if (ceName && searchName && ceName === searchName) return true;
                    if (ce.exerciseId && String(ce.exerciseId) === exerciseName) return true;
                    return false;
                });
                if (completedEx) {
                    return {
                        date: log.date,
                        sessionName: log.sessionName,
                        programId: log.programId,
                        sets: completedEx.sets,
                    };
                }
                return null;
            })
            .filter((log): log is NonNullable<typeof log> => log !== null)
            .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
    }, [history, exerciseName]);

    const chartData = useMemo(() => {
        return [...exerciseHistory]
            .reverse()
            .map(log => {
                const maxWeight = Math.max(...(log.sets.map(s => s.weight || 0)));
                const totalVolume = log.sets.reduce((acc, s) => acc + (s.weight || 0) * (s.completedReps || s.targetReps || 0), 0);
                return {
                    date: new Date(log.date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' }),
                    'Peso Máx': maxWeight > 0 ? maxWeight : null,
                    'Vol': totalVolume > 0 ? Math.round(totalVolume) : null,
                };
            })
            .filter(d => d['Peso Máx'] !== null || d['Vol'] !== null);
    }, [exerciseHistory]);

    const totalSets = useMemo(() => exerciseHistory.reduce((acc, log) => acc + log.sets.length, 0), [exerciseHistory]);
    const totalVolume = useMemo(() => {
        return exerciseHistory.reduce((acc, log) => {
            return acc + log.sets.reduce((s, set) => s + (set.weight || 0) * (set.completedReps || set.targetReps || 0), 0);
        }, 0);
    }, [exerciseHistory]);

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm animate-fade-in" onClick={onClose}>
            <div
                className="bg-[#FEF7FF] w-full max-w-md max-h-[85vh] flex flex-col rounded-[28px] overflow-hidden shadow-2xl relative animate-slide-up"
                onClick={e => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex justify-between items-center p-5 border-b border-[#ECE6F0] bg-white">
                    <h2 className="text-lg font-bold text-[#1D1B20] tracking-tight truncate pr-4">
                        Historial: {exerciseName}
                    </h2>
                    <button onClick={onClose} className="p-2 rounded-full hover:bg-black/5 text-[#49454F] transition-colors flex-shrink-0">
                        <XIcon size={20} />
                    </button>
                </div>

                {/* Content */}
                <div className="p-5 overflow-y-auto custom-scrollbar space-y-6">
                    {/* Stats Grid */}
                    <div className="grid grid-cols-3 gap-3">
                        <div className="bg-[#ECE6F0] rounded-2xl p-4 flex flex-col items-center justify-center">
                            <span className="text-[10px] font-bold text-[#49454F] uppercase tracking-widest mb-1">Sesiones</span>
                            <span className="text-xl font-black text-[#1D1B20]">{exerciseHistory.length}</span>
                        </div>
                        <div className="bg-[#ECE6F0] rounded-2xl p-4 flex flex-col items-center justify-center">
                            <span className="text-[10px] font-bold text-[#49454F] uppercase tracking-widest mb-1">Series</span>
                            <span className="text-xl font-black text-[#1D1B20]">{totalSets}</span>
                        </div>
                        <div className="bg-[#E8DEF8] rounded-2xl p-4 flex flex-col items-center justify-center">
                            <span className="text-[10px] font-bold text-[#6750A4] uppercase tracking-widest mb-1">Vol total</span>
                            <span className="text-xl font-black text-[#6750A4]">{Math.round(totalVolume)}</span>
                        </div>
                    </div>

                    {/* Chart */}
                    {chartData.length > 1 ? (
                        <div className="bg-white p-4 rounded-3xl border border-[#ECE6F0] shadow-sm">
                            <h4 className="text-xs font-bold text-[#49454F] uppercase tracking-widest mb-4 flex items-center gap-2">
                                <BarChartIcon size={14} className="text-blue-600" /> Progresión
                            </h4>
                            <div className="h-[200px] w-full">
                                <ResponsiveContainer width="100%" height="100%">
                                    <LineChart data={chartData} margin={{ top: 5, right: 10, left: -20, bottom: 0 }}>
                                        <CartesianGrid strokeDasharray="3 3" stroke="#ECE6F0" vertical={false} />
                                        <XAxis dataKey="date" stroke="#49454F" tick={{ fill: '#49454F', fontSize: 10 }} tickLine={false} axisLine={false} dy={10} />
                                        <YAxis stroke="#49454F" tick={{ fill: '#49454F', fontSize: 10 }} tickLine={false} axisLine={false} unit={settings.weightUnit} domain={['dataMin - 5', 'auto']} dx={-5} />
                                        <Tooltip
                                            contentStyle={{ backgroundColor: '#FEF7FF', border: '1px solid #ECE6F0', borderRadius: '12px', fontSize: '12px', color: '#1D1B20', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                                            itemStyle={{ color: '#6750A4', fontWeight: 'bold' }}
                                        />
                                        <Line type="monotone" dataKey="Peso Máx" stroke="#6750A4" strokeWidth={3} dot={{ r: 4, strokeWidth: 2 }} activeDot={{ r: 6, stroke: '#6750A4', strokeWidth: 2, fill: '#fff' }} />
                                    </LineChart>
                                </ResponsiveContainer>
                            </div>
                        </div>
                    ) : (
                        <div className="text-center text-[#49454F] text-xs p-8 bg-[#ECE6F0]/50 rounded-3xl border border-[#ECE6F0] border-dashed">
                            {exerciseHistory.length < 2 ? 'Necesitas más sesiones para ver la gráfica de progresión.' : 'No hay datos de peso suficientes.'}
                        </div>
                    )}

                    {/* Log History */}
                    <div>
                        <h4 className="text-xs font-bold text-[#49454F] uppercase tracking-widest mb-3 flex items-center gap-2">
                            <ActivityIcon size={14} className="text-blue-600" /> Historial Reciente
                        </h4>
                        <div className="space-y-3">
                            {exerciseHistory.slice(0, 10).map((log, index) => (
                                <div key={index} className="bg-white border border-[#ECE6F0] p-4 rounded-2xl shadow-sm hover:shadow-md transition-shadow">
                                    <div className="flex justify-between items-center mb-3 pb-2 border-b border-[#ECE6F0]">
                                        <span className="text-sm font-bold text-[#1D1B20] capitalize">
                                            {new Date(log.date).toLocaleDateString('es-ES', { weekday: 'short', day: 'numeric', month: 'short' })}
                                        </span>
                                        <span className="text-xs font-medium text-[#49454F] bg-[#ECE6F0] px-2 py-1 rounded-full">
                                            {log.sets.length} sets
                                        </span>
                                    </div>
                                    <div className="space-y-2">
                                        {log.sets.map((set, setIndex) => {
                                            if (set.completedReps === undefined && !set.weight) return null;
                                            return (
                                                <div key={setIndex} className="flex items-center gap-3 text-sm">
                                                    <span className="text-[#49454F] font-medium w-5 text-center bg-[#ECE6F0] rounded-full text-xs py-0.5">
                                                        {setIndex + 1}
                                                    </span>
                                                    <span className="flex-1 font-medium text-[#1D1B20]">
                                                        {set.weight || '—'}{settings.weightUnit} × {set.completedReps ?? set.targetReps ?? '—'}
                                                    </span>
                                                    {set.completedRPE && (
                                                        <span className="text-xs font-bold text-[#6750A4] bg-[#E8DEF8] px-2 py-0.5 rounded-full">
                                                            RPE {set.completedRPE}
                                                        </span>
                                                    )}
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>
                            ))}
                        </div>
                        {exerciseHistory.length > 10 && (
                            <p className="text-xs text-[#49454F] font-medium mt-4 text-center">
                                +{exerciseHistory.length - 10} sesiones anteriores
                            </p>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default ExerciseHistoryModal;