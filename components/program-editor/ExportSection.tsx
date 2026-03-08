import React from 'react';
import { Program } from '../../types';
import { ActivityIcon } from '../icons';

interface ExportSectionProps {
    program: Program;
    onDuplicate?: () => void;
    addToast: (msg: string, type: string) => void;
}

const ExportSection: React.FC<ExportSectionProps> = ({ program, onDuplicate, addToast }) => {
    const handleExportJSON = () => {
        const blob = new Blob([JSON.stringify(program, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${program.name?.replace(/[^a-zA-Z0-9]/g, '_') || 'program'}.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        addToast('Programa exportado como JSON', 'success');
    };

    const handleCopyToClipboard = async () => {
        try {
            await navigator.clipboard.writeText(JSON.stringify(program, null, 2));
            addToast('Copiado al portapapeles', 'success');
        } catch {
            addToast('Error al copiar', 'danger');
        }
    };

    return (
        <div className="space-y-6 p-4 rounded-3xl">
            <h3 className="text-title-sm font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-[0.2em] flex items-center gap-2">
                <ActivityIcon size={16} className="text-[var(--md-sys-color-primary)]" /> Exportación y Transferencia
            </h3>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <button
                    onClick={handleExportJSON}
                    className="bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-6 text-left hover:border-[var(--md-sys-color-primary)]/30 hover:shadow-xl transition-all group active:scale-95"
                >
                    <div className="w-10 h-10 rounded-xl bg-[var(--md-sys-color-primary-container)] flex items-center justify-center text-[var(--md-sys-color-on-primary-container)] mb-4 group-hover:bg-[var(--md-sys-color-primary)] group-hover:text-[var(--md-sys-color-on-primary)] transition-colors">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="7 10 12 15 17 10" /><line x1="12" y1="15" x2="12" y2="3" /></svg>
                    </div>
                    <span className="text-label-large font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-tighter block mb-1 group-hover:text-[var(--md-sys-color-primary)] transition-colors">Descargar Formato JSON</span>
                    <p className="text-[10px] text-[var(--md-sys-color-on-surface-variant)] font-bold uppercase tracking-tight">Archivo nativo compatible con el importador global.</p>
                </button>

                <button
                    onClick={handleCopyToClipboard}
                    className="bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-6 text-left hover:border-[var(--md-sys-color-primary)]/30 hover:shadow-xl transition-all group active:scale-95"
                >
                    <div className="w-10 h-10 rounded-xl bg-[var(--md-sys-color-primary-container)] flex items-center justify-center text-[var(--md-sys-color-on-primary-container)] mb-4 group-hover:bg-[var(--md-sys-color-primary)] group-hover:text-[var(--md-sys-color-on-primary)] transition-colors">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2" /><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" /></svg>
                    </div>
                    <span className="text-label-large font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-tighter block mb-1 group-hover:text-[var(--md-sys-color-primary)] transition-colors">Copiar al Portapapeles</span>
                    <p className="text-[10px] text-[var(--md-sys-color-on-surface-variant)] font-bold uppercase tracking-tight">Estructura bruta para transferencia rápida manual.</p>
                </button>

                {onDuplicate && (
                    <button
                        onClick={onDuplicate}
                        className="bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-6 text-left hover:border-[var(--md-sys-color-primary)]/30 hover:shadow-xl transition-all group active:scale-95"
                    >
                        <div className="w-10 h-10 rounded-xl bg-[var(--md-sys-color-primary-container)] flex items-center justify-center text-[var(--md-sys-color-on-primary-container)] mb-4 group-hover:bg-[var(--md-sys-color-primary)] group-hover:text-[var(--md-sys-color-on-primary)] transition-colors">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2" /><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" /></svg>
                        </div>
                        <span className="text-label-large font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-tighter block mb-1 group-hover:text-[var(--md-sys-color-primary)] transition-colors">Clonar Plantilla</span>
                        <p className="text-[10px] text-[var(--md-sys-color-on-surface-variant)] font-bold uppercase tracking-tight">Crea una instancia idéntica para ajustes paralelos.</p>
                    </button>
                )}
            </div>
        </div>
    );
};

export default ExportSection;
