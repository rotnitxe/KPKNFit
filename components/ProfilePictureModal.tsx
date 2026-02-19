
// components/ProfilePictureModal.tsx
import React, { useState, useRef } from 'react';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { CameraIcon, UploadIcon, SparklesIcon, TrashIcon } from './icons';
import { takePicture } from '../services/cameraService';
import { optimizeImage } from '../services/imageService';
import { generateImage } from '../services/aiService';
import { useAppState, useAppDispatch } from '../contexts/AppContext';

interface ProfilePictureModalProps {
    isOpen: boolean;
    onClose: () => void;
    currentPicture?: string;
    onSave: (newPicture: string | undefined) => void;
}

const ProfilePictureModal: React.FC<ProfilePictureModalProps> = ({ isOpen, onClose, currentPicture, onSave }) => {
    const { settings, isOnline } = useAppState();
    const { addToast } = useAppDispatch();
    
    const [preview, setPreview] = useState<string | undefined>(currentPicture);
    const [aiPrompt, setAiPrompt] = useState('');
    const [isGenerating, setIsGenerating] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const handleCamera = async () => {
        const photoUri = await takePicture();
        if (photoUri) {
            const optimized = await optimizeImage(photoUri);
            setPreview(optimized);
        }
    };

    const handleUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (file) {
            const reader = new FileReader();
            reader.onloadend = async () => {
                const optimized = await optimizeImage(reader.result as string);
                setPreview(optimized);
            };
            reader.readAsDataURL(file);
        }
    };

    const handleAIGenerate = async () => {
        if (!aiPrompt.trim() || !isOnline) return;
        setIsGenerating(true);
        try {
            const fullPrompt = `Anime style portrait, fitness theme, ${aiPrompt}, masterpiece, best quality, vivid colors, 8k resolution`;
            const imageUrl = await generateImage(fullPrompt, '1:1', settings);
            const optimized = await optimizeImage(imageUrl);
            setPreview(optimized);
            addToast("Avatar generado con éxito.", "success");
        } catch (error: any) {
            addToast("Error al generar imagen IA.", "danger");
        } finally {
            setIsGenerating(false);
        }
    };

    const handleRemove = () => {
        setPreview(undefined);
    };

    const handleSave = () => {
        onSave(preview);
        onClose();
    };

    return (
        <Modal isOpen={isOpen} onClose={onClose} title="Foto de Perfil">
            <div className="space-y-6 p-2">
                
                {/* Preview Circle */}
                <div className="flex justify-center">
                    <div className="relative w-32 h-32 rounded-full border-4 border-slate-700 overflow-hidden bg-slate-800">
                        {preview ? (
                            <img src={preview} alt="Avatar Preview" className="w-full h-full object-cover" />
                        ) : (
                            <div className="w-full h-full flex items-center justify-center text-slate-500">
                                <span className="text-4xl">?</span>
                            </div>
                        )}
                        {preview && (
                            <button onClick={handleRemove} className="absolute bottom-2 right-2 p-1 bg-red-600 rounded-full text-white shadow-lg">
                                <TrashIcon size={12}/>
                            </button>
                        )}
                    </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                    <Button onClick={handleCamera} variant="secondary" className="!py-3 !text-xs">
                        <CameraIcon size={16}/> Cámara
                    </Button>
                    <div className="relative">
                         <input type="file" ref={fileInputRef} onChange={handleUpload} accept="image/*" className="hidden" />
                        <Button onClick={() => fileInputRef.current?.click()} variant="secondary" className="w-full !py-3 !text-xs">
                            <UploadIcon size={16}/> Galería
                        </Button>
                    </div>
                </div>

                <div className="bg-gradient-to-br from-purple-900/30 to-blue-900/30 p-3 rounded-xl border border-white/10">
                    <h4 className="text-xs font-bold text-white mb-2 flex items-center gap-2">
                        <SparklesIcon size={14} className="text-purple-400"/> Generador IA (Anime)
                    </h4>
                    <div className="flex gap-2">
                        <input 
                            type="text" 
                            value={aiPrompt} 
                            onChange={e => setAiPrompt(e.target.value)} 
                            placeholder="Ej: Guerrero espartano, pelo azul..."
                            className="flex-grow bg-slate-900/80 border border-slate-700 rounded-lg px-3 py-2 text-xs text-white placeholder-slate-500 focus:border-purple-500 outline-none"
                        />
                        <Button 
                            onClick={handleAIGenerate} 
                            isLoading={isGenerating} 
                            disabled={!isOnline || !aiPrompt.trim()} 
                            className="!px-3 !py-2 !text-xs bg-purple-600 hover:bg-purple-500 border-none"
                        >
                            Generar
                        </Button>
                    </div>
                </div>

                <div className="pt-4 border-t border-slate-700 flex justify-end gap-2">
                    <Button onClick={onClose} variant="secondary">Cancelar</Button>
                    <Button onClick={handleSave}>Guardar Perfil</Button>
                </div>
            </div>
        </Modal>
    );
};

export default ProfilePictureModal;
