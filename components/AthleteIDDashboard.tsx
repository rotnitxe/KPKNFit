
// components/AthleteIDDashboard.tsx
import React, { useState, useMemo, useEffect } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { View, Settings, BiomechanicalData } from '../types';
import { 
    XIcon, SettingsIcon, MapPinIcon, 
    ActivityIcon, TrendingUpIcon, ChevronDownIcon, 
    CameraIcon, DumbbellIcon, TrophyIcon, StarIcon, CheckCircleIcon,
    LinkIcon, TargetIcon, PencilIcon, SaveIcon, InfoIcon, 
    UtensilsIcon, BrainIcon, BodyIcon, CalendarIcon, RulerIcon, UserBadgeIcon
} from './icons';
import Button from './ui/Button';
import Card from './ui/Card';
import BodyWeightChart from './BodyWeightChart';
import BodyFatChart from './BodyFatChart';
import FFMIChart from './FFMIChart';
import { calculateFFMI, calculateIPFGLPoints, calculateBrzycki1RM } from '../utils/calculations';
import { shareElementAsImage } from '../services/shareService';
import ProfilePictureModal from './ProfilePictureModal';

interface AthleteIDDashboardProps {
    isOpen: boolean;
    onClose: () => void;
    onNavigate: (view: View) => void;
    onSettingsClick: () => void;
}

interface RecordData {
    name: string;
    oneRM: number;
    goal1RM?: number;
    history: { date: string; value: number }[];
    isFavorite?: boolean;
    projectedDate?: string;
    weeklyImprovement?: number;
}

// --- MICRO COMPONENTS FOR UI ---

const StatBox: React.FC<{ label: string; value: string | number; unit?: string; trend?: string; color?: string }> = ({ label, value, unit, trend, color = "text-white" }) => (
    <div className="bg-[#111111] border border-white/5 rounded-2xl p-4 flex flex-col items-center justify-center relative overflow-hidden group">
        <div className="absolute inset-0 bg-gradient-to-b from-white/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none" />
        <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest mb-1 z-10">{label}</span>
        <div className="flex items-baseline gap-1 z-10">
            <span className={`text-3xl font-black ${color} tracking-tighter`}>{value}</span>
            {unit && <span className="text-[10px] font-bold text-slate-600">{unit}</span>}
        </div>
        {trend && <span className="text-[9px] font-mono text-emerald-500 mt-1 z-10">{trend}</span>}
    </div>
);

const SectionHeader: React.FC<{ title: string; icon?: React.ReactNode; action?: React.ReactNode }> = ({ title, icon, action }) => (
    <div className="flex items-center justify-between mb-4 px-1">
        <div className="flex items-center gap-2 text-slate-200">
            {icon && <span className="text-primary-color">{icon}</span>}
            <h3 className="text-sm font-black uppercase tracking-widest">{title}</h3>
        </div>
        {action}
    </div>
);

const InputField: React.FC<{ label: string; value: string | number; onChange: (v: string) => void; type?: string; placeholder?: string }> = ({ label, value, onChange, type = "text", placeholder }) => (
    <div className="bg-[#0A0A0A] border border-white/10 rounded-xl px-4 py-3 focus-within:border-primary-color/50 transition-colors group">
        <label className="block text-[9px] font-black text-slate-500 uppercase tracking-widest mb-1 group-focus-within:text-primary-color transition-colors">{label}</label>
        <input 
            type={type} 
            value={value} 
            onChange={e => onChange(e.target.value)} 
            placeholder={placeholder}
            className="w-full bg-transparent border-none p-0 text-white font-bold text-sm focus:ring-0 placeholder-slate-700" 
        />
    </div>
);

const MeasurementInput: React.FC<{ label: string; value: string; onChange: (v: string) => void }> = ({ label, value, onChange }) => (
    <div>
        <label className="text-[10px] font-black text-slate-500 uppercase mb-1 ml-1">{label}</label>
        <input type="number" step="0.1" value={value} onChange={e => onChange(e.target.value)} className="w-full bg-slate-900 border border-white/5 rounded-xl p-3 text-center font-bold text-white focus:border-primary-color outline-none" />
    </div>
);

const SomatotypeSlider: React.FC<{ label: string; value: number; onChange: (v: number) => void }> = ({ label, value, onChange }) => (
    <div className="mb-4 last:mb-0">
        <label className="text-xs font-bold text-slate-300 flex justify-between uppercase mb-1"><span>{label}</span> <span className="text-primary-color">{value}</span></label>
        <input type="range" min="1" max="7" value={value} onChange={e => onChange(parseInt(e.target.value))} className="w-full h-1.5 bg-slate-800 rounded-full appearance-none cursor-pointer accent-primary-color" />
    </div>
);

const AthleteIDDashboard: React.FC<AthleteIDDashboardProps> = ({ isOpen, onClose, onNavigate, onSettingsClick }) => {
    const { settings, bodyProgress, history, programs, biomechanicalData, activeSubTabs } = useAppState();
    const { setSettings, addToast, setBiomechanicalData } = useAppDispatch();
    
    const [activeTab, setActiveTab] = useState<'id' | 'edit'>('id');
    const [editSubTab, setEditSubTab] = useState<'vitals' | 'anatomy' | 'nutrition'>('vitals');
    const [isSharing, setIsSharing] = useState(false);
    const [isPhotoModalOpen, setIsPhotoModalOpen] = useState(false);

    // Profile Editing Local State
    const [localSettings, setLocalSettings] = useState<Settings>(() => JSON.parse(JSON.stringify(settings)));
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        if (isOpen) {
            setLocalSettings(JSON.parse(JSON.stringify(settings)));
        }
    }, [isOpen, settings]);

    // Calculate hasChanges
    const hasChanges = useMemo(() => JSON.stringify(settings) !== JSON.stringify(localSettings), [settings, localSettings]);

    const handleSettingChange = <K extends keyof Settings>(key: K, value: Settings[K]) => {
        setLocalSettings(prev => ({ ...prev, [key]: value }));
    };

    const handleVitalsChange = <K extends keyof Settings['userVitals']>(key: K, value: Settings['userVitals'][K]) => {
        setLocalSettings(prev => {
            const newVitals = { ...prev.userVitals, [key]: value };
            if ((key === 'targetWeight' || key === 'targetDate') && value) {
                if (!prev.userVitals.targetStartDate) {
                    newVitals.targetStartDate = new Date().toISOString().split('T')[0];
                    newVitals.targetStartWeight = prev.userVitals.weight || 0;
                }
            }
            return { ...prev, userVitals: newVitals };
        });
    };

    const handleSaveProfile = async () => {
        setIsSaving(true);
        try {
            setSettings(localSettings);
            
            // Handle Biomechanics update logic
            const currentBio = (biomechanicalData || {}) as Partial<BiomechanicalData>;
            const localBio = {
                height: localSettings.userVitals.height || 0,
                wingspan: localSettings.userVitals.wingspan || 0,
                torsoLength: localSettings.userVitals.torsoLength || 0,
                femurLength: localSettings.userVitals.femurLength || 0,
                tibiaLength: localSettings.userVitals.tibiaLength || 0,
                humerusLength: localSettings.userVitals.humerusLength || 0,
                forearmLength: localSettings.userVitals.forearmLength || 0
            };
            
            const hasBioChanged = Object.keys(localBio).some(k => (currentBio as any)[k] !== (localBio as any)[k]);
            const isBioComplete = Object.values(localBio).every(val => val > 0);
            
            if (hasBioChanged && isBioComplete) {
                await setBiomechanicalData(localBio as BiomechanicalData);
            }
            
            addToast("Perfil actualizado.", "success");
            setActiveTab('id');
        } finally {
            setIsSaving(false);
        }
    };

    const latestWeight = useMemo(() => {
        const sorted = [...bodyProgress].filter(l => l.weight).sort((a,b) => new Date(b.date).getTime() - new Date(a.date).getTime());
        return sorted[sorted.length - 1]?.weight || settings.userVitals.weight;
    }, [bodyProgress, settings.userVitals.weight]);

    const ffmiValue = useMemo(() => {
        const h = settings.userVitals.height;
        const w = latestWeight;
        const bf = settings.userVitals.bodyFatPercentage;
        if (h && w && bf !== undefined) {
            const result = calculateFFMI(h, w, bf);
            return result ? result.normalizedFfmi : 'N/A';
        }
        return 'N/A';
    }, [settings.userVitals, latestWeight]);

    const ipfPoints = useMemo(() => {
        let total = 0;
        const liftKeywords = { squat: ['sentadilla'], bench: ['press de banca', 'bench press'], deadlift: ['peso muerto', 'deadlift'] };
        const maxLifts = { squat: 0, bench: 0, deadlift: 0 };

        history.forEach(log => log.completedExercises.forEach(ex => {
            const name = ex.exerciseName.toLowerCase();
            const max1RM = Math.max(...ex.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)));
            if (liftKeywords.squat.some(k => name.includes(k))) maxLifts.squat = Math.max(maxLifts.squat, max1RM);
            if (liftKeywords.bench.some(k => name.includes(k))) maxLifts.bench = Math.max(maxLifts.bench, max1RM);
            if (liftKeywords.deadlift.some(k => name.includes(k))) maxLifts.deadlift = Math.max(maxLifts.deadlift, max1RM);
        }));

        total = maxLifts.squat + maxLifts.bench + maxLifts.deadlift;
        if (total === 0 || !settings.userVitals.weight) return '---';
        
        return calculateIPFGLPoints(total, settings.userVitals.weight, {
            gender: settings.userVitals.gender || 'male',
            equipment: 'classic',
            lift: 'total',
            weightUnit: settings.weightUnit
        }).toFixed(0);
    }, [history, settings]);

    const strengthRecords = useMemo<RecordData[]>(() => {
        const recordsMap = new Map<string, RecordData>();
        const exercisesOfInterest = new Set<string>();
        const exerciseGoals = new Map<string, number>();

        programs.forEach(p => {
             if (p.goals) {
                 if (p.goals.squat1RM) exerciseGoals.set('Sentadilla', p.goals.squat1RM);
                 if (p.goals.bench1RM) exerciseGoals.set('Press de Banca', p.goals.bench1RM);
                 if (p.goals.deadlift1RM) exerciseGoals.set('Peso Muerto', p.goals.deadlift1RM);
             }
             p.macrocycles.flatMap(m => (m.blocks||[]).flatMap(b => b.mesocycles.flatMap(me => me.weeks.flatMap(w => w.sessions.flatMap(s => s.exercises))))).forEach(ex => {
                 if (ex.isStarTarget || ex.isFavorite) {
                     const nameLower = ex.name.toLowerCase();
                     let key = ex.name;
                     if (nameLower.includes('sentadilla')) key = 'Sentadilla';
                     else if (nameLower.includes('press de banca')) key = 'Press de Banca';
                     else if (nameLower.includes('peso muerto')) key = 'Peso Muerto';
                     exercisesOfInterest.add(key);
                     if (ex.goal1RM) exerciseGoals.set(key, ex.goal1RM);
                 }
             });
        });

        history.forEach(log => log.completedExercises.forEach(ex => {
            const nameLower = ex.exerciseName.toLowerCase();
            let key = ex.exerciseName;
            if (nameLower.includes('sentadilla')) key = 'Sentadilla';
            else if (nameLower.includes('press de banca')) key = 'Press de Banca';
            else if (nameLower.includes('peso muerto')) key = 'Peso Muerto';

            if (exercisesOfInterest.has(key) || ['Sentadilla', 'Press de Banca', 'Peso Muerto'].includes(key)) {
                 const sessionMax = Math.max(...ex.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)));
                 if (sessionMax > 0) {
                     if (!recordsMap.has(key)) {
                         recordsMap.set(key, { name: key, oneRM: 0, goal1RM: exerciseGoals.get(key), history: [], isFavorite: exercisesOfInterest.has(key) });
                     }
                     const record = recordsMap.get(key)!;
                     if (sessionMax > record.oneRM) record.oneRM = sessionMax;
                     record.history.push({ date: log.date, value: sessionMax });
                 }
            }
        }));

        return Array.from(recordsMap.values()).sort((a,b) => b.oneRM - a.oneRM);
    }, [history, programs]);

    const athleteTitle = useMemo(() => {
        const topLift = strengthRecords[0];
        if (!topLift) return "Aspirante";
        const ratio = topLift.oneRM / (settings.userVitals.weight || 75);
        if (ratio > 2.5) return "Élite";
        if (ratio > 2.0) return "Avanzado";
        if (ratio > 1.5) return "Intermedio";
        return "Novato";
    }, [strengthRecords, settings.userVitals.weight]);

    const handleShareCard = async () => {
        setIsSharing(true);
        await shareElementAsImage('athlete-id-card-content', 'Mi Athlete ID', 'Nivel Prime alcanzado.');
        setIsSharing(false);
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-[200] flex flex-col bg-black overflow-hidden animate-fade-in text-white font-sans selection:bg-primary-color/30">
            <ProfilePictureModal 
                isOpen={isPhotoModalOpen} 
                onClose={() => setIsPhotoModalOpen(false)} 
                currentPicture={localSettings.profilePicture}
                onSave={(pic) => handleSettingChange('profilePicture', pic)}
            />

            {/* TOP NAVIGATION BAR */}
            <div className="flex justify-between items-center px-4 pt-6 pb-4 bg-black/80 backdrop-blur-xl border-b border-white/5 sticky top-0 z-50">
                <button onClick={onClose} className="p-2 -ml-2 text-slate-400 hover:text-white transition-colors rounded-full hover:bg-white/5"><XIcon size={24} /></button>
                
                <div className="flex bg-[#111] p-1 rounded-full border border-white/10">
                    <button onClick={() => setActiveTab('id')} className={`px-5 py-1.5 rounded-full text-[10px] font-black uppercase transition-all duration-300 ${activeTab === 'id' ? 'bg-white text-black shadow-lg shadow-white/10' : 'text-slate-500 hover:text-white'}`}>Athlete ID</button>
                    <button onClick={() => setActiveTab('edit')} className={`px-5 py-1.5 rounded-full text-[10px] font-black uppercase transition-all duration-300 ${activeTab === 'edit' ? 'bg-white text-black shadow-lg shadow-white/10' : 'text-slate-500 hover:text-white'}`}>Editar</button>
                </div>

                <div className="flex gap-2">
                    {activeTab === 'id' && (
                        <button onClick={handleShareCard} disabled={isSharing} className={`p-2 rounded-full border border-white/10 bg-[#111] text-slate-400 hover:text-cyan-400 hover:border-cyan-500/30 transition-colors ${isSharing ? 'animate-pulse' : ''}`}><LinkIcon size={18} /></button>
                    )}
                    <button onClick={() => { onSettingsClick(); onClose(); }} className="p-2 rounded-full border border-white/10 bg-[#111] text-slate-400 hover:text-white transition-colors"><SettingsIcon size={18} /></button>
                </div>
            </div>

            <div className="flex-grow overflow-y-auto custom-scrollbar p-0">
                
                {/* --- TAB: ATHLETE ID (VIEW MODE) --- */}
                {activeTab === 'id' ? (
                    <div className="space-y-8 p-4 pb-32">
                        {/* ID CARD VISUALIZER */}
                        <div id="athlete-id-card-content" className="relative w-full aspect-[4/5] max-h-[500px] mx-auto bg-black rounded-[2rem] border border-white/10 overflow-hidden shadow-2xl flex flex-col">
                            {/* Background Elements */}
                            <div className="absolute inset-0 bg-gradient-to-br from-[#111] to-black z-0" />
                            <div className="absolute top-0 inset-x-0 h-32 bg-gradient-to-b from-blue-900/20 to-transparent pointer-events-none" />
                            <div className="absolute -bottom-20 -right-20 w-64 h-64 bg-primary-color/10 blur-[80px] rounded-full pointer-events-none" />
                            
                            {/* Top Details */}
                            <div className="relative z-10 flex justify-between items-start p-6">
                                <div className="flex flex-col">
                                    <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Nivel</span>
                                    <span className="text-xl font-black text-white uppercase tracking-tighter italic">{athleteTitle}</span>
                                </div>
                                <div className="bg-white/5 border border-white/10 backdrop-blur-md px-3 py-1 rounded-full flex items-center gap-1.5">
                                    <div className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse" />
                                    <span className="text-[9px] font-bold text-slate-300 uppercase tracking-wider">Activo</span>
                                </div>
                            </div>

                            {/* Avatar & Name */}
                            <div className="relative z-10 flex flex-col items-center mt-2">
                                <div className="w-32 h-32 rounded-full p-1 bg-gradient-to-b from-white/20 to-white/5 border border-white/10 shadow-2xl relative group">
                                    <div className="w-full h-full rounded-full overflow-hidden bg-black relative">
                                         {settings.profilePicture ? (
                                            <img src={settings.profilePicture} alt="Avatar" className="w-full h-full object-cover" />
                                        ) : (
                                            <div className="w-full h-full flex items-center justify-center text-slate-700 bg-slate-900"><UserBadgeIcon size={48}/></div>
                                        )}
                                    </div>
                                    <div className="absolute bottom-0 right-0 bg-black border border-white/20 rounded-full p-1.5 shadow-lg">
                                        <div className="w-3 h-3 bg-gradient-to-tr from-yellow-400 to-orange-500 rounded-full" />
                                    </div>
                                </div>
                                <h1 className="text-3xl font-black text-white mt-4 uppercase tracking-tighter">{settings.username || 'Atleta'}</h1>
                                <p className="text-xs font-bold text-slate-500 uppercase tracking-[0.2em] mt-1">{settings.athleteType}</p>
                            </div>

                            {/* Main Stats Grid */}
                            <div className="relative z-10 mt-auto p-6">
                                <div className="grid grid-cols-3 gap-3">
                                    <div className="flex flex-col items-center p-3 bg-white/5 rounded-2xl border border-white/5 backdrop-blur-sm">
                                        <span className="text-2xl font-black text-white">{latestWeight || '--'}</span>
                                        <span className="text-[8px] font-bold text-slate-500 uppercase tracking-widest">Peso</span>
                                    </div>
                                    <div className="flex flex-col items-center p-3 bg-white/5 rounded-2xl border border-white/5 backdrop-blur-sm">
                                        <span className="text-2xl font-black text-white">{settings.userVitals.bodyFatPercentage || '--'}%</span>
                                        <span className="text-[8px] font-bold text-slate-500 uppercase tracking-widest">Grasa</span>
                                    </div>
                                    <div className="flex flex-col items-center p-3 bg-white/5 rounded-2xl border border-white/5 backdrop-blur-sm">
                                        <span className="text-2xl font-black text-cyan-400">{ffmiValue}</span>
                                        <span className="text-[8px] font-bold text-slate-500 uppercase tracking-widest">FFMI</span>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* SECTION: PERFORMANCE */}
                        <div className="space-y-4">
                            <SectionHeader title="Métricas de Rendimiento" icon={<ActivityIcon size={16}/>} />
                            
                            <div className="bg-[#0A0A0A] border border-white/5 rounded-3xl p-5 relative overflow-hidden">
                                <div className="absolute top-0 right-0 w-32 h-32 bg-yellow-500/5 blur-[50px] rounded-full pointer-events-none" />
                                <div className="flex justify-between items-center mb-6">
                                    <div>
                                        <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">IPF GL Points</p>
                                        <p className="text-4xl font-black text-white tracking-tighter">{ipfPoints}</p>
                                    </div>
                                    <TrophyIcon size={32} className="text-yellow-500/80" />
                                </div>
                                <div className="space-y-3">
                                    {strengthRecords.slice(0, 3).map((record, i) => (
                                        <div key={i} className="flex justify-between items-center py-2 border-b border-white/5 last:border-0">
                                            <span className="text-xs font-bold text-slate-300">{record.name}</span>
                                            <span className="text-sm font-black text-white">{record.oneRM} <span className="text-[10px] text-slate-600 font-bold">{settings.weightUnit}</span></span>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        </div>

                        {/* SECTION: PROGRESS CHARTS */}
                        <div className="space-y-4">
                            <SectionHeader title="Evolución Física" icon={<TrendingUpIcon size={16}/>} />
                            <div className="bg-[#0A0A0A] border border-white/5 rounded-3xl p-1 overflow-hidden">
                                <BodyWeightChart progress={bodyProgress} settings={settings} />
                            </div>
                            <div className="grid grid-cols-2 gap-3">
                                <div className="bg-[#0A0A0A] border border-white/5 rounded-3xl p-1 overflow-hidden"><BodyFatChart /></div>
                                <div className="bg-[#0A0A0A] border border-white/5 rounded-3xl p-1 overflow-hidden"><FFMIChart /></div>
                            </div>
                        </div>
                    </div>
                ) : (
                    /* --- TAB: EDIT MODE --- */
                    <div className="p-4 pb-32 animate-fade-in space-y-8">
                        {/* Sub-Nav Pills */}
                        <div className="flex gap-2 overflow-x-auto hide-scrollbar pb-2">
                            {[
                                { id: 'vitals', label: 'Básicos', icon: <BodyIcon size={14}/> },
                                { id: 'anatomy', label: 'Biometría', icon: <RulerIcon size={14}/> },
                                { id: 'nutrition', label: 'Metas', icon: <UtensilsIcon size={14}/> }
                            ].map(tab => (
                                <button 
                                    key={tab.id}
                                    onClick={() => setEditSubTab(tab.id as any)}
                                    className={`flex items-center gap-2 px-4 py-2.5 rounded-xl border transition-all whitespace-nowrap ${editSubTab === tab.id ? 'bg-white text-black border-white' : 'bg-[#0A0A0A] text-slate-500 border-white/10 hover:border-white/30'}`}
                                >
                                    {tab.icon}
                                    <span className="text-[10px] font-black uppercase tracking-wider">{tab.label}</span>
                                </button>
                            ))}
                        </div>

                        {/* EDIT FORMS */}
                        <div className="bg-[#111] border border-white/5 rounded-3xl p-5 space-y-6 relative">
                             {/* Save Floater */}
                             {hasChanges && (
                                <div className="absolute -top-16 left-0 right-0 flex justify-center animate-bounce z-50">
                                    <Button onClick={handleSaveProfile} isLoading={isSaving} className="!py-2 !px-6 !text-xs font-black uppercase shadow-xl shadow-primary-color/30">
                                        <SaveIcon size={14} className="mr-2"/> Guardar Cambios
                                    </Button>
                                </div>
                            )}

                            {editSubTab === 'vitals' && (
                                <div className="space-y-6 animate-fade-in">
                                    <div className="flex justify-center mb-6">
                                        <div className="relative group cursor-pointer" onClick={() => setIsPhotoModalOpen(true)}>
                                            <div className="w-24 h-24 rounded-full border-2 border-white/10 overflow-hidden bg-black">
                                                {localSettings.profilePicture ? <img src={localSettings.profilePicture} className="w-full h-full object-cover opacity-80 group-hover:opacity-100 transition-opacity"/> : <div className="w-full h-full flex items-center justify-center text-slate-600"><CameraIcon size={32}/></div>}
                                            </div>
                                            <div className="absolute inset-0 flex items-center justify-center bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity rounded-full">
                                                <PencilIcon size={20} className="text-white"/>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="space-y-4">
                                        <InputField label="Nombre de Usuario" value={localSettings.username || ''} onChange={v => handleSettingChange('username', v)} placeholder="Tu nombre" />
                                        
                                        <div className="grid grid-cols-2 gap-4">
                                            <InputField label="Edad" value={localSettings.userVitals.age || ''} onChange={v => handleVitalsChange('age', parseInt(v))} type="number" placeholder="25" />
                                            <div className="bg-[#0A0A0A] border border-white/10 rounded-xl px-4 py-3">
                                                <label className="block text-[9px] font-black text-slate-500 uppercase tracking-widest mb-1">Género</label>
                                                <select value={localSettings.userVitals.gender || ''} onChange={e => handleVitalsChange('gender', e.target.value as any)} className="w-full bg-transparent border-none p-0 text-white font-bold text-sm focus:ring-0">
                                                    <option value="male">Hombre</option><option value="female">Mujer</option><option value="other">Otro</option>
                                                </select>
                                            </div>
                                            <InputField label={`Peso (${settings.weightUnit})`} value={localSettings.userVitals.weight || ''} onChange={v => handleVitalsChange('weight', parseFloat(v))} type="number" placeholder="75.5" />
                                            <InputField label="Altura (cm)" value={localSettings.userVitals.height || ''} onChange={v => handleVitalsChange('height', parseFloat(v))} type="number" placeholder="175" />
                                            <InputField label="% Grasa" value={localSettings.userVitals.bodyFatPercentage || ''} onChange={v => handleVitalsChange('bodyFatPercentage', parseFloat(v))} type="number" placeholder="15" />
                                        </div>
                                    </div>
                                </div>
                            )}

                            {editSubTab === 'anatomy' && (
                                <div className="space-y-6 animate-fade-in">
                                    <div className="p-4 bg-blue-900/10 border border-blue-500/20 rounded-2xl mb-4">
                                        <div className="flex gap-3">
                                            <BrainIcon className="text-blue-400 shrink-0" />
                                            <p className="text-xs text-blue-200/80 leading-relaxed">
                                                La IA usa estas medidas para calcular tu <strong>análisis biomecánico</strong> y sugerir ejercicios óptimos para tus palancas.
                                            </p>
                                        </div>
                                    </div>
                                    <div className="grid grid-cols-2 gap-4">
                                        <MeasurementInput label="Envergadura" value={String(localSettings.userVitals.wingspan || '')} onChange={v => handleVitalsChange('wingspan', parseFloat(v))} />
                                        <MeasurementInput label="Torso" value={String(localSettings.userVitals.torsoLength || '')} onChange={v => handleVitalsChange('torsoLength', parseFloat(v))} />
                                        <MeasurementInput label="Fémur" value={String(localSettings.userVitals.femurLength || '')} onChange={v => handleVitalsChange('femurLength', parseFloat(v))} />
                                        <MeasurementInput label="Tibia" value={String(localSettings.userVitals.tibiaLength || '')} onChange={v => handleVitalsChange('tibiaLength', parseFloat(v))} />
                                        <MeasurementInput label="Long. Húmero" value={String(localSettings.userVitals.humerusLength || '')} onChange={v => handleVitalsChange('humerusLength', parseFloat(v))} />
                                        <MeasurementInput label="Long. Antebrazo" value={String(localSettings.userVitals.forearmLength || '')} onChange={v => handleVitalsChange('forearmLength', parseFloat(v))} />
                                    </div>
                                    
                                    <div className="pt-4 border-t border-white/5">
                                        <h4 className="text-xs font-black text-white uppercase mb-4">Somatotipo</h4>
                                        <SomatotypeSlider label="Endomorfo" value={localSettings.userVitals.somatotype?.endomorph || 1} onChange={v => handleVitalsChange('somatotype', {...(localSettings.userVitals.somatotype || {endomorph:1, mesomorph:1, ectomorph:1}), endomorph: v})} />
                                        <SomatotypeSlider label="Mesomorfo" value={localSettings.userVitals.somatotype?.mesomorph || 1} onChange={v => handleVitalsChange('somatotype', {...(localSettings.userVitals.somatotype || {endomorph:1, mesomorph:1, ectomorph:1}), mesomorph: v})} />
                                        <SomatotypeSlider label="Ectomorfo" value={localSettings.userVitals.somatotype?.ectomorph || 1} onChange={v => handleVitalsChange('somatotype', {...(localSettings.userVitals.somatotype || {endomorph:1, mesomorph:1, ectomorph:1}), ectomorph: v})} />
                                    </div>
                                </div>
                            )}

                            {editSubTab === 'nutrition' && (
                                <div className="space-y-6 animate-fade-in">
                                    <div className="bg-[#0A0A0A] border border-white/10 rounded-2xl p-4">
                                        <div className="flex justify-between items-center mb-4">
                                            <h4 className="text-xs font-black text-white uppercase tracking-widest flex items-center gap-2"><TargetIcon size={14} className="text-red-500"/> Misión Principal</h4>
                                            {localSettings.userVitals.targetWeight && <button onClick={() => handleVitalsChange('targetWeight', undefined)} className="text-[9px] font-bold text-red-400 hover:text-red-300 uppercase">Borrar</button>}
                                        </div>
                                        <div className="grid grid-cols-2 gap-4">
                                            <div>
                                                <label className="text-[9px] font-black text-slate-500 uppercase">Peso Meta</label>
                                                <input type="number" step="0.1" value={localSettings.userVitals.targetWeight || ''} onChange={e => handleVitalsChange('targetWeight', parseFloat(e.target.value))} className="w-full bg-transparent border-b border-white/10 py-2 text-xl font-black text-white focus:border-primary-color outline-none" placeholder="0.0" />
                                            </div>
                                            <div>
                                                <label className="text-[9px] font-black text-slate-500 uppercase">Fecha Límite</label>
                                                <input type="date" value={localSettings.userVitals.targetDate || ''} onChange={e => handleVitalsChange('targetDate', e.target.value)} className="w-full bg-transparent border-b border-white/10 py-2 text-sm font-bold text-white focus:border-primary-color outline-none" />
                                            </div>
                                        </div>
                                    </div>

                                    <div className="space-y-4">
                                        <h4 className="text-xs font-black text-slate-400 uppercase tracking-widest">Objetivos Diarios</h4>
                                        <InputField label="Calorías (kcal)" value={localSettings.dailyCalorieGoal || ''} onChange={v => handleSettingChange('dailyCalorieGoal', parseInt(v))} type="number" />
                                        <div className="grid grid-cols-3 gap-3">
                                            <InputField label="Proteínas (g)" value={localSettings.dailyProteinGoal || ''} onChange={v => handleSettingChange('dailyProteinGoal', parseInt(v))} type="number" />
                                            <InputField label="Carbs (g)" value={localSettings.dailyCarbGoal || ''} onChange={v => handleSettingChange('dailyCarbGoal', parseInt(v))} type="number" />
                                            <InputField label="Grasas (g)" value={localSettings.dailyFatGoal || ''} onChange={v => handleSettingChange('dailyFatGoal', parseInt(v))} type="number" />
                                        </div>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default AthleteIDDashboard;
