
// components/SessionBackgroundModal.tsx
import React, { useState, useRef } from 'react';
import { SessionBackground, CoverStyle } from '../types';
import { optimizeImage } from '../services/imageService';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { UploadIcon, SearchIcon, ImageIcon, CheckCircleIcon, Wand2Icon } from './icons';
import { useAppState } from '../contexts/AppContext';
import ImageSearchModal from './ImageSearchModal';

interface BackgroundEditorModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (background?: SessionBackground, coverStyle?: CoverStyle, coverImage?: string) => void;
  initialBackground?: SessionBackground;
  initialCoverImage?: string;
  initialCoverStyle?: CoverStyle;
  previewTitle: string;
  isOnline: boolean;
}

const PRESET_COLORS = [
  '#000000',
  '#1e293b', '#334155', '#475569', // Slate
  '#3f3f46', '#52525b', '#71717a', // Zinc
  '#b91c1c', '#dc2626', '#f87171', // Red
  '#c2410c', '#ea580c', '#fb923c', // Orange
  '#166534', '#16a34a', '#4ade80', // Green
  '#1d4ed8', '#2563eb', '#60a5fa', // Blue
  '#7e22ce', '#9333ea', '#c084fc', // Purple
  '#be185d', '#db2777', '#f472b6', // Pink
];

const BackgroundEditorModal: React.FC<BackgroundEditorModalProps> = ({ 
    isOpen, onClose, onSave, 
    initialBackground, initialCoverImage, initialCoverStyle, 
    previewTitle, isOnline 
}) => {
  const { settings } = useAppState();
  
  // --- STATE ---
  const [editTab, setEditTab] = useState<'cover' | 'background'>('cover');

  // Cover State
  const [currentCoverImage, setCurrentCoverImage] = useState<string | undefined>(initialCoverImage || (initialBackground?.type === 'image' ? initialBackground.value : undefined));
  const [coverStyle, setCoverStyle] = useState<CoverStyle>(initialCoverStyle || {
      filters: { contrast: 100, saturation: 100, brightness: 100, grayscale: 0, sepia: 0, vignette: 0 },
      enableMotion: false
  });

  // Background State
  const [useCoverAsBackground, setUseCoverAsBackground] = useState(true);
  const [backgroundConfig, setBackgroundConfig] = useState<SessionBackground | undefined>(initialBackground);

  // Aux State
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isImageSearchOpen, setIsImageSearchOpen] = useState(false);

  // --- HANDLERS ---
  
  const handleSetNewImage = (url: string) => {
      setCurrentCoverImage(url);
      setCoverStyle(prev => ({ ...prev, filters: { ...prev.filters!, contrast: 100, saturation: 100, brightness: 100, grayscale: 0, sepia: 0 } })); // Reset filters
      // Auto-set background
      setBackgroundConfig({ type: 'image', value: url, style: { blur: 16, brightness: 0.4 } });
      setUseCoverAsBackground(true);
      setEditTab('cover');
  };

  const handleUpdateCoverFilter = (filter: keyof CoverStyle['filters'], value: number) => {
      setCoverStyle(prev => ({
          ...prev,
          filters: { ...prev.filters!, [filter]: value }
      }));
  };

  const handleUpdateBackgroundStyle = (style: Partial<SessionBackground['style']>) => {
    setBackgroundConfig(prev => {
        if (!prev || prev.type !== 'image') return prev;
        return { ...prev, style: { ...(prev.style || { blur: 10, brightness: 0.5 }), ...style }};
    });
  };

  const handleUploadImage = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = async () => {
        const optimizedUrl = await optimizeImage(reader.result as string);
        handleSetNewImage(optimizedUrl);
      };
      reader.readAsDataURL(file);
    }
  };

  const handleSave = () => {
      // Determine Final Background
      let finalBackground = backgroundConfig;
      if (useCoverAsBackground && currentCoverImage) {
           finalBackground = {
               type: 'image',
               value: currentCoverImage,
               style: backgroundConfig?.style || { blur: 16, brightness: 0.4 }
           };
      }
      
      onSave(finalBackground, coverStyle, currentCoverImage);
      onClose();
  };

  // CSS Filter String Generator
  const getFilterString = () => {
      const f = coverStyle.filters!;
      return `contrast(${f.contrast}%) saturate(${f.saturation}%) brightness(${f.brightness}%) grayscale(${f.grayscale}%) sepia(${f.sepia}%)`;
  };

  return (
    <>
      <ImageSearchModal
        isOpen={isImageSearchOpen}
        onClose={() => setIsImageSearchOpen(false)}
        onSelectImage={(url) => { handleSetNewImage(url); setIsImageSearchOpen(false); }}
        initialQuery={previewTitle}
      />
      
      <Modal isOpen={isOpen} onClose={onClose} title="Prime Art Studio" className="!max-w-2xl !bg-slate-950">
        <div className="flex flex-col h-[75vh]">
            
            {/* --- PREVIEW AREA (Top) --- */}
            <div className="flex-shrink-0 relative w-full aspect-video bg-black rounded-xl overflow-hidden shadow-2xl mb-4 border border-white/10 group">
                {currentCoverImage ? (
                    <>
                        <div 
                            className={`absolute inset-0 bg-cover bg-center transition-all duration-300 ${coverStyle.enableMotion ? 'animate-breathing-zoom' : ''}`}
                            style={{ 
                                backgroundImage: `url(${currentCoverImage})`,
                                filter: getFilterString()
                            }}
                        />
                        {/* Vignette Overlay */}
                        <div className="absolute inset-0 pointer-events-none" style={{ background: `radial-gradient(circle, transparent 50%, rgba(0,0,0,${(coverStyle.filters?.vignette || 0)/100}) 100%)` }}></div>
                        
                        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-black/20 pointer-events-none"></div>
                        
                        <div className="absolute bottom-4 left-4 z-10">
                            <h3 className="text-2xl font-black text-white uppercase tracking-tighter drop-shadow-lg">{previewTitle}</h3>
                        </div>
                    </>
                ) : (
                    <div className="w-full h-full flex flex-col items-center justify-center text-slate-600 bg-slate-900">
                        <ImageIcon size={48} className="mb-2"/>
                        <p className="text-xs uppercase font-bold tracking-widest">Sin Portada</p>
                    </div>
                )}
            </div>

            {/* --- CONTROLS AREA (Bottom) --- */}
            <div className="flex-grow flex flex-col min-h-0 bg-slate-900/50 rounded-xl border border-white/5 overflow-hidden">
                
                {/* Mode Tabs */}
                <div className="flex border-b border-white/5">
                    <button onClick={() => setEditTab('cover')} className={`flex-1 py-3 text-xs font-black uppercase tracking-widest transition-colors ${editTab === 'cover' ? 'bg-white/5 text-white border-b-2 border-primary-color' : 'text-slate-500 hover:text-slate-300'}`}>
                        Editor de Portada
                    </button>
                    <button onClick={() => setEditTab('background')} className={`flex-1 py-3 text-xs font-black uppercase tracking-widest transition-colors ${editTab === 'background' ? 'bg-white/5 text-white border-b-2 border-primary-color' : 'text-slate-500 hover:text-slate-300'}`}>
                        Editor de Fondo
                    </button>
                </div>

                <div className="flex-grow overflow-y-auto p-4 custom-scrollbar">
                    
                    {/* --- COVER EDITOR --- */}
                    {editTab === 'cover' && (
                        <div className="space-y-6">
                            {/* Source Selection Mini-Bar */}
                            <div className="flex gap-2 justify-center mb-6">
                                <Button variant="secondary" onClick={() => fileInputRef.current?.click()} className="!py-2 !px-3 !text-[10px] uppercase font-bold"><UploadIcon size={14} className="mr-1"/> Subir</Button>
                                <input type="file" accept="image/*" ref={fileInputRef} className="hidden" onChange={handleUploadImage} />
                                
                                <Button variant="secondary" onClick={() => setIsImageSearchOpen(true)} className="!py-2 !px-3 !text-[10px] uppercase font-bold" disabled={!isOnline}><SearchIcon size={14} className="mr-1"/> Buscar</Button>
                            </div>

                            {/* Filters */}
                            <div className="space-y-4">
                                <div className="flex justify-between items-center">
                                    <span className="text-xs font-bold text-slate-300 uppercase">Efecto de Movimiento</span>
                                    <button 
                                        onClick={() => setCoverStyle(p => ({...p, enableMotion: !p.enableMotion}))} 
                                        className={`w-10 h-5 rounded-full relative transition-colors ${coverStyle.enableMotion ? 'bg-green-500' : 'bg-slate-700'}`}
                                    >
                                        <div className={`w-3 h-3 rounded-full bg-white absolute top-1 transition-transform ${coverStyle.enableMotion ? 'left-6' : 'left-1'}`}/>
                                    </button>
                                </div>

                                <div className="space-y-2">
                                    <div className="flex justify-between text-[10px] font-bold text-slate-500 uppercase"><span>Contraste</span><span>{coverStyle.filters?.contrast}%</span></div>
                                    <input type="range" min="50" max="150" value={coverStyle.filters?.contrast} onChange={e => handleUpdateCoverFilter('contrast', parseInt(e.target.value))} className="w-full h-1 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-white"/>
                                </div>
                                <div className="space-y-2">
                                    <div className="flex justify-between text-[10px] font-bold text-slate-500 uppercase"><span>Saturación</span><span>{coverStyle.filters?.saturation}%</span></div>
                                    <input type="range" min="0" max="200" value={coverStyle.filters?.saturation} onChange={e => handleUpdateCoverFilter('saturation', parseInt(e.target.value))} className="w-full h-1 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-white"/>
                                </div>
                                <div className="space-y-2">
                                    <div className="flex justify-between text-[10px] font-bold text-slate-500 uppercase"><span>Viñeta (Oscuridad Bordes)</span><span>{coverStyle.filters?.vignette}%</span></div>
                                    <input type="range" min="0" max="100" value={coverStyle.filters?.vignette} onChange={e => handleUpdateCoverFilter('vignette', parseInt(e.target.value))} className="w-full h-1 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-white"/>
                                </div>
                                 <div className="space-y-2">
                                    <div className="flex justify-between text-[10px] font-bold text-slate-500 uppercase"><span>Filtro B/N</span><span>{coverStyle.filters?.grayscale}%</span></div>
                                    <input type="range" min="0" max="100" value={coverStyle.filters?.grayscale} onChange={e => handleUpdateCoverFilter('grayscale', parseInt(e.target.value))} className="w-full h-1 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-white"/>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* --- BACKGROUND EDITOR --- */}
                    {editTab === 'background' && (
                        <div className="space-y-6">
                            <div className="flex items-center justify-between p-3 bg-white/5 rounded-xl border border-white/5">
                                <div className="flex items-center gap-3">
                                    <Wand2Icon size={20} className="text-primary-color"/>
                                    <div>
                                        <p className="text-sm font-bold text-white">Sincronizar con Portada</p>
                                        <p className="text-[10px] text-slate-400">Usa la misma imagen desenfocada</p>
                                    </div>
                                </div>
                                <button 
                                    onClick={() => setUseCoverAsBackground(!useCoverAsBackground)} 
                                    className={`w-10 h-5 rounded-full relative transition-colors ${useCoverAsBackground ? 'bg-primary-color' : 'bg-slate-700'}`}
                                >
                                    <div className={`w-3 h-3 rounded-full bg-white absolute top-1 transition-transform ${useCoverAsBackground ? 'left-6' : 'left-1'}`}/>
                                </button>
                            </div>

                            {useCoverAsBackground && (
                                <div className="space-y-4 pt-2">
                                    <div className="space-y-2">
                                        <div className="flex justify-between text-[10px] font-bold text-slate-500 uppercase"><span>Desenfoque (Blur)</span><span>{backgroundConfig?.style?.blur || 0}px</span></div>
                                        <input type="range" min="0" max="40" value={backgroundConfig?.style?.blur || 0} onChange={e => handleUpdateBackgroundStyle({ blur: parseInt(e.target.value) })} className="w-full h-1 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-white"/>
                                    </div>
                                    <div className="space-y-2">
                                        <div className="flex justify-between text-[10px] font-bold text-slate-500 uppercase"><span>Oscuridad</span><span>{Math.round((1 - (backgroundConfig?.style?.brightness || 1)) * 100)}%</span></div>
                                        <input type="range" min="0.1" max="1" step="0.05" value={backgroundConfig?.style?.brightness || 0.5} onChange={e => handleUpdateBackgroundStyle({ brightness: parseFloat(e.target.value) })} className="w-full h-1 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-white"/>
                                    </div>
                                </div>
                            )}

                            {!useCoverAsBackground && (
                                <div className="space-y-3">
                                    <p className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-2">Colores Sólidos</p>
                                    <div className="grid grid-cols-6 gap-2">
                                        {PRESET_COLORS.map(color => (
                                            <button 
                                                key={color} 
                                                onClick={() => setBackgroundConfig({ type: 'color', value: color })} 
                                                style={{backgroundColor: color}} 
                                                className={`w-full h-10 rounded-lg border-2 transition-transform hover:scale-110 ${backgroundConfig?.value === color ? 'border-white scale-110 shadow-lg' : 'border-transparent'}`}
                                            />
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    )}

                </div>
            </div>

            {/* --- ACTION FOOTER --- */}
            <div className="mt-4 flex gap-3 pt-4 border-t border-white/10">
                 <Button onClick={onClose} variant="secondary" className="flex-1 !py-3 !text-xs font-bold uppercase">Cancelar</Button>
                 <Button onClick={handleSave} className="flex-[2] !py-3 !text-xs font-black uppercase shadow-xl shadow-primary-color/20"><CheckCircleIcon size={16} className="mr-2"/> Guardar Diseño</Button>
            </div>
        </div>
      </Modal>
    </>
  );
};

export default BackgroundEditorModal;
