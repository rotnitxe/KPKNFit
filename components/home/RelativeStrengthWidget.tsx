// components/home/RelativeStrengthWidget.tsx
// Versión compacta de fuerza relativa en básicos (sentadilla, banca, peso muerto)

import React, { useMemo } from 'react';
import { useAppState } from '../../contexts/AppContext';
import { calculateBrzycki1RM } from '../../utils/calculations';
import { ActivityIcon } from '../icons';

const PATTERNS = {
    squat: { pl: ['trasera barra alta', 'trasera barra baja', 'high bar', 'low bar'], vars: ['zercher', 'frontal', 'front', 'safety', 'ssb'] },
    bench: { pl: ['táctil', 'touch and go'], vars: ['inclinado con barra', 'inclinado con mancuerna', 'plano con mancuerna', 'smith'] },
    deadlift: { pl: ['convencional', 'sumo'], vars: ['rumano', 'rdl', 'rígida', 'stiff'] },
};

const findBest1RM = (history: any[], plNames: string[], varNames: string[]) => {
    let best = { name: '', rm: 0 };
    history.forEach(log => {
        (log.completedExercises || []).forEach((ex: any) => {
            const name = (ex.exerciseName || ex.name || '').toLowerCase();
            const maxRm = (ex.sets || []).reduce((max: number, s: any) => {
                if (s.weight && (s.completedReps ?? s.reps)) {
                    const rm = calculateBrzycki1RM(s.weight, s.completedReps ?? s.reps);
                    return rm > max ? rm : max;
                }
                return max;
            }, 0);
            const matchesPL = plNames.some(p => name.includes(p));
            const matchesVar = varNames.some(p => name.includes(p));
            if ((matchesPL || matchesVar) && maxRm > best.rm) {
                best = { name: ex.exerciseName || ex.name, rm: maxRm };
            }
        });
    });
    return best;
};

export const RelativeStrengthWidget: React.FC<{
    onNavigate?: () => void;
}> = ({ onNavigate }) => {
    const { history, settings } = useAppState();
    const bodyWeight = settings?.userVitals?.weight || 0;

    const squat = useMemo(() => findBest1RM(history, PATTERNS.squat.pl, PATTERNS.squat.vars), [history]);
    const bench = useMemo(() => findBest1RM(history, PATTERNS.bench.pl, PATTERNS.bench.vars), [history]);
    const deadlift = useMemo(() => findBest1RM(history, PATTERNS.deadlift.pl, PATTERNS.deadlift.vars), [history]);

    const basics = [
        { label: 'Sentadilla', rm: squat.rm, name: squat.name },
        { label: 'Banca', rm: bench.rm, name: bench.name },
        { label: 'P. Muerto', rm: deadlift.rm, name: deadlift.name },
    ];

    const hasAny = basics.some(b => b.rm > 0);

    if (!hasAny) {
        return (
            <div className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl p-4">
                <div className="flex justify-between items-center mb-3">
                    <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em] flex items-center gap-2">
                        <ActivityIcon size={10} className="text-emerald-400" /> Fuerza Relativa
                    </span>
                </div>
                <p className="text-[10px] text-zinc-500 font-mono">Sin registros de básicos</p>
            </div>
        );
    }

    return (
        <div className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl p-4">
            <div className="flex justify-between items-center mb-3">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em] flex items-center gap-2">
                    <ActivityIcon size={10} className="text-emerald-400" /> Fuerza Relativa
                </span>
                {bodyWeight > 0 && (
                    <span className="text-[8px] text-zinc-600 font-mono">BW {bodyWeight} kg</span>
                )}
            </div>
            <div className="space-y-2">
                {basics.map(({ label, rm, name }) => {
                    const ratio = bodyWeight > 0 && rm > 0 ? (rm / bodyWeight).toFixed(2) : null;
                    return (
                        <div key={label} className="flex items-center justify-between py-2 px-3 rounded-lg bg-black/30 border border-white/5">
                            <div className="min-w-0">
                                <span className="text-[10px] font-bold text-white">{label}</span>
                                {name && rm === 0 ? null : name && (
                                    <p className="text-[8px] text-zinc-500 truncate">{name}</p>
                                )}
                            </div>
                            <div className="flex items-center gap-2 shrink-0">
                                <span className="text-sm font-black text-emerald-400">{rm > 0 ? `${Math.round(rm)} kg` : '—'}</span>
                                {ratio != null && rm > 0 && (
                                    <span className="text-[9px] font-mono text-zinc-500">{ratio}x BW</span>
                                )}
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
};
