// components/Settings.tsx
import React, { useState, useEffect, useRef } from 'react';
import { Settings, Program, WorkoutLog, BodyProgressLog, NutritionLog, LocalSnapshot, SessionBackground, SkippedWorkoutLog, UseGoogleDriveReturn } from '../types';
import Card from './ui/Card';
import Button from './ui/Button';
import { Volume2Icon, VolumeXIcon, SaveIcon, DownloadIcon, UploadIcon, TypeIcon, TrashIcon, CloudIcon, UploadCloudIcon, DownloadCloudIcon, KeyIcon, PaletteIcon, BellIcon, PlusCircleIcon, DumbbellIcon, ChevronRightIcon, PencilIcon, SettingsIcon, Wand2Icon, CheckIcon, BodyIcon, ActivityIcon, UtensilsIcon } from './icons';
import useStorage from '../hooks/useLocalStorage';
import { storageService } from '../services/storageService';
import BackgroundEditorModal from './SessionBackgroundModal';
import { useAppDispatch } from '../contexts/AppContext';

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

const SettingsCard: React.FC<{ title: string; icon: React.ReactNode; children: React.ReactNode, defaultOpen?: boolean }> = ({ title, icon, children, defaultOpen = false }) => (
    <details className="settings-card" open={defaultOpen}>
        <summary>
            <h3 className="text-xl font-bold text-white flex items-center gap-3">{icon} {title}</h3>
            <ChevronRightIcon className="details-arrow transition-transform text-slate-400" />
        </summary>
        <div className="card-content">
            {children}
        </div>
    </details>
);

const ToggleSwitch: React.FC<{ checked: boolean, onChange: (checked: boolean) => void, disabled?: boolean }> = ({ checked, onChange, disabled }) => (
    <button type="button" onClick={() => !disabled && onChange(!checked)} className={`relative inline-flex flex-shrink-0 h-6 w-11 border-2 border-transparent rounded-full cursor-pointer transition-colors ease-in-out duration-200 focus:outline-none ${disabled ? 'opacity-50' : ''} ${checked ? 'bg-primary-color' : 'bg-slate-600'}`}>
        <span className={`inline-block h-5 w-5 rounded-full bg-white shadow transform ring-0 transition ease-in-out duration-200 ${checked ? 'translate-x-5' : 'translate-x-0'}`} />
    </button>
);

const SettingsComponent: React.FC<SettingsProps> = ({ settings, onSettingsChange, setPrograms, setHistory, setSkippedLogs, setBodyProgress, setNutritionLogs, drive, installPromptEvent, setInstallPromptEvent, isOnline }) => {
    const [pendingSettings, setPendingSettings] = useState<Settings>(settings);
    const [hasChanges, setHasChanges] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const dbFileInputRef = useRef<HTMLInputElement>(null);
    const { exportExerciseDatabase, importExerciseDatabase, navigateTo } = useAppDispatch();
    const [snapshots, setSnapshots] = useStorage<LocalSnapshot[]>('yourprime-snapshots', []);
    const [isBgModalOpen, setIsBgModalOpen] = useState(false);

    const handleInstallClick = () => {
        if (!installPromptEvent) {
            return;
        }
        installPromptEvent.prompt();
        installPromptEvent.userChoice.then((choiceResult: { outcome: string }) => {
            if (choiceResult.outcome === 'accepted') {
                console.log('User accepted the A2HS prompt');
            } else {
                console.log('User dismissed the A2HS prompt');
            }
            setInstallPromptEvent(null);
        });
    };

    useEffect(() => {
        if (!hasChanges) {
            setPendingSettings(settings);
        }
    }, [settings, hasChanges]);

    const handleSettingChange = <K extends keyof Settings>(key: K, value: Settings[K]) => {
        setPendingSettings(prev => ({ ...prev, [key]: value }));
        setHasChanges(true);
    };
    
    const handleApiKeyChange = (provider: 'gemini' | 'deepseek' | 'gpt', value: string) => {
        setPendingSettings(prev => ({
            ...prev,
            apiKeys: {
                ...prev.apiKeys,
                [provider]: value,
            }
        }));
        setHasChanges(true);
    };

    const handleSaveChanges = () => {
        onSettingsChange(pendingSettings);
        setHasChanges(false);
    };

    const handleDiscardChanges = () => {
        setPendingSettings(settings);
        setHasChanges(false);
    };

    const handleExportData = async () => {
        try {
            const data = await storageService.getAllDataForExport();
            const jsonString = `data:text/json;charset=utf-8,${encodeURIComponent(
                JSON.stringify(data, null, 2)
            )}`;
            const link = document.createElement("a");
            link.href = jsonString;
            link.download = `yourprime_backup_${new Date().toISOString().split('T')[0]}.json`;
            link.click();
        } catch (error) {
            alert('Error al exportar los datos.');
            console.error(error);
        }
    };
    
    const handleImportData = () => {
        if (!fileInputRef.current?.files?.length) {
            alert('Por favor, selecciona un archivo primero.');
            return;
        }
        const file = fileInputRef.current.files[0];
        const reader = new FileReader();
        reader.onload = (event) => {
            try {
                const text = event.target?.result;
                if (typeof text !== 'string') {
                    throw new Error('El contenido del archivo no es válido.');
                }
                const data = JSON.parse(text);

                if (!data || typeof data !== 'object' || !Array.isArray(data.programs) || !Array.isArray(data.history) || typeof data.settings !== 'object' || data.settings === null) {
                    throw new Error("El archivo de importación es inválido o no tiene el formato correcto.");
                }

                setPrograms(data.programs);
                setHistory(data.history);
                onSettingsChange(data.settings);
                setBodyProgress(data['body-progress'] || []);
                setNutritionLogs(data['nutrition-logs'] || []);
                setSkippedLogs(data['skipped-logs'] || []);

                alert('Datos importados con éxito. La aplicación se recargará.');
                setTimeout(() => window.location.reload(), 500);

            } catch (error) {
                console.error("Error al importar datos:", error);
                alert(`Error al importar los datos: ${error instanceof Error ? error.message : 'Error desconocido'}`);
            }
        };
        reader.readAsText(file);
    };

    const handleImportDb = () => {
        if (!dbFileInputRef.current?.files?.length) {
            alert('Por favor, selecciona un archivo de base de datos de ejercicios primero.');
            return;
        }
        const file = dbFileInputRef.current.files[0];
        const reader = new FileReader();
        reader.onload = (event) => {
            try {
                const text = event.target?.result;
                if (typeof text !== 'string') {
                    throw new Error('El contenido del archivo no es válido.');
                }
                importExerciseDatabase(text);
            } catch (error) {
                console.error("Error al importar la base de datos de ejercicios:", error);
                alert(`Error al importar: ${error instanceof Error ? error.message : 'Error desconocido'}`);
            }
        };
        reader.readAsText(file);
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

    const handleRestoreSnapshot = (snapshotId: string) => {
        const snapshot = snapshots.find(s => s.id === snapshotId);
        if (!snapshot) return;
        if (window.confirm(`¿Seguro que quieres restaurar la copia "${snapshot.name}"? Esto reemplazará todos tus datos actuales.`)) {
            try {
                setPrograms(snapshot.data.programs);
                setHistory(snapshot.data.history);
                onSettingsChange(snapshot.data.settings);
                setBodyProgress(snapshot.data['body-progress'] || []);
                setNutritionLogs(snapshot.data['nutrition-logs'] || []);
                alert('Datos restaurados con éxito. La aplicación se recargará.');
                setTimeout(() => window.location.reload(), 500);
            } catch (error) {
                 console.error("Error al restaurar la copia de seguridad:", error);
                 alert("No se pudo restaurar la copia de seguridad.");
            }
        }
    };

    const handleDeleteSnapshot = (snapshotId: string) => {
        if (window.confirm("¿Estás seguro de que quieres eliminar esta copia de seguridad?")) {
            setSnapshots(prev => prev.filter(s => s.id !== snapshotId));
        }
    };
    
    const weekDays = [
        { label: 'Domingo', value: 0 }, { label: 'Lunes', value: 1 }, { label: 'Martes', value: 2 },
        { label: 'Miércoles', value: 3 }, { label: 'Jueves', value: 4 }, { label: 'Viernes', value: 5 },
        { label: 'Sábado', value: 6 },
    ];

    return (
        <div className="max-w-4xl mx-auto space-y-6 animate-fade-in">
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
            <div>
                <h1 className="text-4xl font-bold uppercase tracking-wider">Ajustes</h1>
            </div>
            
            {hasChanges && (
                <div className="sticky top-[75px] z-40 animate-fade-in-up">
                    <div className="glass-card !p-3 flex justify-between items-center">
                        <p className="text-sm font-semibold text-yellow-300">Tienes cambios sin guardar.</p>
                        <div className="flex gap-2">
                             <Button onClick={handleDiscardChanges} variant="secondary" className="!text-xs !py-1">Descartar</Button>
                            <Button onClick={handleSaveChanges} className="!text-xs !py-1">Guardar</Button>
                        </div>
                    </div>
                </div>
            )}
            
            <SettingsCard title="Perfil de Atleta" icon={<BodyIcon />} defaultOpen={true}>
                <p className="text-sm text-slate-400 mb-4">Gestiona todos tus datos personales, físicos, anatómicos y nutricionales en un solo lugar.</p>
                <Button onClick={() => navigateTo('athlete-profile')} className="w-full">
                    Ir a mi Perfil
                </Button>
            </SettingsCard>
            
            <SettingsCard title="Metas de Nutrición" icon={<UtensilsIcon />}>
                <div className="space-y-4">
                    <p className="text-sm text-slate-400">Establece tus objetivos diarios. Estos se usarán en el dashboard de nutrición para seguir tu progreso. También puedes dejar que la IA los calcule en tu Perfil de Atleta.</p>
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm text-slate-300 mb-1">Calorías (kcal)</label>
                            <input
                                type="number"
                                value={pendingSettings.dailyCalorieGoal || ''}
                                onChange={e => handleSettingChange('dailyCalorieGoal', parseInt(e.target.value) || undefined)}
                                className="w-full"
                                placeholder="Ej: 2500"
                            />
                        </div>
                        <div>
                            <label className="block text-sm text-slate-300 mb-1">Proteínas (g)</label>
                            <input
                                type="number"
                                value={pendingSettings.dailyProteinGoal || ''}
                                onChange={e => handleSettingChange('dailyProteinGoal', parseInt(e.target.value) || undefined)}
                                className="w-full"
                                placeholder="Ej: 180"
                            />
                        </div>
                        <div>
                            <label className="block text-sm text-slate-300 mb-1">Carbohidratos (g)</label>
                            <input
                                type="number"
                                value={pendingSettings.dailyCarbGoal || ''}
                                onChange={e => handleSettingChange('dailyCarbGoal', parseInt(e.target.value) || undefined)}
                                className="w-full"
                                placeholder="Ej: 300"
                            />
                        </div>
                        <div>
                            <label className="block text-sm text-slate-300 mb-1">Grasas (g)</label>
                            <input
                                type="number"
                                value={pendingSettings.dailyFatGoal || ''}
                                onChange={e => handleSettingChange('dailyFatGoal', parseInt(e.target.value) || undefined)}
                                className="w-full"
                                placeholder="Ej: 70"
                            />
                        </div>
                    </div>
                </div>
            </SettingsCard>

            <SettingsCard title="Tema de la Aplicación" icon={<PaletteIcon/>}>
                 <div className="space-y-4">
                    <p className="text-sm text-slate-400">Elige el aspecto visual de la aplicación.</p>
                    <div className="grid grid-cols-2 gap-4">
                        <button onClick={() => handleSettingChange('appTheme', 'default')} className={`p-4 rounded-lg border-2 ${(!pendingSettings.appTheme || pendingSettings.appTheme === 'default') ? 'border-primary-color' : 'border-transparent'} bg-slate-800`}>
                            <h4 className="font-bold text-white">Default</h4>
                            <p className="text-xs text-slate-400">Tema original con efecto de cristal.</p>
                        </button>
                        <button onClick={() => handleSettingChange('appTheme', 'deep-black')} className={`p-4 rounded-lg border-2 ${pendingSettings.appTheme === 'deep-black' ? 'border-primary-color' : 'border-transparent'} bg-slate-800`}>
                            <h4 className="font-bold text-white">Deep Black</h4>
                            <p className="text-xs text-slate-400">Modo oscuro puro para pantallas OLED.</p>
                        </button>
                        <button onClick={() => handleSettingChange('appTheme', 'volt')} className={`p-4 rounded-lg border-2 ${pendingSettings.appTheme === 'volt' ? 'border-primary-color' : 'border-transparent'} bg-slate-800`}>
                            <h4 className="font-bold text-white">Volt (Neon)</h4>
                            <p className="text-xs text-slate-400">Estética Cyberpunk con brillos neón.</p>
                        </button>
                    </div>
                </div>
            </SettingsCard>
            
            <SettingsCard title="Rendimiento y Apariencia" icon={<ActivityIcon />}>
                <div className="space-y-4">
                    <div className="flex justify-between items-center pt-4 border-t border-slate-700/50">
                        <div className="pr-4">
                            <span className="text-slate-300">Modo Zen / Rendimiento</span>
                            <p className="text-xs text-slate-500">Activa un modo de bajo consumo para el registro de entrenamientos. Elimina animaciones y fondos complejos para garantizar la máxima fluidez.</p>
                        </div>
                        <ToggleSwitch checked={pendingSettings.enableZenMode} onChange={(c) => handleSettingChange('enableZenMode', c)} />
                    </div>
                    <div className="flex justify-between items-center">
                        <span className="text-slate-300">Efecto Parallax de Fondo</span>
                        <ToggleSwitch checked={pendingSettings.enableParallax} onChange={(c) => handleSettingChange('enableParallax', c)} />
                    </div>

                    <div className="flex justify-between items-center pt-4 border-t border-slate-700/50">
                        <div className="pr-4">
                            <span className="text-slate-300">Efecto de Vidrio (Glassmorphism)</span>
                            <p className="text-xs text-slate-500">Desactivar esto (Modo Sólido) mejorará drásticamente la fluidez del scroll.</p>
                        </div>
                        <ToggleSwitch checked={pendingSettings.enableGlassmorphism} onChange={(c) => handleSettingChange('enableGlassmorphism', c)} />
                    </div>

                    <div className="flex justify-between items-center pt-4 border-t border-slate-700/50">
                        <div className="pr-4">
                            <span className="text-slate-300">Animaciones de Interfaz</span>
                            <p className="text-xs text-slate-500">Desactiva las transiciones de página y animaciones complejas.</p>
                        </div>
                        <ToggleSwitch checked={pendingSettings.enableAnimations} onChange={(c) => handleSettingChange('enableAnimations', c)} />
                    </div>

                    <div className="flex justify-between items-center pt-4 border-t border-slate-700/50">
                        <div className="pr-4">
                            <span className="text-slate-300">Efectos de Brillo (Glow)</span>
                            <p className="text-xs text-slate-500">Desactiva las sombras de color (ej. en temas Volt/Neon) para ahorrar batería.</p>
                        </div>
                        <ToggleSwitch checked={pendingSettings.enableGlowEffects} onChange={(c) => handleSettingChange('enableGlowEffects', c)} />
                    </div>
                </div>
            </SettingsCard>

            <SettingsCard title="Proveedor de IA y Claves API" icon={<KeyIcon/>}>
                 <div className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-slate-300 mb-1">Proveedor Principal</label>
                        <select value={pendingSettings.apiProvider} onChange={e => handleSettingChange('apiProvider', e.target.value as any)} className="w-full">
                            <option value="gemini">Google Gemini</option>
                            <option value="gpt">OpenAI GPT</option>
                            <option value="deepseek">DeepSeek</option>
                        </select>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-slate-300 mb-1">Clave API Gemini</label>
                        <input type="password" value={pendingSettings.apiKeys?.gemini || ''} onChange={e => handleApiKeyChange('gemini', e.target.value)} className="w-full" placeholder="Pega tu clave aquí"/>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-slate-300 mb-1">Clave API OpenAI (GPT)</label>
                        <input type="password" value={pendingSettings.apiKeys?.gpt || ''} onChange={e => handleApiKeyChange('gpt', e.target.value)} className="w-full" placeholder="Opcional"/>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-slate-300 mb-1">Clave API DeepSeek</label>
                        <input type="password" value={pendingSettings.apiKeys?.deepseek || ''} onChange={e => handleApiKeyChange('deepseek', e.target.value)} className="w-full" placeholder="Opcional"/>
                    </div>
                    <div className="flex justify-between items-center pt-4 border-t border-slate-700/50">
                        <span className="text-slate-300">Activar proveedores de respaldo</span>
                        <ToggleSwitch checked={pendingSettings.fallbackEnabled} onChange={(c) => handleSettingChange('fallbackEnabled', c)} />
                    </div>
                </div>
            </SettingsCard>
            
            <SettingsCard title="General" icon={<SettingsIcon/>}>
                <div className="space-y-4">
                    <div>
                        <label className="block text-sm text-slate-300 mb-1">Unidad de Peso</label>
                        <div className="flex gap-2"><Button onClick={() => handleSettingChange('weightUnit', 'kg')} variant={pendingSettings.weightUnit === 'kg' ? 'primary' : 'secondary'} className="flex-1">kg</Button><Button onClick={() => handleSettingChange('weightUnit', 'lbs')} variant={pendingSettings.weightUnit === 'lbs' ? 'primary' : 'secondary'} className="flex-1">lbs</Button></div>
                    </div>
                    <div>
                        <label htmlFor="barbellWeight" className="flex items-center gap-2 text-sm text-slate-300 mb-1"><DumbbellIcon size={16}/> Peso de la Barra ({settings.weightUnit})</label>
                        <input id="barbellWeight" type="number" value={pendingSettings.barbellWeight} onChange={(e) => handleSettingChange('barbellWeight', parseFloat(e.target.value) || 0)} className="w-full"/>
                    </div>
                    <div>
                        <label htmlFor="startWeekOn" className="block text-sm text-slate-300 mb-1">La semana empieza en</label>
                        <select
                            id="startWeekOn"
                            value={pendingSettings.startWeekOn}
                            onChange={(e) => handleSettingChange('startWeekOn', parseInt(e.target.value, 10))}
                            className="w-full"
                        >
                            {weekDays.map(day => (
                                <option key={day.value} value={day.value}>{day.label}</option>
                            ))}
                        </select>
                    </div>
                    <div className="flex justify-between items-center pt-4 border-t border-slate-700/50">
                        <div className="pr-4">
                            <span className="text-slate-300">Modo de Registro Pro</span>
                            <p className="text-xs text-slate-500">El Modo Pro te permite registrar métricas avanzadas como RPE, RED, Tempo y Calidad Técnica. El Modo Simple solo registra Repeticiones y Peso.</p>
                        </div>
                        <ToggleSwitch checked={pendingSettings.workoutLoggerMode === 'pro'} onChange={(c) => handleSettingChange('workoutLoggerMode', c ? 'pro' : 'simple')} />
                    </div>
                    <div className="flex justify-between items-center"><span className="text-slate-300 flex items-center gap-2">{pendingSettings.soundsEnabled ? <Volume2Icon/> : <VolumeXIcon/>} Sonidos de la app</span><ToggleSwitch checked={pendingSettings.soundsEnabled} onChange={(c) => handleSettingChange('soundsEnabled', c)} /></div>
                    <div className="flex justify-between items-center"><span className="text-slate-300">Mostrar PRs en entrenamiento</span><ToggleSwitch checked={pendingSettings.showPRsInWorkout} onChange={(c) => handleSettingChange('showPRsInWorkout', c)} /></div>
                    <div className="flex justify-between items-center"><span className="text-slate-300">Vibración (Haptic Feedback)</span><ToggleSwitch checked={pendingSettings.hapticFeedbackEnabled} onChange={(c) => handleSettingChange('hapticFeedbackEnabled', c)} /></div>
                    <div className="flex justify-between items-center"><span className="text-slate-300">Chequeo de Preparación (Pre-Entreno)</span><ToggleSwitch checked={pendingSettings.readinessCheckEnabled} onChange={(c) => handleSettingChange('readinessCheckEnabled', c)} /></div>
                </div>
            </SettingsCard>

            <SettingsCard title="Sincronización y Datos" icon={<CloudIcon/>}>
                <div className="space-y-6">
                    <div>
                        <h4 className="font-semibold text-white mb-2">Google Drive</h4>
                        {!drive.isSupported ? <p className="text-sm text-yellow-400">La sincronización no está configurada. El desarrollador necesita añadir un Client ID de Google.</p> : drive.isAuthLoading ? <p className="text-sm text-slate-400">Verificando...</p> : drive.isSignedIn && drive.user ? (
                            <div className="space-y-4"><div className="flex items-center gap-3 glass-card-nested p-2 rounded-lg"><img src={drive.user.picture} alt="User" className="w-10 h-10 rounded-full" /><div><p className="font-semibold text-white">{drive.user.name}</p><p className="text-xs text-slate-400">{drive.user.email}</p></div></div><div className="text-xs text-slate-400">Última sinc.: {drive.lastSyncDate ? new Date(drive.lastSyncDate).toLocaleString() : 'Nunca'}</div><div className="grid grid-cols-2 gap-2"><Button onClick={drive.syncToDrive} isLoading={drive.isSyncing} variant="secondary" disabled={!isOnline}><UploadCloudIcon size={16}/> Sincronizar</Button><Button onClick={drive.loadFromDrive} isLoading={drive.isLoading} variant="secondary" disabled={!isOnline}><DownloadCloudIcon size={16}/> Cargar</Button></div><Button onClick={drive.signOut} variant="danger" className="w-full">Cerrar Sesión</Button></div>
                        ) : (
                            <div className="space-y-3"><p className="text-sm text-slate-400">Guarda tus datos de forma segura en tu Google Drive.</p><Button onClick={drive.signIn} className="w-full" disabled={!isOnline}>Iniciar Sesión con Google</Button></div>
                        )}
                        <div className="flex justify-between items-center mt-4"><span className="text-slate-300">Sincronización automática</span><ToggleSwitch checked={pendingSettings.autoSyncEnabled} onChange={(c) => handleSettingChange('autoSyncEnabled', c)} disabled={!isOnline} /></div>
                    </div>
                    <div className="pt-4 border-t border-slate-700/50">
                        <h4 className="font-semibold text-white mb-2">Gestión de Datos Locales</h4>
                        <div className="space-y-3">
                            <h5 className="font-medium text-slate-300">Copia de Seguridad (Datos de Usuario)</h5>
                            <div className="grid grid-cols-2 gap-2">
                               <Button onClick={handleExportData} variant="secondary"><DownloadIcon size={16}/> Exportar Datos</Button>
                               <input type="file" ref={fileInputRef} className="hidden" accept=".json" onChange={handleImportData} />
                               <Button onClick={() => fileInputRef.current?.click()} variant="secondary"><UploadIcon size={16}/> Importar Datos</Button>
                            </div>

                            <h5 className="font-medium text-slate-300 pt-3">Base de Datos de Ejercicios</h5>
                            <div className="grid grid-cols-2 gap-2">
                               <Button onClick={exportExerciseDatabase} variant="secondary"><DownloadIcon size={16}/> Exportar DB</Button>
                               <input type="file" ref={dbFileInputRef} className="hidden" accept=".json" onChange={handleImportDb} />
                               <Button onClick={() => dbFileInputRef.current?.click()} variant="secondary"><UploadIcon size={16}/> Importar DB</Button>
                            </div>
                            <p className="text-xs text-slate-500 pt-1">Importar una base de datos reemplazará tu lista de ejercicios actual. Útil para compartir o actualizar la información por defecto de la app.</p>

                            <h5 className="font-medium text-slate-300 pt-3">Snapshots (Datos de Usuario)</h5>
                            <Button onClick={handleCreateSnapshot} className="w-full"><PlusCircleIcon size={16}/> Crear Copia Local</Button>
                            <div className="space-y-2 max-h-48 overflow-y-auto">{snapshots.sort((a,b) => new Date(b.date).getTime() - new Date(a.date).getTime()).map(snapshot => (<div key={snapshot.id} className="glass-card-nested p-2 rounded-md flex justify-between items-center text-sm"><div><p className="font-semibold text-slate-200">{snapshot.name}</p><p className="text-xs text-slate-400">{new Date(snapshot.date).toLocaleString()}</p></div><div className="flex gap-1"><Button onClick={() => handleRestoreSnapshot(snapshot.id)} variant="secondary" className="!p-2"><SaveIcon size={14}/></Button><Button onClick={() => handleDeleteSnapshot(snapshot.id)} variant="danger" className="!p-2"><TrashIcon size={14}/></Button></div></div>))}</div>
                        </div>
                    </div>
                </div>
            </SettingsCard>
            
            <SettingsCard title="Notificaciones" icon={<BellIcon/>}>
                <div className="space-y-4">
                    <div className="flex justify-between items-center"><span className="text-slate-300">Activar recordatorios</span><ToggleSwitch checked={pendingSettings.remindersEnabled} onChange={(c) => handleSettingChange('remindersEnabled', c)} /></div>
                    {pendingSettings.remindersEnabled && (
                        <div><label className="block text-sm text-slate-300 mb-1">Hora del recordatorio</label><input type="time" value={pendingSettings.reminderTime || '17:00'} onChange={(e) => handleSettingChange('reminderTime', e.target.value)} className="w-full"/></div>
                    )}
                    <p className="text-xs text-slate-500">Recibirás notificaciones en los días de la semana que tengas sesiones asignadas.</p>
                </div>
            </SettingsCard>

            <SettingsCard title="Instalar Aplicación" icon={<DownloadIcon/>}>
                {installPromptEvent ? (
                    <div><p className="text-sm text-slate-400 mb-4">Instala YourPrime en tu dispositivo para un acceso más rápido y una experiencia de aplicación nativa.</p><Button onClick={handleInstallClick} className="w-full">Instalar en el dispositivo</Button></div>
                ) : (
                    <p className="text-sm text-slate-400">La aplicación ya está instalada o tu navegador no soporta la instalación.</p>
                )}
            </SettingsCard>
        </div>
    );
};

export default SettingsComponent;