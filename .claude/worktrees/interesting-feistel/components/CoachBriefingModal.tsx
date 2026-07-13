// components/CoachBriefingModal.tsx
import React from 'react';
import { SparklesIcon } from './icons';
import Button from './ui/Button';

interface CoachBriefingModalProps {
  isOpen: boolean;
  onClose: () => void;
  briefing: string;
}

const CoachBriefingModal: React.FC<CoachBriefingModalProps> = ({ isOpen, onClose, briefing }) => {
    if (!isOpen) return null;

    return (
        <>
            <div className="bottom-sheet-backdrop" />
            <div className="bottom-sheet-content open !h-auto max-h-[90vh]">
                <div className="bottom-sheet-grabber" />
                <header className="flex-shrink-0 p-4 text-center">
                    <SparklesIcon size={40} className="mx-auto text-sky-400" />
                    <h2 className="text-xl font-bold text-white mt-2">Informe de tu Coach IA</h2>
                </header>
                <div className="overflow-y-auto px-4 pb-4">
                    <p className="text-center text-slate-300 whitespace-pre-wrap">{briefing}</p>
                </div>
                <footer className="p-4 bg-slate-900 border-t border-slate-700/50 flex-shrink-0">
                    <Button onClick={onClose} className="w-full !py-3">
                        Entendido, ¡vamos!
                    </Button>
                </footer>
            </div>
        </>
    );
};

export default CoachBriefingModal;