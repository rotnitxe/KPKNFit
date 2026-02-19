// components/CreatePostModal.tsx
import React, { useState } from 'react';
import { WorkoutLog } from '../types';
import { useAppState } from '../contexts/AppContext';
import { takePicture } from '../services/cameraService';
import { optimizeImage } from '../services/imageService';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { CameraIcon, SaveIcon, TrashIcon } from './icons';

interface CreatePostModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (log: WorkoutLog) => void;
}

const CreatePostModal: React.FC<CreatePostModalProps> = ({ isOpen, onClose, onSave }) => {
  const { history } = useAppState();
  const [title, setTitle] = useState('');
  const [summary, setSummary] = useState('');
  const [photo, setPhoto] = useState<string | null>(null);
  const [linkedLogId, setLinkedLogId] = useState<string | null>(null);

  const handleSave = () => {
    const baseLog = linkedLogId ? history.find(h => h.id === linkedLogId) : null;

    const newPost: WorkoutLog = {
        ...(baseLog || {
            id: crypto.randomUUID(),
            programId: 'custom',
            programName: 'Publicación Personalizada',
            sessionId: 'custom',
            sessionName: 'Entrada del Diario',
            completedExercises: [],
            fatigueLevel: 5,
            mentalClarity: 5,
        }),
        date: new Date().toISOString(),
        postTitle: title.trim() ? title.trim() : (baseLog?.sessionName || "Nueva Publicación"),
        postSummary: summary.trim(),
        postPhotos: photo ? [photo] : [],
        isCustomPost: true,
        photo: photo || undefined,
    };

    onSave(newPost);
    onClose();
  };
  
  const handleTakePhoto = async () => {
      const imageDataUrl = await takePicture();
      if(imageDataUrl) {
          const optimized = await optimizeImage(imageDataUrl);
          setPhoto(optimized);
      }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Crear Nueva Publicación">
        <div className="space-y-4">
            <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Título</label>
                <input type="text" value={title} onChange={e => setTitle(e.target.value)} className="w-full" placeholder="Título de tu publicación..."/>
            </div>
            <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Resumen / Notas</label>
                <textarea value={summary} onChange={e => setSummary(e.target.value)} rows={5} className="w-full" placeholder="Escribe sobre tu entrenamiento, tus pensamientos, etc..."/>
            </div>
            <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Vincular Entrenamiento (Opcional)</label>
                <select value={linkedLogId || ''} onChange={e => setLinkedLogId(e.target.value || null)} className="w-full">
                    <option value="">-- Ninguno --</option>
                    {history.slice().sort((a,b) => new Date(b.date).getTime() - new Date(a.date).getTime()).map(log => (
                        <option key={log.id} value={log.id}>
                            {new Date(log.date).toLocaleDateString()} - {log.sessionName}
                        </option>
                    ))}
                </select>
            </div>
             <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Foto</label>
                 {photo ? (
                    <div className="relative">
                        <img src={photo} alt="Foto de la publicación" className="w-full rounded-lg" />
                        <button onClick={() => setPhoto(null)} className="absolute top-2 right-2 bg-black/50 p-1.5 rounded-full text-white"><TrashIcon size={18} /></button>
                    </div>
                 ) : (
                    <Button onClick={handleTakePhoto} variant="secondary" className="w-full"><CameraIcon size={16} /> Añadir Foto</Button>
                 )}
            </div>
            <div className="flex justify-end gap-2 pt-4 border-t border-slate-700">
                <Button variant="secondary" onClick={onClose}>Cancelar</Button>
                <Button onClick={handleSave}><SaveIcon /> Publicar</Button>
            </div>
        </div>
    </Modal>
  );
};

export default CreatePostModal;