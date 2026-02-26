
// components/SleepTrackerWidget.tsx
import React, { useMemo, useState, useEffect } from 'react';
import { useAppDispatch, useAppState } from '../contexts/AppContext';
import { SunIcon, MoonIcon, CalendarIcon, BriefcaseIcon, ActivityIcon, SparklesIcon, SettingsIcon, CheckIcon, BedIcon, XIcon, BrainIcon, AlertTriangleIcon, PencilIcon, ClockIcon } from './icons';
import { BarChart, Bar, XAxis, ResponsiveContainer, Cell } from 'recharts';
import { SleepLog, DailyWellbeingLog, IntensityLevel } from '../types';
import { TacticalModal } from './ui/TacticalOverlays';
import Button from './ui/Button';
import { calculateSleepRecommendations } from '../services/auge';
import { scheduleBedtimeReminder } from '../services/notificationService';
import { getLocalDateString } from '../utils/dateUtils';

// --- NUEVO MODAL: REGISTRO DE ACTIVIDAD (TRABAJO, ESTUDIO, ÁNIMO) ---
const ActivityLogModal: React.FC<{ isOpen: boolean; onClose: () => void }> = ({ isOpen, onClose }) => {
    const { dailyWellbeingLogs } = useAppState();
    const { handleLogDailyWellbeing, addToast } = useAppDispatch();
    const todayStr = getLocalDateString();
    const existing = dailyWellbeingLogs.find(l => l.date.startsWith(todayStr));

    const [workHours, setWorkHours] = useState(existing?.workHours || 0);
    const [workIntensity, setWorkIntensity] = useState<IntensityLevel>(existing?.workIntensity || 'moderate');
    const [studyHours, setStudyHours] = useState(existing?.studyHours || 0);
    const [studyIntensity, setStudyIntensity] = useState<IntensityLevel>(existing?.studyIntensity || 'moderate');
    const [mood, setMood] = useState(existing?.moodState || 'neutral');
    const [isDepressive, setIsDepressive] = useState(existing?.isDepressiveEpisode || false);

    const handleSave = () => {
        handleLogDailyWellbeing({
            date: getLocalDateString(),
            workHours,
            workIntensity,
            studyHours,
            studyIntensity,
            moodState: mood as any,
            isDepressiveEpisode: isDepressive,
            sleepQuality: existing?.sleepQuality || 3,
            stressLevel: workIntensity === 'high' || studyIntensity === 'high' ? 5 : 3,
            doms: existing?.doms || 1,
            motivation: mood === 'energetic' ? 5 : mood === 'sad' ? 2 : 3
        });
        addToast("Actividad diaria registrada", "success");
        onClose();
    };

    const intensities: { id: IntensityLevel, label: string }[] = [
        { id: 'light', label: 'Ligera' },
        { id: 'moderate', label: 'Media' },
        { id: 'high', label: 'Alta' }
    ];

    const moods = [
        { id: 'sad', emoji: '😔', label: 'Bajo' },
        { id: 'neutral', emoji: '😐', label: 'Normal' },
        { id: 'happy', emoji: '🙂', label: 'Bien' },
        { id: 'energetic', emoji: '🔥', label: 'A tope' }
    ];

    return (
        <TacticalModal isOpen={isOpen} onClose={onClose} title="Registro de Actividad">
            <div className="space-y-8 p-1 pb-6">
                {/* TRABAJO */}
                <div className="bg-white/5 p-4 rounded-3xl border border-white/10 space-y-4">
                    <div className="flex justify-between items-center">
                        <h4 className="text-xs font-black text-blue-400 uppercase tracking-widest flex items-center gap-2">
                            <BriefcaseIcon size={14}/> Horas de Trabajo
                        </h4>
                        <span className="text-lg font-black text-white">{workHours}h</span>
                    </div>
                    <input type="range" min="0" max="14" step="0.5" value={workHours} onChange={e => setWorkHours(parseFloat(e.target.value))} className="w-full h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-blue-500" />
                    <div className="flex gap-2">
                        {intensities.map(i => (
                            <button key={i.id} onClick={() => setWorkIntensity(i.id)} className={`flex-1 py-2 rounded-xl text-[10px] font-black uppercase transition-all ${workIntensity === i.id ? 'bg-blue-600 text-white shadow-lg shadow-blue-900/40' : 'bg-slate-800 text-slate-500'}`}>{i.label}</button>
                        ))}
                    </div>
                </div>

                {/* ESTUDIOS */}
                <div className="bg-white/5 p-4 rounded-3xl border border-white/10 space-y-4">
                    <div className="flex justify-between items-center">
                        <h4 className="text-xs font-black text-purple-400 uppercase tracking-widest flex items-center gap-2">
                            <ActivityIcon size={14}/> Horas de Estudio
                        </h4>
                        <span className="text-lg font-black text-white">{studyHours}h</span>
                    </div>
                    <input type="range" min="0" max="14" step="0.5" value={studyHours} onChange={e => setStudyHours(parseFloat(e.target.value))} className="w-full h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-purple-500" />
                    <div className="flex gap-2">
                        {intensities.map(i => (
                            <button key={i.id} onClick={() => setStudyIntensity(i.id)} className={`flex-1 py-2 rounded-xl text-[10px] font-black uppercase transition-all ${studyIntensity === i.id ? 'bg-purple-600 text-white shadow-lg shadow-purple-900/40' : 'bg-slate-800 text-slate-500'}`}>{i.label}</button>
                        ))}
                    </div>
                </div>

                {/* ESTADO ANÍMICO */}
                <div className="space-y-4">
                    <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest text-center block">¿Cómo está tu ánimo hoy?</label>
                    <div className="flex justify-between bg-white/5 p-2 rounded-2xl border border-white/5">
                        {moods.map(m => (
                            <button key={m.id} onClick={() => setMood(m.id)} className={`flex flex-col items-center gap-1 p-2 rounded-xl transition-all ${mood === m.id ? 'bg-white/10 scale-105 shadow-xl' : 'opacity-40'}`}>
                                <span className="text-2xl">{m.emoji}</span>
                                <span className="text-[8px] font-black uppercase text-slate-400">{m.label}</span>
                            </button>
                        ))}
                    </div>
                </div>

                {/* EPISODIO DEPRESIVO */}
                <div className={`p-4 rounded-3xl border transition-all flex items-center justify-between ${isDepressive ? 'bg-red-900/20 border-red-500/40' : 'bg-white/5 border-white/5'}`}>
                    <div className="flex items-center gap-3">
                        <AlertTriangleIcon size={20} className={isDepressive ? 'text-red-500' : 'text-slate-600'} />
                        <div>
                            <p className={`text-sm font-bold ${isDepressive ? 'text-white' : 'text-slate-400'}`}>Crisis de Salud Mental</p>
                            <p className="text-[9px] text-slate-500 uppercase font-black">Activar solo en episodios depresivos serios</p>
                        </div>
                    </div>
                    <button onClick={() => setIsDepressive(!isDepressive)} className={`w-12 h-6 rounded-full relative transition-all duration-300 ${isDepressive ? 'bg-red-600' : 'bg-slate-700'}`}>
                        <div className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-all duration-300 ${isDepressive ? 'right-1' : 'left-1'}`} />
                    </button>
                </div>

                <Button onClick={handleSave} className="w-full !py-4 shadow-xl">Guardar Reporte Diario</Button>
            </div>
        </TacticalModal>
    );
};

// --- MODAL: REGISTRO MANUAL DE SUEÑO ---
const ManualSleepModal: React.FC<{ isOpen: boolean; onClose: () => void }> = ({ isOpen, onClose }) => {
    const { setSleepLogs, addToast } = useAppDispatch();
    const [hours, setHours] = useState(8);
    const [date, setDate] = useState(getLocalDateString());

    const handleSave = () => {
        const newLog: SleepLog = {
            id: crypto.randomUUID(),
            startTime: new Date(date).toISOString(),
            endTime: new Date(date).toISOString(),
            duration: hours,
            date: date
        };
        setSleepLogs(prev => [newLog, ...prev]);
        addToast("Sueño registrado manualmente", "success");
        onClose();
    };

    return (
        <TacticalModal isOpen={isOpen} onClose={onClose} title="Registro Manual de Sueño">
            <div className="space-y-6 p-2 pb-6">
                <div className="text-center">
                    <p className="text-[10px] text-slate-500 font-bold uppercase tracking-[0.2em] mb-2">Horas Totales</p>
                    <p className="text-6xl font-black text-white">{hours}<span className="text-xl ml-1 text-slate-500">h</span></p>
                    <input type="range" min="1" max="15" step="0.5" value={hours} onChange={e => setHours(parseFloat(e.target.value))} className="w-full mt-6 accent-indigo-500 h-1.5 bg-slate-800 rounded-full" />
                </div>
                <div className="space-y-1">
                    <label className="text-[10px] text-slate-500 font-bold uppercase ml-1">Fecha de la noche</label>
                    <input type="date" value={date} onChange={e => setDate(e.target.value)} className="w-full bg-slate-900 border border-white/10 rounded-xl p-3 text-white font-bold" />
                </div>
                <Button onClick={handleSave} className="w-full !py-4 !bg-indigo-600 !border-none">Guardar Registro</Button>
            </div>
        </TacticalModal>
    );
};

const SleepTrackerWidget: React.FC<{ onEditLog: (log: SleepLog) => void }> = ({ onEditLog }) => {
    const { sleepStartTime, sleepLogs, dailyWellbeingLogs, history, settings, exerciseList } = useAppState();
    const { handleLogSleep, addToast } = useAppDispatch();
    const [isActivityOpen, setIsActivityOpen] = useState(false);
    const [isManualOpen, setIsManualOpen] = useState(false);

    const isSleeping = sleepStartTime !== null;
    const todayStr = getLocalDateString();
    const todayContext = useMemo(() => dailyWellbeingLogs.find(l => l.date === todayStr), [dailyWellbeingLogs, todayStr]);
    const todayWorkout = useMemo(() => history.find(l => l.date.startsWith(todayStr)), [history, todayStr]);
    
    const sleepPlan = useMemo(() => calculateSleepRecommendations(settings, todayContext, todayWorkout, exerciseList), [settings, todayContext, todayWorkout, exerciseList]);

    // Programar la notificación cuando cambia el cálculo del plan de sueño
    useEffect(() => {
        if (settings.smartSleepEnabled && sleepPlan.bedTime) {
            scheduleBedtimeReminder(sleepPlan.bedTime);
        }
    }, [settings.smartSleepEnabled, sleepPlan.bedTime]);

    const sortedLogs = useMemo(() => [...sleepLogs].sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()), [sleepLogs]);
    const lastNight = sortedLogs[0];

    const chartData = useMemo(() => {
        const days = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
        return Array.from({length: 7}, (_, i) => {
            const d = new Date();
            d.setDate(d.getDate() - (6 - i));
            const dayName = days[d.getDay()];
            const log = sortedLogs.find(l => l.date === getLocalDateString(d));
            return { name: dayName, horas: log?.duration || 0, isToday: i === 6 };
        });
    }, [sortedLogs]);

    return (
        <div className="absolute inset-0 w-full h-full overflow-y-auto hide-scrollbar bg-black z-0">
            <ActivityLogModal isOpen={isActivityOpen} onClose={() => setIsActivityOpen(false)} />
            <ManualSleepModal isOpen={isManualOpen} onClose={() => setIsManualOpen(false)} />
            
            <div className="relative z-10 px-6 pt-44 flex flex-col min-h-full max-w-lg mx-auto tab-bar-safe-area">
                <div className="text-center mb-12">
                    <h3 className="text-[10px] font-black text-indigo-400 uppercase tracking-[0.4em] mb-4 flex items-center justify-center gap-2">
                         {isSleeping ? <SunIcon size={12} className="text-yellow-400 animate-pulse" /> : <MoonIcon size={12}/>}
                         {isSleeping ? 'MODO SUEÑO ACTIVO' : 'HORAS ÚLTIMA NOCHE'}
                    </h3>
                    <div className="flex items-baseline justify-center gap-1">
                        {isSleeping ? (
                            <BedIcon size={80} className="text-white opacity-80 animate-pulse drop-shadow-[0_0_25px_rgba(255,255,255,0.3)]" />
                        ) : (
                            <>
                                <p className="text-8xl font-black text-white tracking-tighter leading-none">{lastNight?.duration.toFixed(1) || '--'}</p>
                                <span className="text-2xl text-slate-500 font-bold uppercase ml-1">h</span>
                            </>
                        )}
                    </div>
                </div>

                <div className="grid grid-cols-2 gap-3 mb-8">
                    <button 
                        onClick={() => handleLogSleep(isSleeping ? 'end' : 'start')} 
                        className={`col-span-2 py-5 rounded-[2rem] flex items-center justify-center gap-3 shadow-2xl transition-all active:scale-95 border border-white/10 ${isSleeping ? 'bg-yellow-500/20 text-yellow-300' : 'bg-indigo-600 text-white shadow-lg shadow-indigo-900/40'}`}
                    >
                        {isSleeping ? <SunIcon size={24}/> : <MoonIcon size={24}/>}
                        <span className="font-black uppercase tracking-widest text-lg">{isSleeping ? 'Despertar' : 'Ir a Dormir'}</span>
                    </button>
                    
                    <button onClick={() => setIsActivityOpen(true)} className="flex flex-col items-center justify-center p-5 rounded-[2rem] bg-white/[0.03] border border-white/5 active:scale-95 group hover:bg-white/5 transition-all">
                        <ActivityIcon size={24} className="text-blue-400 mb-2 group-hover:scale-110 transition-transform"/>
                        <span className="text-[8px] font-black text-slate-400 uppercase text-center leading-tight">Registro de<br/>Actividad</span>
                    </button>
                    
                    <button onClick={() => setIsManualOpen(true)} className="flex flex-col items-center justify-center p-5 rounded-[2rem] bg-white/[0.03] border border-white/5 active:scale-95 group hover:bg-white/5 transition-all">
                        <CalendarIcon size={24} className="text-emerald-400 mb-2 group-hover:scale-110 transition-transform"/>
                        <span className="text-[8px] font-black text-slate-400 uppercase text-center leading-tight">Registro<br/>Manual</span>
                    </button>
                </div>
                
                {settings.smartSleepEnabled && (
                    <div className="bg-gradient-to-br from-indigo-950/40 to-black p-6 rounded-[2.5rem] border border-white/10 mb-10 shadow-2xl relative overflow-hidden animate-fade-in-up">
                        <div className="absolute top-0 right-0 p-4 opacity-5"><SparklesIcon size={80}/></div>
                        <div className="flex items-center justify-between mb-4">
                            <h4 className="text-[10px] font-black text-indigo-400 uppercase tracking-widest flex items-center gap-2">
                                <BrainIcon size={12}/> Recomendación Prime
                            </h4>
                            {sleepPlan.isWorkDayTomorrow && <span className="text-[9px] bg-slate-800 text-slate-400 px-2 py-0.5 rounded font-bold uppercase">Mañana: Laboral</span>}
                        </div>
                        
                        <div className="grid grid-cols-2 gap-4 text-center mb-5">
                            <div className="bg-white/5 p-3 rounded-2xl border border-white/5">
                                <p className="text-2xl font-black text-white leading-none">{sleepPlan.bedTime}</p>
                                <p className="text-[8px] text-slate-500 font-bold uppercase tracking-widest mt-1">Irse a dormir</p>
                            </div>
                            <div className="bg-white/5 p-3 rounded-2xl border border-white/5">
                                <p className="text-2xl font-black text-white leading-none">{sleepPlan.wakeTime}</p>
                                <p className="text-[8px] text-slate-500 font-bold uppercase tracking-widest mt-1">Despertar (Fijo)</p>
                            </div>
                        </div>
                        
                        <div className="flex flex-wrap gap-2 justify-center">
                            <span className="text-[9px] bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 px-3 py-1 rounded-full font-black uppercase tracking-tight">
                                Meta: {sleepPlan.duration}h
                            </span>
                            {sleepPlan.reasons.map((reason, idx) => (
                                <span key={idx} className="text-[9px] bg-white/5 text-slate-400 border border-white/10 px-3 py-1 rounded-full font-black uppercase tracking-tight">
                                    {reason}
                                </span>
                            ))}
                        </div>
                    </div>
                )}

                <div className="w-full mb-10">
                    <h4 className="text-[10px] font-black text-slate-600 uppercase tracking-[0.3em] mb-4 ml-1">Consistencia Semanal</h4>
                    <div className="h-44 w-full bg-white/[0.02] rounded-[2rem] p-5 border border-white/5">
                        <ResponsiveContainer width="100%" height="100%">
                            <BarChart data={chartData}>
                                <Bar dataKey="horas" radius={[6, 6, 6, 6]} barSize={12}>
                                    {chartData.map((entry, index) => (
                                        <Cell key={`cell-${index}`} fill={entry.isToday ? '#818cf8' : '#312e81'} />
                                    ))}
                                </Bar>
                                <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{fontSize: 9, fill: '#475569', fontWeight: 800}} dy={10} />
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                </div>

                <div className="space-y-3">
                    <h4 className="text-[10px] font-black text-slate-600 uppercase tracking-[0.3em] ml-1">Historial de Sueño</h4>
                    {sortedLogs.slice(0, 5).map(log => (
                        <div key={log.id} onClick={() => onEditLog(log)} className="flex items-center justify-between p-4 bg-white/5 border border-white/5 rounded-2xl hover:bg-white/10 transition-all cursor-pointer group">
                            <div className="flex items-center gap-3">
                                <MoonIcon size={16} className="text-indigo-400"/>
                                <div>
                                    <p className="text-xs font-bold text-white">{new Date(log.date).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })}</p>
                                    <p className="text-[10px] text-slate-500 uppercase font-black">
                                        {log.isAuto ? 'Registro Automático' : 'Registro Manual'}
                                    </p>
                                </div>
                            </div>
                            <div className="flex items-center gap-3">
                                <span className="text-lg font-black text-white">{log.duration.toFixed(1)}h</span>
                                <PencilIcon size={12} className="text-slate-600 group-hover:text-white transition-colors" />
                            </div>
                        </div>
                    ))}
                    {sortedLogs.length === 0 && <p className="text-center text-xs text-slate-600 py-4 italic">No hay registros de sueño aún.</p>}
                </div>
            </div>
        </div>
    );
};

export default SleepTrackerWidget;
