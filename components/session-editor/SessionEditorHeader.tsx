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
        <div className="relative flex-shrink-0 bg-black border-b border-white/10 z-20">
            <button onClick={onCancel} className="absolute top-3 right-3 z-50 p-2 bg-black/60 backdrop-blur-md rounded-full text-zinc-400 hover:text-white transition-colors border border-white/10">
                <XIcon size={16} />
            </button>

            <div className="absolute inset-0 z-0 opacity-20" style={{ ...headerStyle, backgroundSize: 'cover', filter: session.coverStyle ? getFilterString() : 'none' }}></div>
            <div className="absolute inset-0 z-0 bg-gradient-to-b from-black/30 via-black/70 to-black"></div>

            <div className="relative z-10 px-5 pt-6 pb-4 space-y-3">
                <input
                    type="text"
                    value={session.name}
                    onChange={e => updateSession(d => { d.name = e.target.value; })}
                    placeholder="NOMBRE DE LA SESIÓN"
                    className="text-2xl font-black text-white bg-transparent border-none focus:ring-0 w-[85%] p-0 leading-tight tracking-tighter uppercase placeholder-zinc-700"
                />
                <input
                    type="text"
                    value={session.description}
                    onChange={e => updateSession(d => { d.description = e.target.value; })}
                    placeholder="Descripción..."
                    className="text-[11px] text-zinc-400 bg-transparent border-none focus:ring-0 w-full p-0 placeholder-zinc-700 font-medium"
                />

                <div className="flex items-center gap-2 pt-1">
                    <button onClick={onOpenBgModal} className="p-2 rounded-lg border border-white/10 text-zinc-500 hover:text-white hover:bg-white/5 transition-all" title="Fondo">
                        <ImageIcon size={14} />
                    </button>
                    {activeSessionId !== 'empty' && (
                        <button onClick={onOpenTransferModal} className="px-3 py-1.5 bg-zinc-900 border border-white/10 hover:bg-zinc-800 rounded-lg text-[9px] font-bold uppercase text-zinc-400 hover:text-white transition-all flex items-center gap-1.5">
                            <LayersIcon size={12} /> Transferir
                        </button>
                    )}
                    <button
                        onClick={() => updateSession(d => { d.isMeetDay = !d.isMeetDay; })}
                        className={`p-2 rounded-lg border transition-all ${session.isMeetDay ? 'bg-yellow-500/20 border-yellow-500/30 text-yellow-400' : 'border-white/10 text-zinc-500 hover:text-white hover:bg-white/5'}`}
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
