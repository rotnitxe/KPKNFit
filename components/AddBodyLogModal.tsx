// components/AddBodyLogModal.tsx
import React, { useState } from 'react';
import { BodyProgressLog, Settings } from '../types';
import { takePicture } from '../services/cameraService';
import { optimizeImage } from '../services/imageService';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { SaveIcon, CameraIcon, TrashIcon, SparklesIcon } from './icons';
import { useAppDispatch } from '../contexts/AppContext';
import { analyzeProgressPhoto } from '../services/aiService';
import SkeletonLoader from './ui/SkeletonLoader';

interface AddBodyLogModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (log: BodyProgressLog) => void;
  settings: Settings;
  isOnline: boolean;
}

const PREDEFINED_MEASUREMENTS = ["Pecho", "Cintura", "Cadera", "Bíceps (Izq)", "Bíceps (Der)", "Muslo (Izq)", "Muslo (Der)"];

const AddBodyLogModal: React.FC<AddBodyLogModalProps> = ({ isOpen, onClose, onSave, settings, isOnline }) => {
  const { addToast } = useAppDispatch();
  const [weight, setWeight] = useState('');
  const [bodyFat, setBodyFat] = useState('');
  const [photos, setPhotos] = useState<string[]>([]);
  const [measurements, setMeasurements] = useState<{ [key: string]: number }>({});
  const [aiInsight, setAiInsight] = useState<string | undefined>(undefined);
  const [isAnalyzing, setIsAnalyzing] = useState(false);

  const handleTakePhoto = async () => {
    if (photos.length >= 4) {
      addToast("Puedes añadir un máximo de 4 fotos por registro.", "suggestion");
      return;
    }
    const photoDataUrl = await takePicture();
    if (photoDataUrl) {
      addToast("Optimizando y analizando foto...", "suggestion");
      setIsAnalyzing(true);
      const optimizedPhoto = await optimizeImage(photoDataUrl);
      setPhotos(prev => [...prev, optimizedPhoto]);

      if (isOnline) {
        try {
          const insight = await analyzeProgressPhoto(optimizedPhoto, settings);
          setAiInsight(prev => (prev ? prev + '\n\n' : '') + `Análisis Foto #${photos.length + 1}:\n${insight}`);
          addToast("Análisis de IA completado.", "success");
        } catch (e) {
            addToast("No se pudo analizar la foto con IA.", "danger");
        }
      }
      setIsAnalyzing(false);
    }
  };

  const handleRemovePhoto = (indexToRemove: number) => {
    setPhotos(prev => prev.filter((_, index) => index !== indexToRemove));
  };

  const handleMeasurementChange = (key: string, value: string) => {
    const numValue = parseFloat(value);
    setMeasurements(prev => ({
      ...prev,
      [key]: isNaN(numValue) ? 0 : numValue,
    }));
  };

  const handleSave = () => {
    if (!weight) {
      addToast("El peso es un campo obligatorio.", "danger");
      return;
    }
    const finalMeasurements = Object.entries(measurements).reduce((acc, [key, value]) => {
        if (typeof value === 'number' && value > 0) {
            acc[key] = value;
        }
        return acc;
    }, {} as Record<string, number>);

    const newLog: BodyProgressLog = {
      id: crypto.randomUUID(),
      date: new Date().toISOString(),
      weight: parseFloat(weight),
      bodyFatPercentage: bodyFat ? parseFloat(bodyFat) : undefined,
      photos: photos.length > 0 ? photos : undefined,
      measurements: Object.keys(finalMeasurements).length > 0 ? finalMeasurements : undefined,
      aiInsight: aiInsight
    };

    onSave(newLog);
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Añadir Registro Corporal">
      <div className="space-y-4 max-h-[70vh] overflow-y-auto p-1">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1">Peso ({settings.weightUnit}) *</label>
            <input
              type="number"
              step="0.1"
              value={weight}
              onChange={e => setWeight(e.target.value)}
              className="w-full"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1">% Grasa Corporal</label>
            <input
              type="number"
              step="0.1"
              value={bodyFat}
              onChange={e => setBodyFat(e.target.value)}
              className="w-full"
            />
          </div>
        </div>
        
        <div>
            <label className="block text-sm font-medium text-slate-300 mb-2">Fotos de Progreso</label>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {photos.map((photo, index) => (
                    <div key={index} className="relative aspect-square group">
                        <img src={photo} alt={`Progreso ${index + 1}`} className="w-full h-full object-cover rounded-md" />
                        <button onClick={() => handleRemovePhoto(index)} className="absolute top-1 right-1 bg-black/50 p-1 rounded-full text-white opacity-0 group-hover:opacity-100 transition-opacity">
                            <TrashIcon size={14} />
                        </button>
                    </div>
                ))}
                {photos.length < 4 && (
                    <button onClick={handleTakePhoto} disabled={isAnalyzing} className="aspect-square flex flex-col items-center justify-center bg-slate-800/50 border-2 border-dashed border-slate-600 rounded-md hover:border-slate-500 hover:bg-slate-800 transition-colors disabled:opacity-50">
                        {isAnalyzing ? <SkeletonLoader type="circle" className="w-8 h-8"/> : <CameraIcon className="text-slate-400 mb-1" />}
                        <span className="text-xs text-slate-400">{isAnalyzing ? 'Analizando...' : 'Añadir Foto'}</span>
                    </button>
                )}
            </div>
            {aiInsight && (
                <div className="mt-4 p-3 bg-slate-900/50 rounded-lg">
                    <h4 className="text-sm font-semibold text-sky-300 flex items-center gap-1 mb-1"><SparklesIcon size={14}/> Análisis de IA</h4>
                    <p className="text-xs text-slate-300 whitespace-pre-wrap">{aiInsight}</p>
                </div>
            )}
        </div>

        <div>
            <label className="block text-sm font-medium text-slate-300 mb-2">Medidas (cm)</label>
            <div className="grid grid-cols-2 gap-x-4 gap-y-2">
                {PREDEFINED_MEASUREMENTS.map(name => (
                    <div key={name}>
                         <label className="block text-xs font-medium text-slate-400 mb-1">{name}</label>
                         <input
                            type="number"
                            step="0.1"
                            value={measurements[name] || ''}
                            onChange={e => handleMeasurementChange(name, e.target.value)}
                            className="w-full"
                         />
                    </div>
                ))}
            </div>
        </div>

        <div className="flex justify-end gap-4 pt-4 border-t border-slate-700">
          <Button onClick={onClose} variant="secondary">Cancelar</Button>
          <Button onClick={handleSave}><SaveIcon size={16}/> Guardar Registro</Button>
        </div>
      </div>
    </Modal>
  );
};

export default AddBodyLogModal;