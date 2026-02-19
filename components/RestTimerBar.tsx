// components/RestTimerBar.tsx
import React, { useState, useEffect } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { MinusIcon, PlusIcon, XIcon } from './icons';

const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60).toString().padStart(2, '0');
    const secs = (seconds % 60).toString().padStart(2, '0');
    return `${mins}:${secs}`;
};

const RestTimerBar: React.FC = () => {
    const { restTimer } = useAppState();
    const { handleAdjustRestTimer, handleSkipRestTimer } = useAppDispatch();
    const [isDone, setIsDone] = useState(false);

    useEffect(() => {
        if (restTimer && restTimer.remaining <= 0) {
            setIsDone(true);
            const timer = setTimeout(() => setIsDone(false), 2000); // The "Done!" message shows for 2s
            return () => clearTimeout(timer);
        } else if (restTimer && restTimer.remaining > 0) {
            setIsDone(false);
        }
    }, [restTimer]);
    
    const progress = restTimer && restTimer.duration > 0 ? ((restTimer.duration - restTimer.remaining) / restTimer.duration) * 100 : 0;

    return (
        <div className="rest-timer-wrapper" style={{ maxHeight: restTimer ? '60px' : '0' }}>
            {restTimer && (
                <div className="rest-timer-bar">
                    <div className="flex items-center gap-3">
                        <button onClick={() => handleAdjustRestTimer(-15)} className="text-slate-300 hover:text-white">-15s</button>
                        <div className="text-center">
                            <p className="text-xs text-slate-400">Descanso: {restTimer.exerciseName}</p>
                            {isDone ? (
                                <p className="text-lg font-bold text-green-400 animate-pulse">¡A seguir!</p>
                            ) : (
                                <p className="text-2xl font-mono font-bold text-white">{formatTime(restTimer.remaining)}</p>
                            )}
                        </div>
                        <button onClick={() => handleAdjustRestTimer(15)} className="text-slate-300 hover:text-white">+15s</button>
                    </div>
                    <button onClick={handleSkipRestTimer} className="text-slate-300 hover:text-white">Saltar</button>
                    <div className="rest-timer-progress" style={{ width: `${progress}%` }} />
                </div>
            )}
        </div>
    );
};

export default RestTimerBar;