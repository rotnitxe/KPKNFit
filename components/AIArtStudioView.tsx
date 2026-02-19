// components/AIArtStudioView.tsx
import React, { useState, useRef } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { generateImage, editImageWithText } from '../services/aiService';
import { ArrowLeftIcon, SparklesIcon, ImageIcon, UploadIcon, PencilIcon } from './icons';
import Button from './ui/Button';
import Card from './ui/Card';
import SkeletonLoader from './ui/SkeletonLoader';
import { optimizeImage } from '../services/imageService';

const AIArtStudioView: React.FC = () => {
    const { settings, isOnline } = useAppState();
    const { handleBack, addToast } = useAppDispatch();
    const [activeTab, setActiveTab] = useState<'generate' | 'edit'>('generate');
    
    // Generation State
    const [genPrompt, setGenPrompt] = useState('');
    const [aspectRatio, setAspectRatio] = useState('1:1');
    const [generatedImage, setGeneratedImage] = useState<string | null>(null);
    const [isGenerating, setIsGenerating] = useState(false);

    // Editing State
    const [editPrompt, setEditPrompt] = useState('');
    const [originalImage, setOriginalImage] = useState<string | null>(null);
    const [editedImage, setEditedImage] = useState<string | null>(null);
    const [isEditing, setIsEditing] = useState(false);
    const editFileInputRef = useRef<HTMLInputElement>(null);
    
    const handleGenerate = async () => {
        if (!genPrompt.trim() || !isOnline) return;
        setIsGenerating(true);
        setGeneratedImage(null);
        try {
            const result = await generateImage(genPrompt, aspectRatio, settings);
            setGeneratedImage(result);
        } catch (e: any) {
            addToast(e.message || "Error al generar imagen.", "danger");
        } finally {
            setIsGenerating(false);
        }
    };

    const handleUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (file) {
            const reader = new FileReader();
            reader.onload = async (event) => {
                addToast("Optimizando imagen...", "suggestion");
                const optimized = await optimizeImage(event.target?.result as string);
                setOriginalImage(optimized);
                setEditedImage(null); // Clear previous edit
            };
            reader.readAsDataURL(file);
        }
    };

    const handleEdit = async () => {
        if (!editPrompt.trim() || !originalImage || !isOnline) return;
        setIsEditing(true);
        setEditedImage(null);
        try {
            const base64Data = originalImage.split(',')[1];
            const result = await editImageWithText(base64Data, editPrompt, settings);
            setEditedImage(result);
        } catch (e: any) {
            addToast(e.message || "Error al editar la imagen.", "danger");
        } finally {
            setIsEditing(false);
        }
    };
    
    return (
        <div className="pb-20 animate-fade-in">
            <header className="flex items-center gap-4 mb-6 -mx-4 px-4">
                <button onClick={handleBack} className="p-2 text-slate-300">
                    <ArrowLeftIcon />
                </button>
                <div>
                    <h1 className="text-3xl font-bold text-white">AI Art Studio</h1>
                    <p className="text-slate-400 text-sm">Crea y edita imágenes con IA.</p>
                </div>
            </header>
            
            <div className="space-y-6">
                <div className="flex bg-panel-color-light p-1 rounded-full">
                    <button onClick={() => setActiveTab('generate')} className={`w-full py-1.5 rounded-full text-sm font-semibold flex items-center justify-center gap-2 ${activeTab === 'generate' ? 'bg-primary-color text-white' : 'text-slate-300'}`}><SparklesIcon size={16}/> Generar</button>
                    <button onClick={() => setActiveTab('edit')} className={`w-full py-1.5 rounded-full text-sm font-semibold flex items-center justify-center gap-2 ${activeTab === 'edit' ? 'bg-primary-color text-white' : 'text-slate-300'}`}><PencilIcon size={16}/> Editar</button>
                </div>
                
                {activeTab === 'generate' && (
                    <Card>
                        <div className="space-y-4">
                            <textarea value={genPrompt} onChange={e => setGenPrompt(e.target.value)} placeholder="Describe la imagen que quieres crear..." rows={3} className="w-full"/>
                            <select value={aspectRatio} onChange={e => setAspectRatio(e.target.value)} className="w-full">
                                <option value="1:1">Cuadrado (1:1)</option>
                                <option value="16:9">Horizontal (16:9)</option>
                                <option value="9:16">Vertical (9:16)</option>
                                <option value="4:3">Paisaje (4:3)</option>
                                <option value="3:4">Retrato (3:4)</option>
                            </select>
                            <Button onClick={handleGenerate} isLoading={isGenerating} disabled={!genPrompt.trim() || !isOnline} className="w-full">
                                <SparklesIcon /> Generar Imagen
                            </Button>
                        </div>
                        {isGenerating && <div className="mt-4 aspect-square bg-slate-700/50 rounded-lg animate-pulse" />}
                        {generatedImage && (
                            <div className="mt-4">
                                <img src={generatedImage} alt="Generated art" className="w-full rounded-lg" />
                            </div>
                        )}
                    </Card>
                )}
                
                {activeTab === 'edit' && (
                    <Card>
                        <div className="space-y-4">
                            <input type="file" ref={editFileInputRef} onChange={handleUpload} accept="image/*" className="hidden"/>
                            <Button onClick={() => editFileInputRef.current?.click()} variant="secondary" className="w-full">
                                <UploadIcon /> {originalImage ? 'Cambiar Imagen' : 'Subir Imagen'}
                            </Button>

                            {originalImage && (
                                <div className="space-y-4">
                                    <textarea value={editPrompt} onChange={e => setEditPrompt(e.target.value)} placeholder="Describe la edición... (ej: 'añade un filtro retro', 'quita a la persona del fondo')" rows={2} className="w-full"/>
                                    <Button onClick={handleEdit} isLoading={isEditing} disabled={!editPrompt.trim() || !isOnline} className="w-full">
                                        <PencilIcon /> Editar con IA
                                    </Button>
                                </div>
                            )}

                             <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-4">
                                {originalImage && (
                                    <div>
                                        <h4 className="font-semibold text-sm mb-1">Original</h4>
                                        <img src={originalImage} alt="Original" className="w-full rounded-lg" />
                                    </div>
                                )}
                                {isEditing ? (
                                     <div>
                                        <h4 className="font-semibold text-sm mb-1">Editando...</h4>
                                        <div className="aspect-square bg-slate-700/50 rounded-lg animate-pulse" />
                                    </div>
                                ) : editedImage && (
                                    <div>
                                        <h4 className="font-semibold text-sm mb-1">Editada</h4>
                                        <img src={editedImage} alt="Edited" className="w-full rounded-lg" />
                                    </div>
                                )}
                            </div>
                        </div>
                    </Card>
                )}
            </div>
        </div>
    );
};

export default AIArtStudioView;