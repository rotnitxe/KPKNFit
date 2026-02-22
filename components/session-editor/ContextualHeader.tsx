import React, { useState, useEffect, useRef } from 'react';
import { Session } from '../../types';
import { XIcon, ImageIcon, LayersIcon, TrophyIcon, ClockIcon } from '../icons';

interface ContextualHeaderProps {
    session: Session;
    updateSession: (updater: (draft: Session) => void) => void;
    onCancel: () => void;
    onOpenBgDrawer: () => void;
    onOpenTransferDrawer: () => void;
    onOpenHistoryDrawer: () => void;
    activeSessionId: string;
    dayLabel?: string;
}

const ContextualHeader: React.FC<ContextualHeaderProps> = ({
    session, updateSession, onCancel, onOpenBgDrawer, onOpenTransferDrawer,
    onOpenHistoryDrawer, activeSessionId, dayLabel,
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
                    <div className="flex items-center gap-3">
                        <button onClick={onCancel} className="text-[#555] hover:text-white transition-colors shrink-0">
                            <XIcon size={16} />
                        </button>
                        <h1 className="text-sm font-semibold text-white truncate flex-1">
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
                            <button onClick={onCancel} className="text-[#555] hover:text-white transition-colors shrink-0 ml-3">
                                <XIcon size={16} />
                            </button>
                        </div>

                        {/* Action bar */}
                        <div className="flex items-center gap-2">
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
                            <button onClick={onOpenHistoryDrawer} className="px-2.5 py-1.5 rounded-lg border border-white/[0.08] text-[#999] hover:text-white hover:bg-white/5 transition-all text-[10px] font-medium flex items-center gap-1.5">
                                <ClockIcon size={12} /> Historial
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </>
    );
};

export default ContextualHeader;
