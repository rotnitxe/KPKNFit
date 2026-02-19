// components/FeedCustomizationSheet.tsx
import React, { useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { XIcon, CheckIcon } from './icons';
import Button from './ui/Button';

interface FeedCustomizationSheetProps {
  isOpen: boolean;
  onClose: () => void;
}

const PRESET_BG_STYLES = [
    { name: 'Nebulosa', value: 'mesh-gradient-1' },
    { name: 'Aurora', value: 'mesh-gradient-2' },
    { name: 'Amanecer', value: 'mesh-gradient-3' },
    { name: 'Oceano', value: 'mesh-gradient-4' },
    { name: 'Default', value: 'default' },
];

const PRESET_CARD_COLORS = [
    { name: 'Transparente', value: 'rgba(18, 18, 20, 0.4)' },
    { name: 'Azul translúcido', value: 'rgba(30, 58, 138, 0.5)' },
    { name: 'Morado translúcido', value: 'rgba(76, 29, 149, 0.5)' },
    { name: 'Gris Oscuro', value: 'rgba(23, 25, 38, 0.65)'},
];

export const FeedCustomizationSheet: React.FC<FeedCustomizationSheetProps> = ({ isOpen, onClose }) => {
    const { settings } = useAppState();
    const { setSettings } = useAppDispatch();
    const [background, setBackground] = useState(settings.feedSettings?.background || 'default');
    const [cardColor, setCardColor] = useState(settings.feedSettings?.cardColor || 'rgba(18, 18, 20, 0.4)');

    const handleSave = () => {
        setSettings({
            feedSettings: {
                background,
                cardColor,
            }
        });
        onClose();
    };

    if (!isOpen) return null;

    return (
        <>
            <div className="bottom-sheet-backdrop" onClick={onClose} />
            <div className="bottom-sheet-content open !h-auto max-h-[90vh]">
                <div className="bottom-sheet-grabber" />
                 <header className="flex items-center justify-between p-4 flex-shrink-0 border-b border-border-color">
                    <h2 className="text-xl font-bold text-white">Personalizar Feed</h2>
                    <button onClick={onClose} className="p-2 text-slate-300"><XIcon /></button>
                </header>
                <div className="overflow-y-auto p-4 space-y-6">
                    <div>
                        <h3 className="text-lg font-semibold text-slate-200 mb-2">Estilo de Fondo</h3>
                        <div className="grid grid-cols-2 gap-3">
                            {PRESET_BG_STYLES.map(style => (
                                <button key={style.value} onClick={() => setBackground(style.value)} className={`relative p-4 rounded-lg h-20 flex items-end ${style.value === 'default' ? 'bg-slate-800' : style.value}`}>
                                    {background === style.value && <div className="absolute inset-0 bg-black/30 ring-2 ring-primary-color rounded-lg" />}
                                    <span className="relative text-white font-semibold text-sm">{style.name}</span>
                                </button>
                            ))}
                        </div>
                    </div>
                    <div>
                        <h3 className="text-lg font-semibold text-slate-200 mb-2">Color de Tarjetas</h3>
                        <div className="grid grid-cols-2 gap-3">
                           {PRESET_CARD_COLORS.map(color => (
                                <button key={color.value} onClick={() => setCardColor(color.value)} style={{ backgroundColor: color.value }} className="relative p-4 rounded-lg h-20 flex items-end border border-white/10">
                                     {cardColor === color.value && <div className="absolute inset-0 bg-black/30 ring-2 ring-primary-color rounded-lg" />}
                                    <span className="relative text-white font-semibold text-sm">{color.name}</span>
                                </button>
                            ))}
                        </div>
                    </div>
                </div>
                <footer className="p-4 bg-slate-900 border-t border-slate-700/50 flex-shrink-0">
                    <Button onClick={handleSave} className="w-full !py-3">
                        <CheckIcon/> Guardar Cambios
                    </Button>
                </footer>
            </div>
        </>
    );
};