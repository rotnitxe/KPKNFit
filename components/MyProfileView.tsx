// components/MyProfileView.tsx
// Vista de Mi Perfil - estilo liquid glass white como Home.tsx
// Rescata elementos del AthleteIDDashboard pero con diseño Material 3 claro

import React, { useState, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { 
    UserBadgeIcon, 
    CameraIcon, 
    PencilIcon, 
    SaveIcon,
    BodyIcon,
    RulerIcon,
    TargetIcon,
    TrophyIcon,
    ActivityIcon,
    CalendarIcon
} from './icons';
import { calculateFFMI, calculateIPFGLPoints, calculateBrzycki1RM } from '../utils/calculations';
import ProfilePictureModal from './ProfilePictureModal';

type EditTab = 'vitals' | 'anatomy' | 'goals';

const MyProfileView: React.FC = () => {
    const { settings, bodyProgress, history, programs, biomechanicalData } = useAppState();
    const { setSettings, addToast } = useAppDispatch();
    
    const [editMode, setEditMode] = useState(false);
    const [editTab, setEditTab] = useState<EditTab>('vitals');
    const [isPhotoModalOpen, setIsPhotoModalOpen] = useState(false);
    
    // Estado local para edición
    const [localSettings, setLocalSettings] = useState(() => JSON.parse(JSON.stringify(settings)));
    const [localBio, setLocalBio] = useState(() => JSON.parse(JSON.stringify(biomechanicalData || {})));

    // Calcular peso más reciente
    const latestWeight = useMemo(() => {
        const sorted = [...bodyProgress].filter(l => l.weight).sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
        return sorted[sorted.length - 1]?.weight || settings.userVitals.weight;
    }, [bodyProgress, settings.userVitals.weight]);

    // Calcular FFMI
    const ffmiValue = useMemo(() => {
        const h = settings.userVitals.height;
        const w = latestWeight;
        const bf = settings.userVitals.bodyFatPercentage;
        if (h && w && bf !== undefined) {
            const result = calculateFFMI(h, w, bf);
            return result ? result.normalizedFfmi.toFixed(1) : 'N/A';
        }
        return 'N/A';
    }, [settings.userVitals, latestWeight]);

    // Calcular IPF Points
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

    // Determinar nivel del atleta
    const athleteLevel = useMemo(() => {
        const squat = history.reduce((max, log) => {
            const ex = log.completedExercises.find(e => e.exerciseName.toLowerCase().includes('sentadilla'));
            if (!ex) return max;
            const rm = Math.max(...ex.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)));
            return Math.max(max, rm);
        }, 0);
        
        const ratio = squat / (settings.userVitals.weight || 75);
        if (ratio > 2.5) return 'Élite';
        if (ratio > 2.0) return 'Avanzado';
        if (ratio > 1.5) return 'Intermedio';
        return 'Novato';
    }, [history, settings.userVitals.weight]);

    const handleSave = () => {
        setSettings(localSettings);
        setBiomechanicalData(localBio);
        addToast('Perfil actualizado.', 'success');
        setEditMode(false);
    };

    const handleCancel = () => {
        setLocalSettings(JSON.parse(JSON.stringify(settings)));
        setLocalBio(JSON.parse(JSON.stringify(biomechanicalData || {})));
        setEditMode(false);
    };

    return (
        <div className="min-h-full flex flex-col bg-[var(--md-sys-color-surface)] pb-8">
            <ProfilePictureModal
                isOpen={isPhotoModalOpen}
                onClose={() => setIsPhotoModalOpen(false)}
                currentPicture={localSettings.profilePicture}
                onSave={(pic) => setLocalSettings(prev => ({ ...prev, profilePicture: pic }))}
            />

            {/* Header */}
            <div className="px-4 pt-6 pb-4">
                <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2">
                        <UserBadgeIcon size={20} className="text-[#6750A4]" />
                        <p className="text-[9px] font-black uppercase tracking-[0.2em] text-[#49454F]">Perfil de Atleta</p>
                    </div>
                    <div className="flex gap-2">
                        {!editMode ? (
                            <button
                                onClick={() => setEditMode(true)}
                                className="px-4 py-2 rounded-full bg-[var(--md-sys-color-primary)] text-white text-[10px] font-black uppercase tracking-wider flex items-center gap-1.5"
                            >
                                <PencilIcon size={14} />
                                Editar
                            </button>
                        ) : (
                            <>
                                <button
                                    onClick={handleCancel}
                                    className="px-4 py-2 rounded-full bg-[var(--md-sys-color-surface-container-high)] text-[#1D1B20] text-[10px] font-black uppercase tracking-wider"
                                >
                                    Cancelar
                                </button>
                                <button
                                    onClick={handleSave}
                                    className="px-4 py-2 rounded-full bg-[var(--md-sys-color-primary)] text-white text-[10px] font-black uppercase tracking-wider flex items-center gap-1.5"
                                >
                                    <SaveIcon size={14} />
                                    Guardar
                                </button>
                            </>
                        )}
                    </div>
                </div>
            </div>

            {/* Profile Card */}
            <div className="px-4 pb-6">
                <div className="rounded-[32px] border border-black/[0.04] bg-gradient-to-b from-white/90 to-white/70 backdrop-blur-xl p-6 relative overflow-hidden">
                    {/* Background decoration */}
                    <div className="absolute -top-10 -right-10 w-40 h-40 bg-[#6750A4]/10 blur-[60px] rounded-full pointer-events-none" />
                    <div className="absolute -bottom-10 -left-10 w-40 h-40 bg-[#006A6A]/10 blur-[60px] rounded-full pointer-events-none" />

                    <div className="relative flex flex-col items-center">
                        {/* Profile Picture */}
                        <div className="relative mb-4">
                            <div className="w-28 h-28 rounded-full border-4 border-white shadow-lg overflow-hidden bg-[#ECE6F0]">
                                {localSettings.profilePicture ? (
                                    <img src={localSettings.profilePicture} alt="Profile" className="w-full h-full object-cover" />
                                ) : (
                                    <div className="w-full h-full flex items-center justify-center">
                                        <UserBadgeIcon size={48} className="text-[#6750A4]/40" />
                                    </div>
                                )}
                            </div>
                            {editMode && (
                                <button
                                    onClick={() => setIsPhotoModalOpen(true)}
                                    className="absolute bottom-0 right-0 w-9 h-9 rounded-full bg-[var(--md-sys-color-primary)] text-white flex items-center justify-center shadow-lg"
                                >
                                    <CameraIcon size={16} />
                                </button>
                            )}
                        </div>

                        {/* Name & Level */}
                        <h1 className="text-[26px] font-black text-[#1D1B20] uppercase tracking-tight">
                            {localSettings.username || 'Atleta'}
                        </h1>
                        <div className="flex items-center gap-2 mt-1.5">
                            <span className="px-3 py-1 rounded-full bg-[#6750A4]/10 text-[#6750A4] text-[9px] font-black uppercase tracking-wider">
                                {athleteLevel}
                            </span>
                            <span className="text-[11px] text-[#49454F] font-medium">
                                {localSettings.athleteType || 'Powerlifter'}
                            </span>
                        </div>

                        {/* Quick Stats */}
                        <div className="grid grid-cols-3 gap-4 mt-6 w-full">
                            <div className="text-center">
                                <p className="text-[22px] font-black text-[#1D1B20]">{latestWeight || '--'}</p>
                                <p className="text-[8px] font-black uppercase tracking-wider text-[#49454F]">Peso ({settings.weightUnit})</p>
                            </div>
                            <div className="text-center">
                                <p className="text-[22px] font-black text-[#1D1B20]">{settings.userVitals.bodyFatPercentage || '--'}%</p>
                                <p className="text-[8px] font-black uppercase tracking-wider text-[#49454F]">Grasa</p>
                            </div>
                            <div className="text-center">
                                <p className="text-[22px] font-black text-[#6750A4]">{ffmiValue}</p>
                                <p className="text-[8px] font-black uppercase tracking-wider text-[#49454F]">FFMI</p>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            {/* Performance Card */}
            <div className="px-4 pb-6">
                <div className="rounded-[24px] border border-black/[0.04] bg-white/70 backdrop-blur-xl p-5">
                    <div className="flex items-center gap-2 mb-4">
                        <TrophyIcon size={18} className="text-[#B3261E]" />
                        <h2 className="text-[13px] font-black text-[#1D1B20] uppercase tracking-wide">Rendimiento</h2>
                    </div>
                    
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <p className="text-[9px] font-black uppercase tracking-wider text-[#49454F] mb-0.5">IPF GL Points</p>
                            <p className="text-[32px] font-black text-[#1D1B20] tracking-tighter">{ipfPoints}</p>
                        </div>
                        <TrophyIcon size={40} className="text-[#B3261E]/40" />
                    </div>

                    <div className="space-y-2">
                        {history.length > 0 && (
                            <>
                                <div className="flex justify-between items-center py-2 border-b border-black/[0.05]">
                                    <span className="text-[11px] font-medium text-[#49454F]">Sentadilla</span>
                                    <span className="text-[12px] font-black text-[#1D1B20]">
                                        {Math.max(...history.flatMap(log => 
                                            log.completedExercises
                                                .filter(e => e.exerciseName.toLowerCase().includes('sentadilla'))
                                                .flatMap(e => e.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)))
                                        ), 0)} {settings.weightUnit}
                                    </span>
                                </div>
                                <div className="flex justify-between items-center py-2 border-b border-black/[0.05]">
                                    <span className="text-[11px] font-medium text-[#49454F]">Press de Banca</span>
                                    <span className="text-[12px] font-black text-[#1D1B20]">
                                        {Math.max(...history.flatMap(log => 
                                            log.completedExercises
                                                .filter(e => e.exerciseName.toLowerCase().includes('press de banca') || e.exerciseName.toLowerCase().includes('bench'))
                                                .flatMap(e => e.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)))
                                        ), 0)} {settings.weightUnit}
                                    </span>
                                </div>
                                <div className="flex justify-between items-center py-2">
                                    <span className="text-[11px] font-medium text-[#49454F]">Peso Muerto</span>
                                    <span className="text-[12px] font-black text-[#1D1B20]">
                                        {Math.max(...history.flatMap(log => 
                                            log.completedExercises
                                                .filter(e => e.exerciseName.toLowerCase().includes('peso muerto') || e.exerciseName.toLowerCase().includes('deadlift'))
                                                .flatMap(e => e.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)))
                                        ), 0)} {settings.weightUnit}
                                    </span>
                                </div>
                            </>
                        )}
                    </div>
                </div>
            </div>

            {/* Edit Sections */}
            {editMode && (
                <div className="px-4 pb-8">
                    {/* Tabs */}
                    <div className="flex gap-2 mb-4 overflow-x-auto">
                        {[
                            { id: 'vitals', label: 'Básicos', icon: <BodyIcon size={14} /> },
                            { id: 'anatomy', label: 'Biometría', icon: <RulerIcon size={14} /> },
                            { id: 'goals', label: 'Metas', icon: <TargetIcon size={14} /> }
                        ].map(tab => (
                            <button
                                key={tab.id}
                                onClick={() => setEditTab(tab.id as EditTab)}
                                className={`flex items-center gap-2 px-4 py-2.5 rounded-xl border transition-all whitespace-nowrap ${
                                    editTab === tab.id
                                        ? 'bg-[var(--md-sys-color-primary)] text-white border-[var(--md-sys-color-primary)]'
                                        : 'bg-white/70 text-[#49454F] border-black/[0.08]'
                                }`}
                            >
                                {tab.icon}
                                <span className="text-[10px] font-black uppercase tracking-wider">{tab.label}</span>
                            </button>
                        ))}
                    </div>

                    {/* Vitals Section */}
                    {editTab === 'vitals' && (
                        <div className="rounded-[24px] border border-black/[0.04] bg-white/70 backdrop-blur-xl p-5 space-y-4">
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="text-[9px] font-black uppercase tracking-wider text-[#49454F] mb-1 block">Nombre</label>
                                    <input
                                        type="text"
                                        value={localSettings.username || ''}
                                        onChange={(e) => setLocalSettings(prev => ({ ...prev, username: e.target.value }))}
                                        className="w-full bg-white border border-black/[0.1] rounded-xl px-3 py-2.5 text-[13px] font-bold text-[#1D1B20] outline-none focus:border-[var(--md-sys-color-primary)]"
                                    />
                                </div>
                                <div>
                                    <label className="text-[9px] font-black uppercase tracking-wider text-[#49454F] mb-1 block">Tipo de Atleta</label>
                                    <input
                                        type="text"
                                        value={localSettings.athleteType || ''}
                                        onChange={(e) => setLocalSettings(prev => ({ ...prev, athleteType: e.target.value }))}
                                        className="w-full bg-white border border-black/[0.1] rounded-xl px-3 py-2.5 text-[13px] font-bold text-[#1D1B20] outline-none focus:border-[var(--md-sys-color-primary)]"
                                    />
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="text-[9px] font-black uppercase tracking-wider text-[#49454F] mb-1 block">Peso (kg)</label>
                                    <input
                                        type="number"
                                        value={localSettings.userVitals.weight || ''}
                                        onChange={(e) => setLocalSettings(prev => ({ 
                                            ...prev, 
                                            userVitals: { ...prev.userVitals, weight: parseFloat(e.target.value) }
                                        }))}
                                        className="w-full bg-white border border-black/[0.1] rounded-xl px-3 py-2.5 text-[13px] font-bold text-[#1D1B20] outline-none focus:border-[var(--md-sys-color-primary)]"
                                    />
                                </div>
                                <div>
                                    <label className="text-[9px] font-black uppercase tracking-wider text-[#49454F] mb-1 block">Altura (cm)</label>
                                    <input
                                        type="number"
                                        value={localSettings.userVitals.height || ''}
                                        onChange={(e) => setLocalSettings(prev => ({ 
                                            ...prev, 
                                            userVitals: { ...prev.userVitals, height: parseFloat(e.target.value) }
                                        }))}
                                        className="w-full bg-white border border-black/[0.1] rounded-xl px-3 py-2.5 text-[13px] font-bold text-[#1D1B20] outline-none focus:border-[var(--md-sys-color-primary)]"
                                    />
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="text-[9px] font-black uppercase tracking-wider text-[#49454F] mb-1 block">% Grasa</label>
                                    <input
                                        type="number"
                                        step="0.1"
                                        value={localSettings.userVitals.bodyFatPercentage || ''}
                                        onChange={(e) => setLocalSettings(prev => ({ 
                                            ...prev, 
                                            userVitals: { ...prev.userVitals, bodyFatPercentage: parseFloat(e.target.value) }
                                        }))}
                                        className="w-full bg-white border border-black/[0.1] rounded-xl px-3 py-2.5 text-[13px] font-bold text-[#1D1B20] outline-none focus:border-[var(--md-sys-color-primary)]"
                                    />
                                </div>
                                <div>
                                    <label className="text-[9px] font-black uppercase tracking-wider text-[#49454F] mb-1 block">% Músculo</label>
                                    <input
                                        type="number"
                                        step="0.1"
                                        value={localSettings.userVitals.muscleMassPercentage || ''}
                                        onChange={(e) => setLocalSettings(prev => ({ 
                                            ...prev, 
                                            userVitals: { ...prev.userVitals, muscleMassPercentage: parseFloat(e.target.value) }
                                        }))}
                                        className="w-full bg-white border border-black/[0.1] rounded-xl px-3 py-2.5 text-[13px] font-bold text-[#1D1B20] outline-none focus:border-[var(--md-sys-color-primary)]"
                                    />
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Anatomy Section */}
                    {editTab === 'anatomy' && (
                        <div className="rounded-[24px] border border-black/[0.04] bg-white/70 backdrop-blur-xl p-5 space-y-4">
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="text-[9px] font-black uppercase tracking-wider text-[#49454F] mb-1 block">Envergadura (cm)</label>
                                    <input
                                        type="number"
                                        value={localBio.wingspan || ''}
                                        onChange={(e) => setLocalBio(prev => ({ ...prev, wingspan: parseFloat(e.target.value) }))}
                                        className="w-full bg-white border border-black/[0.1] rounded-xl px-3 py-2.5 text-[13px] font-bold text-[#1D1B20] outline-none focus:border-[var(--md-sys-color-primary)]"
                                    />
                                </div>
                                <div>
                                    <label className="text-[9px] font-black uppercase tracking-wider text-[#49454F] mb-1 block">Torso (cm)</label>
                                    <input
                                        type="number"
                                        value={localBio.torsoLength || ''}
                                        onChange={(e) => setLocalBio(prev => ({ ...prev, torsoLength: parseFloat(e.target.value) }))}
                                        className="w-full bg-white border border-black/[0.1] rounded-xl px-3 py-2.5 text-[13px] font-bold text-[#1D1B20] outline-none focus:border-[var(--md-sys-color-primary)]"
                                    />
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="text-[9px] font-black uppercase tracking-wider text-[#49454F] mb-1 block">Fémur (cm)</label>
                                    <input
                                        type="number"
                                        value={localBio.femurLength || ''}
                                        onChange={(e) => setLocalBio(prev => ({ ...prev, femurLength: parseFloat(e.target.value) }))}
                                        className="w-full bg-white border border-black/[0.1] rounded-xl px-3 py-2.5 text-[13px] font-bold text-[#1D1B20] outline-none focus:border-[var(--md-sys-color-primary)]"
                                    />
                                </div>
                                <div>
                                    <label className="text-[9px] font-black uppercase tracking-wider text-[#49454F] mb-1 block">Tibia (cm)</label>
                                    <input
                                        type="number"
                                        value={localBio.tibiaLength || ''}
                                        onChange={(e) => setLocalBio(prev => ({ ...prev, tibiaLength: parseFloat(e.target.value) }))}
                                        className="w-full bg-white border border-black/[0.1] rounded-xl px-3 py-2.5 text-[13px] font-bold text-[#1D1B20] outline-none focus:border-[var(--md-sys-color-primary)]"
                                    />
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Goals Section */}
                    {editTab === 'goals' && (
                        <div className="rounded-[24px] border border-black/[0.04] bg-white/70 backdrop-blur-xl p-5 space-y-4">
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="text-[9px] font-black uppercase tracking-wider text-[#49454F] mb-1 block">Peso Objetivo</label>
                                    <input
                                        type="number"
                                        value={localSettings.userVitals.targetWeight || ''}
                                        onChange={(e) => setLocalSettings(prev => ({ 
                                            ...prev, 
                                            userVitals: { ...prev.userVitals, targetWeight: parseFloat(e.target.value) }
                                        }))}
                                        className="w-full bg-white border border-black/[0.1] rounded-xl px-3 py-2.5 text-[13px] font-bold text-[#1D1B20] outline-none focus:border-[var(--md-sys-color-primary)]"
                                    />
                                </div>
                                <div>
                                    <label className="text-[9px] font-black uppercase tracking-wider text-[#49454F] mb-1 block">Fecha Objetivo</label>
                                    <input
                                        type="date"
                                        value={localSettings.userVitals.targetDate || ''}
                                        onChange={(e) => setLocalSettings(prev => ({ 
                                            ...prev, 
                                            userVitals: { ...prev.userVitals, targetDate: e.target.value }
                                        }))}
                                        className="w-full bg-white border border-black/[0.1] rounded-xl px-3 py-2.5 text-[13px] font-bold text-[#1D1B20] outline-none focus:border-[var(--md-sys-color-primary)]"
                                    />
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default MyProfileView;
