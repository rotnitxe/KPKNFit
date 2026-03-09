// components/workout/InCardTimer.tsx
import React, { useState, useEffect, useRef } from 'react';
import { PlayIcon, PauseIcon, CheckCircleIcon } from '../icons';

interface InCardTimerProps {
    initialTime: number;
    onSave: (duration: number) => void;
}

export const InCardTimer: React.FC<InCardTimerProps> = ({ initialTime, onSave }) => {
    const [time, setTime] = useState(initialTime * 1000);
    const [isRunning, setIsRunning] = useState(false);
    const intervalRef = useRef<number | null>(null);
    const startTimeRef = useRef<number | null>(null);

    useEffect(() => {
        if (isRunning) {
            startTimeRef.current = Date.now() - time;
            intervalRef.current = window.setInterval(() => {
                setTime(Date.now() - (startTimeRef.current || 0));
            }, 50);
        } else if (intervalRef.current) {
            clearInterval(intervalRef.current);
        }
        return () => { if (intervalRef.current) clearInterval(intervalRef.current); };
    }, [isRunning, time]);

    const handleToggle = (e: React.MouseEvent) => { e.preventDefault(); e.stopPropagation(); setIsRunning(!isRunning); };
    const handleStop = (e: React.MouseEvent) => { e.preventDefault(); e.stopPropagation(); setIsRunning(false); onSave(Math.ceil(time / 1000)); };
    const formatMs = (ms: number) => { const totalSeconds = Math.floor(ms / 1000); const mins = Math.floor(totalSeconds / 60).toString().padStart(2, '0'); const secs = (totalSeconds % 60).toString().padStart(2, '0'); return `${mins}:${secs}`; };

    return (
        <div className="flex items-center gap-2 rounded-[12px] p-1 pr-2 border border-[var(--md-sys-color-outline-variant)] bg-[var(--md-sys-color-surface)]">
            <button onClick={handleToggle} className={`w-8 h-8 flex items-center justify-center rounded-full transition-colors ${isRunning ? 'bg-[var(--md-sys-color-secondary-container)] text-[var(--md-sys-color-on-secondary-container)]' : 'bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)]'}`}>
                {isRunning ? <PauseIcon size={14} /> : <PlayIcon size={14} />}
            </button>
            <span className={`font-mono font-bold text-lg w-14 text-center ${isRunning ? 'text-[var(--md-sys-color-on-surface)]' : 'text-[var(--md-sys-color-on-surface-variant)]'}`}>{formatMs(time)}</span>
            <button onClick={handleStop} className="p-1.5 text-[var(--md-sys-color-on-surface-variant)] hover:text-[var(--md-sys-color-on-surface)] bg-[var(--md-sys-color-surface-container)] rounded-full transition-colors" title="Detener y guardar"><CheckCircleIcon size={16} /></button>
        </div>
    );
};
