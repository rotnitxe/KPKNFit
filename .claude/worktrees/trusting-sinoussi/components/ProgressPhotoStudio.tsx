// components/ProgressPhotoStudio.tsx
import React, { useState, useMemo, useEffect } from 'react';
import { useAppState } from '../contexts/AppContext';
import Card from './ui/Card';
import { SparklesIcon } from './icons';

const ProgressPhotoStudio: React.FC = () => {
    const { bodyProgress } = useAppState();
    const [startLogId, setStartLogId] = useState<string | null>(null);
    const [endLogId, setEndLogId] = useState<string | null>(null);
    const [sliderValue, setSliderValue] = useState(50);

    const logsWithPhotos = useMemo(() => bodyProgress.filter(log => log.photos && log.photos.length > 0).sort((a,b) => new Date(a.date).getTime() - new Date(b.date).getTime()), [bodyProgress]);

    useEffect(() => {
        if (logsWithPhotos.length > 0) {
            setStartLogId(logsWithPhotos[0].id);
            setEndLogId(logsWithPhotos[logsWithPhotos.length - 1].id);
        }
    }, [logsWithPhotos]);

    const startLog = useMemo(() => logsWithPhotos.find(log => log.id === startLogId), [logsWithPhotos, startLogId]);
    const endLog = useMemo(() => logsWithPhotos.find(log => log.id === endLogId), [logsWithPhotos, endLogId]);

    const clipPathStyle = { clipPath: `inset(0 ${100 - sliderValue}% 0 0)` };

    if (logsWithPhotos.length < 2) return null;

    return (
        <Card>
             <h3 className="text-xl font-bold mb-4">Estudio de Comparación de Fotos</h3>
             <div className="grid grid-cols-2 gap-4 mb-4">
                 <select value={startLogId || ''} onChange={e => setStartLogId(e.target.value)} className="w-full"><option value="">-- Foto Inicial --</option>{logsWithPhotos.map(log => <option key={log.id} value={log.id}>{new Date(log.date).toLocaleDateString()}</option>)}</select>
                 <select value={endLogId || ''} onChange={e => setEndLogId(e.target.value)} className="w-full"><option value="">-- Foto Final --</option>{logsWithPhotos.map(log => <option key={log.id} value={log.id}>{new Date(log.date).toLocaleDateString()}</option>)}</select>
             </div>
            {startLog && endLog && (
                <div className="relative aspect-[3/4] w-full max-w-sm mx-auto select-none">
                    <img src={startLog.photos![0]} alt="Start" className="absolute inset-0 w-full h-full object-contain rounded-lg" />
                    <img src={endLog.photos![0]} alt="End" className="absolute inset-0 w-full h-full object-contain rounded-lg" style={clipPathStyle} />
                    <input type="range" min="0" max="100" value={sliderValue} onChange={e => setSliderValue(Number(e.target.value))} className="absolute inset-x-0 bottom-0 w-full h-1 cursor-pointer opacity-50 hover:opacity-100 transition-opacity" />
                </div>
            )}
            {endLog?.aiInsight && (
                <div className="mt-4 p-3 bg-slate-900/50 rounded-lg">
                    <h4 className="text-sm font-semibold text-sky-300 flex items-center gap-1 mb-1"><SparklesIcon size={14}/> Análisis de IA (Foto Final)</h4>
                    <p className="text-xs text-slate-300 whitespace-pre-wrap">{endLog.aiInsight}</p>
                </div>
            )}
        </Card>
    );
};

export default ProgressPhotoStudio;
