// components/SettingsComponent.tsx
// Rediseño completo con Material 3 + Liquid Glass (referencia Home.tsx)

import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  Settings,
  Program,
  WorkoutLog,
  BodyProgressLog,
  NutritionLog,
  SkippedWorkoutLog,
  UseGoogleDriveReturn,
  LocalSnapshot,
  OneRMFormula,
  HapticIntensity,
  View,
} from '../types';
import {
  SearchIcon,
  SaveIcon,
  DownloadIcon,
  UploadIcon,
  BellIcon,
  DumbbellIcon,
  ActivityIcon,
  UtensilsIcon,
  ClockIcon,
  ZapIcon,
  TargetIcon,
  MoonIcon,
  SunIcon,
  CalculatorIcon,
  ChevronRightIcon,
  XIcon,
  RefreshCwIcon,
  CalendarIcon,
  SparklesIcon,
  BrainIcon,
  FoodIcon,
  DatabaseIcon,
  ShieldIcon,
  UserIcon,
  PaletteIcon,
  Volume2Icon,
  LayoutIcon,
  TypeIcon,
  TimerIcon,
  FlagIcon,
  TrendingUpIcon,
  AlertTriangleIcon,
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
import { motion } from 'framer-motion';

const APP_VERSION = '3.2';

const TABS = [
  { id: 'general', label: 'General', icon: UserIcon },
  { id: 'entreno', label: 'Entreno', icon: DumbbellIcon },
  { id: 'algoritmos', label: 'Algoritmos', icon: BrainIcon },
  { id: 'notif', label: 'Notif.', icon: BellIcon },
  { id: 'datos', label: 'Datos', icon: DatabaseIcon },
  { id: 'nutricion', label: 'Nutrición', icon: FoodIcon },
] as const;

// --- Sub-components ---

const Toggle: React.FC<{
  checked: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
  ariaLabel?: string;
}> = ({ checked, onChange, disabled, ariaLabel }) => (
  <button
    type="button"
    role="switch"
    aria-checked={checked}
    aria-label={ariaLabel}
    disabled={disabled}
    onClick={() => !disabled && onChange(!checked)}
    className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:opacity-50 ${
      checked ? 'bg-primary' : 'bg-[#ECE6F0]'
    }`}
  >
    <span
      className={`inline-block h-5 w-5 rounded-full bg-white shadow transition-transform ${
        checked ? 'translate-x-5' : 'translate-x-0.5'
      }`}
    />
  </button>
);

interface SettingCardProps {
  title?: string;
  icon?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}

const SettingCard: React.FC<SettingCardProps> = ({ title, icon, children, className = '' }) => (
  <motion.div
    initial={{ opacity: 0, y: 10 }}
    animate={{ opacity: 1, y: 0 }}
    className={`rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm overflow-hidden ${className}`}
  >
    {title && (
      <div className="px-5 py-4 border-b border-black/[0.03] flex items-center gap-3">
        {icon}
        <h3 className="text-[10px] font-black uppercase tracking-widest text-[#49454F] opacity-50">
          {title}
        </h3>
      </div>
    )}
    <div className="p-2">{children}</div>
  </motion.div>
);

interface SettingRowProps {
  label: string;
  description?: string;
  children: React.ReactNode;
  border?: boolean;
}

const SettingRow: React.FC<SettingRowProps> = ({ label, description, children, border = true }) => (
  <div
    className={`flex items-center justify-between gap-4 px-5 py-4 min-h-[56px] ${
      border ? 'border-b border-black/[0.03]' : ''
    }`}
  >
    <div className="min-w-0 flex-1">
      <span className="text-sm font-bold text-[#1D1B20]">{label}</span>
      {description && (
        <p className="text-[10px] font-medium text-[#49454F] opacity-50 mt-0.5">{description}</p>
      )}
    </div>
    <div className="shrink-0">{children}</div>
  </div>
);

const AppUpdateCheckItem: React.FC = () => {
  const [status, setStatus] = useState<'idle' | 'checking' | 'available' | 'updating' | 'error' | 'current'>(
    'idle'
  );
  const [info, setInfo] = useState<{ currentVersion: string; availableVersion?: string } | null>(null);

  const handleCheck = async () => {
    setStatus('checking');
    const result = await checkForAppUpdate();
    if (!result) {
      setStatus('error');
      return;
    }
    setInfo({ currentVersion: result.currentVersion, availableVersion: result.availableVersion });
    setStatus(result.updateAvailable ? 'available' : 'current');
  };

  const handleUpdate = async () => {
    setStatus('updating');
    await performImmediateUpdate();
  };

  return (
    <SettingCard title="Actualizaciones" icon={<RefreshCwIcon size={16} className="text-primary" />}>
      <SettingRow label="Buscar actualizaciones" border={false}>
        <button
          onClick={handleCheck}
          disabled={status === 'checking'}
          className="px-4 py-2 text-[10px] font-black uppercase tracking-widest rounded-full bg-[#ECE6F0] text-primary hover:bg-primary/10 transition-colors disabled:opacity-50"
        >
          {status === 'checking' ? 'Buscando...' : 'Comprobar'}
        </button>
      </SettingRow>
      {status === 'available' && info && (
        <div className="mx-5 mb-4 p-4 rounded-2xl bg-[#ECE6F0] border border-black/[0.03]">
          <p className="text-[10px] font-bold text-primary">v{info.availableVersion} disponible</p>
          <button
            onClick={handleUpdate}
            className="mt-2 px-4 py-2 text-[9px] font-black uppercase tracking-widest rounded-full bg-primary text-white"
          >
            Actualizar
          </button>
        </div>
      )}
      {status === 'current' && (
        <p className="mx-5 mb-4 text-[9px] font-medium text-[#49454F] opacity-50">Última versión instalada</p>
      )}
      {status === 'error' && (
        <p className="mx-5 mb-4 text-[9px] font-medium text-red-500">Error al comprobar actualizaciones</p>
      )}
    </SettingCard>
  );
};

const TabBarCustomizer: React.FC<{
  currentTabs: View[];
  onChange: (tabs: View[]) => void;
  style?: string;
}> = ({ currentTabs, onChange, style }) => {
  const AVAILABLE_TABS: { id: View; label: string }[] = [
    { id: 'home', label: 'Entreno' },
    { id: 'nutrition', label: 'Nutrición' },
    { id: 'recovery', label: 'Batería' },
    { id: 'sleep', label: 'Descanso' },
    { id: 'kpkn', label: 'KPKN' },
    { id: 'progress', label: 'Progreso' },
    { id: 'coach', label: 'Coach' },
    { id: 'tasks', label: 'Tareas' },
  ];

  const toggleTab = (tabId: View) => {
    if (currentTabs.includes(tabId)) {
      if (tabId === 'home') return;
      onChange(currentTabs.filter((t) => t !== tabId));
    } else {
      if (currentTabs.length >= 4) return;
      onChange([...currentTabs, tabId]);
    }
  };

  return (
    <SettingCard title="Menú Inferior" icon={<LayoutIcon size={16} className="text-primary" />}>
      <SettingRow label="Estilo actual" description="Máximo 4 pestañas" border={false}>
        <span className="text-[10px] font-bold text-[#49454F] opacity-50 px-3 py-1.5 rounded-full bg-[#ECE6F0]">
          {style === 'compact' ? 'Compacto' : style === 'icons-only' ? 'Solo íconos' : 'Predeterminado'}
        </span>
      </SettingRow>
      <div className="px-5 pb-4 grid grid-cols-2 gap-2">
        {AVAILABLE_TABS.map((tab) => {
          const isSelected = currentTabs.includes(tab.id);
          const isFull = !isSelected && currentTabs.length >= 4;
          return (
            <button
              key={tab.id}
              onClick={() => toggleTab(tab.id)}
              disabled={isFull || tab.id === 'home'}
              className={`px-3 py-3 text-[10px] font-black uppercase tracking-widest rounded-2xl border transition-all ${
                isSelected
                  ? 'border-primary bg-[#ECE6F0] text-primary'
                  : 'border-black/[0.05] text-[#49454F] opacity-50'
              } ${isFull || tab.id === 'home' ? 'opacity-40 cursor-not-allowed' : 'hover:bg-white/50'}`}
            >
              {tab.label}
            </button>
          );
        })}
      </div>
    </SettingCard>
  );
};

// --- Main Component ---

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

export const SettingsComponent: React.FC<SettingsProps> = ({
  settings,
  onSettingsChange,
  setPrograms,
  setHistory,
  setSkippedLogs,
  setBodyProgress,
  setNutritionLogs,
  drive,
  installPromptEvent,
  setInstallPromptEvent,
  isOnline,
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

  useEffect(() => {
    if (!hasChanges) setPendingSettings(settings);
  }, [settings, hasChanges]);

  const handleSettingChange = <K extends keyof Settings>(key: K, value: Settings[K]) => {
    setPendingSettings((prev) => ({ ...prev, [key]: value }));
    setHasChanges(true);
  };

  const handleAlgorithmChange = (key: keyof Settings['algorithmSettings'], value: number | boolean) => {
    setPendingSettings((prev) => ({
      ...prev,
      algorithmSettings: { ...prev.algorithmSettings, [key]: value as never },
    }));
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
        programs: (await storageService.get<Program[]>('programs')) || [],
        history: (await storageService.get<WorkoutLog[]>('history')) || [],
        settings: (await storageService.get<Settings>('yourprime-settings')) || settings,
        'body-progress': (await storageService.get<BodyProgressLog[]>('body-progress')) || [],
        'nutrition-logs': (await storageService.get<NutritionLog[]>('nutrition-logs')) || [],
      };
      const newSnapshot: LocalSnapshot = {
        id: crypto.randomUUID(),
        name,
        date: new Date().toISOString(),
        data: dataToBackup,
      };
      setSnapshots((prev) => [...prev, newSnapshot]);
      alert('Copia creada correctamente');
    } catch (e) {
      console.error(e);
      alert('Error al crear copia');
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
      alert('Error al exportar');
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

  const filterItems = useMemo(() => {
    if (!searchQuery.trim()) return null;
    const q = searchQuery.toLowerCase();
    return (label: string, desc?: string) =>
      label.toLowerCase().includes(q) || (desc && desc.toLowerCase().includes(q));
  }, [searchQuery]);

  const matchesFilter = (label: string, desc?: string) => !filterItems || filterItems(label, desc);

  return (
    <div className="min-h-screen bg-[#FDFCFE] pb-32">
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
      <NutritionPlanEditorModal
        isOpen={isNutritionPlanEditorOpen}
        onClose={() => setIsNutritionPlanEditorOpen(false)}
      />

      {/* Header */}
      <header className="sticky top-0 z-20 bg-[#FDFCFE]/90 backdrop-blur-xl border-b border-black/[0.05] px-6 py-4">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-[#ECE6F0] flex items-center justify-center">
              <UserIcon size={20} className="text-primary" />
            </div>
            <div>
              <h1 className="text-[28px] font-black text-[#1D1B20] tracking-tighter leading-[0.95]">
                Ajustes
              </h1>
              <p className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-40">
                Personaliza tu experiencia
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              aria-label="Buscar ajustes"
              onClick={() => setSearchOpen(!searchOpen)}
              className="w-10 h-10 rounded-full bg-white/70 backdrop-blur-xl border border-black/[0.03] flex items-center justify-center text-[#49454F] hover:bg-white transition-colors shadow-sm"
            >
              <SearchIcon size={18} />
            </button>
            {hasChanges && (
              <>
                <button
                  onClick={() => setPendingSettings(settings)}
                  className="px-4 py-2 text-[9px] font-black uppercase tracking-widest rounded-full border border-black/[0.05] text-[#49454F] opacity-60 hover:bg-white/50 transition-colors"
                >
                  Cancelar
                </button>
                <button
                  onClick={handleSaveChanges}
                  className="px-4 py-2 text-[9px] font-black uppercase tracking-widest rounded-full bg-primary text-white shadow-lg shadow-primary/20"
                >
                  Guardar
                </button>
              </>
            )}
          </div>
        </div>

        {/* Search Bar */}
        {searchOpen && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            className="mt-4 flex gap-2"
          >
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Buscar configuración..."
              className="flex-1 px-4 py-3 rounded-2xl bg-white/70 backdrop-blur-xl border border-black/[0.05] text-[#1D1B20] placeholder:text-[#49454F] opacity-50 focus:outline-none focus:ring-2 focus:ring-primary/20 text-sm font-medium"
              autoFocus
            />
            <button
              onClick={() => {
                setSearchOpen(false);
                setSearchQuery('');
              }}
              className="w-10 h-10 rounded-full bg-white/70 backdrop-blur-xl border border-black/[0.03] flex items-center justify-center text-[#49454F] hover:bg-white transition-colors"
            >
              <XIcon size={18} />
            </button>
          </motion.div>
        )}
      </header>

      {/* Tab Navigation */}
      <div className="sticky top-[88px] z-10 bg-[#FDFCFE]/90 backdrop-blur-xl border-b border-black/[0.05] px-6 py-3">
        <div className="flex gap-2 overflow-x-auto no-scrollbar">
          {TABS.map((tab) => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex-shrink-0 px-4 py-2.5 rounded-full text-[10px] font-black uppercase tracking-widest transition-all flex items-center gap-2 ${
                  isActive
                    ? 'bg-[#ECE6F0] text-primary'
                    : 'bg-white/50 text-[#49454F] opacity-50 hover:bg-white hover:opacity-70'
                }`}
              >
                <Icon size={14} />
                {tab.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* Content */}
      <div className="px-6 py-6 space-y-4">
        {/* GENERAL TAB */}
        {activeTab === 'general' && (
          <>
            {matchesFilter('Tema', 'Oscuro Claro') && (
              <SettingCard title="Apariencia" icon={<PaletteIcon size={16} className="text-primary" />}>
                <SettingRow label="Tema">
                  <div className="flex gap-2">
                    {(['dark', 'light'] as const).map((t) => (
                      <button
                        key={t}
                        data-testid={t === 'dark' ? 'settings-theme-dark' : 'settings-theme-light'}
                        onClick={() =>
                          handleSettingChange('appTheme', t === 'dark' ? 'default' : 'light')
                        }
                        className={`px-4 py-2 text-[10px] font-black uppercase tracking-widest rounded-full border transition-all ${
                          settings.appTheme === (t === 'dark' ? 'default' : 'light')
                            ? 'border-primary bg-[#ECE6F0] text-primary'
                            : 'border-black/[0.05] text-[#49454F] opacity-50'
                        }`}
                      >
                        {t === 'dark' ? 'Oscuro' : 'Claro'}
                      </button>
                    ))}
                  </div>
                </SettingRow>
                {matchesFilter('Pack de sonidos') && (
                  <SettingRow label="Pack de sonidos">
                    <select
                      value={pendingSettings.soundPack || 'classic'}
                      onChange={(e) =>
                        handleSettingChange('soundPack', e.target.value as 'classic' | 'minimal' | 'none')
                      }
                      className="px-4 py-2 rounded-full bg-[#ECE6F0] border border-black/[0.03] text-[10px] font-bold text-[#1D1B20] focus:outline-none"
                    >
                      <option value="classic">Clásico</option>
                      <option value="minimal">Minimal</option>
                      <option value="none">Ninguno</option>
                    </select>
                  </SettingRow>
                )}
                {matchesFilter('Estilo barra inferior') && (
                  <SettingRow label="Estilo barra inferior">
                    <select
                      value={pendingSettings.tabBarStyle || 'default'}
                      onChange={(e) =>
                        handleSettingChange(
                          'tabBarStyle',
                          e.target.value as 'default' | 'compact' | 'icons-only'
                        )
                      }
                      className="px-4 py-2 rounded-full bg-[#ECE6F0] border border-black/[0.03] text-[10px] font-bold text-[#1D1B20] focus:outline-none"
                    >
                      <option value="default">Predeterminado</option>
                      <option value="compact">Compacta</option>
                      <option value="icons-only">Solo íconos</option>
                    </select>
                  </SettingRow>
                )}
                {matchesFilter('Escala de fuente') && (
                  <SettingRow
                    label="Escala de fuente"
                    description={`${Math.round((pendingSettings.fontSizeScale || 1) * 100)}%`}
                  >
                    <input
                      type="range"
                      min="0.8"
                      max="1.3"
                      step="0.05"
                      value={pendingSettings.fontSizeScale || 1}
                      onChange={(e) => handleSettingChange('fontSizeScale', parseFloat(e.target.value))}
                      className="w-24 accent-primary"
                    />
                  </SettingRow>
                )}
              </SettingCard>
            )}

            <TabBarCustomizer
              currentTabs={pendingSettings.enabledTabs || ['home', 'nutrition', 'recovery', 'sleep']}
              onChange={(tabs) => handleSettingChange('enabledTabs', tabs)}
              style={pendingSettings.tabBarStyle}
            />

            {matchesFilter('Tutoriales', 'Ver de nuevo') && (
              <SettingCard>
                <SettingRow
                  label="Ver tutoriales de nuevo"
                  description="Reinicia todos los tours de la app"
                  border={false}
                >
                  <button
                    onClick={handleResetTours}
                    className="px-4 py-2 text-[10px] font-black uppercase tracking-widest rounded-full border border-black/[0.05] hover:bg-white/50 transition-colors"
                  >
                    Reiniciar
                  </button>
                </SettingRow>
              </SettingCard>
            )}
          </>
        )}

        {/* ENTRENO TAB */}
        {activeTab === 'entreno' && (
          <SettingCard title="Experiencia de Entreno" icon={<DumbbellIcon size={16} className="text-primary" />}>
            {matchesFilter('Unidad de peso') && (
              <SettingRow label="Unidad de peso">
                <div className="flex gap-2">
                  {(['kg', 'lbs'] as const).map((u) => (
                    <button
                      key={u}
                      onClick={() => handleSettingChange('weightUnit', u)}
                      className={`px-4 py-2 text-[10px] font-black uppercase tracking-widest rounded-full border transition-all ${
                        pendingSettings.weightUnit === u
                          ? 'border-primary bg-[#ECE6F0] text-primary'
                          : 'border-black/[0.05] text-[#49454F] opacity-50'
                      }`}
                    >
                      {u.toUpperCase()}
                    </button>
                  ))}
                </div>
              </SettingRow>
            )}
            {matchesFilter('Inicio auto descanso') && (
              <SettingRow label="Inicio auto descanso" description="Inicia timer al guardar serie">
                <Toggle
                  checked={pendingSettings.restTimerAutoStart}
                  onChange={(c) => handleSettingChange('restTimerAutoStart', c)}
                />
              </SettingRow>
            )}
            {matchesFilter('Descanso por defecto') && (
              <SettingRow label="Descanso por defecto" description={`${pendingSettings.restTimerDefaultSeconds}s`}>
                <input
                  type="number"
                  step={5}
                  value={pendingSettings.restTimerDefaultSeconds}
                  onChange={(e) =>
                    handleSettingChange('restTimerDefaultSeconds', parseInt(e.target.value) || 90)
                  }
                  className="w-20 px-4 py-2 rounded-full bg-[#ECE6F0] border border-black/[0.03] text-[10px] font-bold text-center text-[#1D1B20] focus:outline-none"
                />
              </SettingRow>
            )}
            {matchesFilter('Vista compacta') && (
              <SettingRow label="Vista compacta sesión">
                <Toggle
                  checked={!!pendingSettings.sessionCompactView}
                  onChange={(c) => handleSettingChange('sessionCompactView', c)}
                />
              </SettingRow>
            )}
            {matchesFilter('Auto-avance campos') && (
              <SettingRow label="Auto-avance entre campos">
                <Toggle
                  checked={pendingSettings.sessionAutoAdvanceFields !== false}
                  onChange={(c) => handleSettingChange('sessionAutoAdvanceFields', c)}
                />
              </SettingRow>
            )}
            {matchesFilter('Métrica de intensidad') && (
              <SettingRow label="Métrica de intensidad">
                <div className="flex gap-2">
                  {(['rpe', 'rir'] as const).map((m) => (
                    <button
                      key={m}
                      onClick={() => handleSettingChange('intensityMetric', m)}
                      className={`px-4 py-2 text-[10px] font-black uppercase tracking-widest rounded-full border transition-all ${
                        pendingSettings.intensityMetric === m
                          ? 'border-primary bg-[#ECE6F0] text-primary'
                          : 'border-black/[0.05] text-[#49454F] opacity-50'
                      }`}
                    >
                      {m.toUpperCase()}
                    </button>
                  ))}
                </div>
              </SettingRow>
            )}
            {matchesFilter('Feedback háptico') && (
              <SettingRow label="Feedback háptico" description="Vibración al interactuar">
                <div className="flex items-center gap-3">
                  <select
                    value={pendingSettings.hapticIntensity}
                    onChange={(e) =>
                      handleSettingChange('hapticIntensity', e.target.value as HapticIntensity)
                    }
                    className="px-4 py-2 rounded-full bg-[#ECE6F0] border border-black/[0.03] text-[10px] font-bold text-[#1D1B20] focus:outline-none"
                  >
                    <option value="soft">Suave</option>
                    <option value="medium">Media</option>
                    <option value="heavy">Fuerte</option>
                  </select>
                  <Toggle
                    checked={pendingSettings.hapticFeedbackEnabled}
                    onChange={(c) => handleSettingChange('hapticFeedbackEnabled', c)}
                  />
                </div>
              </SettingRow>
            )}
          </SettingCard>
        )}

        {/* ALGORITMOS TAB */}
        {activeTab === 'algoritmos' && (
          <>
            <SettingCard title="Configuración Básica" icon={<BrainIcon size={16} className="text-primary" />}>
              {matchesFilter('Fórmula 1RM') && (
                <SettingRow label="Fórmula 1RM">
                  <select
                    value={pendingSettings.oneRMFormula}
                    onChange={(e) =>
                      handleSettingChange('oneRMFormula', e.target.value as OneRMFormula)
                    }
                    className="px-4 py-2 rounded-full bg-[#ECE6F0] border border-black/[0.03] text-[10px] font-bold text-[#1D1B20] focus:outline-none"
                  >
                    <option value="brzycki">Brzycki</option>
                    <option value="epley">Epley</option>
                    <option value="lander">Lander</option>
                  </select>
                </SettingRow>
              )}
              {matchesFilter('Sistema de volumen') && (
                <SettingRow label="Sistema de volumen">
                  <select
                    value={pendingSettings.volumeSystem || 'kpnk'}
                    onChange={(e) =>
                      handleSettingChange('volumeSystem', e.target.value as 'israetel' | 'kpnk' | 'manual')
                    }
                    className="px-4 py-2 rounded-full bg-[#ECE6F0] border border-black/[0.03] text-[10px] font-bold text-[#1D1B20] focus:outline-none"
                  >
                    <option value="israetel">Israetel</option>
                    <option value="kpnk">KPKN Personalizado</option>
                    <option value="manual">Manual</option>
                  </select>
                </SettingRow>
              )}
              {matchesFilter('Conectar nutrición batería') && (
                <SettingRow label="Conectar nutrición con batería AUGE">
                  <Toggle
                    checked={pendingSettings.algorithmSettings?.augeEnableNutritionTracking !== false}
                    onChange={(c) => handleAlgorithmChange('augeEnableNutritionTracking', c)}
                  />
                </SettingRow>
              )}
              {matchesFilter('Tracking sueño AUGE') && (
                <SettingRow label="Tracking sueño AUGE">
                  <Toggle
                    checked={pendingSettings.algorithmSettings?.augeEnableSleepTracking !== false}
                    onChange={(c) => handleAlgorithmChange('augeEnableSleepTracking', c)}
                  />
                </SettingRow>
              )}
              <div className="px-5 pb-4">
                <button
                  onClick={() => setIsNutritionPlanEditorOpen(true)}
                  className="w-full py-3 text-[10px] font-black uppercase tracking-widest rounded-full bg-[#ECE6F0] text-primary hover:bg-primary/10 transition-colors"
                >
                  Editar plan de alimentación
                </button>
              </div>
            </SettingCard>

            <SettingCard>
              <button
                onClick={() => setShowAdvancedAlgo(!showAdvancedAlgo)}
                className="w-full px-5 py-4 flex items-center justify-between gap-2"
              >
                <span className="text-[10px] font-black uppercase tracking-widest text-[#49454F] opacity-50">
                  Opciones avanzadas
                </span>
                <ChevronRightIcon
                  size={16}
                  className={`text-[#49454F] opacity-30 transition-transform ${
                    showAdvancedAlgo ? 'rotate-90' : ''
                  }`}
                />
              </button>
              {showAdvancedAlgo && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  className="px-5 pb-4 space-y-4 border-t border-black/[0.03] pt-4"
                >
                  <div>
                    <div className="flex justify-between text-[9px] font-medium text-[#49454F] opacity-60 mb-2">
                      <span>Decaimiento 1RM %/día</span>
                      <span>{pendingSettings.algorithmSettings.oneRMDecayRate}</span>
                    </div>
                    <input
                      type="range"
                      min={0.01}
                      max={0.5}
                      step={0.01}
                      value={pendingSettings.algorithmSettings.oneRMDecayRate}
                      onChange={(e) =>
                        handleAlgorithmChange('oneRMDecayRate', parseFloat(e.target.value))
                      }
                      className="w-full accent-primary"
                    />
                  </div>
                  <div>
                    <div className="flex justify-between text-[9px] font-medium text-[#49454F] opacity-60 mb-2">
                      <span>Factor fatiga al fallo</span>
                      <span>{pendingSettings.algorithmSettings.failureFatigueFactor}x</span>
                    </div>
                    <input
                      type="range"
                      min={1}
                      max={2}
                      step={0.05}
                      value={pendingSettings.algorithmSettings.failureFatigueFactor}
                      onChange={(e) =>
                        handleAlgorithmChange('failureFatigueFactor', parseFloat(e.target.value))
                      }
                      className="w-full accent-primary"
                    />
                  </div>
                  <div>
                    <div className="flex justify-between text-[9px] font-medium text-[#49454F] opacity-60 mb-2">
                      <span>Factor sinergista</span>
                      <span>{pendingSettings.algorithmSettings.synergistFactor}x</span>
                    </div>
                    <input
                      type="range"
                      min={0}
                      max={0.6}
                      step={0.05}
                      value={pendingSettings.algorithmSettings.synergistFactor}
                      onChange={(e) =>
                        handleAlgorithmChange('synergistFactor', parseFloat(e.target.value))
                      }
                      className="w-full accent-primary"
                    />
                  </div>
                </motion.div>
              )}
            </SettingCard>
          </>
        )}

        {/* NOTIF TAB */}
        {activeTab === 'notif' && (
          <SettingCard title="Notificaciones" icon={<BellIcon size={16} className="text-primary" />}>
            {matchesFilter('Recordatorio sesión') && (
              <SettingRow label="Recordatorio sesión del día">
                <div className="flex items-center gap-3">
                  <input
                    type="time"
                    value={pendingSettings.reminderTime || '17:00'}
                    onChange={(e) => handleSettingChange('reminderTime', e.target.value)}
                    className="px-4 py-2 rounded-full bg-[#ECE6F0] border border-black/[0.03] text-[10px] font-bold text-[#1D1B20] focus:outline-none"
                  />
                  <Toggle
                    checked={!!pendingSettings.remindersEnabled}
                    onChange={(c) => handleSettingChange('remindersEnabled', c)}
                  />
                </div>
              </SettingRow>
            )}
            {matchesFilter('Recordatorios comidas') && (
              <SettingRow label="Recordatorios comidas">
                <Toggle
                  checked={!!pendingSettings.mealRemindersEnabled}
                  onChange={(c) => handleSettingChange('mealRemindersEnabled', c)}
                />
              </SettingRow>
            )}
            {pendingSettings.mealRemindersEnabled && (
              <div className="px-5 pb-4 grid grid-cols-3 gap-2">
                <div>
                  <label className="text-[8px] font-black uppercase tracking-widest text-[#49454F] opacity-40 block mb-1">
                    Desayuno
                  </label>
                  <input
                    type="time"
                    value={pendingSettings.breakfastReminderTime || '08:00'}
                    onChange={(e) => handleSettingChange('breakfastReminderTime', e.target.value)}
                    className="w-full px-3 py-2 rounded-xl bg-[#ECE6F0] border border-black/[0.03] text-[9px] font-bold text-[#1D1B20] focus:outline-none"
                  />
                </div>
                <div>
                  <label className="text-[8px] font-black uppercase tracking-widest text-[#49454F] opacity-40 block mb-1">
                    Almuerzo
                  </label>
                  <input
                    type="time"
                    value={pendingSettings.lunchReminderTime || '14:00'}
                    onChange={(e) => handleSettingChange('lunchReminderTime', e.target.value)}
                    className="w-full px-3 py-2 rounded-xl bg-[#ECE6F0] border border-black/[0.03] text-[9px] font-bold text-[#1D1B20] focus:outline-none"
                  />
                </div>
                <div>
                  <label className="text-[8px] font-black uppercase tracking-widest text-[#49454F] opacity-40 block mb-1">
                    Cena
                  </label>
                  <input
                    type="time"
                    value={pendingSettings.dinnerReminderTime || '21:00'}
                    onChange={(e) => handleSettingChange('dinnerReminderTime', e.target.value)}
                    className="w-full px-3 py-2 rounded-xl bg-[#ECE6F0] border border-black/[0.03] text-[9px] font-bold text-[#1D1B20] focus:outline-none"
                  />
                </div>
              </div>
            )}
            {matchesFilter('Aviso entrenamiento no registrado') && (
              <SettingRow label="Aviso entrenamiento no registrado">
                <div className="flex items-center gap-3">
                  <input
                    type="time"
                    value={pendingSettings.missedWorkoutReminderTime || '21:00'}
                    onChange={(e) => handleSettingChange('missedWorkoutReminderTime', e.target.value)}
                    className="px-4 py-2 rounded-full bg-[#ECE6F0] border border-black/[0.03] text-[10px] font-bold text-[#1D1B20] focus:outline-none"
                  />
                  <Toggle
                    checked={!!pendingSettings.missedWorkoutReminderEnabled}
                    onChange={(c) => handleSettingChange('missedWorkoutReminderEnabled', c)}
                  />
                </div>
              </SettingRow>
            )}
            {matchesFilter('Batería AUGE baja') && (
              <>
                <SettingRow label="Batería AUGE baja">
                  <Toggle
                    checked={!!pendingSettings.augeBatteryReminderEnabled}
                    onChange={(c) => handleSettingChange('augeBatteryReminderEnabled', c)}
                  />
                </SettingRow>
                {pendingSettings.augeBatteryReminderEnabled && (
                  <div className="px-5 pb-4 flex gap-3">
                    <div>
                      <label className="text-[8px] font-black uppercase tracking-widest text-[#49454F] opacity-40 block mb-1">
                        Umbral %
                      </label>
                      <input
                        type="number"
                        min={5}
                        max={50}
                        value={pendingSettings.augeBatteryReminderThreshold ?? 20}
                        onChange={(e) =>
                          handleSettingChange(
                            'augeBatteryReminderThreshold',
                            parseInt(e.target.value) || 20
                          )
                        }
                        className="w-20 px-3 py-2 rounded-xl bg-[#ECE6F0] border border-black/[0.03] text-[9px] font-bold text-[#1D1B20] focus:outline-none"
                      />
                    </div>
                    <div>
                      <label className="text-[8px] font-black uppercase tracking-widest text-[#49454F] opacity-40 block mb-1">
                        Hora
                      </label>
                      <input
                        type="time"
                        value={pendingSettings.augeBatteryReminderTime || '09:00'}
                        onChange={(e) => handleSettingChange('augeBatteryReminderTime', e.target.value)}
                        className="px-3 py-2 rounded-xl bg-[#ECE6F0] border border-black/[0.03] text-[9px] font-bold text-[#1D1B20] focus:outline-none"
                      />
                    </div>
                  </div>
                )}
              </>
            )}
            {matchesFilter('Eventos y bloques') && (
              <SettingRow label="Eventos y bloques" border={false}>
                <Toggle
                  checked={!!pendingSettings.eventRemindersEnabled}
                  onChange={(c) => handleSettingChange('eventRemindersEnabled', c)}
                />
              </SettingRow>
            )}
          </SettingCard>
        )}

        {/* DATOS TAB */}
        {activeTab === 'datos' && (
          <>
            <SettingCard title="Respaldos" icon={<DatabaseIcon size={16} className="text-primary" />}>
              <div className="px-5 pb-4 flex flex-wrap gap-2">
                <button
                  onClick={handleExportData}
                  className="px-4 py-3 text-[10px] font-black uppercase tracking-widest rounded-full bg-[#ECE6F0] text-primary hover:bg-primary/10 transition-colors flex items-center gap-2"
                >
                  <DownloadIcon size={14} /> Exportar JSON
                </button>
                <button
                  onClick={handleCreateSnapshot}
                  className="px-4 py-3 text-[10px] font-black uppercase tracking-widest rounded-full bg-[#ECE6F0] text-primary hover:bg-primary/10 transition-colors flex items-center gap-2"
                >
                  <SaveIcon size={14} /> Crear snapshot
                </button>
              </div>
              <div className="px-5 pb-4">
                <input
                  type="file"
                  ref={fileInputRef}
                  className="hidden"
                  accept=".json"
                  onChange={async (e) => {
                    const file = e.target.files?.[0];
                    if (!file) return;
                    const text = await file.text();
                    importExerciseDatabase(text);
                  }}
                />
                <button
                  onClick={() => fileInputRef.current?.click()}
                  className="w-full py-3 text-[10px] font-black uppercase tracking-widest rounded-full border border-black/[0.05] hover:bg-white/50 transition-colors flex items-center justify-center gap-2"
                >
                  <UploadIcon size={14} /> Importar DB ejercicios
                </button>
              </div>
            </SettingCard>

            <SettingCard>
              <button
                onClick={(e) => {
                  e.preventDefault();
                  setDangerZoneOpen(!dangerZoneOpen);
                }}
                className="w-full px-5 py-4 flex items-center justify-between gap-2"
              >
                <span className="text-[10px] font-black uppercase tracking-widest text-red-500">
                  Zona peligrosa
                </span>
                <ChevronRightIcon
                  size={16}
                  className={`text-red-500 transition-transform ${dangerZoneOpen ? 'rotate-90' : ''}`}
                />
              </button>
              {dangerZoneOpen && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  className="px-5 pb-4 border-t border-red-200 pt-4"
                >
                  <p className="text-[9px] font-medium text-[#49454F] opacity-50 mb-3">
                    Acciones irreversibles. Usar con precaución.
                  </p>
                </motion.div>
              )}
            </SettingCard>

            {devMode && (
              <SettingCard title="Herramientas de desarrollo" icon={<ZapIcon size={16} className="text-amber-500" />}>
                {Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android' && (
                  <AppUpdateCheckItem />
                )}
                <SettingRow label="Probar Sentry" border={false}>
                  <button
                    onClick={() => {
                      captureException(new Error('Test error Settings'));
                      alert('Error enviado a Sentry');
                    }}
                    className="px-4 py-2 text-[9px] font-black uppercase tracking-widest rounded-full border border-red-300 text-red-500 hover:bg-red-50 transition-colors"
                  >
                    Enviar test
                  </button>
                </SettingRow>
              </SettingCard>
            )}
          </>
        )}

        {/* NUTRICION TAB */}
        {activeTab === 'nutricion' && (
          <SettingCard title="Nutrición y Biometría" icon={<FoodIcon size={16} className="text-primary" />}>
            {matchesFilter('Objetivo calorías') && (
              <SettingRow label="Objetivo calorías">
                <select
                  value={pendingSettings.calorieGoalObjective}
                  onChange={(e) =>
                    handleSettingChange(
                      'calorieGoalObjective',
                      e.target.value as 'deficit' | 'maintenance' | 'surplus'
                    )
                  }
                  className="px-4 py-2 rounded-full bg-[#ECE6F0] border border-black/[0.03] text-[10px] font-bold text-[#1D1B20] focus:outline-none"
                >
                  <option value="deficit">Déficit</option>
                  <option value="maintenance">Mantenimiento</option>
                  <option value="surplus">Superávit</option>
                </select>
              </SettingRow>
            )}
            {matchesFilter('Calorías diarias') && pendingSettings.dailyCalorieGoal != null && (
              <SettingRow label="Calorías diarias">
                <input
                  type="number"
                  value={pendingSettings.dailyCalorieGoal}
                  onChange={(e) =>
                    handleSettingChange('dailyCalorieGoal', parseInt(e.target.value) || undefined)
                  }
                  className="w-24 px-4 py-2 rounded-full bg-[#ECE6F0] border border-black/[0.03] text-[10px] font-bold text-right text-[#1D1B20] focus:outline-none"
                />
              </SettingRow>
            )}
            {matchesFilter('Proteína diaria') && pendingSettings.dailyProteinGoal != null && (
              <SettingRow label="Proteína diaria (g)">
                <input
                  type="number"
                  value={pendingSettings.dailyProteinGoal}
                  onChange={(e) =>
                    handleSettingChange('dailyProteinGoal', parseInt(e.target.value) || undefined)
                  }
                  className="w-24 px-4 py-2 rounded-full bg-[#ECE6F0] border border-black/[0.03] text-[10px] font-bold text-right text-[#1D1B20] focus:outline-none"
                />
              </SettingRow>
            )}
            {matchesFilter('Preferencia dietética') && (
              <SettingRow label="Preferencia dietética">
                <select
                  value={pendingSettings.dietaryPreference || 'omnivore'}
                  onChange={(e) =>
                    handleSettingChange('dietaryPreference', e.target.value as any)
                  }
                  className="px-4 py-2 rounded-full bg-[#ECE6F0] border border-black/[0.03] text-[10px] font-bold text-[#1D1B20] focus:outline-none"
                >
                  <option value="omnivore">Omnívoro</option>
                  <option value="vegetarian">Vegetariano</option>
                  <option value="vegan">Vegano</option>
                  <option value="keto">Keto</option>
                </select>
              </SettingRow>
            )}
            <div className="px-5 pb-4">
              <button
                onClick={() => setIsNutritionPlanEditorOpen(true)}
                className="w-full py-3 text-[10px] font-black uppercase tracking-widest rounded-full bg-[#ECE6F0] text-primary hover:bg-primary/10 transition-colors"
              >
                Editar plan de alimentación
              </button>
            </div>
          </SettingCard>
        )}
      </div>

      {/* Version Footer */}
      <div className="px-6 pt-8 pb-4 text-center">
        <div
          onClick={handleDevTap}
          className="inline-block cursor-default"
          role="button"
          tabIndex={0}
          onKeyDown={(e) => e.key === 'Enter' && handleDevTap()}
          aria-label="Área de versión"
        >
          {devMode ? (
            <span className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-40">
              v{APP_VERSION}
            </span>
          ) : (
            <span className="inline-block w-8 h-2" />
          )}
        </div>
      </div>
    </div>
  );
};

export default SettingsComponent;
