import React from 'react';
import { Exercise, WorkoutLog, Settings } from '../types';
import Modal from './ui/Modal';
import { BarChartIcon } from './icons';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';

interface ExerciseHistoryModalProps {
    exercise: Exercise;
    programId: string;
    history: WorkoutLog[];
    settings: Settings;
    onClose: () => void;
}

const ExerciseHistoryModal: React.FC<ExerciseHistoryModalProps> = ({ exercise, programId, history, settings, onClose }) => {
    // 1. Filtrar historial para este ejercicio (primero en este programa, si vacío buscar en todo)
    const exerciseHistory = React.useMemo(() => {
        const matchExercise = (ce: { exerciseId?: string; exerciseDbId?: string; exerciseName?: string }) => {
            if (ce.exerciseDbId && exercise.exerciseDbId && ce.exerciseDbId === exercise.exerciseDbId) return true;
            if (ce.exerciseId === exercise.id) return true;
            const ceName = (ce.exerciseName || '').trim().toLowerCase();
            const exName = (exercise.name || '').trim().toLowerCase();
            if (ceName && exName && ceName === exName) return true;
            return false;
        };
        const extractFromLogs = (logs: typeof history) =>
            logs
                .map(log => {
                    const completedEx = log.completedExercises.find(matchExercise);
                    if (completedEx) return { date: log.date, sets: completedEx.sets };
                    return null;
                })
                .filter((log): log is { date: string; sets: any[] } => log !== null)
                .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());

        const fromProgram = extractFromLogs(history.filter(log => log.programId === programId));
        if (fromProgram.length > 0) return fromProgram;
        return extractFromLogs(history);
    }, [history, programId, exercise.id, exercise.exerciseDbId, exercise.name]);

    // 2. Preparar datos para el gráfico
    const chartData = React.useMemo(() => {
        return [...exerciseHistory]
            .reverse()
            .map(log => {
                const maxWeight = Math.max(...(log.sets.map(s => s.weight || 0)));
                return {
                    date: new Date(log.date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' }),
                    'Peso Máximo': maxWeight > 0 ? maxWeight : null,
                };
            })
            .filter(d => d['Peso Máximo'] !== null);
    }, [exerciseHistory]);

    return (
        <Modal isOpen={true} onClose={onClose} title={`Historial: ${exercise.name}`}>
            <div className="p-2 space-y-4 max-h-[70vh] overflow-y-auto custom-scrollbar">
                {/* Chart Section */}
                {chartData.length > 1 ? (
                    <div className="bg-black/20 p-2 rounded-xl border border-white/5">
                        <h4 className="text-xs font-bold text-gray-400 mb-4 flex items-center gap-2 uppercase tracking-wider">
                            <BarChartIcon size={14}/> Progresión (Peso Máx)
                        </h4>
                        <div className="h-[200px] w-full text-xs">
                            <ResponsiveContainer width="100%" height="100%">
                                <LineChart data={chartData}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="#333" vertical={false} />
                                    <XAxis dataKey="date" stroke="#666" tick={{fill: '#666', fontSize: 10}} tickLine={false} axisLine={false} />
                                    <YAxis stroke="#666" tick={{fill: '#666', fontSize: 10}} tickLine={false} axisLine={false} unit={settings.weightUnit} domain={['dataMin - 5', 'auto']} />
                                    <Tooltip 
                                        contentStyle={{ backgroundColor: '#111', border: '1px solid #333', borderRadius: '8px', fontSize: '12px' }} 
                                        itemStyle={{ color: '#fff' }}
                                    />
                                    <Line type="monotone" dataKey="Peso Máximo" stroke="#3b82f6" strokeWidth={3} dot={{r: 4, fill: '#111', strokeWidth: 2}} activeDot={{r: 6, fill: '#3b82f6'}} />
                                </LineChart>
                            </ResponsiveContainer>
                        </div>
                    </div>
                ) : (
                    <div className="text-center text-gray-500 text-xs p-8 bg-black/20 rounded-xl border border-white/5 border-dashed">
                        Necesitas más de una sesión registrada para ver la gráfica de progreso.
                    </div>
                )}

                {/* Log List Section */}
                <div className="space-y-2">
                    {exerciseHistory.length > 0 ? (
                        exerciseHistory.map((log, index) => (
                            <div key={index} className="bg-zinc-900/50 border border-white/5 p-3 rounded-xl">
                                <p className="font-black text-gray-300 text-[10px] uppercase mb-2 tracking-wider">
                                    {new Date(log.date).toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'short' })}
                                </p>
                                <ul className="space-y-1">
                                    {log.sets.map((set, setIndex) => {
                                        if (set.completedReps === undefined || set.weight === undefined) return null;
                                        return (
                                            <li key={setIndex} className="flex justify-between items-center text-xs text-gray-400">
                                                <div className="flex gap-2">
                                                    <span className="font-bold text-gray-600 w-4">{setIndex + 1}</span>
                                                    <span className="text-white font-mono">{set.weight}<span className="text-[9px] text-gray-500">{settings.weightUnit}</span> x {set.completedReps}</span>
                                                </div>
                                                <div className="flex items-center gap-1">
                                                    {set.completedRPE && <span className="text-[9px] font-bold px-1.5 py-0.5 rounded bg-zinc-800 text-zinc-400">RPE {set.completedRPE}</span>}
                                                </div>
                                            </li>
                                        );
                                    })}
                                </ul>
                            </div>
                        ))
                    ) : (
                        <div className="text-center text-gray-500 text-xs p-4">No hay registros aún.</div>
                    )}
                </div>
            </div>
        </Modal>
    );
};

export default ExerciseHistoryModal;