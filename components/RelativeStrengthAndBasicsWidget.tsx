import React, { useMemo, useState, useEffect } from 'react';
import { Session } from '../types';
import { calculateBrzycki1RM } from '../utils/calculations';
import { useAppContext } from '../contexts/AppContext';
import { ActivityIcon, DumbbellIcon, PlusIcon, XIcon } from './icons';

import { CaupolicanSquat } from './CaupolicanSquat';
import { CaupolicanBench } from './CaupolicanBench';
import { CaupolicanCDL } from './CaupolicanCDL';
import { CaupolicanSDL } from './CaupolicanSDL';

interface Props {
    displayedSessions: Session[];
}

export const RelativeStrengthAndBasicsWidget: React.FC<Props> = ({ displayedSessions }) => {
    const { history, settings, setSettings, exerciseList } = useAppContext();
    const bodyWeight = settings.userVitals?.weight || 0;

    // Estados
    const [mode, setMode] = useState<'pl' | 'custom'>('pl');
    const [isEditingBw, setIsEditingBw] = useState(false);
    const [bwInput, setBwInput] = useState('');
    const [isSaved, setIsSaved] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');

    // Ejercicios personalizados guardados en LocalStorage
    const [customExercises, setCustomExercises] = useState<string[]>(() => {
        const saved = localStorage.getItem('kpkn_custom_lifts');
        return saved ? JSON.parse(saved) : [];
    });

    const handleSaveCustomExercises = (newEx: string[]) => {
        setCustomExercises(newEx);
        localStorage.setItem('kpkn_custom_lifts', JSON.stringify(newEx));
    };

    const handleSaveWeight = () => {
        const w = parseFloat(bwInput);
        if (w > 0 && setSettings) {
            setSettings({ ...settings, userVitals: { ...(settings.userVitals || {}), weight: w } });
            setIsSaved(true);
            setTimeout(() => { setIsSaved(false); setIsEditingBw(false); }, 1200);
        }
    };

    // Inteligencia PL
    const PATTERNS = {
        squat: { pl: ['trasera barra alta', 'trasera barra baja', 'high bar', 'low bar'], vars: ['zercher', 'frontal', 'front', 'safety', 'ssb', 'belt', 'jefferson', 'hack', 'péndulo', 'pendular'] },
        bench: { pl: ['táctil', 'touch and go'], vars: ['inclinado con barra', 'inclinado con mancuerna', 'plano con mancuerna', 'smith', 'larsen'] },
        deadlift: { pl_conv: ['convencional'], pl_sumo: ['sumo'], vars: ['rumano', 'rdl', 'rígida', 'stiff', 'mancuerna', 'zercher'] }
    };

    const findBestLift = (plNames: string[], varNames: string[], patternType: string) => {
        let bestPL = { name: '', rm: 0, type: patternType };
        let bestVar = { name: '', rm: 0, type: patternType };

        history.forEach(log => {
            (log.completedExercises || (log as any).exercises || []).forEach((ex: any) => {
                const name = (ex.exerciseName || ex.name).toLowerCase();
                if (patternType === 'bench' && !name.includes('press')) return;
                const maxRm = ex.sets.reduce((max: number, s: any) => {
                    if (s.weight && (s.completedReps || s.reps)) {
                        const rm = calculateBrzycki1RM(s.weight, s.completedReps || s.reps);
                        return rm > max ? rm : max;
                    }
                    return max;
                }, 0);
                if (plNames.some(p => name.includes(p))) { if (maxRm > bestPL.rm) bestPL = { name: ex.exerciseName || ex.name, rm: maxRm, type: patternType }; }
                else if (varNames.some(p => name.includes(p))) { if (maxRm > bestVar.rm) bestVar = { name: ex.exerciseName || ex.name, rm: maxRm, type: patternType }; }
            });
        });
        if (bestPL.rm > 0) return bestPL;
        if (bestVar.rm > 0) return bestVar;
        return { name: 'Sin registros', rm: 0, type: patternType };
    };

    // Buscador exacto para los personalizados
    const findExactBestLift = (exactName: string) => {
        let best = { name: exactName, rm: 0 };
        history.forEach(log => {
            (log.completedExercises || (log as any).exercises || []).forEach((ex: any) => {
                if ((ex.exerciseName || ex.name) === exactName) {
                    const maxRm = ex.sets.reduce((max: number, s: any) => {
                        if (s.weight && (s.completedReps || s.reps)) {
                            const rm = calculateBrzycki1RM(s.weight, s.completedReps || s.reps);
                            return rm > max ? rm : max;
                        }
                        return max;
                    }, 0);
                    if (maxRm > best.rm) best.rm = maxRm;
                }
            });
        });
        return best;
    };

    const squatStats = useMemo(() => findBestLift(PATTERNS.squat.pl, PATTERNS.squat.vars, 'squat'), [history]);
    const benchStats = useMemo(() => findBestLift(PATTERNS.bench.pl, PATTERNS.bench.vars, 'bench'), [history]);
    const deadliftSlides = useMemo(() => {
        const conv = findBestLift(PATTERNS.deadlift.pl_conv, [], 'deadlift_conv');
        const sumo = findBestLift(PATTERNS.deadlift.pl_sumo, [], 'deadlift_sumo');
        const vars = findBestLift([], PATTERNS.deadlift.vars, 'deadlift_conv');

        const slides: any[] = [];

        // Si tiene convencional, lo agrega
        if (conv.rm > 0) slides.push({ title: 'Peso Muerto Conv.', stats: conv, iconType: 'deadlift_conv' });
        // Si tiene sumo, también lo agrega (o lo pone como único si no hace convencional)
        if (sumo.rm > 0) slides.push({ title: 'Peso Muerto Sumo', stats: sumo, iconType: 'deadlift_sumo' });

        // Si no hace ni sumo ni convencional, mostramos variaciones o el estado "Sin registros"
        if (slides.length === 0) {
            if (vars.rm > 0) {
                slides.push({ title: 'Peso Muerto', stats: vars, iconType: 'deadlift_conv' });
            } else {
                slides.push({ title: 'Peso Muerto', stats: { name: 'Sin registros', rm: 0, type: 'deadlift_conv' }, iconType: 'deadlift_conv' });
            }
        }
        return slides;
    }, [history]);

    // Metas típicas en ratio BW (1x, 1.5x, 2x, 2.5x)
    const BW_GOALS = [1, 1.5, 2, 2.5];
    const BAR_MAX_BW = 2.5;

    const PatternSlide = ({ title, stats, iconType }: any) => {
        const hasWeight = bodyWeight > 0;
        const rm = Math.round(stats.rm);
        const ratioBw = hasWeight && bodyWeight > 0 ? rm / bodyWeight : 0;
        const nextGoal = BW_GOALS.find(g => g > ratioBw) ?? BW_GOALS[BW_GOALS.length - 1];
        const barMax = hasWeight ? bodyWeight * BAR_MAX_BW : 100;
        const rmPercentage = Math.min((rm / barMax) * 100, 100);
        const goalPositions = hasWeight ? BW_GOALS.map(g => (g / BAR_MAX_BW) * 100).filter(p => p > 0 && p < 100) : [];

        return (
            <div className="w-[90vw] max-w-[420px] shrink-0 snap-center flex flex-col items-center bg-transparent">
                {/* Ilustración mucho más grande y sin fondo que distraiga */}
                <div className="w-80 h-80 flex items-center justify-center bg-black rounded-full mb-4">
                    {iconType === 'squat' ? <CaupolicanSquat /> :
                        iconType === 'bench' ? <CaupolicanBench /> :
                            iconType === 'deadlift_sumo' ? <CaupolicanSDL /> :
                                iconType === 'deadlift_conv' ? <CaupolicanCDL /> :
                                    <DumbbellIcon size={120} style={{ color: 'var(--md-sys-color-primary)' }} />}
                </div>

                {/* Datos: ratio BW como principal, kg más pequeño */}
                <div className="text-center w-full px-6">
                    <h4 className="text-title-sm font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-tight leading-none mb-1">{title}</h4>
                    <p className="text-label-sm text-[var(--md-sys-color-on-surface-variant)] truncate opacity-50">{stats.name}</p>
                    <div className="mt-4 flex items-baseline justify-center gap-2">
                        <span className="text-4xl font-black text-[var(--md-sys-color-primary)] leading-none">
                            {hasWeight ? (ratioBw >= 0.1 ? ratioBw.toFixed(1) : '—') : '—'}
                        </span>
                        <span className="text-label-sm text-[var(--md-sys-color-on-surface-variant)] font-black uppercase tracking-widest opacity-40">× peso corp.</span>
                    </div>
                    <p className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] mt-1 opacity-50">{rm} kg 1RM estimado</p>

                    {/* Barra con metas (marcas 1×, 1.5×, 2×, 2.5× BW) */}
                    <div className="mt-6 w-full">
                        <div className="flex justify-between text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] mb-2 opacity-30">
                            <span>0</span>
                            {BW_GOALS.map(g => (
                                <span key={g} className="text-[var(--md-sys-color-on-surface-variant)]">{g}×</span>
                            ))}
                        </div>
                        <div className="w-full h-3 bg-[var(--md-sys-color-surface-container-highest)] rounded-full overflow-visible relative border border-[var(--md-sys-color-outline-variant)]/20 shadow-inner">
                            {goalPositions.map((pos, i) => (
                                <div key={i} className="absolute top-0 bottom-0 w-1 bg-[var(--md-sys-color-outline-variant)]/40 z-10" style={{ left: `${pos}%` }} title={`${BW_GOALS[i]}× BW`} />
                            ))}
                            <div className="h-full bg-[var(--md-sys-color-primary)] rounded-full transition-all duration-1000 ease-out relative z-0 shadow-[0_0_15px_var(--md-sys-color-primary)]" style={{ width: `${rmPercentage}%`, opacity: 0.8 }} />
                        </div>
                        <p className="mt-4 text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] opacity-40 uppercase tracking-widest">Próxima meta: <span className="text-[var(--md-sys-color-primary)] opacity-100">{nextGoal}× BW</span>{hasWeight ? ` (${Math.round(nextGoal * bodyWeight)} kg)` : ''}</p>
                    </div>
                </div>
            </div>
        );
    };

    // COMPONENTE DE FILA (PERSONALIZADOS) - ratio BW + metas
    const CustomListRow: React.FC<{ exName: string }> = ({ exName }) => {
        const stats = findExactBestLift(exName);
        const hasWeight = bodyWeight > 0;
        const rm = Math.round(stats.rm);
        const ratioBw = hasWeight && bodyWeight > 0 ? rm / bodyWeight : 0;
        const nextGoal = BW_GOALS.find(g => g > ratioBw) ?? BW_GOALS[BW_GOALS.length - 1];
        const barMax = hasWeight ? bodyWeight * BAR_MAX_BW : 100;
        const rmPercentage = Math.min((rm / barMax) * 100, 100);
        const goalPositions = hasWeight ? BW_GOALS.map(g => (g / BAR_MAX_BW) * 100).filter(p => p > 0 && p < 100) : [];

        return (
            <div className="pb-6 mb-6 border-b border-[var(--md-sys-color-outline-variant)]/30 last:border-0 last:mb-0 last:pb-0 relative group">
                <button onClick={() => handleSaveCustomExercises(customExercises.filter(e => e !== exName))} className="absolute top-0 right-0 p-3 text-[var(--md-sys-color-on-surface-variant)] hover:text-[var(--md-sys-color-error)] opacity-0 group-hover:opacity-100 transition-all active:scale-90">
                    <XIcon size={18} />
                </button>
                <div className="flex justify-between items-end mb-3 pr-10">
                    <div>
                        <h4 className="text-label-sm font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-wider opacity-80">{exName}</h4>
                        {rm === 0 && <p className="text-label-sm text-[var(--md-sys-color-on-surface-variant)] mt-1 opacity-40">Sin registros registrados</p>}
                    </div>
                    <div className="text-right">
                        <span className="text-lg font-black text-[var(--md-sys-color-primary)]">{hasWeight && ratioBw >= 0.1 ? ratioBw.toFixed(1) : rm}</span>
                        <span className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] ml-1 opacity-50">{hasWeight && ratioBw >= 0.1 ? '× BW' : 'kg'}</span>
                    </div>
                </div>
                <div className="w-full relative">
                    <div className="flex justify-between text-[8px] font-black text-[var(--md-sys-color-on-surface-variant)] mb-1.5 opacity-30">
                        <span>0</span>
                        {BW_GOALS.map(g => <span key={g} className="text-[var(--md-sys-color-on-surface-variant)]">{g}×</span>)}
                    </div>
                    <div className="w-full h-2 bg-[var(--md-sys-color-surface-container-highest)] rounded-full overflow-hidden relative border border-[var(--md-sys-color-outline-variant)]/10 shadow-inner">
                        {goalPositions.map((pos, i) => (
                            <div key={i} className="absolute top-0 bottom-0 w-px bg-[var(--md-sys-color-outline-variant)]/30 z-10" style={{ left: `${pos}%` }} />
                        ))}
                        <div className="h-full bg-[var(--md-sys-color-primary)] relative z-0 rounded-full transition-all duration-1000 ease-out opacity-70" style={{ width: `${rmPercentage}%` }} />
                    </div>
                    {hasWeight && rm > 0 && <p className="mt-2 text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] opacity-30 uppercase tracking-widest">Meta: {nextGoal}× BW</p>}
                </div>
            </div>
        );
    };

    return (
        <div className="mt-8 mb-8 bg-black p-8 rounded-[3rem] border border-white/10 shadow-2xl">
            <div className="flex justify-between items-center mb-10">
                <h3 className="text-title-sm font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-widest flex items-center gap-3">
                    <ActivityIcon size={18} className="text-[var(--md-sys-color-primary)]" /> Fuerza Relativa
                </h3>
                {/* INTERRUPTOR PL / PERSONALIZADO */}
                <div className="flex bg-[var(--md-sys-color-surface-container-high)] rounded-full p-1 border border-[var(--md-sys-color-outline-variant)]">
                    <button onClick={() => setMode('pl')} className={`px-4 py-2 rounded-full text-label-sm font-black uppercase tracking-widest transition-all ${mode === 'pl' ? 'bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] shadow-lg' : 'text-[var(--md-sys-color-on-surface-variant)] opacity-50'}`}>Básicos PL</button>
                    <button onClick={() => setMode('custom')} className={`px-4 py-2 rounded-full text-label-sm font-black uppercase tracking-widest transition-all ${mode === 'custom' ? 'bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] shadow-lg' : 'text-[var(--md-sys-color-on-surface-variant)] opacity-50'}`}>Libre</button>
                </div>
            </div>

            {/* GESTOR DE PESO CORPORAL MEJORADO */}
            <div className="flex justify-center mb-10">
                {!isEditingBw && bodyWeight > 0 ? (
                    <div className="flex items-center gap-4 bg-[var(--md-sys-color-surface-container-highest)] border border-[var(--md-sys-color-outline-variant)]/50 px-6 py-3 rounded-full shadow-md animate-fade-in group">
                        <span className="text-label-sm font-black uppercase text-[var(--md-sys-color-on-surface-variant)] tracking-widest opacity-60">Tu Peso</span>
                        <span className="text-lg font-black text-[var(--md-sys-color-on-surface)]">{bodyWeight} kg</span>
                        <div className="w-px h-5 bg-[var(--md-sys-color-outline-variant)] mx-1 opacity-50"></div>
                        <button onClick={() => { setBwInput(bodyWeight.toString()); setIsEditingBw(true); }} className="text-label-sm font-black text-[var(--md-sys-color-primary)] uppercase tracking-widest hover:scale-110 active:scale-95 transition-all">
                            Modificar
                        </button>
                    </div>
                ) : !isEditingBw ? (
                    <button onClick={() => { setBwInput(''); setIsEditingBw(true); }} className="bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] font-black uppercase text-label-sm tracking-widest px-10 py-4 rounded-full active:scale-95 transition-all shadow-xl hover:scale-105">
                        Añadir Peso Corporal
                    </button>
                ) : (
                    <div className="flex items-center gap-3 bg-[var(--md-sys-color-surface-container-highest)] p-2 rounded-full border border-[var(--md-sys-color-primary)] animate-fade-in w-full max-w-[320px] shadow-2xl">
                        <input type="number" value={bwInput} onChange={(e) => setBwInput(e.target.value)} placeholder="Ej: 80" autoFocus className="bg-transparent text-[var(--md-sys-color-on-surface)] font-black text-lg px-6 outline-none w-full text-center" />
                        <button onClick={handleSaveWeight} className={`px-6 py-3 rounded-full font-black text-label-sm uppercase tracking-widest active:scale-95 transition-all shrink-0 ${isSaved ? 'bg-[var(--md-sys-color-tertiary)] text-[var(--md-sys-color-on-tertiary)]' : 'bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)]'}`}>
                            {isSaved ? '¡Listo!' : 'Guardar'}
                        </button>
                        {bodyWeight > 0 && !isSaved && (
                            <button onClick={() => setIsEditingBw(false)} className="px-4 py-3 rounded-full text-[var(--md-sys-color-on-surface-variant)] opacity-50 hover:opacity-100 transition-opacity">
                                <XIcon size={18} />
                            </button>
                        )}
                    </div>
                )}
            </div>

            {/* VISTA 1: CARRUSEL POWERLIFTING */}
            {mode === 'pl' && (
                <div className="-mx-8">
                    <div className="flex overflow-x-auto gap-12 snap-x snap-mandatory no-scrollbar px-8 pb-4 pt-2">
                        <PatternSlide title="Sentadilla" stats={squatStats} iconType="squat" />
                        <PatternSlide title="Press Banca" stats={benchStats} iconType="bench" />

                        {/* Se renderizan los slides de Peso Muerto dinámicamente (1 o 2 dependiendo de qué haga el usuario) */}
                        {deadliftSlides.map((dl, idx) => (
                            <PatternSlide key={`dl-${idx}`} title={dl.title} stats={dl.stats} iconType={dl.iconType} />
                        ))}

                        {/* Este bloque invisible al final fuerza el espacio del margen derecho para que no se corte */}
                        <div className="w-4 shrink-0" aria-hidden="true"></div>
                    </div>
                </div>
            )}

            {/* VISTA 2: LISTA PERSONALIZADA SIMPLE */}
            {mode === 'custom' && (
                <div className="animate-fade-in">
                    <div className="mb-8 relative">
                        <div className="flex bg-[var(--md-sys-color-surface-container-highest)] border border-[var(--md-sys-color-outline-variant)]/50 rounded-2xl overflow-hidden focus-within:border-[var(--md-sys-color-primary)] transition-all shadow-lg">
                            <input
                                type="text"
                                list="exercise-suggestions"
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                placeholder="Buscar ejercicio a seguir..."
                                className="flex-1 bg-transparent text-[var(--md-sys-color-on-surface)] text-label-sm font-black px-6 py-4 outline-none placeholder:opacity-40"
                            />
                            <button
                                onClick={() => { if (searchQuery) { handleSaveCustomExercises([...customExercises, searchQuery]); setSearchQuery(''); } }}
                                className="bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] px-8 font-black uppercase text-label-sm tracking-widest hover:brightness-110 active:scale-95 transition-all flex items-center gap-2"
                            >
                                <PlusIcon size={16} /> AÑADIR
                            </button>
                        </div>
                        <datalist id="exercise-suggestions">
                            {exerciseList.map(ex => <option key={ex.id} value={ex.name} />)}
                        </datalist>
                    </div>

                    <div className="bg-[var(--md-sys-color-surface-container-high)]/50 p-6 rounded-[2rem] border border-[var(--md-sys-color-outline-variant)]/20 shadow-inner">
                        {customExercises.length > 0 ? (
                            customExercises.map((ex, i) => <CustomListRow key={i} exName={ex} />)
                        ) : (
                            <p className="text-center text-label-sm text-[var(--md-sys-color-on-surface-variant)] font-black uppercase tracking-widest py-10 opacity-30">No hay ejercicios personalizados.</p>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};