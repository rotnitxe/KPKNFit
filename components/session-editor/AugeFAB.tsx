import React, { useState, useRef, useCallback } from 'react';
import { ActivityIcon } from '../icons';

export type AugeSessionStatus = 'optimal' | 'warning' | 'fatiguing';

const STORAGE_KEY = 'kpkn_auge_fab_position';

interface AugeFABProps {
    alertCount: number;
    sessionStatus: AugeSessionStatus;
    hasCritical?: boolean;
    onClick: () => void;
}

const AugeFAB: React.FC<AugeFABProps> = ({ alertCount, sessionStatus, hasCritical, onClick }) => {
    const [position, setPosition] = useState<{ right: number; bottom: number }>(() => {
        try {
            const saved = localStorage.getItem(STORAGE_KEY);
            if (saved) {
                const p = JSON.parse(saved);
                return { right: Number(p.right) ?? 16, bottom: Number(p.bottom) ?? 160 };
            }
        } catch (_) {}
        return { right: 16, bottom: 160 };
    });

    const [isDragging, setIsDragging] = useState(false);
    const dragStart = useRef<{ right: number; bottom: number; clientX: number; clientY: number } | null>(null);

    const handlePointerDown = useCallback((e: React.PointerEvent) => {
        e.preventDefault();
        setIsDragging(true);
        dragStart.current = { right: position.right, bottom: position.bottom, clientX: e.clientX, clientY: e.clientY };
        (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
    }, [position]);

    const handlePointerMove = useCallback((e: React.PointerEvent) => {
        if (!dragStart.current) return;
        const dx = dragStart.current.clientX - e.clientX;
        const dy = e.clientY - dragStart.current.clientY;
        const newRight = Math.max(8, Math.min(window.innerWidth - 56, dragStart.current.right + dx));
        const newBottom = Math.max(80, Math.min(window.innerHeight - 56, dragStart.current.bottom + dy));
        setPosition({ right: newRight, bottom: newBottom });
    }, []);

    const handlePointerUp = useCallback((e: React.PointerEvent) => {
        if (dragStart.current) {
            const moved = Math.abs(e.clientX - dragStart.current.clientX) > 3 || Math.abs(e.clientY - dragStart.current.clientY) > 3;
            if (moved) {
                try { localStorage.setItem(STORAGE_KEY, JSON.stringify(position)); } catch (_) {}
                e.preventDefault();
                e.stopPropagation();
            }
            (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
        }
        setIsDragging(false);
        dragStart.current = null;
    }, [position]);

    const handleClick = useCallback((e: React.MouseEvent) => {
        if (isDragging) { e.preventDefault(); return; }
        onClick();
    }, [onClick, isDragging]);

    // White theme with Liquid Glass effect - Material Design inspired
    const fabBg = sessionStatus === 'fatiguing' 
        ? 'bg-white/80 backdrop-blur-xl border-red-500/30' 
        : sessionStatus === 'warning' 
            ? 'bg-white/80 backdrop-blur-xl border-yellow-500/30' 
            : 'bg-white/80 backdrop-blur-xl border-emerald-500/30';
    const badgeColor = alertCount > 0 ? (hasCritical ? 'bg-red-500' : 'bg-yellow-500') : 'bg-emerald-500';
    const iconColor = sessionStatus === 'fatiguing' 
        ? 'text-red-600' 
        : sessionStatus === 'warning' 
            ? 'text-yellow-600' 
            : 'text-emerald-600';
    const glowColor = sessionStatus === 'fatiguing'
        ? 'shadow-[0_8px_32px_rgba(239,68,68,0.2)]'
        : sessionStatus === 'warning'
            ? 'shadow-[0_8px_32px_rgba(234,179,8,0.2)]'
            : 'shadow-[0_8px_32px_rgba(16,185,129,0.2)]';

    return (
        <div
            className={`fixed z-30 w-12 h-12 rounded-full ${fabBg} border flex items-center justify-center transition-all ${glowColor} ${isDragging ? 'scale-110 cursor-grabbing' : 'cursor-grab hover:opacity-90 active:scale-95'}`}
            style={{ right: position.right, bottom: position.bottom }}
            onPointerDown={handlePointerDown}
            onPointerMove={handlePointerMove}
            onPointerUp={handlePointerUp}
            onPointerCancel={handlePointerUp}
        >
            <button
                type="button"
                onClick={handleClick}
                className="w-full h-full flex items-center justify-center touch-none rounded-full"
            >
                <ActivityIcon size={20} className={`${iconColor} pointer-events-none`} />
                {alertCount > 0 && (
                    <span className={`absolute -top-1 -right-1 w-5 h-5 rounded-full ${badgeColor} text-[9px] font-bold text-white flex items-center justify-center pointer-events-none ${hasCritical ? 'animate-pulse' : ''} shadow-lg`}>
                        {alertCount > 9 ? '9+' : alertCount}
                    </span>
                )}
            </button>
        </div>
    );
};

export default AugeFAB;
