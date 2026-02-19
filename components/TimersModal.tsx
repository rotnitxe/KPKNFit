// components/TimersModal.tsx
import React, { useState, useEffect, useRef } from 'react';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { playSound } from '../services/soundService';
import { hapticImpact, hapticNotification, ImpactStyle, NotificationType } from '../services/hapticsService';

const formatTime = (time: number) => {
    const minutes = Math.floor(time / 60000).toString().padStart(2, '0');
    const seconds = Math.floor((time % 60000) / 1000).toString().padStart(2, '0');
    return `${minutes}:${seconds}`;
};

const Stopwatch: React.FC = () => {
    const [time, setTime] = useState(0);
    const [isRunning, setIsRunning] = useState(false);
    const intervalRef = useRef<number | null>(null);

    useEffect(() => {
        if (isRunning) {
            const startTime = Date.now() - time;
            intervalRef.current = window.setInterval(() => {
                setTime(Date.now() - startTime);
            }, 10);
        } else {
            if (intervalRef.current) clearInterval(intervalRef.current);
        }
        return () => {
            if (intervalRef.current) clearInterval(intervalRef.current);
        };
    }, [isRunning, time]);

    const handleStartStop = () => {
        hapticImpact(isRunning ? ImpactStyle.Light : ImpactStyle.Medium);
        setIsRunning(!isRunning);
    }
    
    const handleReset = () => {
        hapticImpact(ImpactStyle.Light);
        setTime(0);
        setIsRunning(false);
    }

    return (
        <div className="text-center">
            <p className="text-6xl font-mono font-bold text-white mb-4">{formatTime(time)}</p>
            <div className="flex justify-center gap-4">
                <Button onClick={handleStartStop}>{isRunning ? 'Pausar' : 'Iniciar'}</Button>
                <Button onClick={handleReset} variant="secondary">Reset</Button>
            </div>
        </div>
    );
};

const Timer: React.FC = () => {
    const [initialTime, setInitialTime] = useState(60); // 1 minute default
    const [timeLeft, setTimeLeft] = useState(initialTime * 1000);
    const [isRunning, setIsRunning] = useState(false);
    const intervalRef = useRef<number | null>(null);
    const endTimeRef = useRef<number | null>(null);

    useEffect(() => {
        if (isRunning) {
            endTimeRef.current = Date.now() + timeLeft;
            intervalRef.current = window.setInterval(() => {
                const remaining = (endTimeRef.current ?? 0) - Date.now();
                if (remaining <= 0) {
                    setTimeLeft(0);
                    setIsRunning(false);
                    playSound('rest-timer-sound');
                    hapticNotification(NotificationType.SUCCESS);
                } else {
                    setTimeLeft(remaining);
                }
            }, 10);
        } else {
            if (intervalRef.current) clearInterval(intervalRef.current);
        }
        return () => {
            if (intervalRef.current) clearInterval(intervalRef.current);
        };
    }, [isRunning, timeLeft]);
    
    const handleSetTime = (seconds: number) => {
        if (isRunning) return;
        hapticImpact(ImpactStyle.Light);
        setInitialTime(seconds);
        setTimeLeft(seconds * 1000);
    };

    const handleStartStop = () => {
        if (timeLeft <= 0) return;
        hapticImpact(isRunning ? ImpactStyle.Light : ImpactStyle.Medium);
        setIsRunning(!isRunning);
    }

    const handleReset = () => {
        hapticImpact(ImpactStyle.Light);
        setIsRunning(false);
        setTimeLeft(initialTime * 1000);
    };

    return (
        <div className="text-center">
             {!isRunning && timeLeft === initialTime * 1000 && (
                <div className="mb-4 space-x-2">
                    <Button onClick={() => handleSetTime(30)} variant="secondary" className="!py-1 !px-2 !text-xs">30s</Button>
                    <Button onClick={() => handleSetTime(60)} variant="secondary" className="!py-1 !px-2 !text-xs">1m</Button>
                    <Button onClick={() => handleSetTime(120)} variant="secondary" className="!py-1 !px-2 !text-xs">2m</Button>
                    <Button onClick={() => handleSetTime(180)} variant="secondary" className="!py-1 !px-2 !text-xs">3m</Button>
                </div>
            )}
            <p className="text-6xl font-mono font-bold text-white mb-4">{formatTime(timeLeft)}</p>
            <div className="flex justify-center gap-4">
                <Button onClick={handleStartStop} disabled={timeLeft <= 0}>{isRunning ? 'Pausar' : 'Iniciar'}</Button>
                <Button onClick={handleReset} variant="secondary">Reset</Button>
            </div>
        </div>
    );
};

interface TimersModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const TimersModal: React.FC<TimersModalProps> = ({ isOpen, onClose }) => {
    const [activeTab, setActiveTab] = useState<'stopwatch' | 'timer'>('stopwatch');
  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Cronómetros">
      <div className="space-y-4">
        <div className="flex bg-slate-800 p-1 rounded-full">
            <button onClick={() => setActiveTab('stopwatch')} className={`w-full py-1.5 rounded-full text-sm ${activeTab === 'stopwatch' ? 'bg-primary-color' : ''}`}>Cronómetro</button>
            <button onClick={() => setActiveTab('timer')} className={`w-full py-1.5 rounded-full text-sm ${activeTab === 'timer' ? 'bg-primary-color' : ''}`}>Temporizador</button>
        </div>
        {activeTab === 'stopwatch' ? <Stopwatch /> : <Timer />}
      </div>
    </Modal>
  );
};

export default TimersModal;