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
    const formatSeconds = (s: number) => { if (s < 60) return `${s}s`; const m = Math.floor(s/60); const sec = s%60; return `${m}:${sec.toString().padStart(2,'0')}`; };

    return (
        <button onClick={toggleTimer} className={`flex items-center justify-center gap-1 w-8 h-8 rounded-full transition-all shadow-md ${isRunning ? 'bg-red-500 text-white animate-pulse' : duration > 0 ? 'bg-sky-500 text-white' : 'bg-slate-700 text-slate-300'}`} title="Cronómetro de Serie (TUT)">
            {isRunning || duration > 0 ? <span className="text-[9px] font-black">{duration}</span> : <ClockIcon size={14} />}
        </button>
    );
};
