// components/workout/SetTimerButton.tsx
import React, { useState, useEffect, useRef } from 'react';
import { ClockIcon } from '../icons';

interface SetTimerButtonProps {
    onSave: (duration: number) => void;
    initialDuration?: number;
}

export const SetTimerButton: React.FC<SetTimerButtonProps> = ({ onSave, initialDuration }) => {
    const [duration, setDuration] = useState(initialDuration || 0);
    const [isRunning, setIsRunning] = useState(false);
    const startTimeRef = useRef<number | null>(null);
    const intervalRef = useRef<number | null>(null);

    const toggleTimer = (e: React.MouseEvent) => {
        e.preventDefault(); e.stopPropagation();
        if (isRunning) { if (intervalRef.current) clearInterval(intervalRef.current); setIsRunning(false); onSave(duration); }
        else { startTimeRef.current = Date.now() - (duration * 1000); intervalRef.current = window.setInterval(() => { setDuration(Math.floor((Date.now() - (startTimeRef.current || 0)) / 1000)); }, 1000); setIsRunning(true); }
    };

    useEffect(() => { return () => { if (intervalRef.current) clearInterval(intervalRef.current); }; }, []);

    return (
        <button onClick={toggleTimer} className={`flex items-center justify-center gap-1 w-8 h-8 rounded-full transition-all shadow-sm border ${isRunning ? 'bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] border-[var(--md-sys-color-primary)]' : duration > 0 ? 'bg-[var(--md-sys-color-secondary-container)] text-[var(--md-sys-color-on-secondary-container)] border-[var(--md-sys-color-outline-variant)]' : 'bg-[var(--md-sys-color-surface-container)] text-[var(--md-sys-color-on-surface-variant)] border-[var(--md-sys-color-outline-variant)]'}`} title="Cronometro de serie (TUT)">
            {isRunning || duration > 0 ? <span className="text-[9px] font-black">{duration}</span> : <ClockIcon size={14} />}
        </button>
    );
};
