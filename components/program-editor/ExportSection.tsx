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
        <div className="space-y-4">
            <h3 className="text-xs font-black text-white uppercase tracking-widest flex items-center gap-2">
                <ActivityIcon size={14} className="text-zinc-400" /> Exportar y Compartir
            </h3>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <button
                    onClick={handleExportJSON}
                    className="bg-zinc-950 border border-white/5 rounded-xl p-4 text-left hover:border-white/20 transition-colors group"
                >
                    <span className="text-[10px] font-black text-white block mb-1 group-hover:text-blue-400 transition-colors">Exportar JSON</span>
                    <p className="text-[8px] text-zinc-500">Descarga el programa completo como archivo JSON.</p>
                </button>

                <button
                    onClick={handleCopyToClipboard}
                    className="bg-zinc-950 border border-white/5 rounded-xl p-4 text-left hover:border-white/20 transition-colors group"
                >
                    <span className="text-[10px] font-black text-white block mb-1 group-hover:text-blue-400 transition-colors">Copiar al Portapapeles</span>
                    <p className="text-[8px] text-zinc-500">Copia la estructura del programa.</p>
                </button>

                {onDuplicate && (
                    <button
                        onClick={onDuplicate}
                        className="bg-zinc-950 border border-white/5 rounded-xl p-4 text-left hover:border-white/20 transition-colors group"
                    >
                        <span className="text-[10px] font-black text-white block mb-1 group-hover:text-blue-400 transition-colors">Duplicar Programa</span>
                        <p className="text-[8px] text-zinc-500">Crea una copia completa con un nuevo nombre.</p>
                    </button>
                )}

                <div className="bg-zinc-950 border border-white/5 rounded-xl p-4 opacity-50">
                    <span className="text-[10px] font-black text-white block mb-1">Compartir Link</span>
                    <p className="text-[8px] text-zinc-500">Próximamente: genera un link para compartir.</p>
                </div>
            </div>
        </div>
    );
};

export default ExportSection;
