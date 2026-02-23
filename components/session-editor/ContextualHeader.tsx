import React, { useState, useEffect, useRef } from 'react';
import { Session } from '../../types';
import { XIcon, ImageIcon, LayersIcon, TrophyIcon, ClockIcon, SettingsIcon, CheckIcon } from '../icons';

interface ContextualHeaderProps {
    session: Session;
    updateSession: (updater: (draft: Session) => void) => void;
    onCancel: () => void;
    onSave: () => void;
    hasChanges?: boolean;
    onOpenBgDrawer: () => void;
    onOpenTransferDrawer: () => void;
    onOpenHistoryDrawer: () => void;
    onOpenRulesDrawer: () => void;
    activeSessionId: string;
    dayLabel?: string;
}

const ContextualHeader: React.FC<ContextualHeaderProps> = ({
    session, updateSession, onCancel, onSave, hasChanges, onOpenBgDrawer, onOpenTransferDrawer,
    onOpenHistoryDrawer, onOpenRulesDrawer, activeSessionId, dayLabel,
}) => {
    const [isCompact, setIsCompact] = useState(false);
    const sentinelRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const sentinel = sentinelRef.current;
        if (!sentinel) return;
        const observer = new IntersectionObserver(
            ([entry]) => setIsCompact(!entry.isIntersecting),
            { threshold: 0 }
        );
        observer.observe(sentinel);
        return () => observer.disconnect();
    }, []);

    return (
        <>
            {/* Sentinel element for intersection observer */}
            <div ref={sentinelRef} className="h-0 w-0" />

            {/* Header */}
            <div className={`sticky top-0 z-30 bg-black border-b border-white/[0.08] transition-all ${isCompact ? 'py-2 px-4' : 'py-4 px-4'}`}>
                {isCompact ? (
                    /* Compact mode */
                    <div className="flex items-center gap-2">
                        <button onClick={onCancel} className="text-[#555] hover:text-white transition-colors shrink-0 p-1" title="Salir">
                            <XIcon size={16} />
                        </button>
                        <h1 className="text-sm font-semibold text-white truncate flex-1 min-w-0">
                            {session.name || 'Sin nombre'}
                        </h1>
                        {dayLabel && (
                            <span className="text-[10px] font-bold text-[#999] bg-white/5 px-2 py-0.5 rounded shrink-0">
                                {dayLabel}
                            </span>
                        )}
                        {session.isMeetDay && (
                            <TrophyIcon size={12} className="text-yellow-400 shrink-0" />
                        )}
                        {hasChanges && (
                            <div className="flex items-center gap-1 shrink-0">
                                <div className="w-1.5 h-1.5 rounded-full bg-[#FC4C02] animate-pulse" />
                                <span className="text-[10px] text-[#999] font-medium hidden sm:inline">Sin guardar</span>
                            </div>
                        )}
                        {activeSessionId !== 'empty' && (
                            <button
                                onClick={onSave}
                                disabled={!hasChanges}
                                className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 transition-all ${hasChanges ? 'bg-[#FC4C02] text-white hover:brightness-110' : 'bg-white/5 text-[#555] cursor-not-allowed'}`}
                                title="Guardar"
                            >
                                <CheckIcon size={14} strokeWidth={2.5} />
                            </button>
                        )}
                        <button onClick={onOpenHistoryDrawer} className="p-1.5 rounded-lg text-[#555] hover:text-[#FC4C02] hover:bg-[#FC4C02]/10 transition-colors shrink-0" title="Historial">
                            <ClockIcon size={14} />
                        </button>
                        <button onClick={onOpenRulesDrawer} className="p-1.5 rounded-lg text-[#555] hover:text-[#FC4C02] hover:bg-[#FC4C02]/10 transition-colors shrink-0" title="Reglas">
                            <SettingsIcon size={14} />
                        </button>
                    </div>
                ) : (
                    /* Expanded mode */
                    <div className="space-y-3">
                        <div className="flex items-start justify-between">
                            <div className="flex-1 min-w-0 space-y-1">
                                <div className="flex items-center gap-2">
                                    {dayLabel && (
                                        <span className="text-[10px] font-bold text-[#FC4C02] bg-[#FC4C02]/10 px-2 py-0.5 rounded">
                                            {dayLabel}
                                        </span>
                                    )}
                                    {session.isMeetDay && (
                                        <span className="text-[10px] font-bold text-yellow-400 bg-yellow-400/10 px-2 py-0.5 rounded">
                                            Competición
                                        </span>
                                    )}
                                    {hasChanges && (
                                        <div className="flex items-center gap-1">
                                            <div className="w-1.5 h-1.5 rounded-full bg-[#FC4C02] animate-pulse" />
                                            <span className="text-[10px] text-[#999] font-medium">Sin guardar</span>
                                        </div>
                                    )}
                                </div>
                                <input
                                    type="text"
                                    value={session.name}
                                    onChange={e => updateSession(d => { d.name = e.target.value; })}
                                    placeholder="Nombre de la sesión"
                                    className="text-lg font-semibold text-white bg-transparent border-none focus:ring-0 w-full p-0 leading-tight placeholder-[#555] outline-none"
                                />
                                <input
                                    type="text"
                                    value={session.description}
                                    onChange={e => updateSession(d => { d.description = e.target.value; })}
                                    placeholder="Descripción..."
                                    className="text-xs text-[#999] bg-transparent border-none focus:ring-0 w-full p-0 placeholder-[#555] font-medium outline-none"
                                />
                            </div>
                            <div className="flex items-center gap-1.5 shrink-0 ml-3">
                                {activeSessionId !== 'empty' && (
                                    <button
                                        onClick={onSave}
                                        disabled={!hasChanges}
                                        className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 transition-all ${hasChanges ? 'bg-[#FC4C02] text-white hover:brightness-110' : 'bg-white/5 text-[#555] cursor-not-allowed'}`}
                                        title="Guardar"
                                    >
                                        <CheckIcon size={14} strokeWidth={2.5} />
                                    </button>
                                )}
                                <button onClick={onCancel} className="w-8 h-8 rounded-full flex items-center justify-center text-[#555] hover:text-white hover:bg-white/10 transition-colors shrink-0" title="Salir">
                                    <XIcon size={16} />
                                </button>
                            </div>
                        </div>

                        {/* Action bar + Carrusel Historial/Reglas */}
                        <div className="flex items-center gap-2 overflow-x-auto no-scrollbar">
                            <div className="flex items-center gap-1.5 shrink-0">
                                <button onClick={onOpenBgDrawer} className="px-2.5 py-1.5 rounded-lg border border-white/[0.08] text-[#999] hover:text-white hover:bg-white/5 transition-all text-[10px] font-medium flex items-center gap-1.5">
                                    <ImageIcon size={12} /> Fondo
                                </button>
                                {activeSessionId !== 'empty' && (
                                    <button onClick={onOpenTransferDrawer} className="px-2.5 py-1.5 rounded-lg border border-white/[0.08] text-[#999] hover:text-white hover:bg-white/5 transition-all text-[10px] font-medium flex items-center gap-1.5">
                                        <LayersIcon size={12} /> Transferir
                                    </button>
                                )}
                                <button
                                    onClick={() => updateSession(d => { d.isMeetDay = !d.isMeetDay; })}
                                    className={`px-2.5 py-1.5 rounded-lg border transition-all text-[10px] font-medium flex items-center gap-1.5 ${session.isMeetDay ? 'bg-yellow-400/10 border-yellow-400/20 text-yellow-400' : 'border-white/[0.08] text-[#999] hover:text-white hover:bg-white/5'}`}
                                >
                                    <TrophyIcon size={12} /> Comp.
                                </button>
                            </div>
                            <div className="h-6 w-px bg-white/10 shrink-0" />
                            <div className="flex items-center gap-1.5 shrink-0">
                                <button onClick={onOpenHistoryDrawer} className="px-3 py-1.5 rounded-full bg-[#FC4C02]/10 border border-[#FC4C02]/30 text-[#FC4C02] hover:bg-[#FC4C02]/20 transition-all text-[10px] font-bold uppercase tracking-wider flex items-center gap-1.5">
                                    <ClockIcon size={12} /> Historial
                                </button>
                                <button onClick={onOpenRulesDrawer} className="px-3 py-1.5 rounded-full bg-[#FC4C02]/10 border border-[#FC4C02]/30 text-[#FC4C02] hover:bg-[#FC4C02]/20 transition-all text-[10px] font-bold uppercase tracking-wider flex items-center gap-1.5">
                                    <SettingsIcon size={12} /> Reglas
                                </button>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </>
    );
};

export default ContextualHeader;
