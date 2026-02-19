// components/EditPostModal.tsx
import React, { useState, useEffect } from 'react';
import { WorkoutLog } from '../types';
import { takePicture } from '../services/cameraService';
import { optimizeImage } from '../services/imageService';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { CameraIcon, SaveIcon, TrashIcon } from './icons';

interface EditPostModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (updatedLog: WorkoutLog) => void;
  log: WorkoutLog;
}

const EditPostModal: React.FC<EditPostModalProps> = ({ isOpen, onClose, onSave, log }) => {
  const [title, setTitle] = useState('');
  const [summary, setSummary] = useState('');
  const [photos, setPhotos] = useState<string[]>([]);

  useEffect(() => {
    if (log) {
        setTitle(log.postTitle || log.sessionName);
        setSummary(log.postSummary || log.notes || '');
        setPhotos(log.postPhotos || (log.photo ? [log.photo] : []));
    }
  }, [log]);

  const handleSave = () => {
    const updatedLog: WorkoutLog = {
        ...log,
        postTitle: title,
        postSummary: summary,
        postPhotos: photos,
        photo: photos[0] || undefined,
    };
    onSave(updatedLog);
    onClose();
  };
  
  const handleTakePhoto = async () => {
    if (photos.length >= 4) {
      alert("Puedes añadir un máximo de 4 fotos.");
      return;
    }
      const imageDataUrl = await takePicture();
      if(imageDataUrl) {
          const optimized = await optimizeImage(imageDataUrl);
          setPhotos(prev => [...prev, optimized]);
      }
  };
  
  const handleRemovePhoto = (indexToRemove: number) => {
    setPhotos(prev => prev.filter((_, index) => index !== indexToRemove));
  };


  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Editar Publicación">
        <div className="space-y-4">
            <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Título</label>
                <input type="text" value={title} onChange={e => setTitle(e.target.value)} className="w-full"/>
            </div>
            <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Resumen / Notas</label>
                <textarea value={summary} onChange={e => setSummary(e.target.value)} rows={5} className="w-full"/>
            </div>
             <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Fotos</label>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 mb-2">
                    {photos.map((photo, index) => (
                        <div key={index} className="relative aspect-square group">
                            <img src={photo} alt={`Progreso ${index + 1}`} className="w-full h-full object-cover rounded-md" />
                            <button onClick={() => handleRemovePhoto(index)} className="absolute top-1 right-1 bg-black/50 p-1 rounded-full text-white opacity-0 group-hover:opacity-100 transition-opacity">
                                <TrashIcon size={14} />
                            </button>
                        </div>
                    ))}
                </div>
                {photos.length < 4 && (
                    <Button onClick={handleTakePhoto} variant="secondary" className="w-full"><CameraIcon size={16} /> Añadir Foto</Button>
                )}
            </div>
            <div className="flex justify-end gap-2 pt-4 border-t border-slate-700">
                <Button variant="secondary" onClick={onClose}>Cancelar</Button>
                <Button onClick={handleSave}><SaveIcon /> Guardar Cambios</Button>
            </div>
        </div>
    </Modal>
  );
};

export default EditPostModal;