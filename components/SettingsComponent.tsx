// components/SettingsComponent.tsx — NERDIUM: Reforma total
// NOTA: Sección Descanso/Sueño (smartSleepEnabled, workDays, wakeTimeWork/Off, sleepTargetHours) está OCULTA.
// Los datos se mantienen en types/Settings. Resurgirá en una futura gran reforma.
import React, { useState, useEffect, useRef, useMemo } from 'react';
import { Settings, Program, WorkoutLog, BodyProgressLog, NutritionLog, SkippedWorkoutLog, UseGoogleDriveReturn, LocalSnapshot, OneRMFormula, HapticIntensity, View } from '../types';
import {
    SearchIcon, SaveIcon, DownloadIcon, UploadIcon, BellIcon, DumbbellIcon,
    ActivityIcon, UtensilsIcon, ClockIcon, ZapIcon, TargetIcon, MoonIcon,
    CalculatorIcon, ChevronRightIcon, XIcon, RefreshCwIcon, CalendarIcon,
} from './icons';
import { storageService } from '../services/storageService';
import { checkForAppUpdate, performImmediateUpdate } from '../services/appUpdateService';
import { Capacitor } from '@capacitor/core';
import BackgroundEditorModal from './SessionBackgroundModal';
import { NutritionPlanEditorModal } from './nutrition/NutritionPlanEditorModal';
import { useAppDispatch } from '../contexts/AppContext';
import useLocalStorage from '../hooks/useLocalStorage';
import { getLocalDateString } from '../utils/dateUtils';
import { captureException } from '../services/sentryService';

const APP_VERSION = '3.2';

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

// Mapeo tema legacy -> NERDIUM (solo Oscuro/Claro)
const toNerdiumTheme = (t: string): 'dark' | 'light' =>
    t === 'light' ? 'light' : 'dark';
const fromNerdiumTheme = (t: 'dark' | 'light'): Settings['appTheme'] =>
    t === 'light' ? 'light' : 'default';

// --- NERDIUM Components ---
const NerdiumToggle: React.FC<{ checked: boolean; onChange: (v: boolean) => void; disabled?: boolean; ariaLabel?: string }> = ({ checked, onChange, disabled, ariaLabel }) => (
    <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={ariaLabel}
        data-checked={String(checked)}
        disabled={disabled}
        onClick={() => !disabled && onChange(!checked)}
        className={`nerdium-toggle relative inline-flex h-5 w-9 shrink-0 cursor-pointer items-center border transition-colors focus:outline-none focus-ring-nerdium disabled:opacity-50 ${checked ? 'bg-[var(--nerdium-accent)] border-[var(--nerdium-accent-active)]' : 'bg-[var(--nerdium-bg-secondary)] border-[var(--nerdium-border)]'}`}
    >
        <span className={`pointer-events-none inline-block h-4 w-4 bg-[var(--nerdium-text)] transition-transform ${checked ? 'translate-x-4' : 'translate-x-0.5'}`} style={{ backgroundColor: 'var(--nerdium-text)' }} />
    </button>
);

const NerdiumSection: React.FC<{ title?: string; children: React.ReactNode; className?: string }> = ({ title, children, className = '' }) => (
    <div className={`nerdium-section border border-[var(--nerdium-border)] bg-[var(--nerdium-bg-secondary)] p-3 ${className}`}>
        {title && <h4 className="text-[10px] font-bold uppercase tracking-widest text-[var(--nerdium-text-muted)] mb-2">{title}</h4>}
        {children}
    </div>
);

const NerdiumRow: React.FC<{ label: string; description?: string; children: React.ReactNode }> = ({ label, description, children }) => (
    <div className="flex items-center justify-between gap-4 py-2 border-b border-[var(--nerdium-border)] last:border-0 min-h-[36px]">
        <div className="min-w-0 flex-1">
            <span className="text-[11px] font-bold text-[var(--nerdium-text)]">{label}</span>
            {description && <p className="text-[9px] text-[var(--nerdium-text-muted)] mt-0.5 uppercase tracking-wider">{description}</p>}
        </div>
        <div className="shrink-0">{children}</div>
    </div>
);

const TABS = [
    { id: 'general', label: 'General' },
    { id: 'entreno', label: 'Entreno' },
    { id: 'algoritmos', label: 'Algoritmos' },
    { id: 'notif', label: 'Notif.' },
    { id: 'datos', label: 'Datos' },
    { id: 'nutricion', label: 'Nutrición' },
] as const;

const AppUpdateCheckItem: React.FC = () => {
    const [status, setStatus] = useState<'idle' | 'checking' | 'available' | 'updating' | 'error' | 'current'>('idle');
    const [info, setInfo] = useState<{ currentVersion: string; availableVersion?: string } | null>(null);
    const handleCheck = async () => {
        setStatus('checking');
        const result = await checkForAppUpdate();
        if (!result) { setStatus('error'); return; }
        setInfo({ currentVersion: result.currentVersion, availableVersion: result.availableVersion });
        setStatus(result.updateAvailable ? 'available' : 'current');
    };
    const handleUpdate = async () => {
        setStatus('updating');
        await performImmediateUpdate();
    };
    return (
        <NerdiumSection>
            <NerdiumRow label="Buscar actualizaciones">
                <button onClick={handleCheck} disabled={status === 'checking'} className="px-3 py-1.5 text-[10px] font-bold uppercase border border-[var(--nerdium-border)] bg-transparent text-[var(--nerdium-accent)] hover:bg-[var(--nerdium-accent)]/10 transition-colors disabled:opacity-50">
                    {status === 'checking' ? 'Buscando...' : 'Comprobar'}
                </button>
            </NerdiumRow>
            {status === 'available' && info && (
                <div className="mt-2 p-2 border border-[var(--nerdium-accent)]/30 bg-[var(--nerdium-accent)]/5">
                    <p className="text-[10px] font-bold text-[var(--nerdium-accent)]">v{info.availableVersion} disponible</p>
                    <button onClick={handleUpdate} className="mt-1 px-2 py-1 text-[9px] font-bold uppercase border border-[var(--nerdium-accent)] text-[var(--nerdium-accent)]">Actualizar</button>
                </div>
            )}
            {status === 'current' && <p className="text-[9px] text-[var(--nerdium-text-muted)] mt-1">Última versión.</p>}
            {status === 'error' && <p className="text-[9px] text-[var(--nerdium-danger)] mt-1">Error al comprobar.</p>}
        </NerdiumSection>
    );
};

const TabBarCustomizer: React.FC<{ currentTabs: View[]; onChange: (tabs: View[]) => void; style?: string }> = ({ currentTabs, onChange, style }) => {
    const AVAILABLE_TABS: { id: View; label: string }[] = [
        { id: 'home', label: 'Entreno' }, { id: 'nutrition', label: 'Nutrición' }, { id: 'recovery', label: 'Batería' },
        { id: 'sleep', label: 'Descanso' }, { id: 'kpkn', label: 'KPKN' }, { id: 'progress', label: 'Progreso' },
        { id: 'coach', label: 'Coach' }, { id: 'tasks', label: 'Tareas' },
    ];
    const toggleTab = (tabId: View) => {
        if (currentTabs.includes(tabId)) {
            if (tabId === 'home') return;
            onChange(currentTabs.filter(t => t !== tabId));
        } else {
            if (currentTabs.length >= 4) return;
            onChange([...currentTabs, tabId]);
        }
    };
    return (
        <NerdiumSection title="Menú inferior (máx. 4)">
            {style && (
                <div className="mb-2">
                    <span className="text-[9px] text-[var(--nerdium-text-muted)]">Estilo: {style}</span>
                </div>
            )}
            <div className="grid grid-cols-2 gap-1">
                {AVAILABLE_TABS.map(tab => {
                    const isSelected = currentTabs.includes(tab.id);
                    const isFull = !isSelected && currentTabs.length >= 4;
                    return (
                        <button
                            key={tab.id}
                            onClick={() => toggleTab(tab.id)}
                            disabled={isFull || tab.id === 'home'}
                            className={`px-2 py-1.5 text-[9px] font-bold uppercase border text-left transition-colors ${isSelected ? 'border-[var(--nerdium-accent)] bg-[var(--nerdium-accent)]/10 text-[var(--nerdium-accent)]' : 'border-[var(--nerdium-border)] text-[var(--nerdium-text-muted)]'} ${(isFull || tab.id === 'home') ? 'opacity-60' : ''}`}
                        >
                            {tab.label}
                        </button>
                    );
                })}
            </div>
        </NerdiumSection>
    );
};

export const SettingsComponent: React.FC<SettingsProps> = ({
    settings, onSettingsChange, setPrograms, setHistory, setSkippedLogs, setBodyProgress, setNutritionLogs,
    drive, installPromptEvent, setInstallPromptEvent, isOnline,
}) => {
    const [pendingSettings, setPendingSettings] = useState<Settings>(settings);
    const [hasChanges, setHasChanges] = useState(false);
    const [activeTab, setActiveTab] = useState<(typeof TABS)[number]['id']>('general');
    const [searchOpen, setSearchOpen] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [showAdvancedAlgo, setShowAdvancedAlgo] = useState(false);
    const [dangerZoneOpen, setDangerZoneOpen] = useState(false);
    const [devMode, setDevMode] = useState(false);
    const [devTapCount, setDevTapCount] = useState(0);
    const [isBgModalOpen, setIsBgModalOpen] = useState(false);
    const [isNutritionPlanEditorOpen, setIsNutritionPlanEditorOpen] = useState(false);
    const [snapshots, setSnapshots] = useLocalStorage<LocalSnapshot[]>('yourprime-snapshots', []);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const { navigateTo, importExerciseDatabase } = useAppDispatch();

    const resolvedTheme = toNerdiumTheme(
        ['dark', 'light'].includes(settings.appTheme) ? settings.appTheme : 'dark'
    );

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
            algorithmSettings: { ...prev.algorithmSettings, [key]: value as never },
        }));
        setHasChanges(true);
    };

    const toggleWorkDay = (dayIndex: number) => {
        setPendingSettings(prev => {
            const currentDays = prev.workDays || [];
            const newDays = currentDays.includes(dayIndex) ? currentDays.filter(d => d !== dayIndex) : [...currentDays, dayIndex];
            return { ...prev, workDays: newDays.sort() };
        });
        setHasChanges(true);
    };

    const handleSaveChanges = () => {
        onSettingsChange(pendingSettings);
        setHasChanges(false);
    };

    const handleCreateSnapshot = async () => {
        const name = prompt('Nombre para esta copia:', `Snapshot ${new Date().toLocaleDateString()}`);
        if (!name) return;
        try {
            const dataToBackup = {
                programs: await storageService.get<Program[]>('programs') || [],
                history: await storageService.get<WorkoutLog[]>('history') || [],
                settings: await storageService.get<Settings>('yourprime-settings') || settings,
                'body-progress': await storageService.get<BodyProgressLog[]>('body-progress') || [],
                'nutrition-logs': await storageService.get<NutritionLog[]>('nutrition-logs') || [],
            };
            const newSnapshot: LocalSnapshot = { id: crypto.randomUUID(), name, date: new Date().toISOString(), data: dataToBackup };
            setSnapshots(prev => [...prev, newSnapshot]);
            alert('Copia creada.');
        } catch (e) {
            console.error(e);
            alert('Error al crear copia.');
        }
    };

    const handleExportData = async () => {
        try {
            const data = await storageService.getAllDataForExport();
            const jsonString = `data:text/json;charset=utf-8,${encodeURIComponent(JSON.stringify(data, null, 2))}`;
            const link = document.createElement('a');
            link.href = jsonString;
            link.download = `kpkn_backup_${getLocalDateString()}.json`;
            link.click();
        } catch (e) {
            alert('Error al exportar.');
        }
    };

    const handleResetTours = () => {
        handleSettingChange('hasSeenWelcome', false);
        handleSettingChange('hasSeenHomeTour', false);
        handleSettingChange('hasSeenProgramEditorTour', false);
        handleSettingChange('hasSeenSessionEditorTour', false);
        handleSettingChange('hasSeenKPKNTour', false);
        handleSettingChange('hasSeenNutritionWizard', false);
        handleSettingChange('hasSeenGeneralWizard', false);
        alert('Tutoriales reiniciados. Vuelve al inicio para verlos.');
    };

    const handleDevTap = () => {
        const next = devTapCount + 1;
        setDevTapCount(next);
        if (next >= 7) {
            setDevMode(true);
            setDevTapCount(0);
        }
    };

    const daysOfWeek = [
        { label: 'D', value: 0 }, { label: 'L', value: 1 }, { label: 'M', value: 2 },
        { label: 'M', value: 3 }, { label: 'J', value: 4 }, { label: 'V', value: 5 }, { label: 'S', value: 6 },
    ];

    const filterItems = useMemo(() => {
        if (!searchQuery.trim()) return null;
        const q = searchQuery.toLowerCase();
        return (label: string, desc?: string) =>
            label.toLowerCase().includes(q) || (desc && desc.toLowerCase().includes(q));
    }, [searchQuery]);

    const matchesFilter = (label: string, desc?: string) => !filterItems || filterItems(label, desc);

    return (
        <div data-testid="settings-page" className="nerdium-settings h-full flex flex-col bg-[var(--nerdium-bg)] text-[var(--nerdium-text)] animate-fade-in">
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
            <NutritionPlanEditorModal isOpen={isNutritionPlanEditorOpen} onClose={() => setIsNutritionPlanEditorOpen(false)} />

            <header className="sticky top-0 z-10 bg-[var(--nerdium-bg)] border-b border-[var(--nerdium-border)] px-4 py-3">
                <div className="flex items-center justify-between gap-2">
                    <h1 className="text-sm font-bold uppercase tracking-widest">Ajustes</h1>
                    <div className="flex items-center gap-2">
                        <button
                            aria-label="Buscar ajustes"
                            onClick={() => setSearchOpen(!searchOpen)}
                            className="p-1.5 border border-[var(--nerdium-border)] hover:border-[var(--nerdium-accent)]/50 transition-colors"
                        >
                            <SearchIcon size={14} />
                        </button>
                        {hasChanges && (
                            <>
                                <button onClick={() => setPendingSettings(settings)} className="px-2 py-1 text-[9px] font-bold uppercase border border-[var(--nerdium-border)]">Cancelar</button>
                                <button onClick={handleSaveChanges} className="px-2 py-1 text-[9px] font-bold uppercase bg-[var(--nerdium-accent)] text-[var(--nerdium-bg)] border border-[var(--nerdium-accent-active)]">Guardar</button>
                            </>
                        )}
                    </div>
                </div>
                {searchOpen && (
                    <div className="mt-2 flex gap-2">
                        <input
                            type="text"
                            value={searchQuery}
                            onChange={e => setSearchQuery(e.target.value)}
                            placeholder="Buscar..."
                            className="nerdium-input flex-1 px-2 py-1.5 text-[11px]"
                            autoFocus
                        />
                        <button onClick={() => { setSearchOpen(false); setSearchQuery(''); }} aria-label="Cerrar búsqueda">
                            <XIcon size={14} />
                        </button>
                    </div>
                )}
            </header>

            <div className="overflow-x-auto no-scrollbar border-b border-[var(--nerdium-border)]" role="tablist" aria-label="Secciones de ajustes">
                <div className="flex min-w-max px-2">
                    {TABS.map((tab, idx) => (
                        <button
                            key={tab.id}
                            data-active={activeTab === tab.id}
                            onClick={() => setActiveTab(tab.id)}
                            className="nerdium-tab focus-ring-nerdium"
                            role="tab"
                            aria-selected={activeTab === tab.id}
                            tabIndex={activeTab === tab.id ? 0 : -1}
                            aria-controls={`panel-${tab.id}`}
                            id={`tab-${tab.id}`}
                        >
                            {tab.label}
                        </button>
                    ))}
                </div>
            </div>

            <div role="tabpanel" aria-labelledby={`tab-${activeTab}`} id={`panel-${activeTab}`} className="p-4 flex-1 overflow-y-auto custom-scrollbar space-y-4">
                {activeTab === 'general' && (
                    <>
                        {matchesFilter('Tema', 'Oscuro Claro') && (
                            <NerdiumSection title="Apariencia">
                                <NerdiumRow label="Tema">
                                    <div className="flex gap-1">
                                        {(['dark', 'light'] as const).map(t => (
                                            <button
                                                key={t}
                                                data-testid={t === 'dark' ? 'settings-theme-dark' : 'settings-theme-light'}
                                                onClick={() => handleSettingChange('appTheme', fromNerdiumTheme(t))}
                                                className={`px-3 py-1.5 text-[10px] font-bold uppercase border ${resolvedTheme === t ? 'border-[var(--nerdium-accent)] bg-[var(--nerdium-accent)]/20 text-[var(--nerdium-accent)]' : 'border-[var(--nerdium-border)] text-[var(--nerdium-text-muted)]'}`}
                                            >
                                                {t === 'dark' ? 'Oscuro' : 'Claro'}
                                            </button>
                                        ))}
                                    </div>
                                </NerdiumRow>
                                {matchesFilter('Pack de sonidos') && (
                                    <NerdiumRow label="Pack de sonidos">
                                        <select
                                            value={pendingSettings.soundPack || 'classic'}
                                            onChange={e => handleSettingChange('soundPack', e.target.value as 'classic' | 'minimal' | 'none')}
                                            className="nerdium-input px-2 py-1 text-[10px]"
                                        >
                                            <option value="classic">Clásico</option>
                                            <option value="minimal">Minimal</option>
                                            <option value="none">Ninguno</option>
                                        </select>
                                    </NerdiumRow>
                                )}
                                {matchesFilter('Estilo barra inferior', 'Tab bar') && (
                                    <NerdiumRow label="Estilo barra inferior">
                                        <select
                                            value={pendingSettings.tabBarStyle || 'default'}
                                            onChange={e => handleSettingChange('tabBarStyle', e.target.value as 'default' | 'compact' | 'icons-only')}
                                            className="nerdium-input px-2 py-1 text-[10px]"
                                        >
                                            <option value="default">Predeterminado</option>
                                            <option value="compact">Compacta</option>
                                            <option value="icons-only">Solo íconos</option>
                                        </select>
                                    </NerdiumRow>
                                )}
                                <TabBarCustomizer
                                    currentTabs={pendingSettings.enabledTabs || ['home', 'nutrition', 'recovery', 'sleep']}
                                    onChange={tabs => handleSettingChange('enabledTabs', tabs)}
                                    style={pendingSettings.tabBarStyle}
                                />
                                {matchesFilter('Escala de fuente') && (
                                    <NerdiumRow label="Escala de fuente" description={`${Math.round((pendingSettings.fontSizeScale || 1) * 100)}%`}>
                                        <input
                                            type="range"
                                            min="0.8"
                                            max="1.3"
                                            step="0.05"
                                            value={pendingSettings.fontSizeScale || 1}
                                            onChange={e => handleSettingChange('fontSizeScale', parseFloat(e.target.value))}
                                            className="w-24 accent-amber-500"
                                        />
                                    </NerdiumRow>
                                )}
                            </NerdiumSection>
                        )}
                        {matchesFilter('Tutoriales', 'Ver de nuevo') && (
                            <NerdiumSection>
                                <NerdiumRow label="Ver tutoriales de nuevo" description="Reinicia los tours de la app">
                                    <button onClick={handleResetTours} className="px-2 py-1 text-[10px] font-bold uppercase border border-[var(--nerdium-border)] hover:border-[var(--nerdium-accent)]/50">Reiniciar</button>
                                </NerdiumRow>
                            </NerdiumSection>
                        )}
                    </>
                )}

                {activeTab === 'entreno' && (
                    <NerdiumSection title="Experiencia de entreno">
                        {matchesFilter('Unidad de peso') && (
                            <NerdiumRow label="Unidad de peso">
                                <div className="flex gap-1">
                                    {(['kg', 'lbs'] as const).map(u => (
                                        <button
                                            key={u}
                                            onClick={() => handleSettingChange('weightUnit', u)}
                                            className={`px-3 py-1.5 text-[10px] font-bold uppercase border ${pendingSettings.weightUnit === u ? 'border-[var(--nerdium-accent)] bg-[var(--nerdium-accent)]/20' : 'border-[var(--nerdium-border)]'}`}
                                        >
                                            {u.toUpperCase()}
                                        </button>
                                    ))}
                                </div>
                            </NerdiumRow>
                        )}
                        {matchesFilter('Inicio auto descanso') && (
                            <NerdiumRow label="Inicio auto descanso" description="Timer al guardar serie">
                                <NerdiumToggle checked={pendingSettings.restTimerAutoStart} onChange={c => handleSettingChange('restTimerAutoStart', c)} />
                            </NerdiumRow>
                        )}
                        {matchesFilter('Descanso por defecto') && (
                            <NerdiumRow label="Descanso por defecto (s)">
                                <input
                                    type="number"
                                    step={5}
                                    value={pendingSettings.restTimerDefaultSeconds}
                                    onChange={e => handleSettingChange('restTimerDefaultSeconds', parseInt(e.target.value) || 90)}
                                    className="nerdium-input w-16 px-2 py-1 text-center text-[10px]"
                                />
                            </NerdiumRow>
                        )}
                        {matchesFilter('Vista compacta') && (
                            <NerdiumRow label="Vista compacta sesión">
                                <NerdiumToggle checked={!!pendingSettings.sessionCompactView} onChange={c => handleSettingChange('sessionCompactView', c)} />
                            </NerdiumRow>
                        )}
                        {matchesFilter('Auto-avance campos') && (
                            <NerdiumRow label="Auto-avance entre campos">
                                <NerdiumToggle checked={pendingSettings.sessionAutoAdvanceFields !== false} onChange={c => handleSettingChange('sessionAutoAdvanceFields', c)} />
                            </NerdiumRow>
                        )}
                        {matchesFilter('Métrica de intensidad') && (
                            <NerdiumRow label="Métrica de intensidad">
                                <div className="flex gap-1">
                                    {(['rpe', 'rir'] as const).map(m => (
                                        <button
                                            key={m}
                                            onClick={() => handleSettingChange('intensityMetric', m)}
                                            className={`px-3 py-1.5 text-[10px] font-bold uppercase border ${pendingSettings.intensityMetric === m ? 'border-[var(--nerdium-accent)] bg-[var(--nerdium-accent)]/20' : 'border-[var(--nerdium-border)]'}`}
                                        >
                                            {m.toUpperCase()}
                                        </button>
                                    ))}
                                </div>
                            </NerdiumRow>
                        )}
                        {matchesFilter('Feedback háptico') && (
                            <NerdiumRow label="Feedback háptico" description="Vibración">
                                <div className="flex items-center gap-2" data-testid="settings-haptics">
                                    <select
                                        value={pendingSettings.hapticIntensity}
                                        onChange={e => handleSettingChange('hapticIntensity', e.target.value as HapticIntensity)}
                                        className="nerdium-input px-2 py-1 text-[10px]"
                                        aria-label="Intensidad háptica"
                                    >
                                        <option value="soft">Suave</option>
                                        <option value="medium">Media</option>
                                        <option value="heavy">Fuerte</option>
                                    </select>
                                    <NerdiumToggle checked={pendingSettings.hapticFeedbackEnabled} onChange={c => handleSettingChange('hapticFeedbackEnabled', c)} />
                                </div>
                            </NerdiumRow>
                        )}
                    </NerdiumSection>
                )}

                {activeTab === 'algoritmos' && (
                    <>
                        <NerdiumSection title="Básico">
                            {matchesFilter('Fórmula 1RM') && (
                                <NerdiumRow label="Fórmula 1RM">
                                    <select
                                        value={pendingSettings.oneRMFormula}
                                        onChange={e => handleSettingChange('oneRMFormula', e.target.value as OneRMFormula)}
                                        className="nerdium-input px-2 py-1 text-[10px]"
                                    >
                                        <option value="brzycki">Brzycki</option>
                                        <option value="epley">Epley</option>
                                        <option value="lander">Lander</option>
                                    </select>
                                </NerdiumRow>
                            )}
                            {matchesFilter('Sistema de volumen') && (
                                <NerdiumRow label="Sistema de volumen">
                                    <select
                                        value={pendingSettings.volumeSystem || 'kpnk'}
                                        onChange={e => handleSettingChange('volumeSystem', e.target.value as 'israetel' | 'kpnk' | 'manual')}
                                        className="nerdium-input px-2 py-1 text-[10px]"
                                    >
                                        <option value="israetel">Israetel</option>
                                        <option value="kpnk">KPKN Personalizado</option>
                                        <option value="manual">Manual</option>
                                    </select>
                                </NerdiumRow>
                            )}
                            {matchesFilter('Conectar nutrición batería') && (
                                <NerdiumRow label="Conectar nutrición con batería AUGE">
                                    <NerdiumToggle
                                        checked={pendingSettings.algorithmSettings?.augeEnableNutritionTracking !== false}
                                        onChange={c => handleAlgorithmChange('augeEnableNutritionTracking', c)}
                                    />
                                </NerdiumRow>
                            )}
                            {matchesFilter('Tracking sueño AUGE') && (
                                <NerdiumRow label="Tracking sueño AUGE">
                                    <NerdiumToggle
                                        checked={pendingSettings.algorithmSettings?.augeEnableSleepTracking !== false}
                                        onChange={c => handleAlgorithmChange('augeEnableSleepTracking', c)}
                                    />
                                </NerdiumRow>
                            )}
                            <div className="pt-2">
                                <button
                                    onClick={() => setIsNutritionPlanEditorOpen(true)}
                                    className="w-full py-2 text-[10px] font-bold uppercase border border-[var(--nerdium-border)] hover:border-[var(--nerdium-accent)]/50"
                                >
                                    Editar plan de alimentación
                                </button>
                            </div>
                        </NerdiumSection>
                        <NerdiumSection>
                            <button
                                onClick={() => setShowAdvancedAlgo(!showAdvancedAlgo)}
                                className="flex items-center gap-2 w-full text-left text-[10px] font-bold uppercase text-[var(--nerdium-text-muted)]"
                            >
                                <ChevronRightIcon size={12} className={`transition-transform ${showAdvancedAlgo ? 'rotate-90' : ''}`} />
                                Opciones avanzadas
                            </button>
                            {showAdvancedAlgo && (
                                <div className="mt-2 space-y-2 pt-2 border-t border-[var(--nerdium-border)]">
                                    <div>
                                        <div className="flex justify-between text-[9px] text-[var(--nerdium-text-muted)]"><span>Decaimiento 1RM %/día</span><span>{pendingSettings.algorithmSettings.oneRMDecayRate}</span></div>
                                        <input type="range" min={0.01} max={0.5} step={0.01} value={pendingSettings.algorithmSettings.oneRMDecayRate} onChange={e => handleAlgorithmChange('oneRMDecayRate', parseFloat(e.target.value))} className="w-full accent-amber-500" />
                                    </div>
                                    <div>
                                        <div className="flex justify-between text-[9px] text-[var(--nerdium-text-muted)]"><span>Factor fatiga al fallo</span><span>{pendingSettings.algorithmSettings.failureFatigueFactor}x</span></div>
                                        <input type="range" min={1} max={2} step={0.05} value={pendingSettings.algorithmSettings.failureFatigueFactor} onChange={e => handleAlgorithmChange('failureFatigueFactor', parseFloat(e.target.value))} className="w-full accent-amber-500" />
                                    </div>
                                    <div>
                                        <div className="flex justify-between text-[9px] text-[var(--nerdium-text-muted)]"><span>Factor sinergista</span><span>{pendingSettings.algorithmSettings.synergistFactor}x</span></div>
                                        <input type="range" min={0} max={0.6} step={0.05} value={pendingSettings.algorithmSettings.synergistFactor} onChange={e => handleAlgorithmChange('synergistFactor', parseFloat(e.target.value))} className="w-full accent-amber-500" />
                                    </div>
                                </div>
                            )}
                        </NerdiumSection>
                    </>
                )}

                {activeTab === 'notif' && (
                    <NerdiumSection title="Notificaciones">
                        {matchesFilter('Recordatorio sesión') && (
                            <NerdiumRow label="Recordatorio sesión del día">
                                <div className="flex items-center gap-2">
                                    <input type="time" value={pendingSettings.reminderTime || '17:00'} onChange={e => handleSettingChange('reminderTime', e.target.value)} className="nerdium-input w-20 px-1 py-1 text-[10px]" />
                                    <NerdiumToggle checked={!!pendingSettings.remindersEnabled} onChange={c => handleSettingChange('remindersEnabled', c)} />
                                </div>
                            </NerdiumRow>
                        )}
                        {matchesFilter('Recordatorios comidas') && (
                            <NerdiumRow label="Recordatorios comidas">
                                <NerdiumToggle checked={!!pendingSettings.mealRemindersEnabled} onChange={c => handleSettingChange('mealRemindersEnabled', c)} />
                            </NerdiumRow>
                        )}
                        {pendingSettings.mealRemindersEnabled && (
                            <div className="grid grid-cols-3 gap-1 py-2">
                                <div><label className="text-[8px] text-[var(--nerdium-text-muted)]">Desayuno</label><input type="time" value={pendingSettings.breakfastReminderTime || '08:00'} onChange={e => handleSettingChange('breakfastReminderTime', e.target.value)} className="nerdium-input w-full px-1 py-0.5 text-[9px]" /></div>
                                <div><label className="text-[8px] text-[var(--nerdium-text-muted)]">Almuerzo</label><input type="time" value={pendingSettings.lunchReminderTime || '14:00'} onChange={e => handleSettingChange('lunchReminderTime', e.target.value)} className="nerdium-input w-full px-1 py-0.5 text-[9px]" /></div>
                                <div><label className="text-[8px] text-[var(--nerdium-text-muted)]">Cena</label><input type="time" value={pendingSettings.dinnerReminderTime || '21:00'} onChange={e => handleSettingChange('dinnerReminderTime', e.target.value)} className="nerdium-input w-full px-1 py-0.5 text-[9px]" /></div>
                            </div>
                        )}
                        {matchesFilter('Aviso entrenamiento no registrado') && (
                            <NerdiumRow label="Aviso entrenamiento no registrado">
                                <div className="flex items-center gap-2">
                                    <input type="time" value={pendingSettings.missedWorkoutReminderTime || '21:00'} onChange={e => handleSettingChange('missedWorkoutReminderTime', e.target.value)} className="nerdium-input w-20 px-1 py-1 text-[10px]" />
                                    <NerdiumToggle checked={!!pendingSettings.missedWorkoutReminderEnabled} onChange={c => handleSettingChange('missedWorkoutReminderEnabled', c)} />
                                </div>
                            </NerdiumRow>
                        )}
                        {matchesFilter('Batería AUGE baja') && (
                            <NerdiumRow label="Batería AUGE baja">
                                <NerdiumToggle checked={!!pendingSettings.augeBatteryReminderEnabled} onChange={c => handleSettingChange('augeBatteryReminderEnabled', c)} />
                            </NerdiumRow>
                        )}
                        {pendingSettings.augeBatteryReminderEnabled && (
                            <div className="flex gap-2 py-2">
                                <div><label className="text-[8px] text-[var(--nerdium-text-muted)]">Umbral %</label><input type="number" min={5} max={50} value={pendingSettings.augeBatteryReminderThreshold ?? 20} onChange={e => handleSettingChange('augeBatteryReminderThreshold', parseInt(e.target.value) || 20)} className="nerdium-input w-14 px-1 py-0.5 text-[9px]" /></div>
                                <div><label className="text-[8px] text-[var(--nerdium-text-muted)]">Hora</label><input type="time" value={pendingSettings.augeBatteryReminderTime || '09:00'} onChange={e => handleSettingChange('augeBatteryReminderTime', e.target.value)} className="nerdium-input w-20 px-1 py-0.5 text-[9px]" /></div>
                            </div>
                        )}
                        {matchesFilter('Eventos y bloques') && (
                            <NerdiumRow label="Eventos y bloques">
                                <NerdiumToggle checked={!!pendingSettings.eventRemindersEnabled} onChange={c => handleSettingChange('eventRemindersEnabled', c)} />
                            </NerdiumRow>
                        )}
                    </NerdiumSection>
                )}

                {activeTab === 'datos' && (
                    <>
                        <NerdiumSection title="Respaldos">
                            <div className="flex flex-wrap gap-2">
                                <button onClick={handleExportData} className="px-3 py-2 text-[10px] font-bold uppercase border border-[var(--nerdium-border)] hover:border-[var(--nerdium-accent)]/50 flex items-center gap-2">
                                    <DownloadIcon size={12} /> Exportar JSON
                                </button>
                                <button onClick={handleCreateSnapshot} className="px-3 py-2 text-[10px] font-bold uppercase border border-[var(--nerdium-border)] hover:border-[var(--nerdium-accent)]/50 flex items-center gap-2">
                                    <SaveIcon size={12} /> Crear snapshot
                                </button>
                            </div>
                            <div className="pt-2">
                                <input type="file" ref={fileInputRef} className="hidden" accept=".json" onChange={async e => {
                                    const file = e.target.files?.[0];
                                    if (!file) return;
                                    const text = await file.text();
                                    importExerciseDatabase(text);
                                }} />
                                <button onClick={() => fileInputRef.current?.click()} className="w-full py-2 text-[10px] font-bold uppercase border border-[var(--nerdium-border)] hover:border-[var(--nerdium-accent)]/50 flex items-center justify-center gap-2">
                                    <UploadIcon size={12} /> Importar DB ejercicios
                                </button>
                            </div>
                        </NerdiumSection>
                        <details className={`nerdium-danger-zone mt-4 border rounded-sm overflow-hidden ${dangerZoneOpen ? 'open' : ''}`}>
                            <summary onClick={e => { e.preventDefault(); setDangerZoneOpen(!dangerZoneOpen); }} className="p-2 cursor-pointer text-[10px] font-bold uppercase text-[var(--nerdium-danger)] flex items-center gap-2">
                                <ChevronRightIcon size={12} className={`transition-transform ${dangerZoneOpen ? 'rotate-90' : ''}`} />
                                Zona peligrosa
                            </summary>
                            {dangerZoneOpen && (
                                <div className="p-3 border-t border-[var(--nerdium-danger-border)] space-y-2">
                                    <p className="text-[9px] text-[var(--nerdium-text-muted)]">Acciones irreversibles. Usar con precaución.</p>
                                    {/* Placeholder para reset/borrar - extensible */}
                                </div>
                            )}
                        </details>
                        {devMode && (
                            <NerdiumSection title="Herramientas de desarrollo" className="mt-4">
                                {Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android' && <AppUpdateCheckItem />}
                                <NerdiumRow label="Probar Sentry">
                                    <button onClick={() => { captureException(new Error('Test error NERDIUM')); alert('Error enviado a Sentry.'); }} className="px-2 py-1 text-[9px] font-bold uppercase border border-[var(--nerdium-danger)] text-[var(--nerdium-danger)]">Enviar test</button>
                                </NerdiumRow>
                            </NerdiumSection>
                        )}
                    </>
                )}

                {activeTab === 'nutricion' && (
                    <NerdiumSection title="Nutrición y biometría">
                        {matchesFilter('Objetivo calorías') && (
                            <NerdiumRow label="Objetivo calorías" description={pendingSettings.calorieGoalObjective}>
                                <select value={pendingSettings.calorieGoalObjective} onChange={e => handleSettingChange('calorieGoalObjective', e.target.value as 'deficit' | 'maintenance' | 'surplus')} className="nerdium-input px-2 py-1 text-[10px]">
                                    <option value="deficit">Déficit</option>
                                    <option value="maintenance">Mantenimiento</option>
                                    <option value="surplus">Superávit</option>
                                </select>
                            </NerdiumRow>
                        )}
                        {matchesFilter('Calorías diarias') && pendingSettings.dailyCalorieGoal != null && (
                            <NerdiumRow label="Calorías diarias">
                                <input type="number" value={pendingSettings.dailyCalorieGoal} onChange={e => handleSettingChange('dailyCalorieGoal', parseInt(e.target.value) || undefined)} className="nerdium-input w-20 px-2 py-1 text-[10px] text-right" />
                            </NerdiumRow>
                        )}
                        {matchesFilter('Proteína diaria') && pendingSettings.dailyProteinGoal != null && (
                            <NerdiumRow label="Proteína diaria (g)">
                                <input type="number" value={pendingSettings.dailyProteinGoal} onChange={e => handleSettingChange('dailyProteinGoal', parseInt(e.target.value) || undefined)} className="nerdium-input w-20 px-2 py-1 text-[10px] text-right" />
                            </NerdiumRow>
                        )}
                        {matchesFilter('Preferencia dietética') && (
                            <NerdiumRow label="Preferencia dietética">
                                <select value={pendingSettings.dietaryPreference || 'omnivore'} onChange={e => handleSettingChange('dietaryPreference', e.target.value as any)} className="nerdium-input px-2 py-1 text-[10px]">
                                    <option value="omnivore">Omnívoro</option>
                                    <option value="vegetarian">Vegetariano</option>
                                    <option value="vegan">Vegano</option>
                                    <option value="keto">Keto</option>
                                </select>
                            </NerdiumRow>
                        )}
                        <div className="pt-2">
                            <button onClick={() => setIsNutritionPlanEditorOpen(true)} className="w-full py-2 text-[10px] font-bold uppercase border border-[var(--nerdium-border)] hover:border-[var(--nerdium-accent)]/50">
                                Editar plan de alimentación
                            </button>
                        </div>
                    </NerdiumSection>
                )}
            </div>

            <div className="pt-8 pb-4 text-center">
                <div onClick={handleDevTap} className="inline-block cursor-default" role="button" tabIndex={0} onKeyDown={e => e.key === 'Enter' && handleDevTap()} aria-label="Área de versión">
                    {devMode ? <span className="text-[9px] text-[var(--nerdium-text-muted)]">v{APP_VERSION}</span> : <span className="inline-block w-8 h-2" />}
                </div>
            </div>
        </div>
    );
};

export default SettingsComponent;
