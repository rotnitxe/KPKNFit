// components/home/cards/ProgresoEjercicioCards.tsx
// Tarjetas: historial ejercicios, estrella 1RM, fuerza relativa, marcas evento, IPF GL

import React, { useMemo } from 'react';
import { SquareCard } from '../SquareCard';
import { useAppState } from '../../../contexts/AppContext';
import { calculateBrzycki1RM, calculateIPFGLPoints } from '../../../utils/calculations';
import { StarIcon, TrophyIcon, ActivityIcon, CalendarIcon } from '../../icons';

const BASIC_PATTERNS = {
    squat: { pl: ['trasera barra alta', 'trasera barra baja', 'high bar', 'low bar'] },
    bench: { pl: ['press banca', 'bench press'] },
    deadlift: { pl: ['convencional', 'sumo'] },
};

function findBest1RM(history: any[], patterns: string[]) {
    let best = 0;
    history.forEach((log: any) => {
        (log.completedExercises || []).forEach((ex: any) => {
            const name = (ex.exerciseName || ex.name || '').toLowerCase();
            if (!patterns.some(p => name.includes(p))) return;
            (ex.sets || []).forEach((s: any) => {
                if (s.weight && (s.completedReps ?? s.reps)) {
                    const rm = calculateBrzycki1RM(s.weight, s.completedReps ?? s.reps);
                    if (rm > best) best = rm;
                }
            });
        });
    });
    return best;
}

export const ExerciseHistoryCard: React.FC<{ onNavigate: () => void }> = ({ onNavigate }) => {
    const { history, activeProgramState, programs } = useAppState();
    const activeProgram = programs.find(p => p.id === activeProgramState?.programId);

    const exercisesFromRecentSessions = useMemo(() => {
        if (!activeProgram || !history?.length) return [];
        const progLogs = history
            .filter(l => l.programId === activeProgram.id)
            .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
            .slice(0, 14); // ~2 semanas
        const seen = new Set<string>();
        const out: { name: string }[] = [];
        progLogs.forEach((log: any) => {
            (log.completedExercises || []).forEach((ex: any) => {
                const name = ex.exerciseName || ex.name;
                if (name && !seen.has(name)) {
                    seen.add(name);
                    out.push({ name });
                }
            });
        });
        return out.slice(0, 6);
    }, [history, activeProgram]);

    const isEmpty = exercisesFromRecentSessions.length === 0;

    return (
        <SquareCard onClick={onNavigate} emptyMessage={isEmpty ? "Sin historial reciente" : undefined} label="Historial de ejercicios">
            <span className="text-[10px] text-[#49454F] text-center line-clamp-2">
                {exercisesFromRecentSessions.slice(0, 2).map(e => e.name).join(' · ')}
            </span>
            {exercisesFromRecentSessions.length > 2 && (
                <span className="text-[8px] text-[#79747E] mt-0.5">+{exercisesFromRecentSessions.length - 2} más</span>
            )}
        </SquareCard>
    );
};

export const Star1RMCard: React.FC<{ onNavigate: () => void }> = ({ onNavigate }) => {
    const { programs, activeProgramState, history } = useAppState();
    const activeProgram = programs.find(p => p.id === activeProgramState?.programId);

    const { count, withGoal } = useMemo(() => {
        let c = 0, g = 0;
        if (!activeProgram) return { count: 0, withGoal: 0 };
        activeProgram.macrocycles?.forEach(m =>
            m.blocks?.forEach(b =>
                b.mesocycles?.forEach(me =>
                    me.weeks?.forEach(w =>
                        w.sessions?.forEach(s => {
                            const exs = (s.parts?.length ? s.parts.flatMap((p: any) => p.exercises ?? []) : s.exercises ?? []);
                            exs.forEach((ex: any) => {
                                if (ex.isStarTarget && ex.name) {
                                    c++;
                                    if (ex.goal1RM != null && ex.goal1RM > 0) g++;
                                }
                            });
                        })
                    )
                )
            )
        );
        return { count: c, withGoal: g };
    }, [activeProgram]);

    const isEmpty = count === 0;

    return (
        <SquareCard onClick={onNavigate} emptyMessage={isEmpty ? "Marca ejercicios estrella en el editor" : undefined} label="Metas 1RM">
            <StarIcon size={24} className={isEmpty ? "text-[#49454F]" : "text-amber-500 mb-1"} filled={!isEmpty} />
            {!isEmpty && <span className="text-xs font-bold text-[#1C1B1F] mt-0.5">{count} ejercicios</span>}
            {withGoal > 0 && <span className="text-[8px] text-amber-600">{withGoal} con meta</span>}
        </SquareCard>
    );
};

export const RelativeStrengthCard: React.FC<{ onNavigate: () => void }> = ({ onNavigate }) => {
    const { history, settings } = useAppState();
    const bodyWeight = settings?.userVitals?.weight || 0;

    const data = useMemo(() => {
        const squat = findBest1RM(history || [], [...BASIC_PATTERNS.squat.pl]);
        const bench = findBest1RM(history || [], [...BASIC_PATTERNS.bench.pl]);
        const deadlift = findBest1RM(history || [], [...BASIC_PATTERNS.deadlift.pl]);
        return { squat, bench, deadlift };
    }, [history]);

    const hasData = data.squat > 0 || data.bench > 0 || data.deadlift > 0;

    return (
        <SquareCard onClick={onNavigate} emptyMessage={!hasData ? "Registra básicos para ver fuerza relativa" : undefined} label="Fuerza relativa">
            <ActivityIcon size={24} className={!hasData ? "text-[#49454F]" : "text-emerald-500 mb-1"} />
            {hasData && (
                <span className="text-[10px] text-[#49454F] mt-0.5 font-bold">
                    {bodyWeight ? `${Math.round((data.squat + data.bench + data.deadlift) / bodyWeight * 10) / 10}x BW` : '--'}
                </span>
            )}
        </SquareCard>
    );
};

export const EventMarksCard: React.FC<{ onNavigate: () => void }> = ({ onNavigate }) => {
    const { programs, activeProgramState } = useAppState();
    const activeProgram = programs.find(p => p.id === activeProgramState?.programId);

    const eventCount = useMemo(() => {
        if (!activeProgram) return 0;
        const events = (activeProgram as any).events || [];
        return events.length;
    }, [activeProgram]);

    const isEmpty = eventCount === 0;

    return (
        <SquareCard onClick={onNavigate} emptyMessage={isEmpty ? "Añade eventos o fechas clave" : undefined} label="Eventos">
            <CalendarIcon size={24} className={isEmpty ? "text-[#49454F]" : "text-sky-500 mb-1"} />
            {!isEmpty && <span className="text-xs font-bold text-[#1C1B1F] mt-0.5">{eventCount} eventos</span>}
        </SquareCard>
    );
};

export const IPFGLCard: React.FC<{ onNavigate: () => void }> = ({ onNavigate }) => {
    const { history, settings, programs, activeProgramState } = useAppState();
    const activeProgram = programs.find(p => p.id === activeProgramState?.programId);

    const { points, isPowerlifter } = useMemo(() => {
        const mode = (activeProgram as any)?.mode;
        const isPL = mode === 'powerlifting' || mode === 'powerbuilding';
        if (!isPL) return { points: 0, isPowerlifter: false };

        const squat = findBest1RM(history || [], [...BASIC_PATTERNS.squat.pl]);
        const bench = findBest1RM(history || [], [...BASIC_PATTERNS.bench.pl]);
        const deadlift = findBest1RM(history || [], [...BASIC_PATTERNS.deadlift.pl]);
        const total = squat + bench + deadlift;
        const weight = settings?.userVitals?.weight || 0;
        const gender = settings?.userVitals?.gender || 'male';
        const unit = settings?.weightUnit || 'kg';
        const pts = total > 0 && weight > 0
            ? calculateIPFGLPoints(total, weight, { gender, equipment: 'classic', lift: 'total', weightUnit: unit as 'kg' | 'lbs' })
            : 0;
        return { points: pts, isPowerlifter: true };
    }, [history, settings, activeProgram]);

    const hasData = points > 0;

    return (
        <SquareCard
            onClick={onNavigate}
            emptyMessage={!hasData ? "Añade básicos para ver IPF GL" : undefined}
            label="IPF GL"
        >
            <TrophyIcon size={24} className={!hasData ? "text-[#49454F]" : "text-amber-500 mb-1"} />
            {hasData && <span className="text-sm font-black text-[#1C1B1F] mt-0.5">{Math.round(points)}</span>}
        </SquareCard>
    );
};
