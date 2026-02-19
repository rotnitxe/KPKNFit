
// components/AthleteProfileView.tsx
import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { Settings, BiomechanicalData, BiomechanicalAnalysis } from '../types';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { calculateFFMI } from '../utils/calculations';
import Button from './ui/Button';
import Card from './ui/Card';
import { SaveIcon, ArrowLeftIcon, ActivityIcon, BrainIcon, UtensilsIcon, TrashIcon, PlusIcon, SparklesIcon, CameraIcon, PencilIcon, CalendarIcon, TargetIcon, XIcon, AlertTriangleIcon, CheckCircleIcon, InfoIcon } from './icons';
import ProfilePictureModal from './ProfilePictureModal';

const AthleteProfileView: React.FC = () => {
    const { settings, biomechanicalData, biomechanicalAnalysis, isOnline, activeSubTabs } = useAppState();
    const { setSettings, setBiomechanicalData, handleBack, addToast } = useAppDispatch();

    const activeTab = useMemo(() => (activeSubTabs['athlete-profile'] as 'vitals' | 'anatomy' | 'nutrition') || 'vitals', [activeSubTabs]);
    const [localSettings, setLocalSettings] = useState<Settings>(() => JSON.parse(JSON.stringify(settings)));
    const [isSaving, setIsSaving] = useState(false);
    const [isPhotoModalOpen, setIsPhotoModalOpen] = useState(false);

    useEffect(() => {
        setLocalSettings(JSON.parse(JSON.stringify(settings)));
    }, [settings]);

    const handleSettingChange = <K extends keyof Settings>(key: K, value: Settings[K]) => {
        setLocalSettings(prev => ({ ...prev, [key]: value }));
    };

    const handleVitalsChange = <K extends keyof Settings['userVitals']>(key: K, value: Settings['userVitals'][K]) => {
        setLocalSettings(prev => {
            const newVitals = { ...prev.userVitals, [key]: value };
            
            // Logic for Step 4.2: If targetWeight or targetDate is being set, and we don't have a start point, capture it now.
            if ((key === 'targetWeight' || key === 'targetDate') && value) {
                if (!prev.userVitals.targetStartDate) {
                    // Capture current state as the starting point of the mission
                    newVitals.targetStartDate = new Date().toISOString().split('T')[0];
                    newVitals.targetStartWeight = prev.userVitals.weight || 0;
                }
            }

            return {
                ...prev,
                userVitals: newVitals
            };
        });
    };
    
    const handleResetGoal = () => {
        setLocalSettings(prev => ({
            ...prev,
            userVitals: {
                ...prev.userVitals,
                targetWeight: undefined,
                targetDate: undefined,
                targetStartWeight: undefined,
                targetStartDate: undefined
            }
        }));
        addToast("Misión reiniciada. Define un nuevo objetivo cuando estés listo.", "suggestion");
    };
    
    const handleJointNoteChange = (index: number, field: 'joint' | 'note', value: string) => {
        const updatedNotes = [...(localSettings.userVitals.jointHealthNotes || [])];
        updatedNotes[index][field] = value;
        handleVitalsChange('jointHealthNotes', updatedNotes);
    };

    const addJointNote = () => {
        const newNotes = [...(localSettings.userVitals.jointHealthNotes || []), { joint: '', note: '' }];
        handleVitalsChange('jointHealthNotes', newNotes);
    };

    const removeJointNote = (index: number) => {
        const updatedNotes = (localSettings.userVitals.jointHealthNotes || []).filter((_, i) => i !== index);
        handleVitalsChange('jointHealthNotes', updatedNotes);
    };
    
    const handleMacroChange = (macro: 'dailyProteinGoal' | 'dailyCarbGoal' | 'dailyFatGoal', value: string) => {
        const numValue = parseInt(value, 10) || 0;
        setLocalSettings(prev => {
            const newSettings = { ...prev };
            (newSettings as any)[macro] = numValue;
    
            const p = newSettings.dailyProteinGoal || 0;
            const c = newSettings.dailyCarbGoal || 0;
            const f = newSettings.dailyFatGoal || 0;
    
            newSettings.dailyCalorieGoal = Math.round((p * 4) + (c * 4) + (f * 9));
            return newSettings;
        });
    };
    
    const handleCalorieChange = (value: string) => {
        const newCal = parseInt(value, 10) || 0;
        setLocalSettings(prev => {
            const p = prev.dailyProteinGoal || 0;
            const c = prev.dailyCarbGoal || 0;
            const f = prev.dailyFatGoal || 0;
    
            const currentCal = (p * 4) + (c * 4) + (f * 9);
    
            if (currentCal === 0) { // Default 40/40/20 split if no prior macros
                const newP = Math.round((newCal * 0.4) / 4);
                const newC = Math.round((newCal * 0.4) / 4);
                const newF = Math.round((newCal * 0.2) / 9);
                return { ...prev, dailyCalorieGoal: newCal, dailyProteinGoal: newP, dailyCarbGoal: newC, dailyFatGoal: newF };
            }
    
            const pRatio = (p * 4) / currentCal;
            const cRatio = (c * 4) / currentCal;
            const fRatio = (f * 9) / currentCal;
            
            const newP = Math.round((newCal * pRatio) / 4);
            const newC = Math.round((newCal * cRatio) / 4);
            const newF = Math.round((newCal * fRatio) / 9);
    
            return { ...prev, dailyCalorieGoal: newCal, dailyProteinGoal: newP, dailyCarbGoal: newC, dailyFatGoal: newF };
        });
    };

    const calculatedMetrics = useMemo(() => {
        const { age, weight, height, gender, activityLevel } = localSettings.userVitals;
        if (!age || !weight || !height || !gender || !activityLevel) {
            return { bmr: 0, tdee: 0, ffmi: null };
        }
        let bmr = (10 * weight) + (6.25 * height) - (5 * age) + (gender === 'male' ? 5 : -161);
        const activityMultipliers = { sedentary: 1.2, light: 1.375, moderate: 1.55, active: 1.725, very_active: 1.9 };
        const tdee = bmr * activityMultipliers[activityLevel];
        const ffmi = calculateFFMI(height, weight, localSettings.userVitals.bodyFatPercentage || 0);
        return { bmr: Math.round(bmr), tdee: Math.round(tdee), ffmi };
    }, [localSettings.userVitals]);
    
    // --- Step 4.3: Viability Calculation Logic ---
    const missionViability = useMemo(() => {
        const { weight: currentWeight, targetWeight, targetDate } = localSettings.userVitals;
        
        if (!currentWeight || !targetWeight || !targetDate) return null;
        
        const now = new Date();
        const deadline = new Date(targetDate);
        const diffTime = deadline.getTime() - now.getTime();
        const diffWeeks = Math.ceil(diffTime / (1000 * 60 * 60 * 24 * 7));
        
        // Handle dates in the past
        if (diffWeeks <= 0) {
            return { status: 'expired', msg: 'La fecha límite ya ha pasado o es hoy.', color: 'text-red-400', bg: 'bg-red-900/20', icon: AlertTriangleIcon };
        }

        const weightDiff = currentWeight - targetWeight; // Positive = Lose weight, Negative = Gain weight
        const isLoss = weightDiff > 0;
        const requiredWeeklyRate = Math.abs(weightDiff) / diffWeeks;
        const percentageOfBodyweight = (requiredWeeklyRate / currentWeight) * 100;
        
        let status: 'safe' | 'aggressive' | 'dangerous' = 'safe';
        
        // Viability Thresholds (1% per week is generally max recommended for sustainable loss)
        if (percentageOfBodyweight > 1.5) status = 'dangerous';
        else if (percentageOfBodyweight > 0.8) status = 'aggressive';
        
        // Gain logic (Muscle gain is slower than fat loss)
        if (!isLoss) {
             if (percentageOfBodyweight > 0.5) status = 'dangerous'; // Gaining >0.5% week is likely mostly fat
             else if (percentageOfBodyweight > 0.25) status = 'aggressive';
        }
        
        const config = {
            safe: { 
                color: 'text-emerald-400', 
                bg: 'bg-emerald-900/20', 
                border: 'border-emerald-500/30',
                icon: CheckCircleIcon, 
                title: 'Objetivo Sostenible' 
            },
            aggressive: { 
                color: 'text-yellow-400', 
                bg: 'bg-yellow-900/20', 
                border: 'border-yellow-500/30',
                icon: InfoIcon, 
                title: 'Objetivo Agresivo' 
            },
            dangerous: { 
                color: 'text-red-400', 
                bg: 'bg-red-900/20', 
                border: 'border-red-500/30',
                icon: AlertTriangleIcon, 
                title: isLoss ? 'Objetivo Poco Realista' : 'Ganancia Probable de Grasa' 
            }
        };

        return {
            status,
            weeks: diffWeeks,
            rate: requiredWeeklyRate.toFixed(2),
            totalDiff: Math.abs(weightDiff).toFixed(1),
            isLoss,
            ...config[status]
        };

    }, [localSettings.userVitals.weight, localSettings.userVitals.targetWeight, localSettings.userVitals.targetDate]);


    const handleSave = async () => {
        setIsSaving(true);
        try {
            setSettings(localSettings);
            const currentBio = (biomechanicalData || {}) as Partial<BiomechanicalData>;
            const localBio = {
                height: localSettings.userVitals.height,
                wingspan: localSettings.userVitals.wingspan,
                torsoLength: localSettings.userVitals.torsoLength,
                femurLength: localSettings.userVitals.femurLength,
                tibiaLength: localSettings.userVitals.tibiaLength,
                humerusLength: localSettings.userVitals.humerusLength,
                forearmLength: localSettings.userVitals.forearmLength
            };

            const hasBioChanged = (
                currentBio.height !== localBio.height ||
                currentBio.wingspan !== localBio.wingspan ||
                currentBio.torsoLength !== localBio.torsoLength ||
                currentBio.femurLength !== localBio.femurLength ||
                currentBio.tibiaLength !== localBio.tibiaLength ||
                currentBio.humerusLength !== localBio.humerusLength ||
                currentBio.forearmLength !== localBio.forearmLength
            );
            
            const isBioComplete = Object.values(localBio).every(val => typeof val === 'number' && val > 0);
            
            if (hasBioChanged && isBioComplete) {
                await setBiomechanicalData(localBio as BiomechanicalData);
            }
            
            addToast("Perfil guardado con éxito.", "success");
            handleBack();
        } catch (error: any) {
            addToast(`Error al guardar: ${error.message}`, "danger");
        } finally {
            setIsSaving(false);
        }
    };
    
    const handleCalculateAndApplyGoals = useCallback(() => {
        const { age, weight, height, gender, activityLevel } = localSettings.userVitals;
        if (!age || !weight || !height || !gender || !activityLevel) {
            addToast("Completa tus datos personales para poder calcular las metas.", "danger");
            return;
        }
        
        let bmr = (10 * weight) + (6.25 * height) - (5 * age) + (gender === 'male' ? 5 : -161);
        const activityMultipliers = { sedentary: 1.2, light: 1.375, moderate: 1.55, active: 1.725, very_active: 1.9 };
        const tdee = bmr * activityMultipliers[activityLevel];
    
        let calorieGoal: number;
        switch (localSettings.calorieGoalObjective) {
            case 'deficit': calorieGoal = tdee - 500; break;
            case 'surplus': calorieGoal = tdee + 300; break;
            default: calorieGoal = tdee; break;
        }
        
        const proteinGrams = Math.round((calorieGoal * 0.40) / 4);
        const carbGrams = Math.round((calorieGoal * 0.40) / 4);
        const fatGrams = Math.round((calorieGoal * 0.20) / 9);
    
        setLocalSettings(prev => ({
            ...prev,
            dailyCalorieGoal: Math.round(calorieGoal),
            dailyProteinGoal: proteinGrams,
            dailyCarbGoal: carbGrams,
            dailyFatGoal: fatGrams,
        }));
        
        addToast("Metas recomendadas calculadas y aplicadas.", "success");
    }, [localSettings.userVitals, localSettings.calorieGoalObjective, addToast]);
    
    const handleSaveProfilePicture = (newPicture: string | undefined) => {
        handleSettingChange('profilePicture', newPicture);
    }

    const renderVitalsTab = () => (
        <div className="space-y-6 animate-fade-in">
            <Card>
                <div className="flex items-center gap-4 mb-4">
                    <div className="relative group cursor-pointer" onClick={() => setIsPhotoModalOpen(true)}>
                        <div className="w-20 h-20 rounded-full border-2 border-primary-color overflow-hidden bg-slate-800">
                             {localSettings.profilePicture ? (
                                <img src={localSettings.profilePicture} alt="Avatar" className="w-full h-full object-cover"/>
                            ) : (
                                <div className="w-full h-full flex items-center justify-center text-slate-500">
                                    <CameraIcon size={32}/>
                                </div>
                            )}
                        </div>
                        <div className="absolute bottom-0 right-0 bg-primary-color text-white p-1 rounded-full shadow-md border border-slate-900">
                            <PencilIcon size={12} />
                        </div>
                    </div>
                    <div className="flex-grow">
                        <label className="text-[10px] text-slate-400 font-bold uppercase tracking-widest block mb-1">Nombre de Usuario</label>
                        <input 
                            type="text" 
                            value={localSettings.username || ''} 
                            onChange={e => handleSettingChange('username', e.target.value)} 
                            placeholder="Tu Nombre" 
                            className="w-full bg-transparent border-b border-slate-700 text-xl font-bold text-white focus:border-primary-color outline-none py-1"
                        />
                    </div>
                </div>

                <div className="grid grid-cols-2 md:grid-cols-3 gap-4 text-center border-t border-slate-700 pt-4">
                    <div className="bg-slate-900/50 p-3 rounded-lg"><p className="text-2xl font-bold text-primary-color">{calculatedMetrics.bmr}</p><p className="text-xs text-slate-400">TMB (kcal)</p></div>
                    <div className="bg-slate-900/50 p-3 rounded-lg"><p className="text-2xl font-bold text-primary-color">{calculatedMetrics.tdee}</p><p className="text-xs text-slate-400">GETD (kcal)</p></div>
                    <div className="bg-slate-900/50 p-3 rounded-lg col-span-2 md:col-span-1"><p className="text-2xl font-bold text-primary-color">{calculatedMetrics.ffmi?.normalizedFfmi || 'N/A'}</p><p className="text-xs text-slate-400">FFMI</p></div>
                </div>
                {calculatedMetrics.ffmi && <p className="text-center text-sm text-slate-300 mt-2">{calculatedMetrics.ffmi.interpretation}</p>}
            </Card>

            <Card>
                <h3 className="text-xl font-bold mb-4">Datos Físicos</h3>
                <div className="grid grid-cols-2 gap-4">
                    <div><label className="text-sm">Edad</label><input type="number" value={localSettings.userVitals.age || ''} onChange={e => handleVitalsChange('age', parseInt(e.target.value) || undefined)} className="w-full mt-1"/></div>
                    <div><label className="text-sm">Género</label><select value={localSettings.userVitals.gender || ''} onChange={e => handleVitalsChange('gender', e.target.value as any)} className="w-full mt-1"><option value="">Seleccionar</option><option value="male">Masculino</option><option value="female">Femenino</option></select></div>
                    <div><label className="text-sm">Altura (cm)</label><input type="number" value={localSettings.userVitals.height || ''} onChange={e => handleVitalsChange('height', parseInt(e.target.value) || undefined)} className="w-full mt-1"/></div>
                    <div><label className="text-sm">Peso Actual ({settings.weightUnit})</label><input type="number" step="0.1" value={localSettings.userVitals.weight || ''} onChange={e => handleVitalsChange('weight', parseFloat(e.target.value) || undefined)} className="w-full mt-1"/></div>
                    <div><label className="text-sm">% Grasa Corporal</label><input type="number" step="0.1" value={localSettings.userVitals.bodyFatPercentage || ''} onChange={e => handleVitalsChange('bodyFatPercentage', parseFloat(e.target.value) || undefined)} className="w-full mt-1"/></div>
                    <div className="col-span-2"><label className="text-sm">Nivel de Actividad</label><select value={localSettings.userVitals.activityLevel || ''} onChange={e => handleVitalsChange('activityLevel', e.target.value as any)} className="w-full mt-1"><option value="">Seleccionar</option><option value="sedentary">Sedentario</option><option value="light">Ligero</option><option value="moderate">Moderado</option><option value="active">Activo</option><option value="very_active">Muy Activo</option></select></div>
                </div>
            </Card>
        </div>
    );
    
    const renderAnatomyTab = () => (
        <div className="space-y-6 animate-fade-in">
            <Card>
                <h3 className="text-xl font-bold mb-4">Medidas Biomecánicas (cm)</h3>
                <div className="grid grid-cols-2 gap-4">
                    <MeasurementInput label="Envergadura" value={String(localSettings.userVitals.wingspan || '')} onChange={v => handleVitalsChange('wingspan', parseFloat(v) || undefined)} />
                    <MeasurementInput label="Long. Torso" value={String(localSettings.userVitals.torsoLength || '')} onChange={v => handleVitalsChange('torsoLength', parseFloat(v) || undefined)} />
                    <MeasurementInput label="Long. Fémur" value={String(localSettings.userVitals.femurLength || '')} onChange={v => handleVitalsChange('femurLength', parseFloat(v) || undefined)} />
                    <MeasurementInput label="Long. Tibia" value={String(localSettings.userVitals.tibiaLength || '')} onChange={v => handleVitalsChange('tibiaLength', parseFloat(v) || undefined)} />
                    <MeasurementInput label="Long. Húmero" value={String(localSettings.userVitals.humerusLength || '')} onChange={v => handleVitalsChange('humerusLength', parseFloat(v) || undefined)} />
                    <MeasurementInput label="Long. Antebrazo" value={String(localSettings.userVitals.forearmLength || '')} onChange={v => handleVitalsChange('forearmLength', parseFloat(v) || undefined)} />
                </div>
                 {biomechanicalAnalysis && <p className="text-center text-xs text-slate-400 mt-4">Tus medidas ya han sido analizadas por la IA. Guarda cualquier cambio para volver a analizar.</p>}
            </Card>
            <Card>
                <h3 className="text-xl font-bold mb-4">Somatotipo</h3>
                <SomatotypeSlider label="Endomorfo" value={localSettings.userVitals.somatotype?.endomorph || 1} onChange={v => handleVitalsChange('somatotype', {...(localSettings.userVitals.somatotype || {endomorph:1, mesomorph:1, ectomorph:1}), endomorph: v})} />
                <SomatotypeSlider label="Mesomorfo" value={localSettings.userVitals.somatotype?.mesomorph || 1} onChange={v => handleVitalsChange('somatotype', {...(localSettings.userVitals.somatotype || {endomorph:1, mesomorph:1, ectomorph:1}), mesomorph: v})} />
                <SomatotypeSlider label="Ectomorfo" value={localSettings.userVitals.somatotype?.ectomorph || 1} onChange={v => handleVitalsChange('somatotype', {...(localSettings.userVitals.somatotype || {endomorph:1, mesomorph:1, ectomorph:1}), ectomorph: v})} />
            </Card>
            <Card>
                <h3 className="text-xl font-bold mb-4">Distribución Grasa Corporal</h3>
                <select value={localSettings.userVitals.bodyFatDistribution || ''} onChange={e => handleVitalsChange('bodyFatDistribution', e.target.value as any)} className="w-full"><option value="">No especificado</option><option value="android">Androide (manzana)</option><option value="gynoid">Ginoide (pera)</option><option value="even">Uniforme</option></select>
            </Card>
             <Card>
                <h3 className="text-xl font-bold mb-4">Salud Articular</h3>
                <div className="space-y-3">
                    {(localSettings.userVitals.jointHealthNotes || []).map((note, index) => (
                        <div key={index} className="grid grid-cols-12 gap-2 items-center">
                            <input type="text" placeholder="Articulación" value={note.joint} onChange={e => handleJointNoteChange(index, 'joint', e.target.value)} className="col-span-4" />
                            <input type="text" placeholder="Nota (ej: tendinitis rotuliana)" value={note.note} onChange={e => handleJointNoteChange(index, 'note', e.target.value)} className="col-span-7" />
                            <button onClick={() => removeJointNote(index)} className="col-span-1 text-slate-500 hover:text-red-400"><TrashIcon/></button>
                        </div>
                    ))}
                </div>
                <Button onClick={addJointNote} variant="secondary" className="w-full mt-4 !text-sm"><PlusIcon/> Añadir Nota</Button>
            </Card>
        </div>
    );
    
    const renderNutritionTab = () => (
        <div className="space-y-6 animate-fade-in">
            <Card>
                <h3 className="text-xl font-bold mb-4 flex items-center gap-2"><TargetIcon className="text-primary-color"/> Objetivo Nutricional</h3>
                <div className="space-y-4">
                    <div>
                        <label className="block text-sm text-slate-400 mb-2 uppercase tracking-widest font-black">Mi meta es...</label>
                        <div className="flex bg-slate-800 p-1 rounded-full border border-white/5">
                            <button onClick={() => handleSettingChange('calorieGoalObjective', 'deficit')} className={`flex-1 py-2 rounded-full text-xs font-black uppercase transition-all ${localSettings.calorieGoalObjective === 'deficit' ? 'bg-red-600 text-white shadow-lg' : 'text-slate-500 hover:text-slate-300'}`}>Pérdida</button>
                            <button onClick={() => handleSettingChange('calorieGoalObjective', 'maintenance')} className={`flex-1 py-2 rounded-full text-xs font-black uppercase transition-all ${localSettings.calorieGoalObjective === 'maintenance' ? 'bg-sky-600 text-white shadow-lg' : 'text-slate-500 hover:text-slate-300'}`}>Manten.</button>
                            <button onClick={() => handleSettingChange('calorieGoalObjective', 'surplus')} className={`flex-1 py-2 rounded-full text-xs font-black uppercase transition-all ${localSettings.calorieGoalObjective === 'surplus' ? 'bg-green-600 text-white shadow-lg' : 'text-slate-500 hover:text-slate-300'}`}>Ganancia</button>
                        </div>
                    </div>
                    
                    {/* Temporal Mission Card */}
                    <div className="bg-slate-950/40 p-5 rounded-2xl border border-white/5 space-y-4 relative overflow-hidden">
                        <div className="absolute top-0 right-0 p-4 opacity-5"><CalendarIcon size={100} /></div>
                        
                        <div className="flex items-center justify-between mb-2 relative z-10">
                             <div className="flex items-center gap-2">
                                <CalendarIcon size={16} className="text-primary-color" />
                                <h4 className="text-[10px] font-black text-slate-400 uppercase tracking-[0.2em]">Misión Temporal</h4>
                             </div>
                             {(localSettings.userVitals.targetWeight || localSettings.userVitals.targetDate) && (
                                <button onClick={handleResetGoal} className="text-[9px] font-black text-red-400 hover:text-red-300 uppercase tracking-widest">Reiniciar</button>
                             )}
                        </div>
                        
                        <div className="grid grid-cols-2 gap-4 relative z-10">
                            <div>
                                <label className="block text-[10px] font-black text-slate-500 uppercase mb-1 ml-1">Peso Objetivo</label>
                                <div className="relative">
                                    <input 
                                        type="number" 
                                        step="0.1" 
                                        value={localSettings.userVitals.targetWeight || ''} 
                                        onChange={e => handleVitalsChange('targetWeight', parseFloat(e.target.value) || undefined)} 
                                        className="w-full bg-slate-900 border border-white/5 rounded-xl p-3 text-center font-black text-xl text-white focus:border-primary-color outline-none transition-all"
                                        placeholder="0.0"
                                    />
                                    <span className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] font-bold text-slate-600">{settings.weightUnit}</span>
                                </div>
                            </div>
                            <div>
                                <label className="block text-[10px] font-black text-slate-500 uppercase mb-1 ml-1">Fecha Límite</label>
                                <input 
                                    type="date" 
                                    value={localSettings.userVitals.targetDate || ''} 
                                    onChange={e => handleVitalsChange('targetDate', e.target.value)} 
                                    className="w-full bg-slate-900 border border-white/5 rounded-xl p-3 text-center text-xs font-black text-white focus:border-primary-color outline-none transition-all"
                                />
                            </div>
                        </div>
                        <p className="text-[9px] text-slate-500 text-center mt-2 italic">
                            {localSettings.userVitals.targetStartWeight ? 
                                `Misión iniciada desde ${localSettings.userVitals.targetStartWeight}${settings.weightUnit} el ${new Date(localSettings.userVitals.targetStartDate!).toLocaleDateString()}` 
                                : "Define un peso y fecha para iniciar la misión."}
                        </p>
                        
                        {/* Viability Feedback Block (Step 4.3) */}
                        {missionViability && (
                            <div className={`mt-3 p-3 rounded-xl border ${missionViability.bg} ${missionViability.border} animate-fade-in relative z-10`}>
                                <div className="flex items-start gap-3">
                                    <div className={`p-2 rounded-full bg-black/20 ${missionViability.color}`}>
                                        <missionViability.icon size={16} />
                                    </div>
                                    <div>
                                        <h4 className={`text-xs font-bold ${missionViability.color} mb-1`}>{missionViability.title}</h4>
                                        {missionViability.status === 'expired' ? (
                                            <p className="text-[10px] text-slate-300 leading-tight">{missionViability.msg}</p>
                                        ) : (
                                            <p className="text-[10px] text-slate-300 leading-tight">
                                                Para alcanzar tu meta en <strong className="text-white">{missionViability.weeks} semanas</strong>, necesitas {missionViability.isLoss ? 'perder' : 'ganar'} <strong className="text-white">{missionViability.rate} {settings.weightUnit}/semana</strong>.
                                            </p>
                                        )}
                                        {missionViability.status === 'dangerous' && (
                                            <p className="text-[9px] text-red-300 mt-1 font-bold">⚠️ Ritmo no recomendado ({'>'}1% peso/sem).</p>
                                        )}
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </Card>
            <Card>
                <div className="flex justify-between items-center mb-4">
                    <h3 className="text-xl font-bold">Metas Diarias</h3>
                     <Button onClick={handleCalculateAndApplyGoals} variant="secondary" className="!py-1.5 !px-3 !text-[10px] uppercase font-black" disabled={!isOnline || !localSettings.userVitals.age || !localSettings.userVitals.weight || !localSettings.userVitals.height || !localSettings.userVitals.gender || !localSettings.userVitals.activityLevel}>
                        <SparklesIcon size={12}/> Calcular IA
                    </Button>
                </div>
               
                <div className="bg-slate-900/50 p-5 rounded-2xl space-y-6 border border-white/5">
                    <div className="text-center">
                        <label className="block text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Calorías Diarias (kcal)</label>
                        <input type="number" value={localSettings.dailyCalorieGoal || ''} onChange={e => handleCalorieChange(e.target.value)} className="w-full max-w-[200px] text-5xl font-black text-primary-color bg-transparent text-center focus:outline-none placeholder-slate-800" placeholder="0000"/>
                    </div>
                    <div className="grid grid-cols-3 gap-4 text-center pt-5 border-t border-white/5">
                         <div>
                            <input type="number" value={localSettings.dailyProteinGoal || ''} onChange={e => handleMacroChange('dailyProteinGoal', e.target.value)} className="w-full font-black text-xl bg-transparent text-center focus:outline-none text-blue-400 placeholder-slate-800" placeholder="00"/>
                            <p className="text-[9px] font-black text-slate-500 uppercase mt-1">Proteína (g)</p>
                         </div>
                         <div>
                            <input type="number" value={localSettings.dailyCarbGoal || ''} onChange={e => handleMacroChange('dailyCarbGoal', e.target.value)} className="w-full font-black text-xl bg-transparent text-center focus:outline-none text-orange-400 placeholder-slate-800" placeholder="00"/>
                            <p className="text-[9px] font-black text-slate-500 uppercase mt-1">Carbs (g)</p>
                         </div>
                         <div>
                            <input type="number" value={localSettings.dailyFatGoal || ''} onChange={e => handleMacroChange('dailyFatGoal', e.target.value)} className="w-full font-black text-xl bg-transparent text-center focus:outline-none text-yellow-400 placeholder-slate-800" placeholder="00"/>
                            <p className="text-[9px] font-black text-slate-500 uppercase mt-1">Grasas (g)</p>
                         </div>
                    </div>
                </div>
            </Card>
            {/* ... other nutrition fields ... */}
            <Card>
                <h3 className="text-xl font-bold mb-4">Preferencias Dietéticas</h3>
                <select value={localSettings.dietaryPreference || 'omnivore'} onChange={e => handleSettingChange('dietaryPreference', e.target.value as any)} className="w-full bg-slate-900 border-none rounded-xl p-3 text-sm font-bold">
                    <option value="omnivore">Omnívoro</option><option value="vegetarian">Vegetariano</option><option value="vegan">Vegano</option><option value="keto">Cetogénico</option>
                </select>
            </Card>
             <Card>
                <h3 className="text-xl font-bold mb-4">Foco en Micronutrientes</h3>
                <input type="text" value={localSettings.micronutrientFocus?.join(', ') || ''} onChange={e => handleSettingChange('micronutrientFocus', e.target.value.split(',').map(s => s.trim()))} placeholder="Ej: Hierro, Vitamina D, Magnesio" className="w-full bg-slate-900 border-none rounded-xl p-3 text-sm"/>
            </Card>
            <Card>
                <h3 className="text-xl font-bold mb-4">Meta de Hidratación</h3>
                <div className="flex items-center gap-3 bg-slate-900/50 p-3 rounded-xl">
                    <input type="number" step="0.1" value={localSettings.waterIntakeGoal_L || ''} onChange={e => handleSettingChange('waterIntakeGoal_L', parseFloat(e.target.value) || undefined)} className="bg-transparent border-none font-black text-xl w-20 text-center focus:ring-0" placeholder="2.5"/>
                    <span className="font-bold text-slate-400 uppercase text-xs tracking-widest">Litros / día</span>
                </div>
            </Card>
        </div>
    );

    return (
        <div className="pb-32">
            <ProfilePictureModal 
                isOpen={isPhotoModalOpen} 
                onClose={() => setIsPhotoModalOpen(false)} 
                currentPicture={localSettings.profilePicture}
                onSave={handleSaveProfilePicture}
            />

            <header className="flex items-center justify-between mb-6 pt-4">
                <div className="flex items-center gap-2">
                    <button onClick={handleBack} className="p-2 -ml-2 text-slate-300 hover:text-white transition-colors"><ArrowLeftIcon /></button>
                    <h1 className="text-3xl font-black uppercase tracking-tighter text-white">Perfil de Atleta</h1>
                </div>
                <Button onClick={handleSave} isLoading={isSaving} className="!py-2 !px-5 shadow-lg shadow-primary-color/20"><SaveIcon size={18} className="mr-1"/> Guardar</Button>
            </header>

            {activeTab === 'vitals' && renderVitalsTab()}
            {activeTab === 'anatomy' && renderAnatomyTab()}
            {activeTab === 'nutrition' && renderNutritionTab()}
        </div>
    );
};

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

export default AthleteProfileView;