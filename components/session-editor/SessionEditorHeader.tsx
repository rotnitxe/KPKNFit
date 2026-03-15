import React from 'react';
import { Session, SessionBackground, CoverStyle } from '../../types';
import { XIcon, ImageIcon, LayersIcon, TrophyIcon } from '../icons';
import { useImageGradient } from '../../utils/colorUtils';

interface SessionEditorHeaderProps {
    session: Session;
    updateSession: (updater: (draft: Session) => void) => void;
    onCancel: () => void;
    onOpenBgModal: () => void;
    onOpenTransferModal: () => void;
    activeSessionId: string;
}

const SessionEditorHeader: React.FC<SessionEditorHeaderProps> = ({
    session, updateSession, onCancel, onOpenBgModal, onOpenTransferModal, activeSessionId
}) => {
    const { gradient } = useImageGradient(session.background?.type === 'image' ? session.background.value : undefined);
    const headerStyle: React.CSSProperties = { backgroundImage: session.background?.type === 'image' ? `url(${session.background.value})` : gradient };

    const getFilterString = () => {
        if (!session.coverStyle?.filters) return 'none';
        const f = session.coverStyle.filters;
        return `contrast(${f.contrast}%) saturate(${f.saturation}%) brightness(${f.brightness}%) grayscale(${f.grayscale}%) sepia(${f.sepia}%)`;
    };

    return (
        <div className="relative flex-shrink-0 bg-white/90 backdrop-blur-2xl border-b border-slate-200 z-20 shadow-sm">
            <button onClick={onCancel} className="absolute top-3 right-3 z-50 p-2 bg-white/80 backdrop-blur-md rounded-full text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-all border border-slate-200 shadow-sm">
                <XIcon size={16} />
            </button>

            <div className="absolute inset-0 z-0 opacity-10" style={{ ...headerStyle, backgroundSize: 'cover', filter: session.coverStyle ? getFilterString() : 'none' }}></div>
            <div className="absolute inset-0 z-0 bg-gradient-to-b from-white/50 via-white/70 to-white/90"></div>

            <div className="relative z-10 px-5 pt-6 pb-4 space-y-3">
                <input
                    type="text"
                    value={session.name}
                    onChange={e => updateSession(d => { d.name = e.target.value; })}
                    placeholder="NOMBRE DE LA SESIÓN"
                    className="text-2xl font-black text-slate-800 bg-transparent border-none focus:ring-0 w-[85%] p-0 leading-tight tracking-tighter uppercase placeholder-slate-300"
                />
                <input
                    type="text"
                    value={session.description}
                    onChange={e => updateSession(d => { d.description = e.target.value; })}
                    placeholder="Descripción..."
                    className="text-[11px] text-slate-500 bg-transparent border-none focus:ring-0 w-full p-0 placeholder-slate-300 font-medium"
                />

                <div className="flex items-center gap-2 pt-1">
                    <button onClick={onOpenBgModal} className="p-2 rounded-xl border border-slate-200 text-slate-500 hover:text-slate-700 hover:bg-slate-50 transition-all shadow-sm" title="Fondo">
                        <ImageIcon size={14} />
                    </button>
                    {activeSessionId !== 'empty' && (
                        <button onClick={onOpenTransferModal} className="px-3 py-1.5 bg-white border border-slate-200 hover:bg-slate-50 rounded-xl text-[9px] font-bold uppercase text-slate-500 hover:text-slate-700 transition-all flex items-center gap-1.5 shadow-sm">
                            <LayersIcon size={12} /> Transferir
                        </button>
                    )}
                    <button
                        onClick={() => updateSession(d => { d.isMeetDay = !d.isMeetDay; })}
                        className={`p-2 rounded-xl border transition-all shadow-sm ${session.isMeetDay ? 'bg-amber-50 border-amber-200 text-amber-600' : 'border-slate-200 text-slate-500 hover:text-slate-700 hover:bg-slate-50'}`}
                        title="Competición"
                    >
                        <TrophyIcon size={14} />
                    </button>
                </div>
            </div>
        </div>
    );
};

export default SessionEditorHeader;
