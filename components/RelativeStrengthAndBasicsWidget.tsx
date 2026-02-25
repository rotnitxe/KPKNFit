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

    const PatternSlide = ({ title, stats, iconType }: any) => {
        const hasWeight = bodyWeight > 0;
        const rm = Math.round(stats.rm);
        
        // El máximo de la escala permite que el anillo se llene dinámicamente.
        // Si hay peso, el máximo es el mayor entre RM * 1.2 o BW * 2 (Para no llenar el círculo muy rápido)
        const scaleMax = Math.max(rm * 1.2, hasWeight ? bodyWeight * 2 : 100);
        
        const bwPercentage = hasWeight ? Math.min((bodyWeight / scaleMax) * 100, 100) : 0;
        const rmPercentage = Math.min((rm / scaleMax) * 100, 100);
        const nextStep = Math.ceil((rm + 0.1) / 2.5) * 2.5; 

        // Lógica de color de la barra circular brillante
        const isStrongerThanBw = hasWeight && rm >= bodyWeight;
        const ringColorClass = isStrongerThanBw 
            ? 'stroke-emerald-400 drop-shadow-[0_0_12px_rgba(52,211,153,0.8)]' 
            : 'stroke-cyan-400 drop-shadow-[0_0_12px_rgba(34,211,238,0.8)]';
        
        const textColorClass = isStrongerThanBw ? 'text-emerald-400' : 'text-cyan-400';

        // Matemáticas del círculo SVG (Radio = 132px para un tamaño de 288x288)
        const radius = 132;
        const circumference = 2 * Math.PI * radius;
        const rmOffset = circumference - (rmPercentage / 100) * circumference;

        return (
            <div className="w-[90vw] max-w-[420px] shrink-0 snap-center flex flex-col items-center">
                
                {/* 1. CONTENEDOR CIRCULAR CON ANILLO DE ENERGÍA SVG */}
                <div className="relative w-72 h-72 flex items-center justify-center mb-6">
                    
                    {/* SVG Anillo de Progreso */}
                    <svg className="absolute inset-0 w-full h-full transform -rotate-90 pointer-events-none z-20" viewBox="0 0 288 288">
                        {/* Fondo del anillo oscuro */}
                        <circle cx="144" cy="144" r={radius} fill="none" className="stroke-zinc-900" strokeWidth="10" />
                        
                        {/* Marca de Peso Corporal (Línea blanca difuminada en el perímetro) */}
                        {hasWeight && (
                            <circle 
                                cx="144" cy="144" r={radius} fill="none" 
                                className="stroke-white/50" strokeWidth="18"
                                strokeDasharray={`4 ${circumference - 4}`}
                                strokeDashoffset={-((bwPercentage / 100) * circumference)}
                                strokeLinecap="round"
                            />
                        )}

                        {/* Barra de Progreso Circular Brillante (RM) */}
                        <circle 
                            cx="144" cy="144" r={radius} fill="none" 
                            className={`transition-all duration-1000 ease-out ${ringColorClass}`} 
                            strokeWidth="10" 
                            strokeLinecap="round"
                            strokeDasharray={circumference}
                            strokeDashoffset={rmOffset}
                        />
                    </svg>

                    {/* El Círculo Caupolicán Interior - SVG con filter para visibilidad en fondo oscuro */}
                    <div className="w-56 h-56 bg-black rounded-full border-[3px] border-zinc-200 shadow-[0_0_30px_rgba(255,255,255,0.15)] flex items-center justify-center relative overflow-hidden z-10">
                        <div className="relative w-full h-full flex items-center justify-center z-10 scale-[1.25]">
                            {iconType === 'squat' ? <CaupolicanSquat /> : 
                             iconType === 'bench' ? <CaupolicanBench /> : 
                             iconType === 'deadlift_sumo' ? <CaupolicanSDL /> : 
                             iconType === 'deadlift_conv' ? <CaupolicanCDL /> : 
                             <DumbbellIcon size={80} className="text-white" />}
                        </div>
                    </div>

                    {/* Etiqueta flotante del Peso Corporal (si lo superaste, brilla verde) */}
                    {hasWeight && (
                        <div className="absolute bottom-2 left-1/2 -translate-x-1/2 z-30 bg-black/80 px-4 py-1.5 rounded-full border border-white/10 text-[9px] font-black uppercase tracking-widest backdrop-blur-md shadow-lg flex items-center gap-1.5">
                            <span className="text-zinc-500">BW</span>
                            <span className={isStrongerThanBw ? 'text-emerald-400 drop-shadow-[0_0_8px_rgba(52,211,153,0.8)]' : 'text-white'}>
                                {bodyWeight} kg
                            </span>
                        </div>
                    )}
                </div>

                {/* 2. DATOS DE FUERZA */}
                <div className="text-center w-full mt-2">
                    <h4 className="text-4xl font-black text-white uppercase tracking-tighter leading-none mb-1">{title}</h4>
                    <p className="text-[11px] text-zinc-500 font-bold uppercase tracking-widest truncate">{stats.name}</p>
                    
                    <div className="mt-4 flex flex-col items-center">
                        <div className="flex items-baseline gap-1">
                            <span className="text-6xl font-black text-white tracking-tighter leading-none">{rm}</span>
                            <span className="text-2xl text-zinc-600 font-bold ml-1">KG</span>
                        </div>
                        <p className={`text-[10px] font-black uppercase tracking-[0.2em] mt-2 ${textColorClass}`}>
                            1RM Estimado
                        </p>
                    </div>

                    {/* Meta Próxima (Reemplazo visual de la barra lineal inferior) */}
                    <div className="mt-6 flex items-center justify-center gap-2">
                        <div className="h-px bg-white/10 w-12" />
                        <span className="text-[9px] font-black text-zinc-500 uppercase tracking-widest">Próxima meta: <span className="text-white ml-1">{nextStep} kg</span></span>
                        <div className="h-px bg-white/10 w-12" />
                    </div>
                </div>
            </div>
        );
    };
    
    // COMPONENTE DE FILA (PERSONALIZADOS)
    const CustomListRow: React.FC<{ exName: string }> = ({ exName }) => {
        const stats = findExactBestLift(exName);
        const hasWeight = bodyWeight > 0;
        const rm = Math.round(stats.rm);
        const scaleMax = Math.max(rm * 1.5, hasWeight ? bodyWeight * 2 : 100);
        const bwPercentage = hasWeight ? Math.min((bodyWeight / scaleMax) * 100, 100) : 0;
        const rmPercentage = Math.min((rm / scaleMax) * 100, 100);
        const nextStep = Math.ceil((rm + 0.1) / 2.5) * 2.5; 

        return (
            <div className="pb-6 mb-6 border-b border-white/5 last:border-0 last:mb-0 last:pb-0 relative group">
                <button onClick={() => handleSaveCustomExercises(customExercises.filter(e => e !== exName))} className="absolute top-0 right-0 p-2 text-zinc-600 hover:text-red-400 opacity-0 group-hover:opacity-100 transition-opacity">
                    <XIcon size={14} />
                </button>
                <div className="flex justify-between items-end mb-4 pr-8">
                    <div>
                        <h4 className="text-sm font-black text-white uppercase tracking-tight">{exName}</h4>
                        {rm === 0 && <p className="text-[9px] text-zinc-500 font-bold uppercase tracking-widest mt-1">Sin registros aún</p>}
                    </div>
                    <div className="text-right">
                        <span className="text-2xl font-black text-white">{rm}</span><span className="text-[10px] text-zinc-500 ml-1">kg</span>
                    </div>
                </div>
                <div className="w-full relative">
                    <div className="flex justify-between text-[8px] font-black text-zinc-600 uppercase tracking-widest mb-1.5 relative h-3">
                        <span className="absolute left-0 bottom-0">0</span>
                        {hasWeight && <span className="text-emerald-500 absolute bottom-0" style={{ left: `calc(${bwPercentage}% - 10px)` }}>Tú: {bodyWeight}</span>}
                        <span className="text-blue-500 absolute right-0 bottom-0">Próx: {nextStep}</span>
                    </div>
                    <div className="w-full h-1.5 bg-zinc-900 rounded-full overflow-hidden relative">
                        {hasWeight && <div className="absolute top-0 bottom-0 w-[2px] bg-emerald-500 z-20 shadow-[0_0_8px_rgba(16,185,129,1)]" style={{ left: `${bwPercentage}%` }} />}
                        <div className="h-full bg-gradient-to-r from-blue-600 to-cyan-400 relative z-10 rounded-full transition-all duration-1000 ease-out" style={{ width: `${rmPercentage}%` }} />
                    </div>
                </div>
            </div>
        );
    };

    return (
        <div className="mt-8 mb-8 bg-zinc-950/50 p-6 rounded-[2rem] border border-white/5 shadow-xl">
            <div className="flex justify-between items-center mb-8">
                <h3 className="text-[11px] font-black text-white uppercase tracking-widest flex items-center gap-2">
                    <ActivityIcon size={14} className="text-emerald-400" /> Fuerza Relativa
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
                        <button onClick={() => { setBwInput(bodyWeight.toString()); setIsEditingBw(true); }} className="text-[10px] font-bold text-cyan-400 uppercase tracking-widest hover:text-cyan-300 transition-colors">
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
                        <button onClick={handleSaveWeight} className={`px-5 py-2.5 rounded-full font-black text-[10px] uppercase tracking-widest active:scale-95 transition-all shrink-0 ${isSaved ? 'bg-emerald-500 text-black shadow-[0_0_15px_rgba(16,185,129,0.5)]' : 'bg-white text-black hover:bg-zinc-200'}`}>
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
                        <div className="flex bg-black border border-white/10 rounded-2xl overflow-hidden focus-within:border-emerald-500 transition-colors">
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
                                className="bg-emerald-500 text-black px-4 font-black uppercase text-[10px] tracking-widest hover:bg-emerald-400 flex items-center gap-1"
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