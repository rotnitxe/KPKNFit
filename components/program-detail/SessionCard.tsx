import React, { useState, useMemo } from 'react';
import { Session, ExerciseMuscleInfo } from '../../types';
import {
    ChevronDownIcon, ChevronUpIcon, DumbbellIcon, ClockIcon,
    PlayIcon, EditIcon, TrashIcon, ActivityIcon,
} from '../icons';

interface SessionCardProps {
    session: Session;
    index: number;
    onStart: () => void;
    onEdit: () => void;
    onDelete?: () => void;
    dayName?: string;
    exerciseList?: ExerciseMuscleInfo[];
}

const SessionCard: React.FC<SessionCardProps> = ({
    session, index, onStart, onEdit, onDelete, exerciseList = [],
}) => {
    const [isExpanded, setIsExpanded] = useState(false);
    const exercisesToDisplay: any[] = session.parts && session.parts.length > 0
        ? session.parts.flatMap(p => p.exercises || [])
        : (session.exercises || []);

    const totalSets = exercisesToDisplay.reduce((acc, ex) => acc + (ex.sets?.length || 0), 0);

    const averageFatigue = useMemo(() => {
        if (exercisesToDisplay.length === 0 || exerciseList.length === 0) return 0;
        let totalFatigue = 0;
        let validExercises = 0;

        exercisesToDisplay.forEach((ex: any) => {
            const info = exerciseList.find((e: any) => e.id === ex.exerciseDbId || e.name === ex.name);
            if (info) {
                let score = 5;
                const isMultiJoint = info.involvedMuscles.filter((m: any) => m.role === 'primary').length > 1 || info.involvedMuscles.length > 2;
                const equip = info.equipment?.toLowerCase() || '';
                const isMachine = equip.includes('máquina') || equip.includes('maquina') || equip.includes('polea');
                const isFreeWeight = equip.includes('barra') || equip.includes('mancuerna');
                if (isMultiJoint) score += 3; else score -= 1;
                if (isMachine) score += 2;
                if (isFreeWeight) score -= 1;
                totalFatigue += Math.max(1, Math.min(10, score));
                validExercises += 1;
            }
        });

        return validExercises > 0 ? Math.round((totalFatigue / validExercises) * 10) / 10 : 0;
    }, [exercisesToDisplay, exerciseList]);

    const getFatigueColor = (s: number) => {
        if (s === 0) return 'bg-zinc-800 text-zinc-500';
        if (s <= 3) return 'bg-emerald-500 text-emerald-950 shadow-[0_0_10px_rgba(16,185,129,0.5)]';
        if (s <= 7) return 'bg-yellow-400 text-yellow-950 shadow-[0_0_10px_rgba(250,204,21,0.5)]';
        return 'bg-red-500 text-red-950 shadow-[0_0_10px_rgba(239,68,68,0.5)]';
    };

    const getFatigueLabel = (s: number) => {
        if (s === 0) return 'Sin Datos';
        if (s <= 3) return 'Fatiga Baja';
        if (s <= 7) return 'Fatiga Moderada';
        return 'Fatiga Alta';
    };

    return (
        <div className="bg-zinc-900/40 border border-white/5 rounded-2xl overflow-hidden mb-2 hover:bg-zinc-900/60 transition-colors">
            <div className="p-3.5">
                <div className="flex justify-between items-start mb-3">
                    <div className="flex gap-3 items-center">
                        <div className="w-9 h-9 rounded-xl bg-black/50 border border-white/5 flex items-center justify-center text-xs font-black text-white">
                            {index + 1}
                        </div>
                        <div>
                            <h4 className="text-sm font-black text-white uppercase tracking-tight leading-none mb-0.5">{session.name}</h4>
                            <div className="flex items-center gap-3 text-[10px] text-zinc-500 font-medium">
                                <span className="flex items-center gap-1"><DumbbellIcon size={10} /> {exercisesToDisplay.length} Ej.</span>
                                <span className="flex items-center gap-1"><ClockIcon size={10} /> ~{totalSets * 3}m</span>
                            </div>
                        </div>
                    </div>
                    <button
                        onClick={() => setIsExpanded(!isExpanded)}
                        className={`p-1.5 rounded-full transition-colors ${isExpanded ? 'bg-white/10 text-white' : 'text-zinc-600 hover:text-white'}`}
                    >
                        {isExpanded ? <ChevronUpIcon size={16} /> : <ChevronDownIcon size={16} />}
                    </button>
                </div>

                <div className="flex gap-2">
                    <button
                        onClick={onStart}
                        className="flex-1 bg-white text-black text-[10px] font-black uppercase tracking-widest py-2.5 rounded-xl flex items-center justify-center gap-2 hover:bg-zinc-200 transition-colors"
                    >
                        <PlayIcon size={11} fill="black" /> INICIAR
                    </button>
                    <button
                        onClick={e => { e.stopPropagation(); onEdit(); }}
                        className="w-10 bg-black/50 border border-white/10 rounded-xl flex items-center justify-center text-zinc-400 hover:text-white hover:border-white/30 transition-colors"
                    >
                        <EditIcon size={14} />
                    </button>
                    {onDelete && (
                        <button
                            onClick={e => { e.stopPropagation(); onDelete(); }}
                            className="w-10 bg-black/50 border border-white/10 rounded-xl flex items-center justify-center text-zinc-400 hover:text-red-500 hover:border-red-500/30 transition-colors"
                        >
                            <TrashIcon size={14} />
                        </button>
                    )}
                </div>
            </div>

            {isExpanded && (
                <div className="bg-black/20 border-t border-white/5 p-3 space-y-3 animate-slide-down">
                    {averageFatigue > 0 && (
                        <div className="flex items-center justify-between bg-zinc-950 border border-white/5 rounded-xl p-2.5">
                            <div className="flex items-center gap-2">
                                <ActivityIcon size={14} className={averageFatigue > 7 ? 'text-red-500' : averageFatigue > 3 ? 'text-yellow-400' : 'text-emerald-500'} />
                                <div className="flex flex-col">
                                    <span className="text-[9px] font-black uppercase text-zinc-500 tracking-widest">Impacto</span>
                                    <span className="text-[11px] font-bold text-white">{getFatigueLabel(averageFatigue)}</span>
                                </div>
                            </div>
                            <div className={`flex items-center justify-center w-7 h-7 rounded-full ${getFatigueColor(averageFatigue)} font-black text-xs`}>
                                {Math.round(averageFatigue)}
                            </div>
                        </div>
                    )}

                    <div className="overflow-x-auto no-scrollbar">
                        <div className="flex gap-2 pb-1">
                            {exercisesToDisplay.length > 0 ? (
                                exercisesToDisplay.map((exercise: any, idx: number) => (
                                    <div key={idx} className="shrink-0 w-48 bg-zinc-800/40 border border-white/10 rounded-xl p-2.5">
                                        <div className="flex justify-between items-center mb-1">
                                            <span className="text-[11px] font-bold text-zinc-300 truncate max-w-[100px]">{exercise.name}</span>
                                            <span className="text-[9px] text-zinc-400 bg-black/50 px-1.5 py-0.5 rounded">{(exercise.sets || []).length}s</span>
                                        </div>
                                        <div className="border-l border-white/10 pl-2">
                                            {(exercise.sets || []).slice(0, 1).map((set: any, sIdx: number) => (
                                                <p key={sIdx} className="text-[9px] text-zinc-400">
                                                    {set.targetReps} reps {set.targetRPE ? `@ RPE ${set.targetRPE}` : ''}
                                                </p>
                                            ))}
                                            {(exercise.sets || []).length > 1 && (
                                                <p className="text-[8px] text-zinc-600 italic mt-0.5">+ {(exercise.sets || []).length - 1} más</p>
                                            )}
                                        </div>
                                    </div>
                                ))
                            ) : (
                                <div className="w-full text-center py-3">
                                    <p className="text-[10px] text-zinc-500 italic">Sin ejercicios</p>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default SessionCard;
