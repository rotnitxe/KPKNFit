
// components/SettingsComponent.tsx
import React, { useState, useEffect, useRef } from 'react';
import { Settings, Program, WorkoutLog, BodyProgressLog, NutritionLog, SkippedWorkoutLog, UseGoogleDriveReturn, LocalSnapshot, OneRMFormula, HapticIntensity, View } from '../types';
import Card from './ui/Card';
import Button from './ui/Button';
import { 
    Volume2Icon, VolumeXIcon, SaveIcon, DownloadIcon, UploadIcon, 
    CloudIcon, UploadCloudIcon, DownloadCloudIcon, KeyIcon, PaletteIcon, 
    BellIcon, DumbbellIcon, ChevronRightIcon, PencilIcon, SettingsIcon, 
    Wand2Icon, ActivityIcon, UtensilsIcon, BrainIcon, FlaskConical,
    ChevronDownIcon, StarIcon, TrashIcon, PlusCircleIcon, BodyIcon, ImageIcon, TargetIcon, UserBadgeIcon,
    SparklesIcon, LayersIcon, ClockIcon, ZapIcon, TypeIcon, RefreshCwIcon, CheckIcon, MoonIcon, CalculatorIcon,
    TrendingUpIcon, ClipboardListIcon, CheckCircleIcon, GridIcon
} from './icons';
import { storageService } from '../services/storageService';
import BackgroundEditorModal from './SessionBackgroundModal';
import { useAppDispatch } from '../contexts/AppContext';
import useLocalStorage from '../hooks/useLocalStorage';

interface SettingsProps {
  settings: Settings;
  onSettingsChange: (newSettings: Partial<Settings>) => void;
  setPrograms: React.Dispatch<React.SetStateAction<Program[]>>;
  setHistory: React.Dispatch<React.SetStateAction<WorkoutLog[]>>;
  setSkippedLogs: React.Dispatch<React.SetStateAction<SkippedWorkoutLog[]>>;
  setBodyProgress: React.Dispatch<React.SetStateAction<BodyProgressLog[]>>;
  setNutritionLogs: React.Dispatch<React.SetStateAction<NutritionLog[]>>;
  drive: UseGoogleDriveReturn;
  installPromptEvent: any;
  setInstallPromptEvent: (event: any) => void;
  isOnline: boolean;
}

const SettingsSection: React.FC<{ title: string; icon: React.ReactNode; children: React.ReactNode; defaultOpen?: boolean }> = ({ title, icon, children, defaultOpen = false }) => (
    <details className="settings-card group" open={defaultOpen}>
        <summary className="p-4 cursor-pointer flex justify-between items-center bg-slate-900/20 hover:bg-white/5 transition-colors list-none border-b border-white/5 group-open:bg-white/5">
            <div className="flex items-center gap-3">
                <div className="text-primary-color">{icon}</div>
                <h3 className="text-lg font-black text-white uppercase tracking-tight">{title}</h3>
            </div>
            <ChevronRightIcon className="details-arrow transition-transform text-slate-500 group-open:rotate-90" />
        </summary>
        <div className="p-4 space-y-4 bg-black/20 animate-fade-in">
            {children}
        </div>
    </details>
);

const SettingsItem: React.FC<{ label: string; description?: string; children: React.ReactNode; icon?: React.ReactNode }> = ({ label, description, children, icon }) => (
    <div className="flex items-center justify-between p-4 bg-slate-900/40 rounded-2xl border border-white/5 hover:bg-slate-800/40 transition-colors">
        <div className="flex items-center gap-4 overflow-hidden mr-4">
            {icon && <div className="text-slate-400 flex-shrink-0">{icon}</div>}
            <div className="flex flex-col min-w-0">
                <span className="text-sm font-bold text-slate-200 truncate">{label}</span>
                {description && <span className="text-[9px] text-slate-500 font-bold uppercase tracking-wider leading-tight mt-1">{description}</span>}
            </div>
        </div>
        <div className="flex-shrink-0">
            {children}
        </div>
    </div>
);

const ToggleSwitch: React.FC<{ checked: boolean, onChange: (checked: boolean) => void, disabled?: boolean }> = ({ checked, onChange, disabled }) => (
    <button 
        type="button" 
        onClick={() => !disabled && onChange(!checked)} 
        className={`relative inline-flex flex-shrink-0 h-6 w-11 border-2 border-transparent rounded-full cursor-pointer transition-colors ease-in-out duration-200 focus:outline-none ${disabled ? 'opacity-50' : ''} ${checked ? 'bg-primary-color shadow-[0_0_10px_rgba(var(--primary-hue),0.5)]' : 'bg-slate-600'}`}
    >
        <span className={`inline-block h-5 w-5 rounded-full bg-white shadow transform ring-0 transition ease-in-out duration-200 ${checked ? 'translate-x-5' : 'translate-x-0'}`} />
    </button>
);

const TabBarCustomizer: React.FC<{ 
    currentTabs: View[]; 
    onChange: (tabs: View[]) => void; 
}> = ({ currentTabs, onChange }) => {
    // List of all available tabs (must match keys in TabBar.tsx TAB_CONFIG)
    const AVAILABLE_TABS: { id: View, label: string, icon: React.FC<any> }[] = [
        { id: 'home', label: 'Entreno', icon: DumbbellIcon },
        { id: 'nutrition', label: 'Nutrición', icon: UtensilsIcon },
        { id: 'recovery', label: 'Batería', icon: ActivityIcon },
        { id: 'sleep', label: 'Descanso', icon: MoonIcon },
        { id: 'kpkn', label: 'KPKN', icon: ClipboardListIcon },
        { id: 'progress', label: 'Progreso', icon: TrendingUpIcon },
        { id: 'coach', label: 'Coach IA', icon: BrainIcon },
        { id: 'tasks', label: 'Tareas', icon: CheckCircleIcon }
    ];

    const toggleTab = (tabId: View) => {
        if (currentTabs.includes(tabId)) {
             // Don't allow removing 'home' to prevent getting stuck
            if (tabId === 'home') return;
            onChange(currentTabs.filter(t => t !== tabId));
        } else {
            if (currentTabs.length >= 4) {
                // Max limit reached
                return;
            }
            onChange([...currentTabs, tabId]);
        }
    };

    return (
        <div className="bg-slate-900/40 p-4 rounded-3xl border border-white/5">
            <div className="mb-4">
                <span className="text-[10px] font-black text-slate-500 uppercase">Menú Inferior (Máx. 4)</span>
                <p className="text-xs text-slate-400 mt-1">Selecciona qué accesos directos quieres ver en la barra inferior. El botón central siempre será "Crear".</p>
            </div>
            
            <div className="grid grid-cols-2 gap-2">
                {AVAILABLE_TABS.map(tab => {
                    const isSelected = currentTabs.includes(tab.id);
                    const isFull = !isSelected && currentTabs.length >= 4;
                    const isHome = tab.id === 'home';
                    
                    return (
                        <button
                            key={tab.id}
                            onClick={() => toggleTab(tab.id)}
                            disabled={isFull || isHome}
                            className={`flex items-center gap-3 p-3 rounded-xl border transition-all ${
                                isSelected 
                                    ? 'bg-primary-color/20 border-primary-color text-white' 
                                    : 'bg-slate-800 border-transparent text-slate-500 hover:bg-slate-700'
                            } ${isFull ? 'opacity-50 cursor-not-allowed' : ''} ${isHome ? 'cursor-default opacity-80' : ''}`}
                        >
                            <tab.icon size={18} className={isSelected ? 'text-primary-color' : ''}/>
                            <span className="text-xs font-bold uppercase">{tab.label}</span>
                            {isSelected && <div className="ml-auto w-2 h-2 bg-primary-color rounded-full shadow-[0_0_8px_currentColor]"/>}
                        </button>
                    )
                })}
            </div>
        </div>
    );
};

export const SettingsComponent: React.FC<SettingsProps> = ({ settings, onSettingsChange, setPrograms, setHistory, setSkippedLogs, setBodyProgress, setNutritionLogs, drive, installPromptEvent, setInstallPromptEvent, isOnline }) => {
    const [pendingSettings, setPendingSettings] = useState<Settings>(settings);
    const [hasChanges, setHasChanges] = useState(false);
    const [isBgModalOpen, setIsBgModalOpen] = useState(false);
    const [snapshots, setSnapshots] = useLocalStorage<LocalSnapshot[]>('yourprime-snapshots', []);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const { navigateTo, importExerciseDatabase } = useAppDispatch();

    useEffect(() => {
        if (!hasChanges) setPendingSettings(settings);
    }, [settings, hasChanges]);

    const handleSettingChange = <K extends keyof Settings>(key: K, value: Settings[K]) => {
        setPendingSettings(prev => ({ ...prev, [key]: value }));
        setHasChanges(true);
    };

    const handleAlgorithmChange = (key: keyof Settings['algorithmSettings'], value: number | boolean) => {
        setPendingSettings(prev => ({
            ...prev,
            algorithmSettings: { ...prev.algorithmSettings, [key]: value as any }
        }));
        setHasChanges(true);
    };

    const handleApiKeyChange = (provider: 'gemini' | 'deepseek' | 'gpt', value: string) => {
        setPendingSettings(prev => ({
            ...prev,
            apiKeys: { ...(prev.apiKeys || {}), [provider]: value }
        }));
        setHasChanges(true);
    };

    const toggleWorkDay = (dayIndex: number) => {
        setPendingSettings(prev => {
            const currentDays = prev.workDays || [];
            const newDays = currentDays.includes(dayIndex) 
                ? currentDays.filter(d => d !== dayIndex)
                : [...currentDays, dayIndex];
            return { ...prev, workDays: newDays.sort() };
        });
        setHasChanges(true);
    };

    const handleSaveChanges = () => {
        onSettingsChange(pendingSettings);
        setHasChanges(false);
    };
    
    const handleCreateSnapshot = async () => {
        const name = prompt("Introduce un nombre para esta copia de seguridad:", `Snapshot ${new Date().toLocaleDateString()}`);
        if (!name) return;
        try {
            const dataToBackup = {
                programs: await storageService.get<Program[]>('programs') || [],
                history: await storageService.get<WorkoutLog[]>('history') || [],
                settings: await storageService.get<Settings>('yourprime-settings') || settings,
                'body-progress': await storageService.get<BodyProgressLog[]>('body-progress') || [],
                'nutrition-logs': await storageService.get<NutritionLog[]>('nutrition-logs') || [],
            };
            const newSnapshot: LocalSnapshot = {
                id: crypto.randomUUID(),
                name,
                date: new Date().toISOString(),
                data: dataToBackup
            };
            setSnapshots(prev => [...prev, newSnapshot]);
            alert("Copia de seguridad local creada con éxito.");
        } catch (error) {
            console.error("Error al crear la copia de seguridad:", error);
            alert("No se pudo crear la copia de seguridad.");
        }
    };

    const handleExportData = async () => {
        try {
            const data = await storageService.getAllDataForExport();
            const jsonString = `data:text/json;charset=utf-8,${encodeURIComponent(JSON.stringify(data, null, 2))}`;
            const link = document.createElement("a");
            link.href = jsonString;
            link.download = `yourprime_backup_${new Date().toISOString().split('T')[0]}.json`;
            link.click();
        } catch (error) { alert('Error al exportar.'); }
    };
    
    const daysOfWeek = [
        { label: 'D', value: 0 }, { label: 'L', value: 1 }, { label: 'M', value: 2 }, 
        { label: 'M', value: 3 }, { label: 'J', value: 4 }, { label: 'V', value: 5 }, { label: 'S', value: 6 }
    ];

    return (
        <div className="max-w-4xl mx-auto pb-40 animate-fade-in space-y-6">
            {isBgModalOpen && (
                <BackgroundEditorModal
                    isOpen={isBgModalOpen}
                    onClose={() => setIsBgModalOpen(false)}
                    onSave={(bg) => handleSettingChange('appBackground', bg)}
                    initialBackground={pendingSettings.appBackground}
                    previewTitle="Fondo Global"
                    isOnline={isOnline}
                />
            )}

            <header className="flex justify-between items-end mb-6 px-2 pt-4">
                <div>
                    <h1 className="text-4xl font-black uppercase tracking-tighter text-white">Configuración</h1>
                    <p className="text-slate-500 text-[10px] font-black uppercase tracking-[0.3em] mt-1">Nivel Maestro • 10000% Personalizado</p>
                </div>
                {hasChanges && (
                    <div className="flex gap-2 animate-bounce">
                        <Button onClick={() => setPendingSettings(settings)} variant="secondary" className="!py-2 !px-4 font-black uppercase !text-[10px]">Cancelar</Button>
                        <Button onClick={handleSaveChanges} className="!py-2 !px-4 font-black uppercase !text-[10px] shadow-lg shadow-primary-color/40">Guardar</Button>
                    </div>
                )}
            </header>

            {/* --- SECCIÓN: CEREBRO IA --- */}
            <SettingsSection title="Cerebro IA & APIs" icon={<SparklesIcon />} defaultOpen={true}>
                <SettingsItem label="Proveedor Primario" description="Motor que procesa tus consultas y planes." icon={<BrainIcon size={18}/>}>
                    <select value={pendingSettings.apiProvider} onChange={e => handleSettingChange('apiProvider', e.target.value as any)} className="bg-slate-800 border-none rounded-xl text-[10px] font-black text-white p-2">
                        <option value="gemini">Google Gemini 2.5</option>
                        <option value="gpt">OpenAI GPT-4o Mini</option>
                        <option value="deepseek">DeepSeek Chat</option>
                    </select>
                </SettingsItem>

                <div className="grid grid-cols-1 gap-3">
                    <input type="password" value={pendingSettings.apiKeys?.gemini || ''} onChange={e => handleApiKeyChange('gemini', e.target.value)} placeholder="API Key Gemini" className="w-full bg-slate-950/50 border border-white/10 rounded-xl p-3 text-xs font-mono" />
                    <input type="password" value={pendingSettings.apiKeys?.gpt || ''} onChange={e => handleApiKeyChange('gpt', e.target.value)} placeholder="API Key OpenAI (opcional)" className="w-full bg-slate-950/50 border border-white/10 rounded-xl p-3 text-xs font-mono" />
                </div>

                <SettingsItem label="Creatividad (Temp)" description="Controla qué tan 'inventiva' es la IA." icon={<ZapIcon size={16}/>}>
                    <div className="flex items-center gap-3">
                        <input type="range" min="0" max="1" step="0.1" value={pendingSettings.aiTemperature} onChange={e => handleSettingChange('aiTemperature', parseFloat(e.target.value))} className="w-24"/>
                        <span className="text-xs font-mono font-bold text-primary-color">{pendingSettings.aiTemperature}</span>
                    </div>
                </SettingsItem>

                <SettingsItem label="Respaldo (Fallbacks)" description="Usa otros proveedores si el principal falla." icon={<RefreshCwIcon size={16}/>}>
                    <ToggleSwitch checked={pendingSettings.fallbackEnabled} onChange={(c) => handleSettingChange('fallbackEnabled', c)} />
                </SettingsItem>
            </SettingsSection>

            {/* --- SECCIÓN: ALGORITMOS Y CIENCIA --- */}
            <SettingsSection title="Algoritmos & Ciencia" icon={<FlaskConical />}>
                <SettingsItem label="Fórmula de 1RM" description="Cálculo del potencial máximo estimado." icon={<CalculatorIcon size={18}/>}>
                    <select value={pendingSettings.oneRMFormula} onChange={e => handleSettingChange('oneRMFormula', e.target.value as OneRMFormula)} className="bg-slate-800 border-none rounded-xl text-[10px] font-black text-white p-2">
                        <option value="brzycki">Brzycki (Estándar)</option>
                        <option value="epley">Epley (Altas Reps)</option>
                        <option value="lander">Lander</option>
                    </select>
                </SettingsItem>

                <div className="space-y-4 bg-slate-900/40 p-4 rounded-3xl border border-white/5">
                    <div className="space-y-1">
                        <div className="flex justify-between text-[10px] font-black text-slate-500 uppercase"><span>Tasa de Decaimiento (Pérdida de Fuerza)</span><span>{pendingSettings.algorithmSettings.oneRMDecayRate}% / día</span></div>
                        <input type="range" min="0.01" max="0.5" step="0.01" value={pendingSettings.algorithmSettings.oneRMDecayRate} onChange={e => handleAlgorithmChange('oneRMDecayRate', parseFloat(e.target.value))} className="w-full accent-sky-500" />
                    </div>
                    <div className="space-y-1">
                        <div className="flex justify-between text-[10px] font-black text-slate-500 uppercase"><span>Factor Fatiga al Fallo</span><span>{pendingSettings.algorithmSettings.failureFatigueFactor}x</span></div>
                        <input type="range" min="1.0" max="2.0" step="0.05" value={pendingSettings.algorithmSettings.failureFatigueFactor} onChange={e => handleAlgorithmChange('failureFatigueFactor', parseFloat(e.target.value))} className="w-full accent-red-500" />
                    </div>
                    <div className="space-y-1">
                        <div className="flex justify-between text-[10px] font-black text-slate-500 uppercase"><span>Factor Sinergista (Volumen Indirecto)</span><span>{pendingSettings.algorithmSettings.synergistFactor}x</span></div>
                        <input type="range" min="0" max="0.6" step="0.05" value={pendingSettings.algorithmSettings.synergistFactor} onChange={e => handleAlgorithmChange('synergistFactor', parseFloat(e.target.value))} className="w-full accent-emerald-500" />
                    </div>
                </div>

                {/* --- NUEVO: CONFIGURACIÓN DE BATERÍAS AUGE --- */}
                <div className="bg-slate-900/40 p-4 rounded-3xl border border-orange-500/30 space-y-4 mt-4 relative overflow-hidden">
                    <div className="absolute top-0 right-0 w-16 h-16 bg-orange-500/10 rounded-bl-[100px] pointer-events-none"></div>
                    
                    <div className="mb-2 relative z-10">
                        <span className="text-[10px] font-black text-orange-500 uppercase flex items-center gap-1.5"><ActivityIcon size={12}/> Precisión de Baterías (AUGE)</span>
                        <p className="text-[10px] text-slate-400 mt-2 leading-relaxed">
                            Si apagas estas variables, la app dejará de exigirte este registro. <strong className="text-orange-400">Sin embargo, los tiempos de recuperación serán menos exactos</strong> para tu contexto personal, basándose únicamente en matemáticas estándar.
                        </p>
                    </div>
                    
                    <div className="flex items-center justify-between border-t border-white/5 pt-3 relative z-10">
                        <span className="text-xs font-bold text-slate-300 flex items-center gap-2"><UtensilsIcon size={14} className="text-slate-500"/> Tracking de Nutrición</span>
                        <ToggleSwitch 
                            checked={pendingSettings.algorithmSettings?.augeEnableNutritionTracking !== false} 
                            onChange={(c) => handleAlgorithmChange('augeEnableNutritionTracking', c)} 
                        />
                    </div>
                    
                    <div className="flex items-center justify-between border-t border-white/5 pt-3 relative z-10">
                        <span className="text-xs font-bold text-slate-300 flex items-center gap-2"><MoonIcon size={14} className="text-slate-500"/> Tracking de Sueño</span>
                        <ToggleSwitch 
                            checked={pendingSettings.algorithmSettings?.augeEnableSleepTracking !== false} 
                            onChange={(c) => handleAlgorithmChange('augeEnableSleepTracking', c)} 
                        />
                    </div>
                </div>
            </SettingsSection>

            {/* --- SECCIÓN: UI / UX / ESTÉTICA --- */}
            <SettingsSection title="Interfaz & Estética" icon={<PaletteIcon />}>
                <SettingsItem label="Tema Visual" description="Aspecto general de la aplicación." icon={<ImageIcon size={18}/>}>
                    <select value={pendingSettings.appTheme} onChange={e => handleSettingChange('appTheme', e.target.value as any)} className="bg-slate-800 border-none rounded-xl text-[10px] font-black text-white p-2">
                        <option value="default">Default Prime (Glass)</option>
                        <option value="deep-black">Pure Black (OLED)</option>
                        <option value="volt">Volt (Cyber Neon)</option>
                    </select>
                </SettingsItem>
                
                {/* --- CUSTOM TAB BAR --- */}
                <TabBarCustomizer 
                    currentTabs={pendingSettings.enabledTabs || ['home', 'nutrition', 'recovery', 'sleep']}
                    onChange={(tabs) => handleSettingChange('enabledTabs', tabs)}
                />

                <SettingsItem label="Fondo Global" description="Imagen o color maestro." icon={<Wand2Icon size={18}/>}>
                    <button onClick={() => setIsBgModalOpen(true)} className="p-2 bg-primary-color/20 text-primary-color rounded-xl hover:scale-110 transition-transform"><Wand2Icon size={18}/></button>
                </SettingsItem>

                <div className="grid grid-cols-2 gap-3">
                    <SettingsItem label="Animaciones" description="Transiciones de página." icon={<ActivityIcon size={16}/>}>
                        <ToggleSwitch checked={pendingSettings.enableAnimations} onChange={(c) => handleSettingChange('enableAnimations', c)} />
                    </SettingsItem>
                    <SettingsItem label="Efecto Glass" description="Desenfoque translúcido." icon={<LayersIcon size={16}/>}>
                        <ToggleSwitch checked={pendingSettings.enableGlassmorphism} onChange={(c) => handleSettingChange('enableGlassmorphism', c)} />
                    </SettingsItem>
                    <SettingsItem label="Efecto Glow" description="Brillos neón y sombras." icon={<StarIcon size={16}/>}>
                        <ToggleSwitch checked={pendingSettings.enableGlowEffects} onChange={(c) => handleSettingChange('enableGlowEffects', c)} />
                    </SettingsItem>
                    <SettingsItem label="Modo Zen" description="Máximo rendimiento." icon={<ClockIcon size={16}/>}>
                        <ToggleSwitch checked={pendingSettings.enableZenMode} onChange={(c) => handleSettingChange('enableZenMode', c)} />
                    </SettingsItem>
                </div>

                <div className="bg-slate-900/40 p-4 rounded-3xl border border-white/5 space-y-4">
                     <div className="space-y-1">
                        <div className="flex justify-between text-[10px] font-black text-slate-500 uppercase"><span>Intensidad de Desenfoque (Blur)</span><span>{pendingSettings.themeBlurAmount}px</span></div>
                        <input type="range" min="0" max="100" step="5" value={pendingSettings.themeBlurAmount} onChange={e => handleSettingChange('themeBlurAmount', parseInt(e.target.value))} className="w-full" />
                    </div>
                    <div className="space-y-1">
                        <div className="flex justify-between text-[10px] font-black text-slate-500 uppercase"><span>Escala de Fuente</span><span>{Math.round(pendingSettings.fontSizeScale * 100)}%</span></div>
                        <input type="range" min="0.8" max="1.3" step="0.05" value={pendingSettings.fontSizeScale} onChange={e => handleSettingChange('fontSizeScale', parseFloat(e.target.value))} className="w-full" />
                    </div>
                </div>
            </SettingsSection>

            {/* --- SECCIÓN: ENTRENAMIENTO --- */}
            <SettingsSection title="Experiencia de Entreno" icon={<DumbbellIcon />}>
                <SettingsItem label="Unidad de Peso" icon={<TargetIcon size={18}/>}>
                    <div className="flex gap-1 bg-slate-800 p-1 rounded-xl">
                        <button onClick={() => handleSettingChange('weightUnit', 'kg')} className={`px-4 py-1.5 rounded-lg text-[10px] font-black uppercase transition-all ${pendingSettings.weightUnit === 'kg' ? 'bg-white text-black' : 'text-slate-500'}`}>KG</button>
                        <button onClick={() => handleSettingChange('weightUnit', 'lbs')} className={`px-4 py-1.5 rounded-lg text-[10px] font-black uppercase transition-all ${pendingSettings.weightUnit === 'lbs' ? 'bg-white text-black' : 'text-slate-500'}`}>LBS</button>
                    </div>
                </SettingsItem>

                <SettingsItem label="Inicio Auto. Descanso" description="Inicia el timer al guardar una serie." icon={<ClockIcon size={18}/>}>
                    <ToggleSwitch checked={pendingSettings.restTimerAutoStart} onChange={(c) => handleSettingChange('restTimerAutoStart', c)} />
                </SettingsItem>

                <SettingsItem label="Descanso por Defecto" description="En segundos." icon={<ClockIcon size={18}/>}>
                    <input type="number" step="5" value={pendingSettings.restTimerDefaultSeconds} onChange={e => handleSettingChange('restTimerDefaultSeconds', parseInt(e.target.value) || 90)} className="w-20 bg-slate-800 border-none rounded-lg text-xs font-black text-center p-2" />
                </SettingsItem>

                <SettingsItem label="Métrica de Intensidad" icon={<ZapIcon size={18}/>}>
                     <div className="flex gap-1 bg-slate-800 p-1 rounded-xl">
                        <button onClick={() => handleSettingChange('intensityMetric', 'rpe')} className={`px-4 py-1.5 rounded-lg text-[10px] font-black uppercase transition-all ${pendingSettings.intensityMetric === 'rpe' ? 'bg-primary-color text-white' : 'text-slate-500'}`}>RPE</button>
                        <button onClick={() => handleSettingChange('intensityMetric', 'rir')} className={`px-4 py-1.5 rounded-lg text-[10px] font-black uppercase transition-all ${pendingSettings.intensityMetric === 'rir' ? 'bg-primary-color text-white' : 'text-slate-500'}`}>RIR</button>
                    </div>
                </SettingsItem>
                
                <SettingsItem label="Feedback Háptico" description="Vibración en interacciones clave." icon={<ActivityIcon size={18}/>}>
                     <div className="flex items-center gap-3">
                         <select value={pendingSettings.hapticIntensity} onChange={e => handleSettingChange('hapticIntensity', e.target.value as HapticIntensity)} className="bg-slate-800 border-none rounded-xl text-[10px] font-black text-white p-2">
                             <option value="soft">Suave</option>
                             <option value="medium">Media</option>
                             <option value="heavy">Fuerte</option>
                         </select>
                         <ToggleSwitch checked={pendingSettings.hapticFeedbackEnabled} onChange={(c) => handleSettingChange('hapticFeedbackEnabled', c)} />
                     </div>
                </SettingsItem>
            </SettingsSection>

            {/* --- SECCIÓN: SUEÑO --- */}
            <SettingsSection title="Gestión del Descanso" icon={<MoonIcon />}>
                <SettingsItem label="Algoritmo Smart Sleep" description="Ajusta tu meta de sueño según tu fatiga." icon={<BrainIcon size={18}/>}>
                    <ToggleSwitch checked={pendingSettings.smartSleepEnabled} onChange={(c) => handleSettingChange('smartSleepEnabled', c)} />
                </SettingsItem>
                
                <div className="bg-slate-900/40 p-4 rounded-3xl border border-white/5 space-y-4">
                    <div className="flex flex-col gap-2">
                        <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Días Laborales</label>
                        <div className="flex gap-2 justify-between">
                            {daysOfWeek.map((day) => (
                                <button
                                    key={day.value}
                                    onClick={() => toggleWorkDay(day.value)}
                                    className={`w-8 h-8 rounded-full flex items-center justify-center text-[10px] font-bold transition-all border ${
                                        (pendingSettings.workDays || []).includes(day.value) 
                                            ? 'bg-indigo-600 border-indigo-400 text-white shadow-lg' 
                                            : 'bg-slate-800 border-slate-700 text-slate-500'
                                    }`}
                                >
                                    {day.label}
                                </button>
                            ))}
                        </div>
                    </div>
                    
                    <div className="grid grid-cols-2 gap-4">
                        <div className="space-y-1">
                             <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Despertar (Laboral)</label>
                             <input type="time" value={pendingSettings.wakeTimeWork || '07:00'} onChange={e => handleSettingChange('wakeTimeWork', e.target.value)} className="w-full bg-slate-800 border-none rounded-lg text-sm font-bold text-center p-2 text-white" />
                        </div>
                        <div className="space-y-1">
                             <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Despertar (Libre)</label>
                             <input type="time" value={pendingSettings.wakeTimeOff || '09:00'} onChange={e => handleSettingChange('wakeTimeOff', e.target.value)} className="w-full bg-slate-800 border-none rounded-lg text-sm font-bold text-center p-2 text-white" />
                        </div>
                    </div>

                    <div className="space-y-1 pt-2 border-t border-white/5">
                        <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Objetivo de Sueño (h)</label>
                        <div className="flex items-center gap-3">
                            <input type="range" min="5" max="10" step="0.5" value={pendingSettings.sleepTargetHours} onChange={e => handleSettingChange('sleepTargetHours', parseFloat(e.target.value) || 8)} className="w-full accent-indigo-500" />
                            <span className="text-lg font-black text-indigo-400 w-12 text-center">{pendingSettings.sleepTargetHours}h</span>
                        </div>
                    </div>
                </div>
            </SettingsSection>

            {/* --- SECCIÓN: DATOS Y SEGURIDAD --- */}
            <SettingsSection title="Datos & Seguridad" icon={<CloudIcon />}>
                <div className="grid grid-cols-2 gap-3">
                    <Button onClick={handleExportData} variant="secondary" className="!py-4 !text-xs font-black uppercase"><DownloadIcon size={14} className="mr-2"/> Exportar JSON</Button>
                    <Button onClick={handleCreateSnapshot} variant="secondary" className="!py-4 !text-xs font-black uppercase"><SaveIcon size={14} className="mr-2"/> Crear Snapshot</Button>
                </div>
                
                <div className="pt-4 border-t border-white/5 space-y-3">
                    <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-widest px-1">Carga de Base de Datos</h4>
                    <input type="file" ref={fileInputRef} className="hidden" accept=".json" onChange={async (e) => {
                         const file = e.target.files?.[0];
                         if (!file) return;
                         const text = await file.text();
                         importExerciseDatabase(text);
                    }} />
                    <Button onClick={() => fileInputRef.current?.click()} variant="secondary" className="w-full !py-3 !text-xs font-black uppercase"><UploadIcon size={14} className="mr-2"/> Importar DB Ejercicios</Button>
                </div>
            </SettingsSection>

            <div className="text-center pt-10 pb-20">
                <p className="text-[10px] text-slate-700 font-black uppercase tracking-[0.5em]">KPKN Ecosistema • v3.2</p>
            </div>
        </div>
    );
};

export default SettingsComponent;
