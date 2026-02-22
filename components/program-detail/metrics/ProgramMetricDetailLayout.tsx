import React, { useRef, useCallback } from 'react';
import { ArrowLeftIcon } from '../../icons';
import { useAppContext } from '../../../contexts/AppContext';

interface ProgramMetricDetailLayoutProps {
    title: string;
    children: React.ReactNode;
}

const ProgramMetricDetailLayout: React.FC<ProgramMetricDetailLayoutProps> = ({ title, children }) => {
    const { handleBack } = useAppContext();
    const touchStartX = useRef<number>(0);

    const handleTouchStart = useCallback((e: React.TouchEvent) => {
        touchStartX.current = e.touches[0].clientX;
    }, []);

    const handleTouchEnd = useCallback((e: React.TouchEvent) => {
        const deltaX = e.changedTouches[0].clientX - touchStartX.current;
        if (deltaX > 80) handleBack();
    }, [handleBack]);

    return (
        <div
            className="h-full flex flex-col bg-black"
            onTouchStart={handleTouchStart}
            onTouchEnd={handleTouchEnd}
        >
            <header className="shrink-0 sticky top-0 z-20 bg-black border-b border-white/5 px-4 py-3 flex items-center gap-3">
                <button
                    onClick={handleBack}
                    className="p-1.5 -ml-1 rounded-lg text-[#8E8E93] hover:text-white hover:bg-white/5 transition-colors"
                    aria-label="Volver"
                >
                    <ArrowLeftIcon size={20} />
                </button>
                <h1 className="text-sm font-bold text-white uppercase tracking-wide truncate flex-1">
                    {title}
                </h1>
            </header>
            <div className="flex-1 overflow-y-auto custom-scrollbar px-4 py-4">
                {children}
            </div>
        </div>
    );
};

export default ProgramMetricDetailLayout;
