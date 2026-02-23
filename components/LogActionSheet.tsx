// components/LogActionSheet.tsx
import React, { useEffect, useState } from 'react';
import { useAppContext, useAppDispatch } from '../contexts/AppContext';
import { XIcon, PlayIcon, BodyIcon, UtensilsIcon, PlusCircleIcon, DumbbellIcon, IdCardIcon, SettingsIcon, ClipboardListIcon, CalendarIcon, MicIcon, MicOffIcon } from './icons';

interface ActionItem {
    icon: React.FC<any>;
    label: string;
    action: () => void;
    color: string;
    delay: number;
    condition?: boolean; // Optional condition to show
    ariaLabel?: string;
}

const LogActionSheet: React.FC = () => {
    const { 
        isLogActionSheetOpen, 
        setIsLogActionSheetOpen,
        setIsStartWorkoutModalOpen,
        setIsBodyLogModalOpen,
        navigateTo,
        setIsMenuOpen,
        programs,
        activeProgramState,
        openCustomExerciseEditor,
        openFoodEditor,
        settings,
        isGlobalVoiceActive,
        setIsGlobalVoiceActive
    } = useAppContext();
    
    const { addToast } = useAppDispatch();

    const [isVisible, setIsVisible] = useState(false);

    useEffect(() => {
        if (isLogActionSheetOpen) {
            setIsVisible(true);
        } else {
            const timer = setTimeout(() => setIsVisible(false), 300);
            return () => clearTimeout(timer);
        }
    }, [isLogActionSheetOpen]);

    const handleAction = (item: ActionItem) => {
        item.action();
        setIsLogActionSheetOpen(false);
    };
    
    const handleCreateSession = () => {
        if (!programs || programs.length === 0) {
            addToast("Debes crear un Programa antes de añadir una sesión.", "suggestion");
            return;
        }

        if (activeProgramState?.programId) {
            navigateTo('program-editor', { programId: activeProgramState.programId });
            addToast("Añade la sesión a tu programa activo.", "suggestion");
        } else {
            navigateTo('programs');
            addToast("Selecciona un programa para añadir la sesión.", "suggestion");
        }
    };
    
    const toggleVoice = () => {
        setIsGlobalVoiceActive(!isGlobalVoiceActive);
    }

    if (!isLogActionSheetOpen && !isVisible) return null;

    const hasApiKey = !!settings.apiKeys?.gemini;

    const actions: ActionItem[] = [
        // Fila 1: Acciones Principales
        { 
            icon: PlayIcon, 
            label: "Entrenar", 
            action: () => setIsStartWorkoutModalOpen(true),
            color: "bg-emerald-500",
            delay: 0
        },
        { 
            icon: isGlobalVoiceActive ? MicOffIcon : MicIcon, 
            label: "Voz (IA)", 
            action: toggleVoice,
            color: isGlobalVoiceActive ? "bg-red-500 animate-pulse" : "bg-cyan-500",
            delay: 50,
            condition: hasApiKey
        },
        { 
            icon: PlusCircleIcon, 
            label: "Programa", 
            action: () => navigateTo('program-editor'),
            color: "bg-indigo-600",
            delay: 100
        },

        // Fila 2: Creación
        { 
            icon: DumbbellIcon, 
            label: "Ejercicio", 
            action: () => openCustomExerciseEditor(),
            color: "bg-blue-600",
            delay: 150
        },
        { 
            icon: UtensilsIcon, 
            label: "Alimento", 
            action: () => openFoodEditor(),
            color: "bg-cyber-cyan",
            delay: 200
        },
         { 
            icon: BodyIcon, 
            label: "Métricas", 
            action: () => setIsBodyLogModalOpen(true),
            color: "bg-pink-500",
            delay: 250
        },

        // Fila 3: Navegación
        { 
            icon: ClipboardListIcon, 
            label: "KPKN", 
            action: () => navigateTo('kpkn'),
            color: "bg-slate-700",
            delay: 300
        },
        { 
            icon: IdCardIcon, 
            label: "Atleta ID", 
            action: () => setIsMenuOpen(true),
            color: "bg-slate-700",
            delay: 350
        },
         { 
            icon: SettingsIcon, 
            label: "Ajustes", 
            action: () => navigateTo('settings'),
            color: "bg-slate-700",
            delay: 400,
            ariaLabel: "Settings"
        }
    ];
    
    // Filter out items with condition: false (undefined implies true)
    const validActions = actions.filter(item => item.condition !== false);

    return (
        <div 
            className={`fixed inset-0 z-[100] flex flex-col justify-end items-center transition-all duration-300 ease-out
                        ${isLogActionSheetOpen ? 'bg-black/85 backdrop-blur-2xl opacity-100' : 'bg-transparent backdrop-blur-none opacity-0 pointer-events-none'}`}
            onClick={() => setIsLogActionSheetOpen(false)}
        >
            <div className="w-full max-w-md px-6 pb-32" onClick={e => e.stopPropagation()}>
                <div className="grid grid-cols-3 gap-y-8 gap-x-4">
                    {validActions.map((item, index) => (
                        <div 
                            key={index}
                            onClick={() => handleAction(item)}
                            role="button"
                            aria-label={item.ariaLabel || item.label}
                            className={`flex flex-col items-center gap-3 cursor-pointer group animate-emerge-up`}
                            style={{ animationDelay: `${item.delay}ms`, opacity: 0, animationFillMode: 'forwards' }}
                        >
                            <div className={`w-14 h-14 rounded-2xl flex items-center justify-center shadow-lg ${item.color} transition-transform duration-300 group-hover:scale-110 active:scale-95 border border-white/10`}>
                                <item.icon size={24} className="text-white" strokeWidth={2.5} />
                            </div>
                            <span className="text-[10px] font-bold text-white/80 tracking-wide text-center uppercase group-hover:text-white transition-colors leading-tight">{item.label}{item.ariaLabel && item.ariaLabel !== item.label ? <span className="sr-only"> {item.ariaLabel}</span> : null}</span>
                        </div>
                    ))}
                </div>
            </div>

            <button 
                onClick={() => setIsLogActionSheetOpen(false)}
                className={`absolute bottom-8 w-14 h-14 rounded-full bg-white/10 flex items-center justify-center text-white backdrop-blur-md border border-white/10 hover:bg-white/20 transition-all duration-500 active:scale-90 ${isLogActionSheetOpen ? 'opacity-100 rotate-0 scale-100' : 'opacity-0 rotate-90 scale-0'}`}
            >
                <XIcon size={28} />
            </button>
        </div>
    );
};

export default LogActionSheet;