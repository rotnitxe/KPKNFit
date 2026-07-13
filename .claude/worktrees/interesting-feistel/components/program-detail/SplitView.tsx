import React from 'react';
import { SplitAdvancedEditor } from './SplitAdvancedEditor';

interface SplitViewProps {
    program: any;
    selectedBlockId: string | null;
    selectedWeekId: string | null;
    onUpdateProgram: (program: any) => void;
    addToast: (msg: string, type?: 'success' | 'danger' | 'achievement' | 'suggestion', title?: string, duration?: number, why?: string) => void;
}

export const SplitView: React.FC<SplitViewProps> = (props) => {
    // ✅ Detectar si es primera semana con nuevo split (Probar Split)
    const isNewSplit = props.program.selectedSplitId && !props.program.splitTrialSeen;
    
    const handleDismissTrial = () => {
        const updated = { ...props.program, splitTrialSeen: true };
        props.onUpdateProgram(updated);
    };

    return (
        <div className="pb-6">
            {/* Header informativo integrado */}
            <div className="px-4 mb-4">
                {/* ✅ Aviso de Probar Split */}
                {isNewSplit && (
                    <div className="bg-gradient-to-r from-cyan-500 to-blue-600 rounded-2xl p-4 text-white mb-3 shadow-lg shadow-cyan-500/30">
                        <div className="flex items-start gap-3">
                            <div className="w-10 h-10 rounded-full bg-white/20 flex items-center justify-center shrink-0">
                                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
                            </div>
                            <div className="flex-1">
                                <h4 className="text-sm font-black uppercase tracking-widest mb-1">
                                    Periodo de Prueba
                                </h4>
                                <p className="text-[9px] text-white/90 leading-relaxed">
                                    Prueba este split por una semana para ver si se ajusta a tus necesidades. Siempre podrás cambiarlo si no te convence.
                                </p>
                            </div>
                            <button
                                onClick={handleDismissTrial}
                                className="w-8 h-8 rounded-full bg-white/20 hover:bg-white/30 transition-colors flex items-center justify-center shrink-0"
                            >
                                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
                            </button>
                        </div>
                    </div>
                )}

                <div className="bg-gradient-to-r from-purple-500 to-purple-600 rounded-2xl p-4 text-white">
                    <h3 className="text-sm font-black uppercase tracking-widest mb-1">
                        Gestor de Splits
                    </h3>
                    <p className="text-[10px] text-white/80">
                        {props.program.selectedSplitId 
                            ? `Actual: ${props.program.selectedSplitId}` 
                            : 'Sin split definido'}
                    </p>
                </div>
            </div>

            {/* Editor avanzado integrado */}
            <SplitAdvancedEditor {...props} />
        </div>
    );
};
