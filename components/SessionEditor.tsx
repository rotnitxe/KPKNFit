
// components/SessionEditor.tsx
import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { Session, Exercise, ExerciseSet, Settings, ExerciseMuscleInfo, WarmupSetDefinition, CoverStyle, SessionBackground } from '../types';
import { PlusIcon, TrashIcon, SparklesIcon, StarIcon, ArrowDownIcon, ArrowUpIcon, InfoIcon, ChevronRightIcon, XIcon, ImageIcon, BarChartIcon, LinkIcon, ZapIcon, DragHandleIcon, CheckIcon, ClockIcon, TargetIcon, FlameIcon, ActivityIcon, PaletteIcon, LayersIcon, RefreshCwIcon, SearchIcon, DumbbellIcon, SettingsIcon, AlertTriangleIcon } from './icons';
import Button from './ui/Button';
import { getEffectiveRepsForRM, estimatePercent1RM, calculateBrzycki1RM, roundWeight, getOrderedDaysOfWeek } from '../utils/calculations';
import Modal from './ui/Modal';
import BackgroundEditorModal from './SessionBackgroundModal';
import { useAppContext } from '../contexts/AppContext';
import { storageService } from '../services/storageService';
import { useImageGradient } from '../utils/colorUtils';
import { calculatePredictedSessionDrain, calculateSetStress, calculateSpinalScore, getDynamicAugeMetrics, calculatePersonalizedBatteryTanks, calculateSetBatteryDrain, getEffectiveRPE, getEffectiveVolumeMultiplier, HYPERTROPHY_ROLE_MULTIPLIERS, DISPLAY_ROLE_WEIGHTS } from '../services/auge';
import { InfoTooltip } from './ui/InfoTooltip';
import { calculateSessionVolume, calculateAverageVolumeForWeeks } from '../services/analysisService';
import { getCachedAdaptiveData, getConfidenceLabel, getConfidenceColor } from '../services/augeAdaptiveService';
import { GPFatigueCurve, BayesianConfidence, BanisterTrend } from './ui/AugeDeepView';
import { calculateUnifiedMuscleVolume, normalizeMuscleGroup } from '../services/volumeCalculator';
import ToggleSwitch from './ui/ToggleSwitch';

export interface SessionEditorProps {
  // Ahora onSave puede recibir un array de sesiones modificadas para el guardado en lote
  onSave: ((sessions: Session | Session[], programId?: string, macroIndex?: number, mesoIndex?: number, weekId?: string) => void);
  onCancel: () => void;
  existingSessionInfo: { session: Session, programId: string, macroIndex: number; mesoIndex: number; weekId: string; sessionId?: string } | null;
  isOnline: boolean;
  settings: Settings;
  saveTrigger: number;
  addExerciseTrigger: number;
  exerciseList: ExerciseMuscleInfo[];
}

const SESSION_DRAFT_KEY = 'session-editor-draft';
const PRESET_PART_COLORS = ['#3b82f6', '#ef4444', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#6366f1', '#1e293b', '#22d3ee', '#fb7185'];

const UnlinkIcon: React.FC<{ size?: number; className?: string }> = ({ size = 20, className = '' }) => (
    <svg className={className} xmlns="http://www.w3.org/2000/svg" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="m18.84 12.25 1.72-1.71h0a5.003 5.003 0 0 0-7.07-7.07l-1.72 1.71"/><path d="m5.17 11.75-1.71 1.71a5.003 5.003 0 0 0 7.07 7.07l1.71-1.71"/><line x1="8" y1="2" x2="22" y2="16" />
    </svg>
);
const WandIcon: React.FC<{ size?: number; className?: string }> = ({ size = 20, className = '' }) => (
    <svg className={className} xmlns="http://www.w3.org/2000/svg" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m15 5 4 4" /><path d="M13 7 8.7 2.7a2.41 2.41 0 0 0-3.4 0L2.7 5.3a2.41 2.41 0 0 0 0 3.4L7 13" /><path d="m8 6 2-2" /><path d="m2 22 5.5-5.5" /><path d="m11 14 5.5-5.5" /><path d="m22 2-4 4" /><path d="m19 8 3-3" /></svg>
);
const TrophyIcon: React.FC<{ size?: number; className?: string }> = ({ size = 20, className = '' }) => (
    <svg className={className} xmlns="http://www.w3.org/2000/svg" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6" /><path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18" /><path d="M4 22h16" /><path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22" /><path d="M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22" /><path d="M18 2H6v7c0 3.31 2.69 6 6 6s6-2.69 6-6V2Z" /></svg>
);

// FÓRMULA DOTS (Aproximación simplificada para cálculo en vivo rápido)
const calculateDOTS = (total: number, bw: number, isMale: boolean = true) => {
    if(!bw || bw <= 0 || !total) return 0;
    // Simplificación de coeficientes para rendimiento en app móvil
    const coeff = isMale 
        ? (-0.000001093 * Math.pow(bw, 4) + 0.0007391293 * Math.pow(bw, 3) - 0.1918759221 * Math.pow(bw, 2) + 24.0900756 * bw - 307.75076)
        : (-0.0000010706 * Math.pow(bw, 4) + 0.0005158568 * Math.pow(bw, 3) - 0.1126655495 * Math.pow(bw, 2) + 13.6175032 * bw - 57.96288);
    return coeff > 0 ? Math.round((total * (500 / coeff)) * 100) / 100 : 0;
};

const AmrapSelectionModal: React.FC<{
    isOpen: boolean;
    onClose: () => void;
    onConfirm: (isCalibrator: boolean) => void;
}> = ({ isOpen, onClose, onConfirm }) => {
    return (
        <Modal isOpen={isOpen} onClose={onClose} title="Configuración AMRAP">
            <div className="space-y-6 p-2">
                <div className="bg-yellow-900/20 p-4 rounded-xl border border-yellow-500/30 text-center">
                    <FlameIcon size={48} className="mx-auto text-yellow-400 mb-2 animate-pulse"/>
                    <h3 className="text-xl font-bold text-white">Modo "As Many Reps As Possible"</h3>
                    <p className="text-sm text-slate-400 mt-2">Vas a ir al fallo real. ¿Cómo quieres que afecte esto al resto de tu sesión?</p>
                </div>
                <div className="grid gap-4">
                     <button onClick={() => onConfirm(true)} className="relative group p-4 rounded-xl border-2 border-sky-500/50 bg-sky-900/10 hover:bg-sky-900/30 transition-all text-left">
                        <div className="absolute top-3 right-3"><SparklesIcon className="text-sky-400" size={20}/></div>
                        <h4 className="text-lg font-bold text-white mb-1">AMRAP Calibrador (Recomendado)</h4>
                        <p className="text-xs text-slate-300">La IA analizará tu rendimiento en esta serie. Si superas tus marcas, <strong>ajustará automáticamente el peso</strong> de los siguientes ejercicios de este músculo.</p>
                    </button>
                    <button onClick={() => onConfirm(false)} className="group p-4 rounded-xl border border-slate-700 bg-slate-800/50 hover:bg-slate-700/50 transition-all text-left">
                        <h4 className="text-lg font-bold text-white mb-1">AMRAP Aislado</h4>
                        <p className="text-xs text-slate-400">Solo una serie al fallo para romper un récord o terminar el ejercicio. No afectará a las cargas del resto de la sesión.</p>
                    </button>
                </div>
                <div className="flex justify-center"><Button onClick={onClose} variant="secondary" className="!text-xs">Cancelar</Button></div>
            </div>
        </Modal>
    );
};

const SessionAugeDashboard: React.FC<{ 
    currentSession: Session; 
    weekSessions: Session[];
    exerciseList: ExerciseMuscleInfo[];
}> = ({ currentSession, weekSessions, exerciseList = [] }) => {
    const { settings } = useAppContext();
    const [viewMode, setViewMode] = useState<'volume' | 'drain' | 'ranking' | 'prediction'>('volume');
    const [context, setContext] = useState<'session' | 'week'>('session');
    const adaptiveCache = useMemo(() => getCachedAdaptiveData(), []);

    const scrollToExercise = (exId: string) => {
        const el = document.getElementById(`exercise-card-${exId}`);
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    };

    const [showFatigueInfo, setShowFatigueInfo] = useState(false);

    const { hyperStats, globalDrain, sessionAlerts, weeklyAlerts, exerciseRanking } = useMemo(() => {
        const hyperMap: Record<string, { flat: number, effective: number, fail: number }> = {};
        const weeklyHyperMap: Record<string, { flat: number, effective: number }> = {};
        
        let totalCns = 0; let totalSpinal = 0; let totalMuscular = 0;
        let weeklyCns = 0; let weeklySpinal = 0; let weeklyMuscular = 0;
        
        const ranking: { id: string, name: string, fatigue: number, isCurrentSession: boolean }[] = [];
        const tanks = calculatePersonalizedBatteryTanks(settings);

        const processExercises = (exercises: any[], isCurrentSession: boolean) => {
            const muscleSetCount: Record<string, number> = {};

            exercises.forEach(ex => {
                const info = exerciseList.find((e: any) => e.id === ex.exerciseDbId || e.name === ex.name);
                if (!info) return;
                const primaryMuscle = normalizeMuscleGroup(info.involvedMuscles.find((m: any) => m.role === 'primary')?.muscle || 'General');
                
                const validSets = ex.sets?.filter((s: any) => (s as any).type !== 'warmup') || [];
                let exFatigueScore = 0;

                validSets.forEach((set: any) => {
                    let accumulatedSets = muscleSetCount[primaryMuscle] || 0;
                    
                    // --- THE SINGLE SOURCE OF TRUTH (Batería Local Acumulada) ---
                    const drain = calculateSetBatteryDrain(set, info, tanks, accumulatedSets, ex.restTime || 90);
                    
                    muscleSetCount[primaryMuscle] = accumulatedSets + 1;
                    exFatigueScore += drain.cnsDrainPct + drain.muscularDrainPct;

                    if (isCurrentSession) {
                        totalCns += drain.cnsDrainPct;
                        totalSpinal += drain.spinalDrainPct;
                        totalMuscular += drain.muscularDrainPct;
                    }
                    weeklyCns += drain.cnsDrainPct;
                    weeklySpinal += drain.spinalDrainPct;
                    weeklyMuscular += drain.muscularDrainPct;

                    const volMult = getEffectiveVolumeMultiplier(set);

                    info.involvedMuscles.forEach((m: any) => {
                        const parent = normalizeMuscleGroup(m.muscle);
                        const hyperFactor = HYPERTROPHY_ROLE_MULTIPLIERS[m.role] ?? 0;
                        
                        const effVol = hyperFactor * volMult;
                        const flatVol = hyperFactor;

                        if (!weeklyHyperMap[parent]) weeklyHyperMap[parent] = { flat: 0, effective: 0 };
                        weeklyHyperMap[parent].flat += flatVol;
                        weeklyHyperMap[parent].effective += effVol;

                        if (isCurrentSession) {
                            if (!hyperMap[parent]) hyperMap[parent] = { flat: 0, effective: 0, fail: 0 };
                            hyperMap[parent].flat += flatVol;
                            hyperMap[parent].effective += effVol;
                            if (getEffectiveRPE(set) >= 9.5) hyperMap[parent].fail += flatVol;
                        }
                    });
                });

                if (exFatigueScore > 0) {
                    ranking.push({ id: ex.id, name: ex.name, fatigue: exFatigueScore, isCurrentSession });
                }
            });
        };

        const currentExercises = [...(currentSession.exercises || []), ...(currentSession.parts?.flatMap(p => p.exercises) || [])];
        processExercises(currentExercises, true);

        weekSessions.forEach(s => {
            if (s.id !== currentSession.id) {
                const sEx = [...(s.exercises || []), ...(s.parts?.flatMap(p => p.exercises) || [])];
                processExercises(sEx, false);
            }
        });

        const sortMap = (map: Record<string, { flat: number, effective: number, fail?: number }>) => Object.entries(map)
            .map(([muscle, data]) => ({ muscle, volume: Math.round(data.effective * 10) / 10, flat: data.flat, failRatio: data.flat > 0 ? (data.fail || 0) / data.flat : 0 }))
            .filter(item => item.volume > 0).sort((a, b) => b.volume - a.volume);
        
        const sortedHyper = sortMap(hyperMap);
        const sortedWeekly = sortMap(weeklyHyperMap);
        ranking.sort((a, b) => b.fatigue - a.fatigue);

        const limits = settings?.volumeLimits || {};

        const dynamicSessionAlerts = sortedHyper.map(m => {
            const limit = limits[m.muscle]?.maxSession || 6;
            let message = "";
            let isAlert = false;
            if (m.volume > limit) {
                isAlert = true;
                if (m.failRatio >= 0.7) message = `Llevaste muchas series al fallo. Tu sistema nervioso local está frito. Añadir más es Volumen Basura.`;
                else if (m.failRatio <= 0.3) message = `Aunque trabajas con RIR alto (Bombeo), superaste el límite efectivo (${limit} pts). El estímulo decaerá.`;
                else message = `Superaste el umbral óptimo por sesión (${limit} pts). Estás generando daño sin hipertrofia.`;
            }
            return { ...m, threshold: limit, message, isAlert };
        }).filter(m => m.isAlert);

        const dynamicWeeklyAlerts = sortedWeekly.map(m => {
            const mrv = limits[m.muscle]?.max || 18;
            let message = "";
            let isAlert = false;
            if (m.flat > mrv) {
                isAlert = true;
                message = `Programas ${Math.round(m.flat)} series. Tu máximo recuperable (MRV) es ${mrv}. Entrarás en sobreentrenamiento.`;
            }
            return { ...m, mrv, message, isAlert };
        }).filter(m => m.isAlert);

        return { 
            hyperStats: context === 'session' ? sortedHyper : sortedWeekly, 
            globalDrain: { 
                cns: Math.min(100, context === 'session' ? totalCns : weeklyCns), 
                spinal: Math.min(100, context === 'session' ? totalSpinal : weeklySpinal),
                muscular: Math.min(100, context === 'session' ? totalMuscular : weeklyMuscular)
            },
            sessionAlerts: dynamicSessionAlerts,
            weeklyAlerts: dynamicWeeklyAlerts,
            exerciseRanking: context === 'session' ? ranking.filter(r => r.isCurrentSession) : ranking
        };
    }, [currentSession, weekSessions, exerciseList, context, settings]);

    // Exportar alertas al padre de forma segura
    useEffect(() => {
        // @ts-ignore
        const ev = new CustomEvent('augeAlertsUpdated', { detail: sessionAlerts });
        window.dispatchEvent(ev);
    }, [sessionAlerts]);

    return (
        <div className="p-4 border border-[#222] rounded-xl bg-[#0a0a0a] mb-6 shadow-2xl">
            <div className="flex justify-end mb-3">
                <ToggleSwitch checked={context === 'week'} onChange={(c) => setContext(c ? 'week' : 'session')} label={context === 'week' ? 'Contexto: Semana' : 'Contexto: Sesión'} size="sm" isBlackAndWhite={true} />
            </div>
            <div className="flex justify-between items-center mb-4 pb-3 border-b border-[#222]">
                 <div className="flex gap-4 overflow-x-auto hide-scrollbar w-full">
                    <button onClick={() => setViewMode('volume')} className={`text-[10px] font-black uppercase tracking-widest transition-colors flex items-center gap-1 shrink-0 ${viewMode === 'volume' ? 'text-white underline decoration-2 underline-offset-4' : 'text-zinc-600 hover:text-zinc-400'}`}><TargetIcon size={12}/> Estímulo</button>
                    <button onClick={() => setViewMode('drain')} className={`text-[10px] font-black uppercase tracking-widest transition-colors flex items-center gap-1 shrink-0 ${viewMode === 'drain' ? 'text-white underline decoration-2 underline-offset-4' : 'text-zinc-600 hover:text-zinc-400'}`}><ActivityIcon size={12}/> Fatiga</button>
                    <button onClick={() => setViewMode('ranking')} className={`text-[10px] font-black uppercase tracking-widest transition-colors flex items-center gap-1 shrink-0 ${viewMode === 'ranking' ? 'text-white underline decoration-2 underline-offset-4' : 'text-zinc-600 hover:text-zinc-400'}`}><LayersIcon size={12}/> Ranking</button>
                    <button onClick={() => setViewMode('prediction')} className={`text-[10px] font-black uppercase tracking-widest transition-colors flex items-center gap-1 shrink-0 ${viewMode === 'prediction' ? 'text-violet-400 underline decoration-2 underline-offset-4' : 'text-zinc-600 hover:text-zinc-400'}`}><ZapIcon size={12}/> Predicción</button>
                 </div>
            </div>

            {viewMode === 'volume' && context === 'week' && weeklyAlerts.length > 0 && (
                <div className="mb-4 space-y-2 animate-fade-in">
                    {weeklyAlerts.map(alert => (
                        <div key={alert.muscle} className="bg-red-950/30 border border-red-900/50 p-3 rounded-lg flex gap-2 items-start">
                            <FlameIcon size={16} className="text-red-500 shrink-0 mt-0.5 animate-pulse" />
                            <p className="text-[10px] text-red-200 leading-relaxed">
                                <strong className="font-bold text-red-400 uppercase tracking-wide">Sobrecarga: {alert.muscle}.</strong><br/>
                                {alert.message}
                            </p>
                        </div>
                    ))}
                </div>
            )}
            
            {viewMode === 'volume' && context === 'session' && sessionAlerts.length > 0 && (
                <div className="mb-4 space-y-2 animate-fade-in">
                    {sessionAlerts.map(alert => (
                        <div key={alert.muscle} className="bg-orange-950/30 border border-orange-900/50 p-2 rounded-lg flex gap-2 items-start">
                            <InfoIcon size={14} className="text-orange-500 shrink-0 mt-0.5" />
                            <p className="text-[9px] text-orange-200 leading-tight">
                                <strong className="font-bold text-orange-400">Peligro en {alert.muscle}:</strong><br/>
                                {alert.message}
                            </p>
                        </div>
                    ))}
                </div>
            )}

            {viewMode === 'volume' ? (
                <div className="animate-fade-in space-y-2">
                     <div className="max-h-48 overflow-y-auto custom-scrollbar pr-2 space-y-2">
                        {hyperStats.length > 0 ? hyperStats.map(stat => {
                            const isDanger = context === 'week' ? stat.volume > 18 : stat.volume > 6;
                            const maxScale = context === 'week' ? 25 : 10;
                            return (
                            <div key={stat.muscle} className="flex justify-between items-center group">
                                <span className={`text-[9px] font-black uppercase w-24 truncate ${isDanger ? 'text-red-400' : 'text-zinc-300'}`}>{stat.muscle}</span>
                                <div className="flex-1 mx-2 h-1.5 bg-[#111] rounded-full overflow-hidden">
                                    <div className={`h-full transition-all ${isDanger ? 'bg-red-500' : 'bg-white'}`} style={{ width: `${Math.min(100, (stat.volume / maxScale) * 100)}%` }}></div>
                                </div>
                                <span className="text-[10px] font-mono font-bold text-white text-right whitespace-nowrap">{stat.volume} series</span>
                            </div>
                        )}) : <p className="text-[10px] text-zinc-600 font-bold uppercase text-center py-4">Sin datos de volumen</p>}
                     </div>
                </div>
            ) : viewMode === 'drain' ? (
                <div className="space-y-4 animate-fade-in relative">
                     <button onClick={() => setShowFatigueInfo(!showFatigueInfo)} className="absolute -top-10 right-0 text-zinc-500 hover:text-white"><InfoIcon size={16}/></button>
                     {showFatigueInfo && (
                         <div className="bg-zinc-900 p-3 rounded-lg border border-white/10 text-[9px] text-zinc-300 mb-4 animate-fade-in leading-relaxed">
                             <strong className="text-white">Escala de Fatiga (1 al 10):</strong><br/>
                             <span className="text-green-400">1-3:</span> Baja fatiga sistémica. Fácil recuperación.<br/>
                             <span className="text-yellow-400">4-7:</span> Fatiga moderada/alta. Estímulo óptimo.<br/>
                             <span className="text-red-400">8-10:</span> Drenaje extremo. Requiere descanso prolongado.
                         </div>
                     )}
                     <div className="grid grid-cols-3 gap-3">
                        <div className="bg-[#111] p-3 rounded-lg border border-[#222]">
                            <div className="flex justify-between items-center mb-2">
                                <span className="text-[9px] font-black uppercase text-zinc-500">MSC</span>
                                <span className={`text-xs font-mono font-bold ${globalDrain.muscular >= 80 ? 'text-red-500' : globalDrain.muscular >= 40 ? 'text-yellow-500' : 'text-green-500'}`}>{globalDrain.muscular.toFixed(0)}%</span>
                            </div>
                            <div className="w-full h-1.5 bg-[#000] rounded-full overflow-hidden">
                                <div className={`h-full transition-all ${globalDrain.muscular >= 80 ? 'bg-red-500' : globalDrain.muscular >= 40 ? 'bg-yellow-500' : 'bg-green-500'}`} style={{ width: `${globalDrain.muscular}%` }}></div>
                            </div>
                        </div>
                        <div className="bg-[#111] p-3 rounded-lg border border-[#222]">
                            <div className="flex justify-between items-center mb-2">
                                <span className="text-[9px] font-black uppercase text-zinc-500">SNC</span>
                                <span className={`text-xs font-mono font-bold ${globalDrain.cns >= 80 ? 'text-red-500' : globalDrain.cns >= 40 ? 'text-yellow-500' : 'text-green-500'}`}>{globalDrain.cns.toFixed(0)}%</span>
                            </div>
                            <div className="w-full h-1.5 bg-[#000] rounded-full overflow-hidden">
                                <div className={`h-full transition-all ${globalDrain.cns >= 80 ? 'bg-red-500' : globalDrain.cns >= 40 ? 'bg-yellow-500' : 'bg-green-500'}`} style={{ width: `${globalDrain.cns}%` }}></div>
                            </div>
                        </div>
                        <div className="bg-[#111] p-3 rounded-lg border border-[#222]">
                            <div className="flex justify-between items-center mb-2">
                                <span className="text-[9px] font-black uppercase text-zinc-500">ESP</span>
                                <span className={`text-xs font-mono font-bold ${globalDrain.spinal >= 80 ? 'text-red-500' : globalDrain.spinal >= 40 ? 'text-yellow-500' : 'text-green-500'}`}>{globalDrain.spinal.toFixed(0)}%</span>
                            </div>
                            <div className="w-full h-1.5 bg-[#000] rounded-full overflow-hidden">
                                <div className={`h-full transition-all ${globalDrain.spinal >= 80 ? 'bg-red-500' : globalDrain.spinal >= 40 ? 'bg-yellow-500' : 'bg-green-500'}`} style={{ width: `${globalDrain.spinal}%` }}></div>
                            </div>
                        </div>
                    </div>
                </div>
            ) : viewMode === 'ranking' ? (
                <div className="animate-fade-in space-y-2">
                     <p className="text-[9px] text-zinc-500 uppercase font-bold mb-2">Ranking de impacto sistémico</p>
                     <div className="max-h-48 overflow-y-auto custom-scrollbar pr-2 space-y-2">
                        {exerciseRanking.length > 0 ? exerciseRanking.map((rank, idx) => {
                            const maxFatigue = exerciseRanking[0]?.fatigue > 0 ? exerciseRanking[0].fatigue : 1;
                            const percentage = Math.min(100, Math.max(0, (rank.fatigue / maxFatigue) * 100));

                            return (
                                <button key={`${rank.id}-${idx}`} onClick={() => scrollToExercise(rank.id)} className="w-full flex justify-between items-center group bg-[#111] hover:bg-[#222] p-2 rounded-lg transition-colors border border-transparent hover:border-white/10 text-left">
                                    <span className="text-[10px] font-bold text-white truncate pr-2 flex-1"><span className="text-zinc-600 mr-2">{idx + 1}.</span>{rank.name}</span>
                                    <div className="flex items-center gap-2">
                                        <div className="w-16 h-1 bg-black rounded-full overflow-hidden">
                                            <div className="h-full bg-red-500 transition-all" style={{ width: `${percentage}%` }}></div>
                                        </div>
                                        <span className="text-[9px] font-mono text-zinc-400 w-8 text-right">{rank.fatigue.toFixed(0)}</span>
                                    </div>
                                </button>
                            );
                        }) : <p className="text-[10px] text-zinc-600 font-bold uppercase text-center py-4">No hay ejercicios evaluables</p>}
                     </div>
                </div>
            ) : (
                <div className="animate-fade-in space-y-4">
                    <div className="bg-[#111] p-3 rounded-xl border border-violet-500/10">
                        <h4 className="text-[9px] font-black uppercase tracking-widest text-violet-400 mb-3 flex items-center gap-2"><ZapIcon size={10}/> Curva GP — Fatiga Esperada</h4>
                        <GPFatigueCurve data={adaptiveCache.gpCurve} />
                    </div>

                    <div className="bg-[#111] p-3 rounded-xl border border-sky-500/10">
                        <h4 className="text-[9px] font-black uppercase tracking-widest text-sky-400 mb-3">Recuperación Post-Sesión (Bayesiano)</h4>
                        <BayesianConfidence
                            totalObservations={adaptiveCache.totalObservations}
                            personalizedRecoveryHours={adaptiveCache.personalizedRecoveryHours}
                        />
                    </div>

                    <div className="bg-[#111] p-3 rounded-xl border border-emerald-500/10">
                        <h4 className="text-[9px] font-black uppercase tracking-widest text-emerald-400 mb-3">Impacto Banister</h4>
                        {adaptiveCache.banister ? (
                            <>
                                <BanisterTrend systemData={adaptiveCache.banister.systems?.muscular || null} compact />
                                <div className="mt-2 px-2 py-1.5 bg-black/40 rounded-lg">
                                    <p className="text-[9px] text-zinc-300 font-medium italic">{adaptiveCache.banister.verdict || 'Sin veredicto disponible.'}</p>
                                </div>
                            </>
                        ) : (
                            <p className="text-[9px] text-zinc-600 font-bold text-center py-4">Completa más sesiones para activar Banister</p>
                        )}
                    </div>

                    {/* Confidence dots on drain bars */}
                    <div className="grid grid-cols-3 gap-2">
                        {(['muscular', 'cns', 'spinal'] as const).map(sys => {
                            const drainVal = globalDrain[sys === 'cns' ? 'cns' : sys];
                            const confColor = getConfidenceColor(adaptiveCache.totalObservations);
                            return (
                                <div key={sys} className="bg-[#111] px-2 py-1.5 rounded-lg flex items-center justify-between">
                                    <span className="text-[8px] font-black uppercase text-zinc-500">{sys}</span>
                                    <div className="flex items-center gap-1.5">
                                        <span className="text-[9px] font-mono font-bold text-white">{drainVal.toFixed(0)}%</span>
                                        <span className={`w-1.5 h-1.5 rounded-full ${confColor.replace('text-', 'bg-')}`} title={getConfidenceLabel(adaptiveCache.totalObservations)} />
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}
        </div>
    );
};

const WarmupConfigModal: React.FC<{
    isOpen: boolean;
    onClose: () => void;
    exerciseName: string;
    warmupSets: WarmupSetDefinition[];
    onSave: (sets: WarmupSetDefinition[]) => void;
}> = ({ isOpen, onClose, exerciseName, warmupSets, onSave }) => {
    const [sets, setSets] = useState<WarmupSetDefinition[]>([]);
    useEffect(() => {
        if(isOpen) setSets(warmupSets.length > 0 ? [...warmupSets] : [{ id: crypto.randomUUID(), percentageOfWorkingWeight: 50, targetReps: 10 }]);
    }, [isOpen, warmupSets]);
    const addSet = () => {
        const last = sets[sets.length - 1];
        const newSet: WarmupSetDefinition = { id: crypto.randomUUID(), percentageOfWorkingWeight: last ? Math.min(90, last.percentageOfWorkingWeight + 10) : 50, targetReps: last ? Math.max(1, Math.floor(last.targetReps / 2)) : 10 };
        setSets([...sets, newSet]);
    };
    const removeSet = (index: number) => { setSets(sets.filter((_, i) => i !== index)); };
    const updateSet = (index: number, field: keyof WarmupSetDefinition, value: number) => {
        const updated = [...sets];
        (updated[index] as any)[field] = value;
        setSets(updated);
    };
    const handleSave = () => { onSave(sets); onClose(); };
    return (
        <Modal isOpen={isOpen} onClose={onClose} title={`Aproximación: ${exerciseName}`}>
            <div className="space-y-4 p-1">
                <p className="text-sm text-slate-400">Define tus series de calentamiento como un porcentaje del peso efectivo (o 1RMe).</p>
                <div className="space-y-2">
                    {sets.map((set, i) => (
                        <div key={set.id} className="flex items-center gap-2 bg-slate-800 p-2 rounded-md">
                            <span className="text-xs font-bold w-6">{i + 1}</span>
                            <div className="flex flex-col flex-1">
                                <label className="text-[10px] text-slate-400">Carga %</label>
                                <input type="number" value={set.percentageOfWorkingWeight} onChange={e => updateSet(i, 'percentageOfWorkingWeight', parseFloat(e.target.value))} className="w-full bg-slate-900 border-none rounded text-xs p-1"/>
                            </div>
                            <div className="flex flex-col w-16">
                                <label className="text-[10px] text-slate-400">Reps</label>
                                <input type="number" value={set.targetReps} onChange={e => updateSet(i, 'targetReps', parseFloat(e.target.value))} className="w-full bg-slate-900 border-none rounded text-xs p-1"/>
                            </div>
                            <button onClick={() => removeSet(i)} className="text-slate-500 hover:text-red-400"><TrashIcon size={16}/></button>
                        </div>
                    ))}
                </div>
                <Button onClick={addSet} variant="secondary" className="w-full !py-2 !text-xs"><PlusIcon size={14}/> Añadir Serie</Button>
                <div className="flex justify-end gap-2 pt-4 border-t border-slate-700">
                    <Button variant="secondary" onClick={onClose}>Cancelar</Button>
                    <Button onClick={handleSave}>Guardar Configuración</Button>
                </div>
            </div>
        </Modal>
    );
};


// --- ADVANCED EXERCISE PICKER MODAL UNIFICADO (DARK PREMIUM + FATIGA 1-10 + LIST/GRID) ---
import { ScaleIcon, ChevronLeftIcon, MaximizeIcon, GridIcon } from './icons';

export const AdvancedExercisePickerModal: React.FC<{
    isOpen: boolean;
    onClose: () => void;
    onSelect: (exercise: ExerciseMuscleInfo) => void;
    onCreateNew: () => void;
    exerciseList: ExerciseMuscleInfo[];
    initialSearch?: string;
}> = ({ isOpen, onClose, onSelect, onCreateNew, exerciseList, initialSearch }) => {
    const { settings } = useAppContext(); // Extraemos la configuración para el Tanque
    const [search, setSearch] = useState(initialSearch || '');
    const [activeCategory, setActiveCategory] = useState<string | null>(null);
    const [tooltipExId, setTooltipExId] = useState<string | null>(null);
    const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
    
    // Multi-sorting state para modo lista
    const [sortKey, setSortKey] = useState<'name' | 'muscle' | 'fatigue'>('name');
    const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc');
    const inputRef = useRef<HTMLInputElement>(null);

    const categoryMap: Record<string, string[]> = {
        'Pecho': ['pectoral', 'pecho'],
        'Espalda': ['dorsal', 'trapecio', 'espalda', 'romboide'],
        'Hombros': ['deltoide', 'hombro'],
        'Piernas': ['cuádriceps', 'cuadriceps', 'isquio', 'glúteo', 'gluteo', 'pantorrilla', 'pierna', 'femoral'],
        'Brazos': ['bíceps', 'biceps', 'tríceps', 'triceps', 'antebrazo', 'brazo'],
        'Core': ['abdomen', 'core', 'lumbar', 'espalda baja']
    };

    const topTierNames = [
        'sentadilla trasera', 'peso muerto convencional', 'peso muerto rumano', 
        'sentadilla hack', 'sentadilla pendulum', 'extensión de cuádriceps', 'sissy squat',
        'curl femoral sentado', 'curl nórdico', 'hip-thrust', 'press banca',
        'press inclinado', 'cruce de poleas', 'elevaciones laterales en polea',
        'press de hombro en máquina', 'jalón al pecho', 'dominada libre', 'remo en t', 'remo pendlay'
    ];

    const isTopTier = (exName: string) => topTierNames.some(name => exName.toLowerCase().includes(name.toLowerCase()));
    
    const getParentMuscle = (muscleName: string) => {
        const lower = muscleName.toLowerCase();
        if (lower.includes('deltoide')) return muscleName; 
        if (lower.includes('pectoral') || lower.includes('pecho')) return 'Pectoral';
        if (lower.includes('cuádriceps') || lower.includes('cuadriceps') || lower.includes('vasto') || lower.includes('recto femoral')) return 'Cuádriceps';
        if (lower.includes('bíceps') || lower.includes('biceps')) return 'Bíceps';
        if (lower.includes('tríceps') || lower.includes('triceps')) return 'Tríceps';
        if (lower.includes('isquio') || lower.includes('femoral')) return 'Isquiosurales';
        if (lower.includes('glúteo') || lower.includes('gluteo')) return 'Glúteos';
        if (lower.includes('trapecio')) return 'Trapecio';
        if (lower.includes('dorsal')) return 'Dorsal';
        if (lower.includes('gemelo') || lower.includes('pantorrilla') || lower.includes('sóleo')) return 'Pantorrillas';
        if (lower.includes('abdomen') || lower.includes('core')) return 'Abdomen';
        return muscleName;
    };

    const getPrimaryMuscleName = (ex: ExerciseMuscleInfo) => {
        const primary = ex.involvedMuscles.find(m => m.role === 'primary');
        return primary ? getParentMuscle(primary.muscle) : 'Varios';
    };

    // MOTOR DE FATIGA AUGE 3.0 (Battery Drain %)
    const calculateIntrinsicFatigue = (ex: ExerciseMuscleInfo) => {
        // Simulamos una serie estándar efectiva (10 reps @ RPE 8)
        const tanks = calculatePersonalizedBatteryTanks(settings);
        return calculateSetBatteryDrain({ targetReps: 10, targetRPE: 8 }, ex, tanks, 0, 90);
    };

    const getFatigueUI = (drain: { muscularDrainPct: number; cnsDrainPct: number }) => {
        const total = drain.cnsDrainPct + drain.muscularDrainPct;
        if (total <= 3.0) return { color: 'bg-emerald-500', text: 'text-emerald-500' };
        if (total <= 6.0) return { color: 'bg-yellow-500', text: 'text-yellow-500' };
        return { color: 'bg-red-500', text: 'text-red-500' };
    };

    const getAugeIndexes = (exName: string, exInfo: ExerciseMuscleInfo) => {
        return getDynamicAugeMetrics(exInfo, exName);
    };

    useEffect(() => {
        if (isOpen) {
            if (initialSearch) setSearch(initialSearch);
            setTimeout(() => inputRef.current?.focus(), 50);
        }
        else { setSearch(''); setActiveCategory(null); setTooltipExId(null); }
    }, [isOpen, initialSearch]);

    const handleSort = (key: 'name' | 'muscle' | 'fatigue') => {
        if (sortKey === key) setSortDir(prev => prev === 'asc' ? 'desc' : 'asc');
        else { setSortKey(key); setSortDir('asc'); }
    };

    const filteredAndSorted = useMemo(() => {
        let result = exerciseList;

        if (search) {
            result = result.filter(e => e.name.toLowerCase().includes(search.toLowerCase()));
        } else if (activeCategory) {
            if (activeCategory === 'KPKN Top Tier') result = result.filter(e => isTopTier(e.name));
            else if (activeCategory === 'Baja Fatiga') result = result.filter(e => {
                const drain = calculateIntrinsicFatigue(e);
                return (drain.cnsDrainPct + drain.muscularDrainPct) <= 6.0; // Umbral de batería %
            });
            else {
                const terms = categoryMap[activeCategory] || [];
                result = result.filter(e => e.involvedMuscles.some(m => m.role === 'primary' && terms.some(term => m.muscle.toLowerCase().includes(term))));
            }
        } else if (viewMode === 'grid') {
            return []; // No mostrar lista si está en grid y sin buscar
        }

        result = [...result].sort((a, b) => {
            if (sortKey === 'name') return sortDir === 'asc' ? a.name.localeCompare(b.name) : b.name.localeCompare(a.name);
            if (sortKey === 'fatigue') {
                const fA = calculateIntrinsicFatigue(a);
                const fB = calculateIntrinsicFatigue(b);
                const totalA = fA.cnsDrainPct + fA.muscularDrainPct;
                const totalB = fB.cnsDrainPct + fB.muscularDrainPct;
                return sortDir === 'asc' ? totalA - totalB : totalB - totalA;
            }
            if (sortKey === 'muscle') {
                const mA = getPrimaryMuscleName(a);
                const mB = getPrimaryMuscleName(b);
                const comp = sortDir === 'asc' ? mA.localeCompare(mB) : mB.localeCompare(mA);
                if (comp !== 0) return comp;
                const fA = calculateIntrinsicFatigue(a);
                const fB = calculateIntrinsicFatigue(b);
                return (fA.cnsDrainPct + fA.muscularDrainPct) - (fB.cnsDrainPct + fB.muscularDrainPct);
            }
            return 0;
        });

        return result.slice(0, 50); // Límite por rendimiento
    }, [search, activeCategory, exerciseList, viewMode, sortKey, sortDir]);

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-[99999] bg-black/60 backdrop-blur-md flex items-center justify-center p-4 sm:p-6 font-sans overflow-hidden animate-in fade-in duration-200">
            <div className="absolute inset-0" onClick={onClose} aria-hidden="true" />
            {/* Se agrega min-h-[60vh] para evitar saltos bruscos de tamaño */}
            <div className="bg-zinc-950 border border-white/10 shadow-2xl relative z-10 flex flex-col w-full max-w-lg min-h-[60vh] max-h-[85vh] rounded-3xl overflow-hidden animate-in zoom-in-95 duration-200" onClick={(e) => e.stopPropagation()}>
                
                {/* Cabecera Dark Premium */}
                <div className="p-4 border-b border-white/5 bg-black/50 backdrop-blur-lg shrink-0 flex flex-col gap-3">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                            <button onClick={() => setViewMode(prev => prev === 'grid' ? 'list' : 'grid')} className="p-2 bg-white/5 rounded-lg text-zinc-400 hover:text-white hover:bg-white/10 transition-colors">
                                {viewMode === 'grid' ? <ActivityIcon size={16} /> : <GridIcon size={16} />}
                            </button>
                            <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">
                                {viewMode === 'grid' ? 'Categorías' : 'Lista Detallada'}
                            </span>
                        </div>
                        <div className="flex items-center gap-2">
                            <button onClick={onCreateNew} className="px-3 py-1.5 bg-white/5 hover:bg-white/10 border border-white/10 rounded-lg text-[10px] font-black uppercase text-white transition-colors flex items-center gap-1">
                                <PlusIcon size={12}/> Crear
                            </button>
                            <button onClick={onClose} className="p-2 bg-white/5 rounded-full text-zinc-400 hover:text-red-500 transition-colors">
                                <XIcon size={16} />
                            </button>
                        </div>
                    </div>
                    
                    <div className="flex items-center gap-3 bg-zinc-900 border border-white/10 rounded-xl px-3 focus-within:border-white/30 transition-colors">
                        {activeCategory && !search ? (
                            <button onClick={() => setActiveCategory(null)} className="p-1 text-zinc-400 hover:text-white transition-colors"><ChevronLeftIcon size={18} /></button>
                        ) : (
                            <SearchIcon size={18} className="text-zinc-500" />
                        )}
                        <input 
                            ref={inputRef}
                            className="flex-1 bg-transparent border-none outline-none text-sm font-bold text-white placeholder-zinc-600 h-10 p-0 focus:ring-0"
                            placeholder={activeCategory ? `Buscar en ${activeCategory}...` : "Nombre del ejercicio..."}
                            value={search}
                            onChange={e => { setSearch(e.target.value); if (viewMode==='grid') setViewMode('list'); }}
                        />
                    </div>
                </div>
                
                <div className="overflow-y-auto flex-1 custom-scrollbar relative bg-zinc-950 p-2">
                    {/* VISTA GRID (MASONRY) */}
                    {viewMode === 'grid' && !search && !activeCategory ? (
                        <div className="grid grid-cols-2 gap-2 p-2 auto-rows-[80px]">
                            {[
                                { id: 'KPKN Top Tier', cols: 'col-span-2 row-span-1', border: 'border-yellow-500/30', text: 'text-yellow-500', label: '★ KPKN Top Tier' },
                                { id: 'Baja Fatiga', cols: 'col-span-1 row-span-1', border: 'border-emerald-500/30', text: 'text-emerald-500', label: 'Baja Fatiga' },
                                { id: 'Piernas', cols: 'col-span-1 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Piernas' },
                                { id: 'Pecho', cols: 'col-span-1 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Pecho' },
                                { id: 'Espalda', cols: 'col-span-1 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Espalda' },
                                { id: 'Hombros', cols: 'col-span-2 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Hombros' },
                                { id: 'Brazos', cols: 'col-span-1 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Brazos' },
                                { id: 'Core', cols: 'col-span-1 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Core' },
                            ].map(cat => (
                                <button key={cat.id} onClick={() => { setActiveCategory(cat.id); setViewMode('list'); }} className={`${cat.cols} bg-black border ${cat.border} hover:border-white/50 rounded-2xl p-4 text-left flex flex-col justify-center items-start transition-all group relative overflow-hidden`}>
                                    <div className="absolute inset-0 bg-white/0 group-hover:bg-white/5 transition-colors"></div>
                                    <span className={`font-black text-sm uppercase tracking-tight relative z-10 ${cat.text}`}>{cat.label}</span>
                                </button>
                            ))}
                        </div>
                    ) : (
                        /* VISTA LISTA DETALLADA */
                        <div className="flex flex-col h-full">
                            {/* Cabecera de Ordenamiento */}
                            {filteredAndSorted.length > 0 && (
                                <div className="grid grid-cols-[2fr_1fr_1fr] gap-2 px-4 py-2 border-b border-white/5 sticky top-0 bg-zinc-950 z-20">
                                    <button onClick={() => handleSort('name')} className="text-left flex items-center gap-1 text-[9px] font-black uppercase text-zinc-500 hover:text-white">Ejercicio {sortKey === 'name' && (sortDir === 'asc' ? '↑' : '↓')}</button>
                                    <button onClick={() => handleSort('muscle')} className="text-left flex items-center gap-1 text-[9px] font-black uppercase text-zinc-500 hover:text-white">Músculo {sortKey === 'muscle' && (sortDir === 'asc' ? '↑' : '↓')}</button>
                                    <button onClick={() => handleSort('fatigue')} className="text-right flex items-center justify-end gap-1 text-[9px] font-black uppercase text-zinc-500 hover:text-white">Fatiga {sortKey === 'fatigue' && (sortDir === 'asc' ? '↑' : '↓')}</button>
                                </div>
                            )}

                            <div className="space-y-1 p-2">
                                {filteredAndSorted.map(ex => {
                                    const topTier = isTopTier(ex.name);
                                    const fatigueScore = calculateIntrinsicFatigue(ex);
                                    const fatigueUI = getFatigueUI(fatigueScore);
                                    const primaryMuscle = getPrimaryMuscleName(ex);
                                    
                                    // Agrupación matemática correcta (Tomando el MÁXIMO por padre, no la suma)
                                    const groupedMuscles = ex.involvedMuscles.reduce((acc, m) => {
                                        const parent = getParentMuscle(m.muscle);
                                        const value = DISPLAY_ROLE_WEIGHTS[m.role] ?? 0.2;
                                        if (!acc[parent] || value > acc[parent]) acc[parent] = value;
                                        return acc;
                                    }, {} as Record<string, number>);

                                    return (
                                        <div key={ex.id} className="w-full bg-black rounded-xl border border-white/5 hover:border-white/20 transition-all flex flex-col">
                                            <div className="flex items-center justify-between px-2 py-1">
                                            <button onClick={() => onSelect(ex)} className="flex-1 text-left py-2 px-3 flex flex-col group">
                                                    <span className={`font-bold text-[13px] leading-tight mb-1.5 break-words ${topTier ? 'text-yellow-400' : 'text-white'}`}>
                                                        {topTier && '★ '}{ex.name}
                                                    </span>
                                                    <div className="flex justify-between items-center w-full">
                                                        <span className="text-[9px] text-zinc-500 uppercase font-bold truncate">{ex.equipment} • {primaryMuscle}</span>
                                                        <div className="flex items-center gap-1.5 shrink-0">
                                                            <div className={`w-1.5 h-1.5 rounded-full ${fatigueUI.color} shadow-[0_0_8px_currentColor]`}></div>
                                                            <span className="text-[9px] font-mono text-zinc-400 bg-zinc-900 px-1 rounded border border-white/5">
                                                                -{fatigueScore.cnsDrainPct.toFixed(1)}% <span className="text-yellow-500">⚡</span>
                                                            </span>
                                                            <span className="text-[9px] font-mono text-zinc-400 bg-zinc-900 px-1 rounded border border-white/5">
                                                                -{fatigueScore.muscularDrainPct.toFixed(1)}% <span className="text-red-400">🥩</span>
                                                            </span>
                                                        </div>
                                                    </div>
                                                </button>
                                                <button onClick={(e) => { e.stopPropagation(); setTooltipExId(tooltipExId === ex.id ? null : ex.id); }} className={`p-2 transition-colors ${tooltipExId === ex.id ? 'text-blue-400' : 'text-zinc-600 hover:text-white'}`}>
                                                    <InfoIcon size={16} />
                                                </button>
                                            </div>
                                            
                                            {/* Panel de Detalles */}
                                            {tooltipExId === ex.id && (
                                                <div className="bg-zinc-900 border-t border-white/5 p-4 animate-in slide-in-from-top-2 duration-200">
                                                    <div className="grid grid-cols-2 gap-4">
                                                        <div className="space-y-2">
                                                            <span className="text-[9px] font-black uppercase text-zinc-500 tracking-widest">Aporte (1 Serie Efectiva)</span>
                                                            <div className="space-y-1">
                                                            {Object.entries(groupedMuscles).map(([muscle, maxVal], idx) => (
                                                                    <div key={idx} className="flex justify-between items-center text-[10px]">
                                                                        <span className="font-bold text-zinc-300">{muscle}</span>
                                                                        <span className="text-zinc-400 font-mono">+{(maxVal as number).toFixed(1)}</span>
                                                                    </div>
                                                                ))}
                                                            </div>
                                                        </div>
                                                        
                                                        <div className="space-y-2 border-l border-white/10 pl-4">
                                                            <span className="text-[9px] font-black uppercase text-zinc-500 tracking-widest block">Índices AUGE</span>
                                                            <div className="bg-black border border-white/5 p-2 rounded-lg grid grid-cols-1 gap-1.5">
                                                                {(() => {
                                                                    const { efc, ssc, cnc } = getAugeIndexes(ex.name, ex);
                                                                    return (
                                                                        <>
                                                                            <div className="flex justify-between items-center"><span className="text-[9px] text-zinc-400">Metabólico (EFC)</span><span className="text-[10px] font-mono text-white">{efc.toFixed(1)}</span></div>
                                                                            <div className="flex justify-between items-center"><span className="text-[9px] text-zinc-400">Neural (CNC)</span><span className="text-[10px] font-mono text-white">{cnc.toFixed(1)}</span></div>
                                                                            <div className="flex justify-between items-center"><span className="text-[9px] text-zinc-400">Espinal (SSC)</span><span className="text-[10px] font-mono text-red-400">{ssc.toFixed(1)}</span></div>
                                                                        </>
                                                                    )
                                                                })()}
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                            )}
                                        </div>
                                    );
                                })}
                                {filteredAndSorted.length === 0 && (
                                    <div className="text-center py-12">
                                        <DumbbellIcon size={32} className="mx-auto text-zinc-800 mb-2"/>
                                        <p className="text-xs text-zinc-500 font-bold mb-4">No se encontraron ejercicios</p>
                                        <button onClick={onCreateNew} className="px-6 py-2 bg-white text-black rounded-full font-black text-[10px] uppercase tracking-widest hover:scale-105 transition-transform">Crear Ejercicio Personalizado</button>
                                    </div>
                                )}
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

// --- BLOQUE 2 CORREGIDO: LOGICA COMPLETA DE CARGAS (REPS + RPE -> % -> KG) ---

const ExerciseCard = React.forwardRef<HTMLDetailsElement, {
    exercise: Exercise;
    onExerciseChange: (fieldOrUpdate: keyof Exercise | Partial<Exercise>, value?: any) => void;
    onSetChange: (setIndex: number, field: keyof ExerciseSet | Partial<ExerciseSet>, value?: any) => void;
    onAddSet: () => void;
    onRemoveSet: (setIndex: number) => void;
    onRemoveExercise: () => void;
    onReorder: (direction: 'up' | 'down') => void;
    isFirst: boolean;
    isLast: boolean;
    isReorderDisabled?: boolean;
    defaultOpen?: boolean;
    categoryColor?: string;
    onLinkNext?: () => void;
    onUnlink?: () => void;
    isInSuperset?: boolean;
    isSupersetLast?: boolean;
    isSelectionMode?: boolean;
    isSelected?: boolean;
    onToggleSelect?: () => void;
    hideAddSetButton?: boolean;
    isJunkVolumeCulprit?: boolean;
}>((props, ref) => {
    const { exercise, onExerciseChange, onSetChange, onAddSet, onRemoveSet, onRemoveExercise, onReorder, isFirst, isLast, defaultOpen = true, categoryColor, onLinkNext, onUnlink, isInSuperset, isSupersetLast, isSelectionMode, isSelected, onToggleSelect, hideAddSetButton, isJunkVolumeCulprit } = props;
    const { exerciseList = [], openCustomExerciseEditor, setOnExerciseCreated, settings } = useAppContext();
    const [infoModalExercise, setInfoModalExercise] = useState<ExerciseMuscleInfo | null>(null);
    const [activeAutocomplete, setActiveAutocomplete] = useState(false);
    const [isWarmupModalOpen, setIsWarmupModalOpen] = useState(false);
    
    // Estados Calculadora 1RM
    const [rmInputMode, setRmInputMode] = useState<'manual' | 'calculator'>(exercise.prFor1RM ? 'calculator' : 'manual');
    const [prWeight, setPrWeight] = useState(String(exercise.prFor1RM?.weight || ''));
    const [prReps, setPrReps] = useState(String(exercise.prFor1RM?.reps || ''));
    
    const [pendingAmrapSetIndex, setPendingAmrapSetIndex] = useState<number | null>(null);
    const [isConfirmingDelete, setIsConfirmingDelete] = useState(false);
    const [isAdvancedPickerOpen, setIsAdvancedPickerOpen] = useState(false); // Estado del modal avanzado
    const [isDetailsOpen, setIsDetailsOpen] = useState(defaultOpen);

    // Efecto: Cálculo de 1RM Referencial
    useEffect(() => {
        if (rmInputMode === 'calculator' && exercise.trainingMode === 'percent') {
            const w = parseFloat(prWeight);
            const r = parseInt(prReps, 10);
            if (w > 0 && r > 0) {
                const e1rm = calculateBrzycki1RM(w, r);
                onExerciseChange({ reference1RM: parseFloat(e1rm.toFixed(1)), prFor1RM: { weight: w, reps: r } });
            }
        }
    }, [prWeight, prReps, rmInputMode, exercise.trainingMode]);

    // Manejador Inteligente de Cambios en Sets (soporta campo+valor o objeto parcial)
    const handleSetChange = (setIndex: number, fieldOrPartial: keyof ExerciseSet | Partial<ExerciseSet>, value?: any) => {
        const isPartial = typeof fieldOrPartial === 'object';
        const field = isPartial ? null : fieldOrPartial as keyof ExerciseSet;
        const val = isPartial ? undefined : value;

        if (!isPartial && ((field === 'intensityMode' && val === 'amrap') || (field === 'isAmrap' && val === true))) {
            setPendingAmrapSetIndex(setIndex);
            return;
        }

        if (isPartial) {
            onSetChange(setIndex, fieldOrPartial as Partial<ExerciseSet>);
        } else {
            onSetChange(setIndex, field!, val);
            // AUTO-CALCULO: Si estamos en modo %, y cambiamos Reps o Intensidad, recalculamos el %
            if (exercise.trainingMode === 'percent') {
                const currentSet = exercise.sets[setIndex];
                const updatedSet = { ...currentSet, [field as keyof ExerciseSet]: val };
                if (field === 'targetReps' || field === 'targetRPE' || field === 'targetRIR' || field === 'intensityMode') {
                    const repsToFailure = getEffectiveRepsForRM(updatedSet);
                    if (repsToFailure && repsToFailure > 0) {
                        const percent = estimatePercent1RM(repsToFailure);
                        if (percent && percent > 0) {
                            onSetChange(setIndex, 'targetPercentageRM', percent);
                        }
                    }
                }
            }
        }
    };
    
    const confirmAmrapSettings = (isCalibrator: boolean) => {
        if (pendingAmrapSetIndex !== null) {
            onSetChange(pendingAmrapSetIndex, { intensityMode: 'amrap', isAmrap: true, isCalibrator: isCalibrator });
            setPendingAmrapSetIndex(null);
        }
    };

    const exerciseInfo = useMemo(() => exerciseList.find(e => e.id === exercise.exerciseDbId), [exerciseList, exercise.exerciseDbId]);
    
    const handleCreateNewExercise = useCallback(() => {
        if (!exercise.name) return;
        setOnExerciseCreated(() => (newEx: ExerciseMuscleInfo) => { onExerciseChange({ name: newEx.name, exerciseDbId: newEx.id }); });
        openCustomExerciseEditor({ preFilledName: exercise.name });
        setActiveAutocomplete(false);
    }, [exercise.name, onExerciseChange, setOnExerciseCreated, openCustomExerciseEditor]);

    const handleSelectSuggestion = (sugg: any) => {
        if (sugg.isCreateOption) handleCreateNewExercise();
        else { onExerciseChange({ name: sugg.name, exerciseDbId: sugg.id }); setActiveAutocomplete(false); }
    };

    const suggestions = useMemo(() => {
        if (!activeAutocomplete || !exercise.name || exercise.name.length < 2) return [];
        const query = exercise.name.toLowerCase();
        const filtered = exerciseList.filter(e => e.name.toLowerCase().includes(query)).slice(0, 5);
        if (!exerciseList.some(e => e.name.toLowerCase() === query)) filtered.push({ id: 'CREATE', name: `Añadir "${exercise.name}"...`, isCreateOption: true } as any);
        return filtered;
    }, [activeAutocomplete, exercise.name, exerciseList]);

    const restLabel = isInSuperset && !isSupersetLast ? 'Transición (s)' : 'Descanso (s)';

    // Sincronizar isDetailsOpen cuando defaultOpen cambia (ej. desde props)
    useEffect(() => { setIsDetailsOpen(defaultOpen); }, [defaultOpen]);

    // --- MOTOR LOCAL AUGE 3.0 (Drenaje Predictivo en Vivo por Ejercicio) ---
    const localDrain = useMemo(() => {
        const tanks = calculatePersonalizedBatteryTanks(settings);
        let cnsPct = 0, spinalPct = 0, muscularPct = 0;
        
        const safeSets = Array.isArray(exercise.sets) ? exercise.sets : [];
        
        safeSets.forEach((set, idx) => {
            if ((set as any)?.type === 'warmup') return;
            
            // Pasamos `idx` simulando el volumen acumulado intra-ejercicio 
            // para detonar la alerta visual si excede su capacidad.
            const drain = calculateSetBatteryDrain(set, exerciseInfo, tanks, idx, exercise.restTime || 90);
            
            cnsPct += drain.cnsDrainPct;
            spinalPct += drain.spinalDrainPct;
            muscularPct += drain.muscularDrainPct;
        });
        
        return { cns: cnsPct, spinal: spinalPct, muscular: muscularPct };
    }, [exercise.sets, exerciseInfo, exercise.restTime, settings]);

    return (
        <div className="flex gap-2 items-start transition-all duration-300 w-full max-w-full">
             <WarmupConfigModal isOpen={isWarmupModalOpen} onClose={() => setIsWarmupModalOpen(false)} exerciseName={exercise.name} warmupSets={exercise.warmupSets || []} onSave={(sets) => onExerciseChange('warmupSets', sets)} />
            <AmrapSelectionModal isOpen={pendingAmrapSetIndex !== null} onClose={() => setPendingAmrapSetIndex(null)} onConfirm={confirmAmrapSettings} />

            {isSelectionMode && (
                <div className="pt-4 animate-fade-in">
                    <button type="button" onClick={(e) => { e.preventDefault(); onToggleSelect?.(); }} className={`w-6 h-6 rounded flex items-center justify-center border transition-all ${isSelected ? 'bg-white border-white text-black' : 'border-zinc-700 bg-black'}`}>
                        {isSelected && <CheckIcon size={14} strokeWidth={4} />}
                    </button>
                </div>
            )}
            
            <details ref={ref} id={`exercise-card-${exercise.id}`} className={`relative flex-grow w-full border-b bg-black ${activeAutocomplete ? 'z-50 overflow-visible' : 'overflow-hidden'} ${isInSuperset ? '!border-none !shadow-none !bg-transparent' : ''} ${isJunkVolumeCulprit ? 'border-red-500 shadow-[0_0_15px_rgba(239,68,68,0.3)]' : 'border-white/10'}`} open={isDetailsOpen}>
                {isJunkVolumeCulprit && (
                    <div className="absolute top-0 right-2 bg-red-500 text-white text-[8px] font-black uppercase px-2 py-0.5 rounded-b-lg shadow-lg z-10 animate-pulse">
                        Volumen Basura
                    </div>
                )}
                <summary className="py-4 px-2 flex items-center gap-3 cursor-pointer list-none hover:bg-zinc-900 transition-colors rounded-lg group" onClick={(e) => { if (!(e.target as HTMLElement).closest('button')) { e.preventDefault(); setIsDetailsOpen(prev => !prev); } }}>
                    <div className="flex items-center gap-3 flex-grow min-w-0">
                        <button type="button" onClick={(e) => { e.preventDefault(); e.stopPropagation(); onExerciseChange('isStarTarget', !exercise.isStarTarget); }} className={`transition-colors flex-shrink-0 ${exercise.isStarTarget ? 'text-white' : 'text-zinc-700 group-hover:text-zinc-500'}`}>
                            <StarIcon size={18} filled={exercise.isStarTarget} />
                        </button>
                        <div className="relative flex-grow min-w-0 flex flex-col">
                            <button 
                                type="button" 
                                onClick={(e) => { e.preventDefault(); setIsAdvancedPickerOpen(true); }} 
                                className={`w-full text-left !text-lg !font-bold !bg-transparent !border-0 !p-0 uppercase tracking-tight truncate ${exercise.name ? 'text-white' : 'text-zinc-600'}`}
                            >
                                {exercise.name || "Buscar ejercicio..."}
                            </button>
                            {(exerciseInfo?.axialLoadFactor ?? 0) > 0 && (
                                <span className="text-[9px] font-black uppercase text-red-500 tracking-widest mt-0.5 flex items-center gap-1">
                                    <FlameIcon size={10} /> Alta Carga Espinal (SSC: {exerciseInfo!.axialLoadFactor})
                                </span>
                            )}
                            
                            <AdvancedExercisePickerModal
                                isOpen={isAdvancedPickerOpen}
                                initialSearch={exercise.name}
                                onClose={() => setIsAdvancedPickerOpen(false)}
                                exerciseList={exerciseList}
                                onSelect={(sugg) => {
                                    onExerciseChange({ name: sugg.name, exerciseDbId: sugg.id });
                                    setIsAdvancedPickerOpen(false);
                                }}
                                onCreateNew={() => {
                                    setIsAdvancedPickerOpen(false);
                                    // Llamamos a la función original que ya tienes configurada para abrir tu modal
                                    handleSelectSuggestion({ isCreateOption: true, name: exercise.name || "Nuevo Ejercicio" });
                                }}
                            />
                        </div>
                    </div>
                    <div className="flex items-center gap-2 flex-shrink-0">
                        <button type="button" onClick={(e) => { e.preventDefault(); if (exerciseInfo) setInfoModalExercise(exerciseInfo); }} className={`transition-colors ${exerciseInfo ? 'text-white' : 'text-zinc-700'}`}><InfoIcon size={18} /></button>
                        <ChevronRightIcon className="details-arrow text-zinc-500" size={16} />
                    </div>
                </summary>

                <div className="px-2 py-2 flex gap-4 bg-zinc-900/30 rounded mb-4">
                    {!isConfirmingDelete ? (
                        <>
                            {onLinkNext && !isLast && <button onClick={(e) => { e.preventDefault(); onLinkNext(); }} className="text-[9px] font-black uppercase text-zinc-500 hover:text-white transition-colors flex items-center gap-1"><LinkIcon size={12}/> Biserie</button>}
                            {onUnlink && <button onClick={(e) => { e.preventDefault(); onUnlink(); }} className="text-[9px] font-black uppercase text-zinc-500 hover:text-white transition-colors flex items-center gap-1"><UnlinkIcon size={12}/> Separar</button>}
                            <div className="flex-grow"></div>
                            <div className="flex gap-2">
                                <button onClick={(e) => { e.preventDefault(); onReorder('up'); }} disabled={isFirst} className="text-zinc-600 hover:text-white disabled:opacity-20"><ArrowUpIcon size={14}/></button>
                                <button onClick={(e) => { e.preventDefault(); onReorder('down'); }} disabled={isLast} className="text-zinc-600 hover:text-white disabled:opacity-20"><ArrowDownIcon size={14}/></button>
                            </div>
                            <button onClick={(e) => { e.preventDefault(); setIsConfirmingDelete(true); }} className="text-[9px] font-black uppercase text-zinc-600 hover:text-red-500 transition-colors ml-4"><TrashIcon size={14} /></button>
                        </>
                    ) : (
                        <div className="flex items-center gap-4 w-full justify-end">
                            <span className="text-[10px] font-bold text-red-500 uppercase">¿Borrar?</span>
                            <button onClick={(e) => { e.preventDefault(); onRemoveExercise(); }} className="text-[10px] font-black uppercase text-white bg-red-600 px-3 py-1 rounded">Sí</button>
                            <button onClick={(e) => { e.preventDefault(); setIsConfirmingDelete(false); }} className="text-[10px] font-black uppercase text-zinc-400">No</button>
                        </div>
                    )}
                </div>
                
                <div className="px-1 pb-4 space-y-4">
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-xs items-start">
                        <div>
                            <label className={`text-[9px] font-black uppercase tracking-wider ${isInSuperset && !isSupersetLast ? 'text-white' : 'text-zinc-500'}`}>{restLabel}</label>
                            <input type="number" step="5" value={exercise.restTime || ''} onChange={(e) => onExerciseChange('restTime', parseInt(e.target.value))} className="w-full mt-1 bg-transparent border-b border-zinc-700 text-white font-mono focus:border-white focus:ring-0 p-1"/>
                        </div>
                        <div>
                            <label className="text-[9px] text-zinc-500 font-black uppercase tracking-wider">Modo</label>
                            <select value={exercise.trainingMode || 'reps'} onChange={(e) => onExerciseChange('trainingMode', e.target.value as any)} className="w-full mt-1 bg-black text-white border-b border-zinc-700 text-xs font-bold p-1 focus:ring-0 uppercase"><option value="reps">Repeticiones</option><option value="percent">% 1RM</option><option value="time">Tiempo</option><option value="custom">Libre</option></select>
                        </div>
                        
                        {/* CALCULADORA 1RM (Solo visible en modo Percent) */}
                        {exercise.trainingMode === 'percent' && (
                            <div className="col-span-2 bg-zinc-900/50 p-2 rounded border border-white/5 grid grid-cols-3 gap-2 animate-fade-in">
                                <div className="col-span-3 flex justify-between mb-1">
                                    <span className="text-[9px] font-black uppercase text-zinc-400">1RM de Referencia</span>
                                    <div className="flex gap-2">
                                        <button onClick={() => setRmInputMode('manual')} className={`text-[8px] font-bold uppercase px-1 rounded ${rmInputMode === 'manual' ? 'bg-white text-black' : 'text-zinc-500'}`}>Manual</button>
                                        <button onClick={() => setRmInputMode('calculator')} className={`text-[8px] font-bold uppercase px-1 rounded ${rmInputMode === 'calculator' ? 'bg-white text-black' : 'text-zinc-500'}`}>Estimador</button>
                                    </div>
                                </div>
                                {rmInputMode === 'calculator' ? (
                                    <>
                                        <div className="flex flex-col">
                                            <label className="text-[8px] text-zinc-600 uppercase">Peso PR</label>
                                            <input type="number" value={prWeight} onChange={(e) => setPrWeight(e.target.value)} className="bg-black border border-zinc-700 rounded text-xs text-white p-1 text-center" />
                                        </div>
                                        <div className="flex flex-col">
                                            <label className="text-[8px] text-zinc-600 uppercase">Reps</label>
                                            <input type="number" value={prReps} onChange={(e) => setPrReps(e.target.value)} className="bg-black border border-zinc-700 rounded text-xs text-white p-1 text-center" />
                                        </div>
                                        <div className="flex flex-col justify-end">
                                            <div className="flex items-center justify-center bg-white/10 rounded text-xs font-bold text-white border border-white/20 h-[26px]">
                                                {exercise.reference1RM || 0}
                                            </div>
                                        </div>
                                    </>
                                ) : (
                                    <div className="col-span-3 flex items-center gap-2">
                                        <input type="number" value={exercise.reference1RM || ''} onChange={(e) => onExerciseChange('reference1RM', parseFloat(e.target.value))} className="bg-black border border-zinc-700 rounded text-xs text-white p-1 w-full text-center font-bold" placeholder="Tu 1RM..." />
                                    </div>
                                )}
                            </div>
                        )}
                    </div>

                    {/* --- MINI-BATERÍAS AUGE (Drenaje en Vivo) --- */}
                    {exercise.sets.length > 0 && (
                        <div className="col-span-2 md:col-span-4 bg-zinc-950 p-2.5 rounded-lg border border-white/5 space-y-2 mb-3 mt-1 shadow-inner">
                            <div className="flex justify-between items-center border-b border-white/5 pb-1 mb-1">
                                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-widest flex items-center gap-1"><ActivityIcon size={10}/> Drenaje Predictivo Local</span>
                            </div>
                            <div className="grid grid-cols-3 gap-4">
                                <div>
                                    <div className="flex justify-between items-center mb-1"><span className="text-[8px] font-bold text-zinc-400">MSC</span><span className={`text-[8px] font-mono ${localDrain.muscular > 80 ? 'text-red-400' : 'text-white'}`}>{localDrain.muscular.toFixed(0)}%</span></div>
                                    <div className="h-1 w-full bg-zinc-900 rounded-full overflow-hidden"><div className={`h-full transition-all duration-300 ${localDrain.muscular > 80 ? 'bg-red-500' : 'bg-emerald-500'}`} style={{width: `${localDrain.muscular}%`}}></div></div>
                                </div>
                                <div>
                                    <div className="flex justify-between items-center mb-1"><span className="text-[8px] font-bold text-zinc-400">SNC</span><span className={`text-[8px] font-mono ${localDrain.cns > 80 ? 'text-red-400' : 'text-white'}`}>{localDrain.cns.toFixed(0)}%</span></div>
                                    <div className="h-1 w-full bg-zinc-900 rounded-full overflow-hidden"><div className={`h-full transition-all duration-300 ${localDrain.cns > 80 ? 'bg-red-500' : 'bg-yellow-500'}`} style={{width: `${localDrain.cns}%`}}></div></div>
                                </div>
                                <div>
                                    <div className="flex justify-between items-center mb-1"><span className="text-[8px] font-bold text-zinc-400">ESP</span><span className={`text-[8px] font-mono ${localDrain.spinal > 80 ? 'text-red-400' : 'text-white'}`}>{localDrain.spinal.toFixed(0)}%</span></div>
                                    <div className="h-1 w-full bg-zinc-900 rounded-full overflow-hidden"><div className={`h-full transition-all duration-300 ${localDrain.spinal > 80 ? 'bg-red-500' : 'bg-orange-500'}`} style={{width: `${localDrain.spinal}%`}}></div></div>
                                </div>
                            </div>
                        </div>
                    )}
                    
                    {/* BARRA DE HERRAMIENTAS ADICIONAL */}
                    <div className="flex justify-between items-center mb-2 border-b border-white/5 pb-2">
                         <label className="flex items-center gap-2 cursor-pointer group">
                             <input type="checkbox" checked={exercise.isCompetitionLift} onChange={(e) => onExerciseChange('isCompetitionLift', e.target.checked)} className="rounded border-zinc-700 bg-black text-yellow-500 focus:ring-0 w-3 h-3" />
                             <span className={`text-[9px] font-black uppercase tracking-widest transition-colors ${exercise.isCompetitionLift ? 'text-yellow-500' : 'text-zinc-600 group-hover:text-white'}`}>Modo Competición / Tarima</span>
                         </label>
                    </div>

                    <div className="w-full relative">
                        {exercise.isCompetitionLift && (
                            <div className="mb-2 px-3 py-2 bg-yellow-500/10 border border-yellow-500/30 rounded-xl flex items-center gap-2">
                                <TrophyIcon size={14} className="text-yellow-500 shrink-0" />
                                <span className="text-[9px] font-bold text-yellow-400 uppercase tracking-widest">Movimiento de Competición — Las luces de jueceo se activan al entrenar en vivo</span>
                            </div>
                        )}
                        {/* --- MODO STANDARD (Scroll Horizontal de Tarjetas) --- */}
                            <div className="flex gap-2 overflow-x-auto pb-8 custom-scrollbar snap-x items-stretch relative z-20 pointer-events-auto">
                                {exercise.sets.map((set, setIndex) => {
                                    const isAmrap = set.isAmrap || set.intensityMode === 'amrap';
                                    let estimatedLoad: number | null = null;
                                    if (exercise.trainingMode === 'percent' && exercise.reference1RM && set.targetPercentageRM) {
                                        estimatedLoad = Math.round((exercise.reference1RM * set.targetPercentageRM) / 100);
                                    }

                                    return (
                                        <div key={set.id} className={`shrink-0 w-36 bg-[#0a0a0a] border ${isAmrap ? 'border-yellow-500/30 shadow-[0_0_15px_rgba(250,204,21,0.1)]' : 'border-zinc-800'} rounded-2xl p-3 snap-center flex flex-col justify-between relative group`}>
                                            {/* Cabecera Tarjetita */}
                                            <div className="flex justify-between items-center mb-3 pb-2 border-b border-white/5">
                                                <span className="font-black text-zinc-500 text-[10px] bg-black px-2 py-0.5 rounded-full border border-zinc-800">S{setIndex + 1}</span>
                                                <button type="button" onClick={() => onRemoveSet(setIndex)} className="text-zinc-600 hover:text-red-500 opacity-0 group-hover:opacity-100 transition-opacity"><XIcon size={12}/></button>
                                            </div>
                                            
                                            {/* Reps/Tiempo */}
                                            <div className="flex flex-col items-center mb-3 bg-black/50 p-2 rounded-xl">
                                                <span className="text-[8px] text-zinc-500 font-bold uppercase tracking-widest mb-1">{exercise.trainingMode === 'time' ? 'Segundos' : isAmrap ? 'Mínimo Reps' : 'Reps Target'}</span>
                                                {exercise.trainingMode === 'time' ? (
                                                    <input type="number" value={set.targetDuration ?? ''} onChange={e => handleSetChange(setIndex, 'targetDuration', parseInt(e.target.value))} className="w-full text-center bg-transparent text-xl font-black p-0 border-none focus:ring-0 text-white" placeholder="0"/>
                                                ) : (
                                                    <input type="number" value={set.targetReps ?? ''} onChange={e => handleSetChange(setIndex, 'targetReps', parseInt(e.target.value))} placeholder="0" className={`w-full text-center bg-transparent text-xl font-black p-0 border-none focus:ring-0 ${isAmrap ? 'text-yellow-400' : 'text-white'}`}/>
                                                )}
                                            </div>

                                            {/* Intensidad & Carga */}
                                            <div className="flex items-center justify-between gap-1">
                                                {isAmrap ? (
                                                    <div className="flex-1 bg-yellow-900/20 border border-yellow-600/30 rounded p-1.5 flex justify-center text-center"><span className="text-[8px] font-black text-yellow-500 uppercase leading-none">{set.isCalibrator ? 'Calibrador' : 'Al Fallo'}</span></div>
                                                ) : (
                                                    <div className="flex flex-col flex-1 bg-zinc-900 p-1.5 rounded-lg border border-zinc-800">
                                                        <select value={set.intensityMode || 'rpe'} onChange={e => handleSetChange(setIndex, 'intensityMode', e.target.value as any)} className="bg-transparent text-[8px] text-zinc-500 font-bold border-none focus:ring-0 uppercase p-0 mb-0.5"><option value="rpe">RPE</option><option value="rir">RIR</option><option value="failure">FAIL</option></select>
                                                        {set.intensityMode !== 'failure' && (
                                                            <input type="number" step="0.5" value={set.intensityMode === 'rir' ? (set.targetRIR ?? '') : (set.targetRPE ?? '')} onChange={e => handleSetChange(setIndex, set.intensityMode === 'rir' ? 'targetRIR' : 'targetRPE', parseFloat(e.target.value))} className="w-full bg-transparent text-sm font-bold text-white focus:border-white p-0 border-none" placeholder="-"/>
                                                        )}
                                                    </div>
                                                )}

                                                {exercise.trainingMode === 'percent' && (
                                                    <div className="flex flex-col flex-1 bg-blue-900/10 p-1.5 rounded-lg border border-blue-500/20 items-center">
                                                        <div className="flex items-center justify-center">
                                                            <input type="number" value={set.targetPercentageRM ?? ''} onChange={e => handleSetChange(setIndex, 'targetPercentageRM', parseFloat(e.target.value))} className="w-8 text-center bg-transparent text-sm font-black p-0 border-none focus:ring-0 text-blue-400" placeholder="%"/>
                                                        </div>
                                                        {estimatedLoad !== null && <span className="text-[8px] font-mono text-zinc-400 mt-0.5">{estimatedLoad}kg</span>}
                                                    </div>
                                                )}
                                                
                                                <button type="button" onClick={() => {
                                                    const newVal = !isAmrap;
                                                    if (newVal) { setPendingAmrapSetIndex(setIndex); return; }
                                                    handleSetChange(setIndex, { isAmrap: false, intensityMode: 'rpe' });
                                                }} className={`p-2 rounded-lg ml-1 border ${isAmrap ? 'bg-yellow-500/20 border-yellow-500/50 text-yellow-400' : 'bg-black border-zinc-800 text-zinc-600 hover:text-white'}`}><FlameIcon size={12} /></button>
                                            </div>
                                        </div>
                                    )
                                })}
                                {!hideAddSetButton && (
                                    <div className="shrink-0 w-24 flex flex-col gap-2 justify-center pl-2">
                                        <button onClick={() => onAddSet()} className="w-full h-12 border border-zinc-800 bg-zinc-900/50 hover:bg-zinc-900 rounded-xl text-[10px] font-black uppercase text-zinc-400 hover:text-white transition-all flex items-center justify-center shadow-inner"><PlusIcon size={16}/></button>
                                        <button onClick={() => setIsWarmupModalOpen(true)} className="w-full h-10 border border-zinc-800 bg-transparent hover:bg-zinc-900 rounded-xl text-[8px] font-black uppercase text-zinc-500 hover:text-white transition-all">Aprox</button>
                                    </div>
                                )}
                            </div>
                    </div>
                </div>
            </details>
        </div>
    );
});

const MemoizedExerciseCard = React.memo(ExerciseCard);

// --- SUPERSET MANAGER COMPONENT ---
const SupersetManagementBlock: React.FC<{
    exercises: { ex: Exercise, index: number }[];
    onUpdateSession: (updater: (draft: Session) => void) => void;
    partIndex: number;
    partColor?: string;
    onReorderExercise: (partIdx: number, exIdx: number, dir: 'up'|'down') => void;
    onToggleSelect: (id: string) => void;
    selectedIds: Set<string>;
    isSelectionMode: boolean;
    onUnlink: (partIdx: number, exIdx: number) => void;
    onLinkNext: (partIdx: number, exIdx: number) => void;
    culpritIds?: Set<string>;
}> = ({ exercises, onUpdateSession, partIndex, partColor, onReorderExercise, onToggleSelect, selectedIds, isSelectionMode, onUnlink, onLinkNext, culpritIds }) => {
    
    // Derived state: Number of rounds is determined by the max sets of any exercise in the group
    const rounds = useMemo(() => Math.max(...exercises.map(e => e.ex.sets.length)), [exercises]);

    const handleAddRound = () => {
        onUpdateSession(draft => {
            if (!draft.parts?.[partIndex]) return;
            exercises.forEach(({ index }) => {
                draft.parts![partIndex].exercises[index].sets.push({ 
                    id: crypto.randomUUID(), 
                    targetReps: 8, 
                    intensityMode: 'rpe', 
                    targetRPE: 8 
                });
            });
        });
    };

    const handleRemoveRound = () => {
         if (rounds <= 1) return;
         onUpdateSession(draft => {
            if (!draft.parts?.[partIndex]) return;
            exercises.forEach(({ index }) => {
                if (draft.parts![partIndex].exercises[index].sets.length > 0) {
                    draft.parts![partIndex].exercises[index].sets.pop();
                }
            });
        });
    };

    // We designate the rest time of the *last* exercise as "Round Rest"
    // and the rest time of *other* exercises as "Transition Rest" (assuming uniform transition for now)
    const lastExIndex = exercises.length - 1;
    const roundRest = exercises[lastExIndex].ex.restTime || 90;
    const transitionRest = exercises[0].ex.restTime || 0; // Grab from first as representative

    const handleRestChange = (type: 'round' | 'transition', val: number) => {
        onUpdateSession(draft => {
            if (!draft.parts?.[partIndex]) return;
            exercises.forEach(({ index }, i) => {
                if (type === 'round' && i === lastExIndex) {
                    draft.parts![partIndex].exercises[index].restTime = val;
                } else if (type === 'transition' && i !== lastExIndex) {
                    draft.parts![partIndex].exercises[index].restTime = val;
                }
            });
        });
    }

    return (
        <div className="relative pl-3 border-l-2 border-orange-500/50 my-6 py-2 bg-orange-500/[0.02] rounded-r-xl">
            <div className="flex justify-between items-center mb-3 px-1">
                <div className="flex items-center gap-2">
                    <ZapIcon size={16} className="text-orange-400"/>
                    <span className="text-orange-400 font-black text-xs uppercase tracking-widest">Biserie / Circuito</span>
                </div>
                <div className="flex items-center gap-3">
                     <div className="flex items-center gap-2 bg-slate-900/80 px-2 py-1 rounded-lg border border-white/5">
                        <span className="text-[9px] font-bold text-slate-500 uppercase">Rondas</span>
                        <button onClick={handleRemoveRound} className="text-slate-400 hover:text-white px-1 font-bold">-</button>
                        <span className="text-sm font-black text-white w-4 text-center">{rounds}</span>
                        <button onClick={handleAddRound} className="text-slate-400 hover:text-white px-1 font-bold">+</button>
                     </div>
                </div>
            </div>

            <div className="space-y-4">
                {exercises.map(({ ex, index: ei }, i) => (
                    <MemoizedExerciseCard 
                        key={ex.id} 
                        exercise={ex} 
                        categoryColor={partColor} 
                        isInSuperset={true} 
                        isSupersetLast={i === exercises.length - 1} 
                        onExerciseChange={(f, v) => { onUpdateSession(d => { if (typeof f === 'string') (d.parts![partIndex].exercises[ei] as any)[f] = v; else d.parts![partIndex].exercises[ei] = {...d.parts![partIndex].exercises[ei], ...f}; }); }} 
                        onSetChange={(si, f, v) => { onUpdateSession(d => { if (typeof f === 'string') (d.parts![partIndex].exercises[ei].sets[si] as any)[f] = v; else d.parts![partIndex].exercises[ei].sets[si] = {...d.parts![partIndex].exercises[ei].sets[si], ...f}; }); }} 
                        onAddSet={() => { /* Handled globally by block */ }} 
                        onRemoveSet={(si) => onUpdateSession(d => { d.parts![partIndex].exercises[ei].sets.splice(si, 1); })} 
                        onRemoveExercise={() => onUpdateSession(d => { d.parts![partIndex].exercises.splice(ei, 1); })} 
                        onReorder={(dir) => onReorderExercise(partIndex, ei, dir)} 
                        onLinkNext={() => onLinkNext(partIndex, ei)} 
                        onUnlink={() => onUnlink(partIndex, ei)} 
                        isFirst={partIndex === 0 && ei === 0} 
                        isLast={false} // Logic handled by parent usually but less critical here
                        isSelectionMode={isSelectionMode} 
                        isSelected={selectedIds.has(ex.id)} 
                        isJunkVolumeCulprit={culpritIds?.has(ex.id)}
                        onToggleSelect={() => onToggleSelect(ex.id)}
                        hideAddSetButton={true} // Hide individual add set
                    />
                ))}
            </div>

            <div className="mt-3 grid grid-cols-2 gap-3 px-1">
                <div className="bg-slate-900/50 p-2 rounded-lg border border-white/5 flex flex-col items-center">
                    <span className="text-[8px] font-bold text-slate-500 uppercase tracking-wider mb-1">Descanso Interno</span>
                    <input type="number" step="10" value={transitionRest} onChange={e => handleRestChange('transition', parseInt(e.target.value))} className="w-16 bg-transparent text-center font-bold text-white text-sm focus:ring-0 border-b border-slate-700"/>
                </div>
                 <div className="bg-slate-900/50 p-2 rounded-lg border border-white/5 flex flex-col items-center">
                    <span className="text-[8px] font-bold text-slate-500 uppercase tracking-wider mb-1">Descanso de Ronda</span>
                    <input type="number" step="10" value={roundRest} onChange={e => handleRestChange('round', parseInt(e.target.value))} className="w-16 bg-transparent text-center font-bold text-white text-sm focus:ring-0 border-b border-slate-700"/>
                </div>
            </div>
        </div>
    );
};

const SessionEditorComponent: React.FC<SessionEditorProps> = ({ onSave, onCancel, existingSessionInfo, isOnline, settings, saveTrigger, addExerciseTrigger, exerciseList }) => {
    const { isDirty, setIsDirty, addToast, programs = [] } = useAppContext(); // <--- PROTECCIÓN 1
    const [isBgModalOpen, setIsBgModalOpen] = useState(false);
    const [isAnalysisExpanded, setIsAnalysisExpanded] = useState(false);
    const [openColorPickerIndex, setOpenColorPickerIndex] = useState<number | null>(null);
    const [collapsedParts, setCollapsedParts] = useState<Record<string, boolean>>({});

    // Estados para Selección Múltiple y Triggers
    const [selectedExerciseIds, setSelectedExerciseIds] = useState<Set<string>>(new Set());
    const [bulkScope, setBulkScope] = useState<'session' | 'manual'>('manual');
    const lastSaveTrigger = useRef(saveTrigger);
    const lastAddTrigger = useRef(addExerciseTrigger);
    const infoRef = useRef(existingSessionInfo);
    useEffect(() => { infoRef.current = existingSessionInfo; }, [existingSessionInfo]);

    // --- NUEVO: MOTOR DE BÚFER SEMANAL ---
    // Cargamos TODA la semana si venimos de un programa
    const [weekSessions, setWeekSessions] = useState<Session[]>(() => {
        let sessions: Session[] = [];
        if (existingSessionInfo && programs && programs.length > 0) {
            const prog = programs.find(p => p.id === existingSessionInfo.programId);
            if (prog) {
                for (const mac of prog.macrocycles) {
                    for (const blk of (mac.blocks || [])) {
                        for (const mes of blk.mesocycles) {
                            const w = mes.weeks.find(we => we.id === existingSessionInfo.weekId);
                            if (w && w.sessions) {
                                sessions = JSON.parse(JSON.stringify(w.sessions));
                                break;
                            }
                        }
                        if (sessions.length > 0) break;
                    }
                    if (sessions.length > 0) break;
                }
            }
        }
        if (sessions.length === 0) {
            const initial = JSON.parse(JSON.stringify(existingSessionInfo?.session || { id: crypto.randomUUID(), name: '', description: '', exercises: [], warmup: [] }));
            if (!initial.parts) initial.parts = [{ id: crypto.randomUUID(), name: 'Principal', exercises: initial.exercises || [] }];
            sessions = [initial];
        }
        // Asegurar dayOfWeek en todas las sesiones para que el roadmap filtre correctamente
        const firstDay = getOrderedDaysOfWeek(settings.startWeekOn)[0]?.value ?? 1;
        return sessions.map(s => (s.dayOfWeek === undefined ? { ...s, dayOfWeek: firstDay } : s));
    });

    const [activeSessionId, setActiveSessionId] = useState<string>(existingSessionInfo?.session.id || weekSessions[0].id);
    const [modifiedSessionIds, setModifiedSessionIds] = useState<Set<string>>(new Set());
    const [isMultiSaveModalOpen, setIsMultiSaveModalOpen] = useState(false);

    // NUEVOS ESTADOS ROADMAP, GUARDADO Y REGLAS
    const [emptyDaySelected, setEmptyDaySelected] = useState<number | null>(null);
    const [globalSessionAlerts, setGlobalSessionAlerts] = useState<{ muscle: string; volume: number; threshold: number; failRatio: number; message?: string }[]>([]);
    const [notifiedAlerts, setNotifiedAlerts] = useState<Set<string>>(new Set());
    const [isTransferModalOpen, setIsTransferModalOpen] = useState(false);

    // Derivamos la "sesión actual" del búfer en tiempo real aquí arriba para que la lógica de volumen basura la pueda leer
    const session = useMemo(() => weekSessions.find(s => s.id === activeSessionId) || weekSessions[0], [weekSessions, activeSessionId]);

    useEffect(() => {
        globalSessionAlerts.forEach(alert => {
            if (!notifiedAlerts.has(alert.muscle)) {
                addToast(`¡Cuidado! Alerta de volumen en ${alert.muscle}. Revisa la tarjeta roja.`, "danger");
                setNotifiedAlerts(prev => new Set(prev).add(alert.muscle));
            }
        });
    }, [globalSessionAlerts, notifiedAlerts, addToast]);

    // Listener para alertas AUGE (con cleanup para evitar memory leaks)
    useEffect(() => {
        const handler = (e: Event & { detail?: any }) => setGlobalSessionAlerts(e.detail || []);
        window.addEventListener('augeAlertsUpdated', handler);
        return () => window.removeEventListener('augeAlertsUpdated', handler);
    }, []);

    // ESTADO PARA ALERTAS DE CINETICA AVANZADA
    const [neuralAlerts, setNeuralAlerts] = useState<{type: string, message: string, severity: 'warning'|'critical'}[]>([]);

    const culpritExerciseIds = useMemo(() => {
        const culprits = new Set<string>();
        const volMap: Record<string, number> = {};
        let totalSpinalLoad = 0;
        let elbowStress = 0;
        let kneeStress = 0;
        
        const limits = settings?.volumeLimits || {};
        
        const allEx = [...(session?.exercises || [])];
        (session?.parts || []).forEach(p => allEx.push(...p.exercises));
        
        allEx.forEach(ex => {
            const info = exerciseList.find(e => e.id === ex.exerciseDbId || e.name === ex.name);
            if (!info) return;
            
            let isCulprit = false;
            const validSets = ex.sets?.filter(s => (s as any).type !== 'warmup') || [];
            if (validSets.length === 0) return;

            validSets.forEach(set => {
                const volMult = getEffectiveVolumeMultiplier(set);

                info.involvedMuscles.forEach(m => {
                    const parent = normalizeMuscleGroup(m.muscle);
                    const hyperFactor = HYPERTROPHY_ROLE_MULTIPLIERS[m.role] ?? 0;
                    const addedVol = hyperFactor * volMult;
                    const limit = limits[parent]?.maxSession || 6;
                    
                    if ((volMap[parent] || 0) + addedVol > limit) {
                        if ((volMap[parent] || 0) <= limit) isCulprit = true;
                    }
                    volMap[parent] = (volMap[parent] || 0) + addedVol;
                });
                
                // 2. Detección de Carga Espinal
                if ((info.axialLoadFactor || 0) > 0) {
                    totalSpinalLoad += (info.axialLoadFactor || 0);
                }
            });

            // 3. Detección de Toxicidad Articular
            const exName = info.name.toLowerCase();
            const count = validSets.length;
            if (exName.includes('press francés') || exName.includes('rompecráneos') || exName.includes('extensión en polea')) elbowStress += count;
            if (exName.includes('extensión de cuádriceps') || exName.includes('sissy')) kneeStress += count;
            
            if (isCulprit) culprits.add(ex.id);
        });

        const newAlerts: {type: string, message: string, severity: 'warning'|'critical'}[] = [];
        if (totalSpinalLoad > 15) {
            newAlerts.push({ type: 'Espinal', severity: 'critical', message: 'Carga Axial Crítica: Estás acumulando demasiada compresión en la zona lumbar. Considera cambiar ejercicios libres por máquinas para salvar tu espalda baja.' });
        }
        if (elbowStress > 8) {
            newAlerts.push({ type: 'Articular', severity: 'warning', message: 'Estrés de Codo: Alta acumulación de trabajo aislado de tríceps. Sugerimos diversificar ángulos o bajar la intensidad para evitar tendinitis.' });
        }
        if (kneeStress > 8) {
            newAlerts.push({ type: 'Articular', severity: 'warning', message: 'Fricción Patelar: Demasiada cizalla en la rodilla por extensiones puras. Asegura un buen calentamiento previo.' });
        }
        
        setNeuralAlerts(newAlerts);
        return culprits;
    }, [session, settings.volumeLimits, exerciseList]);
    const [transferMode, setTransferMode] = useState<'export'|'import'>('export');
    const [transferTargetId, setTransferTargetId] = useState<string>('');
    const [isRulesModalOpen, setIsRulesModalOpen] = useState(false);
    const [isHistoryModalOpen, setIsHistoryModalOpen] = useState(false);
    const [sessionHistory, setSessionHistory] = useState<Session[]>([]);
    
    const [applyToWholeBlock, setApplyToWholeBlock] = useState(false);

    // Escuchador para conectar con la TabBar nativa de App.tsx
    useEffect(() => {
        const openRules = () => setIsRulesModalOpen(true);
        const openHistory = () => setIsHistoryModalOpen(true);
        window.addEventListener('openSessionRules', openRules);
        window.addEventListener('openSessionHistory', openHistory);
        return () => {
            window.removeEventListener('openSessionRules', openRules);
            window.removeEventListener('openSessionHistory', openHistory);
        };
    }, []);
    const [isSingleSaveModalOpen, setIsSingleSaveModalOpen] = useState(false);
    const [blockScopeSelection, setBlockScopeSelection] = useState<Record<string, boolean>>({});
    
    const sessionRef = useRef(session);
    const weekSessionsRef = useRef(weekSessions);
    const modifiedIdsRef = useRef(modifiedSessionIds);
    const onSaveRef = useRef(onSave);
    
    useEffect(() => { sessionRef.current = session; }, [session]);
    useEffect(() => { weekSessionsRef.current = weekSessions; }, [weekSessions]);
    useEffect(() => { modifiedIdsRef.current = modifiedSessionIds; }, [modifiedSessionIds]);
    useEffect(() => { onSaveRef.current = onSave; }, [onSave]);

    const updateSession = useCallback((updater: (draft: Session) => void) => {
        setWeekSessions(prev => prev.map(s => {
            if (s.id === activeSessionId) {
                const draft = JSON.parse(JSON.stringify(s));
                updater(draft);
                setSessionHistory(hist => [...hist.slice(-15), JSON.parse(JSON.stringify(draft))]);
                return draft;
            }
            return s;
        }));
        setModifiedSessionIds(prev => new Set(prev).add(activeSessionId));
        setIsDirty(true);
    }, [activeSessionId, setIsDirty]);

    const handleDayClick = (dayValue: number, daySessions: Session[]) => {
        if (daySessions.length > 0) {
            setActiveSessionId(daySessions[0].id);
            setEmptyDaySelected(null);
        } else {
            setEmptyDaySelected(dayValue);
            setActiveSessionId('empty');
        }
    };

    const handleCreateFirstSession = (dayValue: number) => {
        const newSession: Session = {
            id: crypto.randomUUID(),
            name: `Sesión Día ${dayValue}`,
            dayOfWeek: dayValue,
            exercises: [],
            parts: [{ id: crypto.randomUUID(), name: 'Principal', exercises: [] }]
        };
        setWeekSessions(prev => [...prev, newSession]);
        setActiveSessionId(newSession.id);
        setEmptyDaySelected(null);
        setModifiedSessionIds(prev => new Set(prev).add(newSession.id));
        setIsDirty(true);
    };

    // Función robusta para evitar colisiones de IDs en React Keys y Base de Datos
    const generateSafeSessionClone = (originalSession: Session, targetDay: number): Session => {
        const clone = JSON.parse(JSON.stringify(originalSession));
        clone.id = crypto.randomUUID();
        clone.dayOfWeek = targetDay;
        clone.scheduleLabel = undefined; // Limpiamos etiquetas específicas
        
        if (clone.exercises) {
            clone.exercises = clone.exercises.map((ex: any) => ({
                ...ex,
                id: crypto.randomUUID(),
                sets: Array.isArray(ex.sets) ? ex.sets.map((set: any) => ({ ...set, id: crypto.randomUUID() })) : []
            }));
        }
        if (clone.parts) {
            clone.parts = clone.parts.map((part: any) => ({
                ...part,
                id: crypto.randomUUID(),
                exercises: Array.isArray(part.exercises) ? part.exercises.map((ex: any) => ({
                    ...ex,
                    id: crypto.randomUUID(),
                    sets: Array.isArray(ex.sets) ? ex.sets.map((set: any) => ({ ...set, id: crypto.randomUUID() })) : []
                })) : []
            }));
        }
        return clone;
    };

    const executeFinalSave = async (sessionsToSave: Session[]) => {
        const currentOnSave = onSaveRef.current;
        const currentInfo = infoRef.current;

        const finalSessions = sessionsToSave.map(s => {
            const sCopy = { ...s };
            // Inyectamos bandera temporal para el AppContext
            if (applyToWholeBlock || blockScopeSelection[s.id]) {
                (sCopy as any)._applyToBlock = true;
            }
            return sCopy;
        });

        if (currentInfo) {
            const payload = finalSessions.length === 1 ? finalSessions[0] : finalSessions;
            // @ts-ignore
            currentOnSave(payload, currentInfo.programId, currentInfo.macroIndex, currentInfo.mesoIndex, currentInfo.weekId);
       } else {
            currentOnSave(finalSessions[0]); 
       }
        await storageService.remove(SESSION_DRAFT_KEY);
        setIsDirty(false);
        setIsMultiSaveModalOpen(false);
        setIsSingleSaveModalOpen(false);
    };

    const handleSave = useCallback(async () => {
        if (activeSessionId !== 'empty' && (!sessionRef.current.name || !sessionRef.current.name.trim())) {
            addToast("La sesión debe tener un nombre antes de guardar.", "danger"); return;
        }

        if (modifiedIdsRef.current.size > 1 && existingSessionInfo) {
            setIsMultiSaveModalOpen(true);
        } else if (existingSessionInfo && existingSessionInfo.macroIndex !== undefined) {
            setIsSingleSaveModalOpen(true);
        } else {
            executeFinalSave([sessionRef.current]);
        }
    }, [activeSessionId, addToast, existingSessionInfo]);

    useEffect(() => {
        if (saveTrigger !== lastSaveTrigger.current) {
            lastSaveTrigger.current = saveTrigger;
            handleSave();
        }
    }, [saveTrigger, handleSave]);


    const togglePartCollapse = (partId: string) => {
        setCollapsedParts(prev => ({
            ...prev,
            [partId]: !prev[partId]
        }));
    };

    // ... (rest of the component logic remains largely the same, ensuring setSession updates correctly)
    
    const handleAddExercise = useCallback((partIndex: number) => {
        const newEx: Exercise = { id: crypto.randomUUID(), name: '', sets: Array.from({ length: 3 }).map(() => ({ id: crypto.randomUUID(), targetReps: 10, intensityMode: 'rpe', targetRPE: 8 })), restTime: 90, isFavorite: false, trainingMode: 'reps' };
        updateSession(draft => { if (!draft.parts) draft.parts = []; if (!draft.parts[partIndex]) return; draft.parts[partIndex].exercises.push(newEx); });
    }, [updateSession]);

    useEffect(() => {
        if (addExerciseTrigger !== lastAddTrigger.current) {
            lastAddTrigger.current = addExerciseTrigger;
            const lastPartIndex = (sessionRef.current.parts?.length || 1) - 1;
            handleAddExercise(lastPartIndex >= 0 ? lastPartIndex : 0);
        }
    }, [addExerciseTrigger, handleAddExercise]);

    const [draggedPartIndex, setDraggedPartIndex] = useState<number | null>(null);

    // ... (Helper functions: groupExercises, etc.) ...
    const groupExercises = (exercises: Exercise[]) => {
        const groups: Array<{ type: 'single', ex: Exercise, index: number } | { type: 'superset', items: { ex: Exercise, index: number }[], id: string }> = [];
        let currentSuperset: { ex: Exercise, index: number }[] | null = null;
        let currentSupersetId: string | null = null;
        exercises.forEach((ex, index) => {
            if (ex.supersetId) {
                if (currentSupersetId === ex.supersetId) currentSuperset?.push({ ex, index });
                else { if (currentSuperset) groups.push({ type: 'superset', items: currentSuperset, id: currentSupersetId! }); currentSuperset = [{ ex, index }]; currentSupersetId = ex.supersetId; }
            } else { if (currentSuperset) groups.push({ type: 'superset', items: currentSuperset, id: currentSupersetId! }); currentSuperset = null; currentSupersetId = null; groups.push({ type: 'single', ex, index }); }
        });
        if (currentSuperset) groups.push({ type: 'superset', items: currentSuperset, id: currentSupersetId! });
        return groups;
    };
    
    const handleToggleSelectExercise = useCallback((exerciseId: string) => { setSelectedExerciseIds(prev => { const newSet = new Set(prev); if (newSet.has(exerciseId)) newSet.delete(exerciseId); else newSet.add(exerciseId); return newSet; }); }, []);
    
    // ... (Bulk Apply logic unchanged) ...

    const { gradient } = useImageGradient(session.background?.type === 'image' ? session.background.value : undefined);
    const headerStyle: React.CSSProperties = { backgroundImage: session.background?.type === 'image' ? `url(${session.background.value})` : gradient };
    const [useCustomLabel, setUseCustomLabel] = useState(!!session.scheduleLabel);

    const getFilterString = () => {
        if (!session.coverStyle?.filters) return 'none';
        const f = session.coverStyle.filters;
        return `contrast(${f.contrast}%) saturate(${f.saturation}%) brightness(${f.brightness}%) grayscale(${f.grayscale}%) sepia(${f.sepia}%)`;
    };

    const handleReorderExercise = (partIndex: number, exIndex: number, direction: 'up' | 'down') => {
        updateSession(draft => {
            const part = draft.parts?.[partIndex];
            if (!part) return;
            if (part.exercises[exIndex].supersetId) part.exercises[exIndex].supersetId = undefined;
            if (direction === 'up') {
                if (exIndex > 0) [part.exercises[exIndex], part.exercises[exIndex - 1]] = [part.exercises[exIndex - 1], part.exercises[exIndex]];
            } else {
                if (exIndex < part.exercises.length - 1) [part.exercises[exIndex], part.exercises[exIndex + 1]] = [part.exercises[exIndex + 1], part.exercises[exIndex]];
            }
        });
    };
    const handleLinkWithNext = (partIndex: number, exerciseIndex: number) => { updateSession(draft => { const part = draft.parts?.[partIndex]; if (!part || !part.exercises[exerciseIndex + 1]) return; const currentEx = part.exercises[exerciseIndex]; const nextEx = part.exercises[exerciseIndex + 1]; const newId = currentEx.supersetId || crypto.randomUUID(); currentEx.supersetId = newId; nextEx.supersetId = newId; }); };
    const handleUnlink = (partIndex: number, exerciseIndex: number) => { updateSession(draft => { const part = draft.parts?.[partIndex]; if (!part) return; part.exercises[exerciseIndex].supersetId = undefined; }); }
    
    const handleAddPart = () => { updateSession(draft => { if (!draft.parts) draft.parts = []; const newIndex = draft.parts.length; draft.parts.push({ id: crypto.randomUUID(), name: "Nueva Sección", exercises: [], color: PRESET_PART_COLORS[newIndex % PRESET_PART_COLORS.length] }); }); };
    
    const handleRemovePart = (index: number, e: React.MouseEvent) => { 
        e.stopPropagation();
        if (window.confirm("¿Eliminar sección?")) { 
            updateSession(draft => { 
                if (!draft.parts) return; 
                draft.parts.splice(index, 1); 
            }); 
        } 
    };
    
    const handleDragStart = (index: number) => setDraggedPartIndex(index);
    const handleDragOver = (e: React.DragEvent) => e.preventDefault();
    const handleDrop = (targetIndex: number) => { if (draggedPartIndex === null || draggedPartIndex === targetIndex) return; updateSession(draft => { if (!draft.parts) return; const partToMove = draft.parts[draggedPartIndex]; draft.parts.splice(draggedPartIndex, 1); draft.parts.splice(targetIndex, 0, partToMove); }); setDraggedPartIndex(null); };

    const orderedDays = getOrderedDaysOfWeek(settings.startWeekOn);

    // --- BLOQUE 1: REEMPLAZAR EL RETURN DEL COMPONENTE PRINCIPAL ---
    return (
        <div className="fixed inset-0 z-[9999] bg-black text-white animate-slide-up overflow-y-auto custom-scrollbar flex flex-col">
            
            {/* --- HEADER (High Contrast Black) --- */}
            <div className="relative flex-shrink-0 bg-black border-b border-white/20 z-20 min-h-[140px] pt-8">
                {/* BOTÓN CERRAR FULLSCREEN */}
                <button onClick={onCancel} className="absolute top-4 right-4 z-50 p-2 bg-black/50 backdrop-blur-md rounded-full text-zinc-400 hover:text-white transition-colors border border-white/10">
                    <XIcon size={18} />
                </button>

                <div className="absolute inset-0 z-0 opacity-30" style={{ ...headerStyle, backgroundSize: 'cover', filter: session.coverStyle ? getFilterString() : 'none' }}></div>
                <div className="absolute inset-0 z-0 bg-gradient-to-b from-black/20 via-black/60 to-black"></div>

                <div className="relative z-10 p-5 space-y-4">
                    <input
                        type="text"
                        value={session.name}
                        onChange={e => updateSession(d => { d.name = e.target.value; })}
                        placeholder="NOMBRE DE LA SESIÓN"
                        className="text-3xl font-black text-white bg-transparent border-none focus:ring-0 w-[85%] p-0 leading-tight tracking-tighter uppercase placeholder-zinc-700"
                    />
                    <div className="flex gap-2 items-center">
                        <input
                            type="text"
                            value={session.description}
                            onChange={e => updateSession(d => { d.description = e.target.value; })}
                            placeholder="Añade una descripción..."
                            className="text-xs text-zinc-300 bg-transparent border-none focus:ring-0 flex-grow p-0 placeholder-zinc-600 font-medium"
                        />
                        <button onClick={() => setIsBgModalOpen(true)} className="p-2 rounded-full border border-white/10 hover:bg-white hover:text-black transition-all text-zinc-400">
                            <ImageIcon size={16} />
                        </button>
                    </div>

                    {/* ACCIONES DE SESIÓN (Transferir, Modo Básico y Varita Mágica) */}
                    <div className="flex items-center justify-between pt-1">
                        <div className="flex items-center gap-3">
                            {activeSessionId !== 'empty' && (
                                <button onClick={() => setIsTransferModalOpen(true)} className="px-4 py-1.5 bg-white text-black hover:scale-105 rounded-lg text-[10px] font-black uppercase transition-all flex items-center gap-1">
                                    <LayersIcon size={14} /> Transferir
                                </button>
                            )}
                        </div>

                        {/* TOGGLE VARITA MÁGICA Y MEET DAY */}
                        <div className="flex items-center gap-2">
                            <button
                                onClick={() => updateSession(d => { d.isMeetDay = !d.isMeetDay; })}
                                className={`p-2 rounded-lg border transition-all ${session.isMeetDay ? 'bg-yellow-500/20 border-yellow-500 text-yellow-400 shadow-[0_0_15px_rgba(250,204,21,0.3)]' : 'bg-black border-white/10 text-zinc-500 hover:text-white'}`}
                                title="Modo Competición (Game Day)"
                            >
                                <TrophyIcon size={16} />
                            </button>
                            <button
                                onClick={() => { setBulkScope(bulkScope === 'manual' ? 'session' : 'manual'); setIsAnalysisExpanded(true); }}
                                className={`p-2 rounded-lg border transition-all flex items-center gap-1 ${bulkScope === 'manual' && isAnalysisExpanded ? 'bg-blue-600 border-blue-400 text-white shadow-[0_0_15px_rgba(37,99,235,0.4)]' : 'bg-black border-white/10 text-zinc-500 hover:text-white'}`}
                                title="Edición Lote (Varita Mágica)"
                            >
                                <WandIcon size={16} />
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            {/* MODAL DE FONDO */}
            {isBgModalOpen && (
                <BackgroundEditorModal
                    isOpen={isBgModalOpen}
                    onClose={() => setIsBgModalOpen(false)}
                    onSave={(background, coverStyle) => {
                        updateSession(d => {
                            if (background) d.background = background;
                            if (coverStyle) d.coverStyle = coverStyle;
                        });
                    }}
                    initialBackground={session.background}
                    initialCoverStyle={session.coverStyle}
                    previewTitle={session.name}
                    isOnline={isOnline}
                />
            )}

            {/* MODALES DE ACCIÓN CONTEXTUAL */}
            <Modal isOpen={isTransferModalOpen} onClose={() => setIsTransferModalOpen(false)} title="Transferencia de Sesión">
                <div className="p-2 space-y-4">
                    <div className="flex gap-2 mb-4 bg-zinc-900 p-1 rounded-xl">
                        <button onClick={() => setTransferMode('export')} className={`flex-1 py-2 text-xs font-bold rounded-lg transition-all ${transferMode === 'export' ? 'bg-white text-black' : 'text-zinc-500'}`}>Exportar (Copiar a...)</button>
                        <button onClick={() => setTransferMode('import')} className={`flex-1 py-2 text-xs font-bold rounded-lg transition-all ${transferMode === 'import' ? 'bg-white text-black' : 'text-zinc-500'}`}>Importar (Recibir de...)</button>
                    </div>
                    <p className="text-xs text-zinc-300">Selecciona la sesión de {transferMode === 'export' ? 'destino' : 'origen'}:</p>
                    <div className="grid grid-cols-2 gap-2 max-h-40 overflow-y-auto custom-scrollbar">
                        {weekSessions.filter(s => s.id !== activeSessionId).map(s => (
                            <button key={s.id} onClick={() => setTransferTargetId(s.id)} className={`p-3 text-left rounded-xl border text-xs font-bold transition-all ${transferTargetId === s.id ? 'bg-blue-500/20 border-blue-500 text-white' : 'bg-black border-zinc-800 text-zinc-400 hover:border-zinc-500'}`}>
                                {s.name || `Sesión Día ${s.dayOfWeek}`}<br/>
                                <span className="text-[9px] text-zinc-500 font-normal">{s.parts?.reduce((acc, p) => acc + p.exercises.length, 0) || 0} Ejercicios</span>
                            </button>
                        ))}
                    </div>
                    {transferTargetId && (
                        <div className="pt-4 mt-2 border-t border-white/10">
                            <Button onClick={() => {
                                const targetSession = weekSessions.find(s => s.id === transferTargetId);
                                if (!targetSession) return;
                                if (transferMode === 'export') {
                                    setWeekSessions(prev => prev.map(s => s.id === transferTargetId ? { ...s, parts: [...(s.parts || []), ...JSON.parse(JSON.stringify(session.parts || []))] } : s));
                                    setModifiedSessionIds(prev => new Set(prev).add(transferTargetId));
                                } else {
                                    updateSession(draft => { draft.parts = [...(draft.parts || []), ...JSON.parse(JSON.stringify(targetSession.parts || []))]; });
                                }
                                setIsTransferModalOpen(false);
                                addToast("Transferencia completada", "success");
                            }} className="w-full">Confirmar Transferencia</Button>
                        </div>
                    )}
                </div>
            </Modal>

            <Modal isOpen={isRulesModalOpen} onClose={() => setIsRulesModalOpen(false)} title="Reglas y Métricas Macro">
                <div className="p-2 space-y-6">
                    <p className="text-xs text-zinc-400 leading-relaxed">Aplica reglas masivas a todos los ejercicios de esta sesión para ahorrar tiempo.</p>
                    <div className="bg-zinc-900 border border-white/10 p-4 rounded-xl space-y-4">
                        <h4 className="text-[10px] font-black uppercase text-white tracking-widest">Sobreescribir Series y Repeticiones</h4>
                        <div className="grid grid-cols-3 gap-2">
                            <div><label className="text-[9px] text-zinc-500 uppercase font-bold">Series</label><input type="number" id="macroSets" defaultValue={3} className="w-full bg-black border border-zinc-800 text-white rounded p-2 text-center text-xs"/></div>
                            <div><label className="text-[9px] text-zinc-500 uppercase font-bold">Reps</label><input type="number" id="macroReps" defaultValue={10} className="w-full bg-black border border-zinc-800 text-white rounded p-2 text-center text-xs"/></div>
                            <div><label className="text-[9px] text-zinc-500 uppercase font-bold">RPE</label><input type="number" id="macroRPE" defaultValue={8} className="w-full bg-black border border-zinc-800 text-white rounded p-2 text-center text-xs"/></div>
                        </div>
                        <Button onClick={() => {
                            const sets = parseInt((document.getElementById('macroSets') as HTMLInputElement).value);
                            const reps = parseInt((document.getElementById('macroReps') as HTMLInputElement).value);
                            const rpe = parseFloat((document.getElementById('macroRPE') as HTMLInputElement).value);
                            updateSession(draft => {
                                draft.parts?.forEach(p => p.exercises.forEach(ex => {
                                    ex.sets = Array.from({length: sets}).map(() => ({ id: crypto.randomUUID(), targetReps: reps, targetRPE: rpe, intensityMode: 'rpe' }));
                                }));
                            });
                            setIsRulesModalOpen(false);
                            addToast("Métricas aplicadas a toda la sesión", "success");
                        }} className="w-full !py-2 !text-[10px]">Aplicar a toda la Sesión</Button>
                    </div>
                </div>
            </Modal>

            <Modal isOpen={isHistoryModalOpen} onClose={() => setIsHistoryModalOpen(false)} title="Historial de Cambios">
                <div className="p-2 space-y-2 max-h-60 overflow-y-auto custom-scrollbar">
                    {sessionHistory.length === 0 ? <p className="text-xs text-zinc-500">No hay cambios recientes.</p> : sessionHistory.map((hist, idx) => (
                        <button key={idx} onClick={() => {
                            setWeekSessions(prev => prev.map(s => s.id === activeSessionId ? JSON.parse(JSON.stringify(hist)) : s));
                            setIsHistoryModalOpen(false);
                            addToast("Sesión restaurada", "success");
                        }} className="w-full text-left p-3 bg-zinc-900 border border-white/5 hover:border-white/20 rounded-xl text-xs flex justify-between items-center group">
                            <span className="text-white font-bold group-hover:text-blue-400">Estado anterior #{sessionHistory.length - idx}</span>
                            <span className="text-[10px] text-zinc-500 uppercase">Restaurar</span>
                        </button>
                    )).reverse()}
                </div>
            </Modal>

            {/* MODAL GUARDADO ÚNICO */}
            <Modal isOpen={isSingleSaveModalOpen} onClose={() => setIsSingleSaveModalOpen(false)} title="Confirmar Cambios">
                <div className="p-2 space-y-4">
                    <p className="text-sm text-zinc-300">Vas a guardar los cambios en <strong className="text-white">{session.name}</strong>.</p>
                    {existingSessionInfo?.macroIndex !== undefined && (
                        <label className="flex items-center gap-3 p-3 bg-zinc-900/50 border border-white/10 rounded-xl cursor-pointer hover:bg-zinc-800/50 transition-colors">
                            <input type="checkbox" checked={applyToWholeBlock} onChange={e => setApplyToWholeBlock(e.target.checked)} className="rounded border-zinc-700 text-white focus:ring-0 bg-black w-4 h-4" />
                            <div className="flex flex-col">
                                <span className="text-xs font-bold text-white uppercase">Aplicar a todo el bloque</span>
                                <span className="text-[10px] text-zinc-500">Aplica este cambio para este día en todas las semanas restantes.</span>
                            </div>
                        </label>
                    )}
                    <div className="flex gap-2 pt-2">
                        <Button variant="secondary" onClick={() => setIsSingleSaveModalOpen(false)} className="flex-1">Cancelar</Button>
                        <Button onClick={() => executeFinalSave([session])} className="flex-1 bg-white text-black">Guardar</Button>
                    </div>
                </div>
            </Modal>

            {/* MODAL MULTI-SAVE */}
            <Modal isOpen={isMultiSaveModalOpen} onClose={() => setIsMultiSaveModalOpen(false)} title="Guardado Múltiple Semanal">
                <div className="p-2 space-y-4">
                    <p className="text-sm text-zinc-300">Has ajustado múltiples sesiones. Selecciona cómo aplicar los cambios:</p>
                    <div className="bg-[#111] border border-[#222] rounded-xl overflow-hidden max-h-60 overflow-y-auto custom-scrollbar">
                        {weekSessions.filter(s => modifiedSessionIds.has(s.id)).map(s => (
                            <div key={s.id} className="flex flex-col gap-2 p-3 border-b border-[#222] last:border-0">
                                <div className="flex items-center gap-3">
                                    <CheckIcon size={16} className="text-white"/>
                                    <div>
                                        <span className="text-xs font-bold text-white block">{s.name}</span>
                                        <span className="text-[9px] text-zinc-500 uppercase tracking-widest block">{s.dayOfWeek !== undefined ? orderedDays.find(d => d.value === s.dayOfWeek)?.label : 'Sin Día'}</span>
                                    </div>
                                </div>
                                {existingSessionInfo?.macroIndex !== undefined && (
                                    <label className="flex items-center gap-2 pl-7 cursor-pointer mt-1">
                                        <input type="checkbox" checked={blockScopeSelection[s.id] || false} onChange={e => setBlockScopeSelection(prev => ({...prev, [s.id]: e.target.checked}))} className="rounded border-zinc-700 text-white focus:ring-0 bg-black w-3 h-3" />
                                        <span className="text-[9px] text-zinc-400 font-bold uppercase">Aplicar a todo el bloque</span>
                                    </label>
                                )}
                            </div>
                        ))}
                    </div>
                    <div className="flex gap-2 pt-2">
                        <Button variant="secondary" onClick={() => executeFinalSave([session])} className="flex-1 !py-3 !text-[10px]">Solo Sesión Actual</Button>
                        <Button onClick={() => executeFinalSave(weekSessions.filter(s => modifiedSessionIds.has(s.id)))} className="flex-1 !py-3 !text-[10px] bg-white text-black">Guardar Seleccionadas</Button>
                    </div>
                </div>
            </Modal>

            {/* --- ROADMAP SEMANAL (Navegación Intra-Semana y Multi-Sesión) --- */}
            {existingSessionInfo && (
                <div className="flex flex-col border-b border-[#222] flex-shrink-0 bg-black">
                    <div className="px-6 py-5 flex items-center justify-between relative overflow-hidden">
                        <div className="absolute top-1/2 left-8 right-8 h-[2px] bg-[#222] -translate-y-1/2 z-0"></div>
                        {orderedDays.map(day => {
                            const daySessions = weekSessions.filter(s => s.dayOfWeek === day.value);
                            const isActive = daySessions.some(s => s.id === activeSessionId) || emptyDaySelected === day.value;
                            const hasSession = daySessions.length > 0;
                            const isModified = daySessions.some(s => modifiedSessionIds.has(s.id));
                            
                            return (
                                <button 
                                    key={day.value} 
                                    onClick={() => handleDayClick(day.value, daySessions)}
                                    className="relative z-10 flex flex-col items-center gap-2 group outline-none"
                                >
                                    <div className={`w-4 h-4 rounded-full border-4 transition-all duration-300 relative ${isActive ? 'bg-white border-white scale-125 shadow-[0_0_15px_rgba(255,255,255,0.4)]' : hasSession ? 'bg-[#222] border-black hover:bg-[#444]' : 'bg-black border-[#222] hover:border-[#444]'}`}>
                                        {isModified && !isActive && <div className="absolute -top-2 -right-2 w-2 h-2 bg-orange-500 rounded-full"></div>}
                                    </div>
                                    <span className={`text-[9px] font-black uppercase tracking-widest absolute -bottom-5 transition-colors ${isActive ? 'text-white' : hasSession ? 'text-zinc-500' : 'text-zinc-700'}`}>{day.label.slice(0,3)}</span>
                                </button>
                            )
                        })}
                    </div>
                    {/* TABS MULTI-SESIÓN PARA EL DÍA SELECCIONADO */}
                    {activeSessionId !== 'empty' && weekSessions.filter(s => s.dayOfWeek === session.dayOfWeek).length > 1 && (
                        <div className="flex px-4 gap-2 overflow-x-auto hide-scrollbar pb-2">
                            {weekSessions.filter(s => s.dayOfWeek === session.dayOfWeek).map((s, idx) => (
                                <button 
                                    key={s.id} 
                                    onClick={() => setActiveSessionId(s.id)} 
                                    className={`px-3 py-1.5 rounded-full text-[10px] font-black uppercase whitespace-nowrap transition-colors border ${activeSessionId === s.id ? 'bg-white text-black border-white' : 'bg-transparent text-zinc-500 border-zinc-800 hover:border-zinc-500'}`}
                                >
                                    {s.name || `Sesión ${idx + 1}`}
                                </button>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* --- MAIN CONTENT (Scroll Fix Applied Here) --- */}
            <div className="flex flex-col p-4 space-y-8 bg-black w-full min-h-max">
                {/* PANEL GAME DAY (MEET PLANNING) */}
                {session.isMeetDay && (
                    <div className="bg-gradient-to-br from-yellow-900/30 to-black border border-yellow-500/50 p-5 rounded-2xl shadow-[0_0_30px_rgba(250,204,21,0.15)] flex flex-col gap-4 animate-fade-in relative overflow-hidden">
                        <div className="absolute -right-10 -top-10 opacity-10 text-yellow-500"><TrophyIcon size={120} /></div>
                        
                        <div className="flex justify-between items-center z-10">
                            <div>
                                <h3 className="text-xl font-black text-white uppercase tracking-tight flex items-center gap-2"><TrophyIcon size={20} className="text-yellow-400"/> Game Day / Meet</h3>
                                <p className="text-[10px] text-yellow-200/60 font-bold uppercase tracking-widest mt-1">Planificación de Competición</p>
                            </div>
                            <div className="flex items-center gap-2 bg-black border border-yellow-500/30 px-3 py-2 rounded-xl">
                                <span className="text-[9px] text-zinc-400 uppercase font-bold">Pesaje (BW)</span>
                                <input type="number" value={session.meetBodyweight || ''} onChange={e => updateSession(d => {d.meetBodyweight = parseFloat(e.target.value)})} className="w-14 bg-transparent text-white font-black text-sm p-0 border-none focus:ring-0 text-center" placeholder="kg"/>
                            </div>
                        </div>

                        <div className="z-10 space-y-2">
                            {(() => {
                                const compLifts = (session.parts || []).flatMap(p => p.exercises.filter(ex => ex.isCompetitionLift));
                                if (compLifts.length === 0) return (
                                    <p className="text-[10px] text-zinc-500 text-center py-4">Marca ejercicios como "Movimiento de Competición" para planificar tus intentos.</p>
                                );
                                return compLifts.map((ex, i) => (
                                    <div key={i} className="bg-black/50 border border-white/10 p-3 rounded-xl flex items-center justify-between">
                                        <span className="text-xs font-bold text-white">{ex.name}</span>
                                        <span className="text-[10px] font-black text-yellow-400">{ex.sets.length} intentos planificados</span>
                                    </div>
                                ));
                            })()}
                            <p className="text-[9px] text-zinc-500 text-center mt-2">Las luces de jueceo y el total se calculan en vivo durante la sesión de entrenamiento.</p>
                        </div>
                    </div>
                )}

                {/* ALERTAS BIOMECÁNICAS Y NEURALES (KPKN ENGINE) */}
                {activeSessionId !== 'empty' && (
                    <div className="space-y-3 sticky top-0 z-30 pt-2 backdrop-blur-md">
                        {globalSessionAlerts.length > 0 && (
                            <div className="bg-red-950/80 border-l-4 border-red-500 p-3 rounded-r-xl shadow-lg flex items-start gap-3 animate-slide-up">
                                <AlertTriangleIcon className="text-red-500 shrink-0 mt-0.5" size={20} />
                                <div className="space-y-1 w-full pr-2">
                                    <h4 className="text-red-400 font-black uppercase tracking-widest text-[9px]">Alerta de Recuperación</h4>
                                    {globalSessionAlerts.map((a: any) => (
                                        <div key={a.muscle} className="text-[10px] text-red-200 leading-tight">
                                            <strong className="text-white">{a.muscle}:</strong> {a.message || 'Límite de volumen superado.'}
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                        {neuralAlerts.map((alert, idx) => (
                            <div key={idx} className={`border-l-4 p-3 rounded-r-xl shadow-lg flex items-start gap-3 animate-slide-up ${alert.severity === 'critical' ? 'bg-orange-950/80 border-orange-500' : 'bg-yellow-950/80 border-yellow-500'}`}>
                                <div className={alert.severity === 'critical' ? 'text-orange-500 shrink-0' : 'text-yellow-500 shrink-0'}>
                                    <ActivityIcon size={20} />
                                </div>
                                <div>
                                    <h4 className={`${alert.severity === 'critical' ? 'text-orange-400' : 'text-yellow-400'} font-black uppercase tracking-widest text-[9px]`}>Alerta {alert.type}</h4>
                                    <p className={`text-[10px] ${alert.severity === 'critical' ? 'text-orange-200' : 'text-yellow-200'} mt-0.5 leading-relaxed`}>{alert.message}</p>
                                </div>
                            </div>
                        ))}
                    </div>
                )}

                {activeSessionId === 'empty' ? (
                    <div className="flex flex-col items-center justify-center h-full text-center space-y-4 pt-20">
                        <LayersIcon size={48} className="text-zinc-800" />
                        <div>
                            <p className="text-sm font-bold text-zinc-400 uppercase tracking-widest">Día libre de entrenamiento</p>
                            <p className="text-xs text-zinc-600 mt-1">No hay ninguna sesión programada para este día.</p>
                        </div>
                        <Button onClick={() => handleCreateFirstSession(emptyDaySelected!)} className="mt-4">
                            Crear Primera Sesión
                        </Button>
                    </div>
                ) : (
                    <>
                        {/* Dashboard AUGE Toggle */}
                        <div className="relative">
                    <button onClick={() => setIsAnalysisExpanded(!isAnalysisExpanded)} className="w-full flex items-center justify-between p-3 border border-white/10 rounded-xl bg-zinc-900/30 hover:bg-zinc-900/50 transition-colors group">
                        <span className="flex items-center gap-2 text-[10px] font-black text-zinc-400 uppercase tracking-widest group-hover:text-white"><ActivityIcon size={12} /> ESTÍMULO VS FATIGA</span>
                        <ChevronRightIcon size={14} className={`text-zinc-500 transition-transform ${isAnalysisExpanded ? 'rotate-90' : ''}`} />
                    </button>
                    <div className={`transition-all duration-500 ease-in-out overflow-hidden ${isAnalysisExpanded ? 'opacity-100 max-h-[1000px] mt-2' : 'opacity-0 max-h-0'}`}>
                         <SessionAugeDashboard 
                            currentSession={session} 
                            weekSessions={weekSessions}
                            exerciseList={exerciseList} 
                         />
                    </div>
                </div>

                {/* Parts List */}
                <div className="space-y-10 pb-20">
                    {session.parts?.map((part, pi) => {
                        const groupedExercises = groupExercises(part.exercises);
                        const isColorPickerOpen = openColorPickerIndex === pi;
                        const themeColor = part.color || '#ffffff';
                        const isCollapsed = collapsedParts[part.id] || false;
                        
                        return (
                        <div key={part.id} className={`relative transition-all duration-300 ${draggedPartIndex === pi ? 'opacity-50' : 'opacity-100'}`} draggable onDragStart={() => handleDragStart(pi)} onDragOver={handleDragOver} onDrop={() => handleDrop(pi)}>
                            
                            {/* Part Header (Wireframe Style) */}
                            <div className="flex items-end gap-3 pb-2 border-b-2 border-white mb-4">
                                <DragHandleIcon className="text-zinc-600 cursor-grab active:cursor-grabbing hover:text-white mb-1" size={18} />
                                <button onClick={(e) => { e.preventDefault(); togglePartCollapse(part.id); }} className="text-zinc-500 hover:text-white transition-colors mb-1"><ChevronRightIcon size={16} className={`transition-transform duration-200 ${isCollapsed ? '' : 'rotate-90'}`} /></button>
                                <button onClick={() => setOpenColorPickerIndex(isColorPickerOpen ? null : pi)} className="w-2 h-6 mb-1 rounded-sm transition-transform active:scale-90" style={{ backgroundColor: themeColor }} title="Color"/>
                                
                                <div className="flex-grow min-w-0">
                                    <input 
                                        value={part.name} 
                                        onChange={e => updateSession(d => { d.parts![pi].name = e.target.value; })} 
                                        className="bg-transparent text-2xl font-black text-white w-full outline-none border-none p-0 focus:ring-0 uppercase tracking-tighter placeholder-zinc-800" 
                                        placeholder="SECCIÓN" 
                                    />
                                </div>
                                <button onClick={(e) => handleRemovePart(pi, e)} className="text-zinc-600 hover:text-red-500 mb-1"><TrashIcon size={16}/></button>
                            </div>

                            <div className={`transition-all duration-300 ease-in-out overflow-hidden ${isCollapsed ? 'max-h-0 opacity-0' : 'max-h-[5000px] opacity-100'}`}>
                                {/* Color Picker */}
                                <div className={`overflow-hidden transition-all duration-300 ease-in-out ${isColorPickerOpen ? 'max-h-24 opacity-100 mb-6' : 'max-h-0 opacity-0'}`}>
                                    <div className="flex flex-wrap gap-3 p-3 border border-white/10 rounded-xl bg-zinc-900/50">
                                        {PRESET_PART_COLORS.map(color => (<button key={color} onClick={() => { updateSession(d => { d.parts![pi].color = color; }); setOpenColorPickerIndex(null); }} className={`w-6 h-6 rounded-full border border-white/20 transition-all hover:scale-110 ${part.color === color ? 'ring-2 ring-white scale-110' : ''}`} style={{ backgroundColor: color }}/>))}
                                    </div>
                                </div>
                                
                                {/* Exercises Grid */}
                                <div className="space-y-4">
                                    {groupedExercises.map((group, groupIdx) => {
                                        if (group.type === 'superset') {
                                            return (
                                                <SupersetManagementBlock
                                                    key={`group-${group.id}-${groupIdx}`}
                                                    exercises={group.items}
                                                    onUpdateSession={updateSession}
                                                    partIndex={pi}
                                                    partColor={part.color}
                                                    onReorderExercise={handleReorderExercise}
                                                    onToggleSelect={handleToggleSelectExercise}
                                                    selectedIds={selectedExerciseIds}
                                                    isSelectionMode={isAnalysisExpanded && bulkScope === 'manual'}
                                                    onUnlink={handleUnlink}
                                                    onLinkNext={handleLinkWithNext}
                                                    culpritIds={culpritExerciseIds}
                                                />
                                            )
                                        } else {
                                            const { ex, index: ei } = group;
                                            return <MemoizedExerciseCard key={ex.id} exercise={ex} categoryColor={part.color} isInSuperset={false} isJunkVolumeCulprit={culpritExerciseIds.has(ex.id)} onExerciseChange={(f, v) => { updateSession(d => { if (typeof f === 'string') (d.parts![pi].exercises[ei] as any)[f] = v; else d.parts![pi].exercises[ei] = {...d.parts![pi].exercises[ei], ...f}; }); }} onSetChange={(si, f, v) => { updateSession(d => { if (typeof f === 'string') (d.parts![pi].exercises[ei].sets[si] as any)[f] = v; else d.parts![pi].exercises[ei].sets[si] = {...d.parts![pi].exercises[ei].sets[si], ...f}; }); }} onAddSet={() => updateSession(d => { d.parts![pi].exercises[ei].sets.push({ id: crypto.randomUUID(), targetReps: 8, intensityMode: 'rpe', targetRPE: 8 }); })} onRemoveSet={(si) => updateSession(d => { d.parts![pi].exercises[ei].sets.splice(si, 1); })} onRemoveExercise={() => updateSession(d => { d.parts![pi].exercises.splice(ei, 1); })} onReorder={(dir) => handleReorderExercise(pi, ei, dir)} onLinkNext={() => handleLinkWithNext(pi, ei)} isFirst={pi === 0 && ei === 0} isLast={pi === (session.parts?.length || 0) - 1 && ei === part.exercises.length - 1} isSelectionMode={isAnalysisExpanded && bulkScope === 'manual'} isSelected={selectedExerciseIds.has(ex.id)} onToggleSelect={() => handleToggleSelectExercise(ex.id)}/>
                                        }
                                    })}
                                    
                                    <button onClick={() => handleAddExercise(pi)} className="relative z-30 w-full py-4 mt-4 border border-dashed border-white/20 hover:border-white text-zinc-500 hover:text-white rounded-xl transition-all text-[10px] font-black uppercase tracking-widest flex items-center justify-center gap-2 group">
                                        <div className="bg-white/10 p-1 rounded-full group-hover:bg-white group-hover:text-black transition-colors"><PlusIcon size={12}/></div>
                                        Añadir Ejercicio
                                    </button>
                                </div>
                            </div>
                        </div>
                    )})}
                    
                    <div className="relative z-30 pt-8 mt-8 border-t border-white/10">
                        <Button onClick={handleAddPart} className="w-full !py-4 font-black uppercase text-xs tracking-widest bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 text-zinc-300 shadow-xl">
                            <LayersIcon size={16} className="mr-2"/> Nueva Sección
                        </Button>
                    </div>
                </div>
                </>
                )}
                
                {/* --- BOTÓN GUARDAR ESTÁTICO (NO FLOTANTE NI BLOQUEANTE) --- */}
                <div className="relative w-full p-6 mt-8 mb-12 bg-transparent z-50 flex justify-center">
                    <button 
                        onClick={handleSave} 
                        className="w-full max-w-sm bg-white text-black px-8 py-4 rounded-full font-black text-xs uppercase tracking-[0.2em] shadow-[0_0_50px_rgba(255,255,255,0.25)] hover:scale-105 active:scale-95 transition-all flex items-center justify-center gap-2 border border-transparent hover:border-black"
                    >
                        <CheckIcon size={18}/>
                        <span>Guardar Sesión</span>
                    </button>
                </div>
            </div>
        </div>
    );
};

export const SessionEditor = React.memo(SessionEditorComponent);
