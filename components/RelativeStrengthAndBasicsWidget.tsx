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
            <div className="w-[90vw] max-w-[420px] shrink-0 snap-center flex flex-col items-center bg-black">
                {/* Ilustración más grande */}
                <div className="w-64 h-64 flex items-center justify-center">
                    {iconType === 'squat' ? <CaupolicanSquat /> :
                     iconType === 'bench' ? <CaupolicanBench /> :
                     iconType === 'deadlift_sumo' ? <CaupolicanSDL /> :
                     iconType === 'deadlift_conv' ? <CaupolicanCDL /> :
                     <DumbbellIcon size={80} className="text-white" />}
                </div>

                {/* Datos: ratio BW como principal, kg más pequeño */}
                <div className="text-center w-full px-4">
                    <h4 className="text-sm font-bold text-white uppercase tracking-tight leading-none mb-0.5">{title}</h4>
                    <p className="text-[9px] text-zinc-500 truncate">{stats.name}</p>
                    <div className="mt-2 flex items-baseline justify-center gap-1">
                        <span className="text-xl font-black text-white leading-none">
                            {hasWeight ? (ratioBw >= 0.1 ? ratioBw.toFixed(1) : '—') : '—'}
                        </span>
                        <span className="text-xs text-zinc-600 font-bold">× peso corporal</span>
                    </div>
                    <p className="text-[9px] text-zinc-500 mt-0.5">{rm} kg 1RM</p>

                    {/* Barra con metas (marcas 1×, 1.5×, 2×, 2.5× BW) */}
                    <div className="mt-3 w-full">
                        <div className="flex justify-between text-[8px] text-zinc-600 mb-1">
                            <span>0</span>
                            {BW_GOALS.map(g => (
                                <span key={g} className="text-zinc-500">{g}×</span>
                            ))}
                        </div>
                        <div className="w-full h-2 bg-zinc-800 rounded-full overflow-visible relative">
                            {goalPositions.map((pos, i) => (
                                <div key={i} className="absolute top-0 bottom-0 w-px bg-white/30 z-10" style={{ left: `${pos}%` }} title={`${BW_GOALS[i]}× BW`} />
                            ))}
                            <div className="h-full bg-white/80 rounded-full transition-all duration-1000 ease-out relative z-0" style={{ width: `${rmPercentage}%` }} />
                        </div>
                        <p className="mt-2 text-[8px] text-zinc-500">Meta: <span className="text-white">{nextGoal}× BW</span>{hasWeight ? ` (${Math.round(nextGoal * bodyWeight)} kg)` : ''}</p>
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
            <div className="pb-4 mb-4 border-b border-white/5 last:border-0 last:mb-0 last:pb-0 relative group">
                <button onClick={() => handleSaveCustomExercises(customExercises.filter(e => e !== exName))} className="absolute top-0 right-0 p-2 text-zinc-600 hover:text-red-400 opacity-0 group-hover:opacity-100 transition-opacity">
                    <XIcon size={14} />
                </button>
                <div className="flex justify-between items-end mb-2 pr-8">
                    <div>
                        <h4 className="text-xs font-bold text-white uppercase tracking-tight">{exName}</h4>
                        {rm === 0 && <p className="text-[8px] text-zinc-500 mt-0.5">Sin registros aún</p>}
                    </div>
                    <div className="text-right">
                        <span className="text-sm font-black text-white">{hasWeight && ratioBw >= 0.1 ? ratioBw.toFixed(1) : rm}</span>
                        <span className="text-[9px] text-zinc-500 ml-1">{hasWeight && ratioBw >= 0.1 ? '× BW' : 'kg'}</span>
                    </div>
                </div>
                <div className="w-full relative">
                    <div className="flex justify-between text-[8px] text-zinc-600 mb-1">
                        <span>0</span>
                        {BW_GOALS.map(g => <span key={g} className="text-zinc-500">{g}×</span>)}
                    </div>
                    <div className="w-full h-1.5 bg-zinc-900 rounded-full overflow-visible relative">
                        {goalPositions.map((pos, i) => (
                            <div key={i} className="absolute top-0 bottom-0 w-px bg-white/30 z-10" style={{ left: `${pos}%` }} />
                        ))}
                        <div className="h-full bg-white/80 relative z-0 rounded-full transition-all duration-1000 ease-out" style={{ width: `${rmPercentage}%` }} />
                    </div>
                    {hasWeight && <p className="mt-1 text-[8px] text-zinc-500">Meta: {nextGoal}× BW</p>}
                </div>
            </div>
        );
    };

    return (
        <div className="mt-8 mb-8 bg-black p-6">
            <div className="flex justify-between items-center mb-8">
                <h3 className="text-[11px] font-black text-white uppercase tracking-widest flex items-center gap-2">
                    <ActivityIcon size={14} className="text-zinc-400" /> Fuerza Relativa
                </h3>
                {/* INTERRUPTOR PL / PERSONALIZADO */}
                <div className="flex bg-black rounded-full p-1 border border-white/10">
                    <button onClick={() => setMode('pl')} className={`px-3 py-1.5 rounded-full text-[9px] font-black uppercase tracking-widest transition-colors ${mode === 'pl' ? 'bg-white text-black' : 'text-zinc-500'}`}>Básicos PL</button>
                    <button onClick={() => setMode('custom')} className={`px-3 py-1.5 rounded-full text-[9px] font-black uppercase tracking-widest transition-colors ${mode === 'custom' ? 'bg-white text-black' : 'text-zinc-500'}`}>Libre</button>
                </div>
            </div>

            {/* GESTOR DE PESO CORPORAL MEJORADO */}
            <div className="flex justify-center mb-8">
                {!isEditingBw && bodyWeight > 0 ? (
                    <div className="flex items-center gap-3 bg-zinc-900/50 border border-white/10 px-5 py-2.5 rounded-full shadow-sm animate-fade-in">
                        <span className="text-[10px] font-black uppercase text-zinc-400 tracking-widest">Tu Peso:</span>
                        <span className="text-sm font-black text-white">{bodyWeight} kg</span>
                        <div className="w-px h-4 bg-white/20 mx-1"></div>
                        <button onClick={() => { setBwInput(bodyWeight.toString()); setIsEditingBw(true); }} className="text-[10px] font-bold text-white/80 uppercase tracking-widest hover:text-white transition-colors">
                            Modificar
                        </button>
                    </div>
                ) : !isEditingBw ? (
                    <button onClick={() => { setBwInput(''); setIsEditingBw(true); }} className="bg-white text-black font-black uppercase text-[10px] tracking-widest px-8 py-3 rounded-full active:scale-95 transition-all shadow-[0_0_20px_rgba(255,255,255,0.15)] hover:scale-105">
                        Añadir Peso Corporal
                    </button>
                ) : (
                    <div className="flex items-center gap-2 bg-zinc-900 p-1.5 rounded-full border border-white/20 animate-fade-in w-full max-w-[280px] shadow-lg">
                        <input type="number" value={bwInput} onChange={(e) => setBwInput(e.target.value)} placeholder="Ej: 80" autoFocus className="bg-transparent text-white font-black text-sm px-4 outline-none w-full text-center" />
                        <button onClick={handleSaveWeight} className={`px-5 py-2.5 rounded-full font-black text-[10px] uppercase tracking-widest active:scale-95 transition-all shrink-0 ${isSaved ? 'bg-white/90 text-black' : 'bg-white text-black hover:bg-zinc-200'}`}>
                            {isSaved ? '¡Guardado!' : 'Guardar'}
                        </button>
                        {bodyWeight > 0 && !isSaved && (
                            <button onClick={() => setIsEditingBw(false)} className="px-3 py-2.5 rounded-full text-zinc-500 hover:text-white transition-colors">
                                <XIcon size={14} />
                            </button>
                        )}
                    </div>
                )}
            </div>

            {/* VISTA 1: CARRUSEL POWERLIFTING */}
            {mode === 'pl' && (
                <div className="-mx-6">
                    <div className="flex overflow-x-auto gap-8 snap-x snap-mandatory no-scrollbar px-6 pb-4 pt-2">
                        <PatternSlide title="Sentadilla" stats={squatStats} iconType="squat" />
                        <PatternSlide title="Press Banca" stats={benchStats} iconType="bench" />
                        
                        {/* Se renderizan los slides de Peso Muerto dinámicamente (1 o 2 dependiendo de qué haga el usuario) */}
                        {deadliftSlides.map((dl, idx) => (
                            <PatternSlide key={`dl-${idx}`} title={dl.title} stats={dl.stats} iconType={dl.iconType} />
                        ))}
                        
                        {/* Este bloque invisible al final fuerza el espacio del margen derecho para que no se corte */}
                        <div className="w-2 shrink-0" aria-hidden="true"></div>
                    </div>
                </div>
            )}

            {/* VISTA 2: LISTA PERSONALIZADA SIMPLE */}
            {mode === 'custom' && (
                <div className="animate-fade-in">
                    <div className="mb-6 relative">
                        <div className="flex bg-zinc-900 border border-white/10 rounded-2xl overflow-hidden focus-within:border-white/20 transition-colors">
                            <input 
                                type="text" 
                                list="exercise-suggestions" 
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                placeholder="Buscar ejercicio a seguir..." 
                                className="flex-1 bg-transparent text-white text-xs font-bold px-4 py-3 outline-none"
                            />
                            <button 
                                onClick={() => { if(searchQuery) { handleSaveCustomExercises([...customExercises, searchQuery]); setSearchQuery(''); } }}
                                className="bg-white text-black px-4 font-black uppercase text-[10px] tracking-widest hover:bg-white/90 flex items-center gap-1"
                            >
                                <PlusIcon size={14} /> Add
                            </button>
                        </div>
                        <datalist id="exercise-suggestions">
                            {exerciseList.map(ex => <option key={ex.id} value={ex.name} />)}
                        </datalist>
                    </div>

                    <div className="bg-black/30 p-4 rounded-3xl border border-white/5">
                        {customExercises.length > 0 ? (
                            customExercises.map((ex, i) => <CustomListRow key={i} exName={ex} />)
                        ) : (
                            <p className="text-center text-[10px] text-zinc-500 font-bold uppercase tracking-widest py-6">No has agregado ejercicios personalizados.</p>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};