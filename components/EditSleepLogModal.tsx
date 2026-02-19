
// components/EditSleepLogModal.tsx
import React, { useState, useEffect } from 'react';
import { SleepLog } from '../types';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { SaveIcon } from './icons';

interface EditSleepLogModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (log: SleepLog) => void;
  log: SleepLog | null;
}

const EditSleepLogModal: React.FC<EditSleepLogModalProps> = ({ isOpen, onClose, onSave, log }) => {
  const [duration, setDuration] = useState('');

  useEffect(() => {
    if (log) {
      setDuration(log.duration.toFixed(1));
    }
  }, [log]);

  const handleSave = () => {
    if (!log) return;
    const newDuration = parseFloat(duration);
    if (isNaN(newDuration) || newDuration <= 0) {
      alert("Por favor, introduce una duración válida.");
      return;
    }
    
    const updatedLog: SleepLog = {
      ...log,
      duration: newDuration,
      isAuto: false, // Mark as manually corrected
    };
    
    onSave(updatedLog);
  };

  if (!log) return null;

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={`Editar Sueño`} className="!bg-[#2d1622] border border-pink-900/50">
      <div className="space-y-6 p-2">
        <p className="text-sm text-pink-200/70 text-center">
          Ajusta el registro del {new Date(log.startTime).toLocaleDateString()}.
        </p>
        <div>
          <label className="block text-xs font-bold text-pink-400 uppercase tracking-widest mb-2 text-center">
            Horas Totales
          </label>
          <div className="flex justify-center">
               <input
                type="number"
                step="0.1"
                value={duration}
                onChange={e => setDuration(e.target.value)}
                className="w-32 text-4xl text-center font-black bg-black/20 border-b-2 border-pink-500/50 focus:border-pink-400 outline-none text-white rounded-t-lg"
                autoFocus
              />
          </div>
        </div>
        <div className="flex justify-end gap-2 pt-4 border-t border-pink-900/30">
          <Button variant="secondary" onClick={onClose} className="!bg-pink-900/20 !text-pink-200 hover:!bg-pink-900/40">Cancelar</Button>
          <Button onClick={handleSave} className="!bg-pink-600 hover:!bg-pink-500 !border-none text-white"><SaveIcon /> Guardar</Button>
        </div>
      </div>
    </Modal>
  );
};

export default EditSleepLogModal;
