// components/WorkoutExitModal.tsx
import React from 'react';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { PauseIcon, CheckCircleIcon, XIcon } from './icons';

interface WorkoutExitModalProps {
    isOpen: boolean;
    onClose: () => void;
    onPauseAndExit: () => void;
    onFinish: () => void;
}

const WorkoutExitModal: React.FC<WorkoutExitModalProps> = ({ isOpen, onClose, onPauseAndExit, onFinish }) => {
    return (
        <Modal isOpen={isOpen} onClose={onClose} title="Entrenamiento en Curso">
            <div className="p-2 space-y-4 text-center">
                <p className="text-slate-300">Tienes una sesión activa. ¿Qué te gustaría hacer?</p>
                
                <Button onClick={onPauseAndExit} variant="secondary" className="w-full !py-3">
                    <PauseIcon /> Pausar y Salir
                </Button>
                
                <Button onClick={onFinish} className="w-full !py-3" style={{ backgroundImage: 'var(--success-gradient)' }}>
                    <CheckCircleIcon /> Finalizar Entrenamiento
                </Button>
                
                <Button onClick={onClose} variant="danger" className="w-full !py-3">
                    <XIcon /> Volver al Entrenamiento
                </Button>
            </div>
        </Modal>
    );
};

export default WorkoutExitModal;